package com.tkrz.qrtix.data.cloud

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveService @Inject constructor(
    private val credentialManager: GoogleCredentialManager
) {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

    val driveService: Drive
        get() = Drive.Builder(httpTransport, jsonFactory, credentialManager.getCredential())
            .setApplicationName("QRTix")
            .build()

    suspend fun uploadFile(name: String, mimeType: String, file: File, folderId: String? = null): String = withContext(Dispatchers.IO) {
        val fileMetadata = com.google.api.services.drive.model.File().apply {
            this.name = name
            if (folderId != null) {
                this.parents = listOf(folderId)
            }
        }
        val mediaContent = FileContent(mimeType, file)
        val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
            .setFields("id")
            .execute()
        uploadedFile.id
    }

    suspend fun downloadFile(fileId: String, destFile: File) = withContext(Dispatchers.IO) {
        FileOutputStream(destFile).use { outputStream ->
            driveService.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream)
        }
    }

    suspend fun createFolder(name: String, parentFolderId: String? = null): String = withContext(Dispatchers.IO) {
        val fileMetadata = com.google.api.services.drive.model.File().apply {
            this.name = name
            this.mimeType = "application/vnd.google-apps.folder"
            if (parentFolderId != null) {
                this.parents = listOf(parentFolderId)
            }
        }
        val folder = driveService.files().create(fileMetadata)
            .setFields("id")
            .execute()
        folder.id
    }

    suspend fun findFileByName(name: String, mimeType: String, parentFolderId: String? = null): String? = withContext(Dispatchers.IO) {
        var query = "name = '$name' and mimeType = '$mimeType' and trashed = false"
        if (parentFolderId != null) {
            query += " and '$parentFolderId' in parents"
        }
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
        
        result.files?.firstOrNull()?.id
    }

    suspend fun createSpreadsheetFile(name: String, parentFolderId: String? = null): String = withContext(Dispatchers.IO) {
        val fileMetadata = com.google.api.services.drive.model.File().apply {
            this.name = name
            this.mimeType = "application/vnd.google-apps.spreadsheet"
            if (parentFolderId != null) {
                this.parents = listOf(parentFolderId)
            }
        }
        val file = driveService.files().create(fileMetadata)
            .setFields("id")
            .execute()
        file.id
    }

    suspend fun renameFile(fileId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                this.name = newName
            }
            driveService.files().update(fileId, fileMetadata).execute()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun moveFile(fileId: String, newParentId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Retrieve the existing parents to remove
            val file = driveService.files().get(fileId)
                .setFields("parents")
                .execute()
            val previousParents = file.parents?.joinToString(",") ?: ""
            
            // Move the file to the new folder
            driveService.files().update(fileId, null)
                .setAddParents(newParentId)
                .setRemoveParents(previousParents)
                .setFields("id, parents")
                .execute()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun listFoldersInFolder(parentFolderId: String): List<com.google.api.services.drive.model.File> = withContext(Dispatchers.IO) {
        try {
            val query = "mimeType = 'application/vnd.google-apps.folder' and trashed = false and '$parentFolderId' in parents"
            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            result.files ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun updateFileContent(fileId: String, mimeType: String, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val mediaContent = FileContent(mimeType, file)
            driveService.files().update(fileId, null, mediaContent).execute()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
