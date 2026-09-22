package com.tkrz.qrtix.data.cloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class UploadState {
    object Idle : UploadState()
    data class Uploading(val progress: Float, val currentFileName: String, val currentCount: Int, val totalFiles: Int) : UploadState()
    data class Completed(val successCount: Int, val failedCount: Int, val failedTasks: List<Pair<UploadTask, String>> = emptyList()) : UploadState()
    data class Error(val message: String) : UploadState()
}

data class UploadTask(
    val file: File,
    val eventName: String,
    val categoryCode: String,
    val mimeType: String = "image/jpeg"
)

enum class UploadResultStatus {
    PENDING, SUCCESS, FAILED
}

data class UploadResult(
    val task: UploadTask,
    val status: UploadResultStatus,
    val errorMessage: String? = null
)

@Singleton
class BackgroundUploadManager @Inject constructor(
    private val driveService: GoogleDriveService,
    private val driveFolderManager: DriveFolderManager,
    private val cloudPreferences: CloudPreferences,
    private val historyLogRepository: com.tkrz.qrtix.data.repository.HistoryLogRepository,
    private val eventPreferences: com.tkrz.qrtix.data.EventPreferences
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState

    private val _uploadResults = MutableStateFlow<List<UploadResult>>(emptyList())
    val uploadResults: StateFlow<List<UploadResult>> = _uploadResults

    private val uploadQueue = mutableListOf<UploadTask>()
    private var isUploading = false

    fun enqueueUploads(tasks: List<UploadTask>) {
        managerScope.launch {
            // Add new tasks to results as PENDING
            val newResults = tasks.map { UploadResult(it, UploadResultStatus.PENDING) }
            _uploadResults.value = _uploadResults.value + newResults
            
            uploadQueue.addAll(tasks)
            if (!isUploading) {
                processQueue()
            }
        }
    }

    private suspend fun processQueue() {
        val tasksToProcess = uploadQueue.toList()
        uploadQueue.clear()

        if (tasksToProcess.isEmpty()) {
            _uploadState.value = UploadState.Idle
            return
        }

        isUploading = true
        var successCount = 0
        var failedCount = 0
        val failedTasks = mutableListOf<Pair<UploadTask, String>>()
        val totalFiles = tasksToProcess.size

        // Ensure Root folder "QRTix" exists
        val rootId = cloudPreferences.folderId ?: driveFolderManager.getOrCreateFolder("QRTix")
        val profilesId = cloudPreferences.profilesFolderId ?: driveFolderManager.getOrCreateFolder("Profiles", rootId)
        
        if (profilesId == null) {
            _uploadState.value = UploadState.Error("Gagal mengakses folder Profiles di Google Drive.")
            isUploading = false
            uploadQueue.clear()
            return
        }
        cloudPreferences.folderId = rootId
        cloudPreferences.profilesFolderId = profilesId

        for (i in 0 until totalFiles) {
            val task = tasksToProcess[i]
            _uploadState.value = UploadState.Uploading(
                progress = i.toFloat() / totalFiles.toFloat(),
                currentFileName = task.file.name,
                currentCount = i + 1,
                totalFiles = totalFiles
            )

            var uploadSuccess = false
            var lastErrorMsg = ""
            var retries = 3
            var retryDelay = 1000L

            while (retries > 0 && !uploadSuccess) {
                var currentParent = profilesId
                try {
                    // 1. QRTix / Profiles / [Event Name]
                    val eventFolderId = driveFolderManager.getOrCreateFolder(task.eventName, currentParent)
                    if (eventFolderId == null) {
                        driveFolderManager.invalidateKey("${currentParent}_${task.eventName}")
                        throw Exception("Gagal membuat folder Event")
                    }
                    currentParent = eventFolderId

                    // 2. QRTix / Profiles / [Event Name] / QR_Images
                    val qrImagesFolderId = driveFolderManager.getOrCreateFolder("QR_Images", currentParent)
                    if (qrImagesFolderId == null) {
                        driveFolderManager.invalidateKey("${currentParent}_QR_Images")
                        throw Exception("Gagal membuat folder QR_Images")
                    }
                    currentParent = qrImagesFolderId

                    // 3. QRTix / [Event Name] / QR Images / [Category Code]
                    val categoryFolderId = driveFolderManager.getOrCreateFolder(task.categoryCode, currentParent)
                    if (categoryFolderId == null) {
                        driveFolderManager.invalidateKey("${currentParent}_${task.categoryCode}")
                        throw Exception("Gagal membuat folder Kategori")
                    }

                    // Upload File
                    val fileId = driveService.uploadFile(
                        name = task.file.name,
                        mimeType = task.mimeType,
                        file = task.file,
                        folderId = categoryFolderId
                    )

                    if (fileId.isNotBlank()) {
                        uploadSuccess = true
                    } else {
                        throw Exception("Upload failed, empty file ID returned")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    lastErrorMsg = e.message ?: "Unknown error"
                    
                    retries--
                    if (retries > 0) {
                        kotlinx.coroutines.delay(retryDelay)
                        retryDelay *= 2
                    }
                }
            }

            if (uploadSuccess) {
                successCount++
                updateResult(task, UploadResultStatus.SUCCESS, null)
            } else {
                failedCount++
                failedTasks.add(Pair(task, lastErrorMsg))
                updateResult(task, UploadResultStatus.FAILED, lastErrorMsg)
            }
        }

        isUploading = false
        if (uploadQueue.isNotEmpty()) {
            processQueue()
        } else {
            _uploadState.value = UploadState.Completed(successCount, failedCount, failedTasks)
            if (successCount > 0 || failedCount > 0) {
                historyLogRepository.insertLog(com.tkrz.qrtix.data.HistoryLog(
                    eventId = eventPreferences.activeEventId.value,
                    action = "Backup",
                    description = "Pencadangan ${successCount + failedCount} gambar QR ke Google Drive",
                    details = "Berhasil: $successCount, Gagal: $failedCount"
                ))
            }
        }
    }

    private fun updateResult(task: UploadTask, status: UploadResultStatus, errorMessage: String?) {
        val currentList = _uploadResults.value.toMutableList()
        val index = currentList.indexOfFirst { it.task == task }
        if (index != -1) {
            currentList[index] = UploadResult(task, status, errorMessage)
        } else {
            currentList.add(UploadResult(task, status, errorMessage))
        }
        _uploadResults.value = currentList
    }

    fun retryFailedUploads(failedTasks: List<UploadTask>) {
        // Remove them from results so they get re-added as PENDING
        val currentList = _uploadResults.value.toMutableList()
        currentList.removeAll { it.task in failedTasks }
        _uploadResults.value = currentList
        
        enqueueUploads(failedTasks)
    }

    fun retryAllFailed() {
        val failed = _uploadResults.value.filter { it.status == UploadResultStatus.FAILED }.map { it.task }
        retryFailedUploads(failed)
    }

    fun resetState() {
        if (!isUploading) {
            _uploadState.value = UploadState.Idle
            _uploadResults.value = emptyList()
        }
    }
}
