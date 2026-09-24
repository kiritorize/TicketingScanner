package com.tkrz.qrtix.data.cloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaManager @Inject constructor(
    private val driveService: GoogleDriveService,
    private val cloudPreferences: CloudPreferences,
    private val driveFolderManager: DriveFolderManager,
    @ApplicationContext private val context: Context
) {
    /**
     * Resolves a media file ID to a local absolute path.
     * If it's already a local path, it returns it as is.
     * If it exists in cache, it returns the cache path.
     * Otherwise, it downloads it from Google Drive and caches it.
     */
    suspend fun getOrDownloadMedia(fileIdOrPath: String?): String? = withContext(Dispatchers.IO) {
        if (fileIdOrPath.isNullOrBlank()) return@withContext null

        // If it already looks like an absolute path, just return it.
        if (fileIdOrPath.startsWith("/")) {
            val file = File(fileIdOrPath)
            if (file.exists()) return@withContext fileIdOrPath
            // If it's a path but the file is missing, try to treat the filename as an ID? 
            // We'll assume it's just a broken path for now.
        }

        // It is likely a Google Drive File ID
        val cacheDir = File(context.cacheDir, "QRTix_Media_Cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val cachedFile = File(cacheDir, "$fileIdOrPath.jpg")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return@withContext cachedFile.absolutePath
        }

        // Try downloading it
        try {
            driveService.downloadFile(fileIdOrPath, cachedFile)
            if (cachedFile.exists() && cachedFile.length() > 0) {
                return@withContext cachedFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If download fails, delete the potentially corrupted empty file
            if (cachedFile.exists()) {
                cachedFile.delete()
            }
        }
        
        return@withContext null
    }

    /**
     * Uploads a local file to the QRTix_Media folder in Google Drive.
     * Returns the Google Drive File ID.
     */
    suspend fun uploadMedia(localFile: File, eventName: String, isLogo: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            if (!localFile.exists()) return@withContext null
            
            val profilesFolderId = cloudPreferences.profilesFolderId ?: return@withContext null
            
            val eventFolderId = driveFolderManager.getOrCreateFolder(eventName, profilesFolderId) ?: return@withContext null
            
            val subfolderName = if (isLogo) "Logo" else "Design"
            val targetFolderId = driveFolderManager.getOrCreateFolder(subfolderName, eventFolderId) ?: return@withContext null
            
            val mimeType = "image/jpeg" // Assuming all media are converted or are jpegs
            val fileId = driveService.uploadFile(localFile.name, mimeType, localFile, targetFolderId)
            
            // Immediately cache this newly uploaded file using its ID so we don't have to download it later
            val cacheDir = File(context.cacheDir, "QRTix_Media_Cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val cachedFile = File(cacheDir, "$fileId.jpg")
            localFile.copyTo(cachedFile, overwrite = true)
            
            return@withContext fileId
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Deletes a media file from Google Drive and removes it from the local cache.
     * Safely handles invalid IDs and ignores exceptions to avoid breaking caller flows.
     */
    suspend fun deleteMedia(fileIdOrPath: String?) = withContext(Dispatchers.IO) {
        if (fileIdOrPath.isNullOrBlank() || fileIdOrPath.startsWith("/")) return@withContext
        try {
            driveService.deleteFile(fileIdOrPath)
            
            val cacheDir = File(context.cacheDir, "QRTix_Media_Cache")
            val cachedFile = File(cacheDir, "$fileIdOrPath.jpg")
            if (cachedFile.exists()) {
                cachedFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Clears all cached media files from the local storage.
     */
    fun clearCache() {
        try {
            val cacheDir = File(context.cacheDir, "QRTix_Media_Cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
