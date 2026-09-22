package com.tkrz.qrtix.data.cloud

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tkrz.qrtix.data.Event
import com.tkrz.qrtix.data.TicketDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventBackupManager @Inject constructor(
    private val driveService: GoogleDriveService,
    private val driveFolderManager: DriveFolderManager,
    private val cloudPreferences: CloudPreferences,
    private val ticketDao: TicketDao,
    @ApplicationContext private val context: Context
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun backupEventToCloud(event: Event, categories: List<String>) = withContext(Dispatchers.IO) {
        try {
            val profilesFolderId = cloudPreferences.profilesFolderId ?: return@withContext
            
            val eventFolderId = driveFolderManager.getOrCreateFolder(event.name, profilesFolderId) ?: return@withContext
            
            val count = ticketDao.getTicketCount(event.id)
            val backupData = mapOf(
                "eventName" to event.name,
                "eventCode" to event.eventCode,
                "createdAt" to event.createdAt,
                "ticketCount" to count,
                "categories" to categories,
                "lastSyncAt" to System.currentTimeMillis()
            )
            
            val jsonString = gson.toJson(backupData)
            
            val cacheDir = File(context.cacheDir, "QRTix_Backups")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val backupFile = File(cacheDir, "QRTix.data")
            backupFile.writeText(jsonString)
            
            // Check if file already exists in cloud
            val existingFileId = driveService.findFileByName("QRTix.data", "application/json", eventFolderId)
            
            if (existingFileId != null) {
                driveService.updateFileContent(existingFileId, "application/json", backupFile)
            } else {
                driveService.uploadFile("QRTix.data", "application/json", backupFile, eventFolderId)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
