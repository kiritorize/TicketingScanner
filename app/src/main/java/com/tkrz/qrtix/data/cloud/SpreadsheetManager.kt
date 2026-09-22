package com.tkrz.qrtix.data.cloud

import com.google.api.services.sheets.v4.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpreadsheetManager @Inject constructor(
    private val cloudPreferences: CloudPreferences,
    private val driveService: GoogleDriveService,
    private val sheetsService: GoogleSheetsService
) {
    suspend fun initializeSpreadsheet(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Ensure QRTix folder exists
            var folderId = cloudPreferences.folderId
            if (folderId == null) {
                folderId = driveService.findFileByName("QRTix", "application/vnd.google-apps.folder")
                if (folderId == null) {
                    folderId = driveService.createFolder("QRTix")
                }
                cloudPreferences.folderId = folderId
            }

            // 1.5. Ensure System and Profiles folders exist
            var systemFolderId = cloudPreferences.systemFolderId
            if (systemFolderId == null) {
                systemFolderId = driveService.findFileByName("System", "application/vnd.google-apps.folder", folderId)
                if (systemFolderId == null) {
                    systemFolderId = driveService.createFolder("System", folderId)
                }
                cloudPreferences.systemFolderId = systemFolderId
            }

            var profilesFolderId = cloudPreferences.profilesFolderId
            if (profilesFolderId == null) {
                profilesFolderId = driveService.findFileByName("Profiles", "application/vnd.google-apps.folder", folderId)
                if (profilesFolderId == null) {
                    profilesFolderId = driveService.createFolder("Profiles", folderId)
                }
                cloudPreferences.profilesFolderId = profilesFolderId
            }

            // 2. Ensure Spreadsheet exists
            var spreadsheetId = cloudPreferences.spreadsheetId
            var needsMigration = false

            if (spreadsheetId != null) {
                // Verify it still exists and is accessible
                try {
                    sheetsService.getSpreadsheet(spreadsheetId)
                } catch (e: Exception) {
                    // Inaccessible or deleted, reset
                    cloudPreferences.spreadsheetId = null
                    spreadsheetId = null
                }
            }

            if (spreadsheetId == null) {
                // Try to find it in System folder first
                spreadsheetId = driveService.findFileByName("QRTix_Data", "application/vnd.google-apps.spreadsheet", systemFolderId)
                
                if (spreadsheetId == null) {
                    // Try to find it in old root folder (QRTix) for migration
                    spreadsheetId = driveService.findFileByName("QRTix_Data", "application/vnd.google-apps.spreadsheet", folderId)
                    if (spreadsheetId != null) {
                        needsMigration = true
                    }
                }
            }

            if (spreadsheetId == null) {
                // Create new spreadsheet in System
                spreadsheetId = driveService.createSpreadsheetFile("QRTix_Data", systemFolderId)
                initializeSchema(spreadsheetId)
            } else if (needsMigration) {
                // Migrate from root to System
                driveService.moveFile(spreadsheetId, systemFolderId)
                
                // Migrate old event folders from root to Profiles
                val oldFolders = driveService.listFoldersInFolder(folderId)
                for (oldFolder in oldFolders) {
                    if (oldFolder.name != "System" && oldFolder.name != "Profiles" && oldFolder.name != "QRTix_Media") {
                        driveService.moveFile(oldFolder.id, profilesFolderId)
                    }
                }
            }

            cloudPreferences.spreadsheetId = spreadsheetId
            Result.success(spreadsheetId)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun initializeSchema(spreadsheetId: String) {
        val spreadsheet = sheetsService.getSpreadsheet(spreadsheetId)
        val defaultSheetId = spreadsheet.sheets.firstOrNull()?.properties?.sheetId

        val requests = mutableListOf<Request>()

        // 1. Add sheets
        val eventsSheetId = 101
        val ticketsSheetId = 102
        val historyLogsSheetId = 103
        val categoriesSheetId = 104

        requests.add(createAddSheetRequest("Events", eventsSheetId))
        requests.add(createAddSheetRequest("Tickets", ticketsSheetId))
        requests.add(createAddSheetRequest("HistoryLogs", historyLogsSheetId))
        requests.add(createAddSheetRequest("Categories", categoriesSheetId))

        // 2. Delete default sheet if exists
        if (defaultSheetId != null) {
            requests.add(Request().setDeleteSheet(DeleteSheetRequest().setSheetId(defaultSheetId)))
        }

        // Execute batch to create sheets
        sheetsService.batchUpdate(spreadsheetId, requests)

        // 3. Setup headers for each sheet
        val eventsHeaders = listOf("id", "name", "createdAt", "lastAccessedAt", "logoFileId", "eventCode", "bgFileId", "qrX", "qrY", "qrScale", "qrRotation", "distributionSheetId")
        val ticketsHeaders = listOf("id", "qrContent", "ticketType", "isScanned", "createdAt", "scannedAt", "isModified", "eventId")
        val historyLogsHeaders = listOf("id", "eventId", "action", "description", "details", "timestamp", "isUndone")
        val categoriesHeaders = listOf("id", "eventId", "categoryName", "categoryCode")

        sheetsService.appendRow(spreadsheetId, "Events!A1", eventsHeaders)
        sheetsService.appendRow(spreadsheetId, "Tickets!A1", ticketsHeaders)
        sheetsService.appendRow(spreadsheetId, "HistoryLogs!A1", historyLogsHeaders)
        sheetsService.appendRow(spreadsheetId, "Categories!A1", categoriesHeaders)

        // 4. Freeze header rows
        val freezeRequests = listOf(
            createFreezeHeaderRequest(eventsSheetId),
            createFreezeHeaderRequest(ticketsSheetId),
            createFreezeHeaderRequest(historyLogsSheetId),
            createFreezeHeaderRequest(categoriesSheetId)
        )
        sheetsService.batchUpdate(spreadsheetId, freezeRequests)
    }

    private fun createAddSheetRequest(title: String, sheetId: Int): Request {
        return Request().setAddSheet(
            AddSheetRequest().setProperties(
                SheetProperties().setTitle(title).setSheetId(sheetId)
            )
        )
    }

    private fun createFreezeHeaderRequest(sheetId: Int): Request {
        return Request().setUpdateSheetProperties(
            UpdateSheetPropertiesRequest()
                .setProperties(
                    SheetProperties()
                        .setSheetId(sheetId)
                        .setGridProperties(GridProperties().setFrozenRowCount(1))
                )
                .setFields("gridProperties.frozenRowCount")
        )
    }
}
