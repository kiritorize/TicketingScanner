package com.tkrz.qrtix.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class DriveFolderManager @Inject constructor(
    private val driveService: GoogleDriveService
) {
    // Cache map: Key = "parentFolderId_folderName", Value = folderId
    private val folderCache = ConcurrentHashMap<String, String>()

    suspend fun getOrCreateFolder(folderName: String, parentFolderId: String? = null): String? = withContext(Dispatchers.IO) {
        val cacheKey = "${parentFolderId ?: "root"}_$folderName"
        
        // 1. Check Cache
        folderCache[cacheKey]?.let { return@withContext it }

        // 2. Check Drive
        try {
            val existingId = driveService.findFileByName(
                name = folderName,
                mimeType = "application/vnd.google-apps.folder",
                parentFolderId = parentFolderId
            )
            if (existingId != null) {
                folderCache[cacheKey] = existingId
                return@withContext existingId
            }

            // 3. Create if not exists
            val newId = driveService.createFolder(folderName, parentFolderId)
            folderCache[cacheKey] = newId
            return@withContext newId
        } catch (e: Exception) {
            e.printStackTrace()
            // In case of 404 on parent, or any other error, return null so caller knows it failed
            return@withContext null
        }
    }

    suspend fun renameFolder(folderId: String, newName: String, parentFolderId: String? = null): Boolean {
        val success = driveService.renameFile(folderId, newName)
        if (success) {
            // Update cache: remove old cache entries that point to this folderId
            // And add the new cache key
            val keysToRemove = folderCache.entries.filter { it.value == folderId }.map { it.key }
            keysToRemove.forEach { folderCache.remove(it) }
            
            val newCacheKey = "${parentFolderId ?: "root"}_$newName"
            folderCache[newCacheKey] = folderId
        }
        return success
    }

    /**
     * Call this when a 404 is encountered during upload to force re-creation of folders.
     */
    fun clearCache() {
        folderCache.clear()
    }
}
