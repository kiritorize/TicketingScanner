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
    data class Completed(val successCount: Int, val failedCount: Int) : UploadState()
    data class Error(val message: String) : UploadState()
}

data class UploadTask(
    val file: File,
    val eventName: String,
    val categoryCode: String,
    val mimeType: String = "image/jpeg"
)

@Singleton
class BackgroundUploadManager @Inject constructor(
    private val driveService: GoogleDriveService,
    private val driveFolderManager: DriveFolderManager,
    private val cloudPreferences: CloudPreferences
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState

    private val uploadQueue = mutableListOf<UploadTask>()
    private var isUploading = false

    fun enqueueUploads(tasks: List<UploadTask>) {
        managerScope.launch {
            uploadQueue.addAll(tasks)
            if (!isUploading) {
                processQueue()
            }
        }
    }

    private suspend fun processQueue() {
        if (uploadQueue.isEmpty()) {
            _uploadState.value = UploadState.Idle
            return
        }

        isUploading = true
        var successCount = 0
        var failedCount = 0
        val totalFiles = uploadQueue.size

        // Ensure Root folder "QRTix" exists
        val rootId = cloudPreferences.folderId ?: driveFolderManager.getOrCreateFolder("QRTix")
        if (rootId == null) {
            _uploadState.value = UploadState.Error("Gagal mengakses folder QRTix di Google Drive.")
            isUploading = false
            uploadQueue.clear()
            return
        }
        cloudPreferences.folderId = rootId

        for (i in 0 until totalFiles) {
            val task = uploadQueue[i]
            _uploadState.value = UploadState.Uploading(
                progress = i.toFloat() / totalFiles.toFloat(),
                currentFileName = task.file.name,
                currentCount = i + 1,
                totalFiles = totalFiles
            )

            try {
                // 1. QRTix / [Event Name]
                val eventFolderId = driveFolderManager.getOrCreateFolder(task.eventName, rootId)
                if (eventFolderId == null) throw Exception("Gagal membuat folder Event")

                // 2. QRTix / [Event Name] / QR Images
                val qrImagesFolderId = driveFolderManager.getOrCreateFolder("QR Images", eventFolderId)
                if (qrImagesFolderId == null) throw Exception("Gagal membuat folder QR Images")

                // 3. QRTix / [Event Name] / QR Images / [Category Code]
                val categoryFolderId = driveFolderManager.getOrCreateFolder(task.categoryCode, qrImagesFolderId)
                if (categoryFolderId == null) throw Exception("Gagal membuat folder Kategori")

                // Upload File
                val fileId = driveService.uploadFile(
                    name = task.file.name,
                    mimeType = task.mimeType,
                    file = task.file,
                    folderId = categoryFolderId
                )

                if (fileId.isNotBlank()) {
                    successCount++
                } else {
                    failedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // If we get an error (like 404 because parent deleted), clear cache for next files
                if (e.message?.contains("404") == true || e.message?.contains("not found") == true) {
                    driveFolderManager.clearCache()
                }
                failedCount++
            }
        }

        uploadQueue.clear()
        isUploading = false
        _uploadState.value = UploadState.Completed(successCount, failedCount)
    }

    fun resetState() {
        if (!isUploading) {
            _uploadState.value = UploadState.Idle
        }
    }
}
