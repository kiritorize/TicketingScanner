package com.tkrz.qrtix.data.cloud

import com.tkrz.qrtix.data.AuthPreferences
import com.google.api.services.sheets.v4.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpreadsheetManager @Inject constructor(
    private val cloudPreferences: CloudPreferences,
    private val driveService: GoogleDriveService,
    private val sheetsService: GoogleSheetsService,
    private val authPreferences: AuthPreferences,
    private val driveFolderManager: DriveFolderManager
) {
    suspend fun initializeSpreadsheet(accountEmail: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            // ── Step 0: Set active email ──
            val email = accountEmail ?: authPreferences.userEmail
                ?: return@withContext Result.failure(Exception("No authenticated user"))
            cloudPreferences.activeEmail = email
            driveFolderManager.clearCache()

            // ── Step 1: Run one-time migration (if needed) ──
            if (!cloudPreferences.hasMigratedForEmail(email)) {
                migrateOldStructure(email)
            }

            // ── Step 2: Ensure QRTix/ root folder exists ──
            var rootFolderId = cloudPreferences.folderId
            if (rootFolderId == null) {
                rootFolderId = driveService.findFileByName("QRTix", "application/vnd.google-apps.folder")
                if (rootFolderId == null) {
                    rootFolderId = driveService.createFolder("QRTix")
                }
                cloudPreferences.folderId = rootFolderId
            }

            // ── Step 3: Ensure QRTix/{email}/ account folder exists ──
            var accountRootId = cloudPreferences.accountRootFolderId
            if (accountRootId == null) {
                accountRootId = driveService.findFileByName(email, "application/vnd.google-apps.folder", rootFolderId)
                if (accountRootId == null) {
                    accountRootId = driveService.createFolder(email, rootFolderId)
                }
                cloudPreferences.accountRootFolderId = accountRootId
            }

            // ── Step 4: Ensure System/ and Profiles/ under account folder ──
            var systemFolderId = cloudPreferences.systemFolderId
            if (systemFolderId == null) {
                systemFolderId = driveService.findFileByName("System", "application/vnd.google-apps.folder", accountRootId)
                if (systemFolderId == null) {
                    systemFolderId = driveService.createFolder("System", accountRootId)
                }
                cloudPreferences.systemFolderId = systemFolderId
            }

            var profilesFolderId = cloudPreferences.profilesFolderId
            if (profilesFolderId == null) {
                profilesFolderId = driveService.findFileByName("Profiles", "application/vnd.google-apps.folder", accountRootId)
                if (profilesFolderId == null) {
                    profilesFolderId = driveService.createFolder("Profiles", accountRootId)
                }
                cloudPreferences.profilesFolderId = profilesFolderId
            }

            // ── Step 5: Ensure Spreadsheet exists ──
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
                    // Try to find it in old account root folder for edge-case migration
                    spreadsheetId = driveService.findFileByName("QRTix_Data", "application/vnd.google-apps.spreadsheet", accountRootId)
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
                // Migrate spreadsheet from account root to System
                driveService.moveFile(spreadsheetId, systemFolderId)
            }

            cloudPreferences.spreadsheetId = spreadsheetId
            Result.success(spreadsheetId)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Migrates the old flat structure (QRTix/System/, QRTix/Profiles/)
     * into the new per-account structure (QRTix/{email}/System/, QRTix/{email}/Profiles/).
     *
     * This runs ONCE per email, automatically and invisibly.
     * Non-fatal: if migration fails, the main flow creates fresh structure.
     */
    private suspend fun migrateOldStructure(email: String) {
        try {
            val oldFolderId = cloudPreferences.getOldUnkeyedFolderId() ?: run {
                // No old data at all — either fresh install or already cleaned
                cloudPreferences.setMigratedForEmail(email)
                return
            }
            val oldSystemId = cloudPreferences.getOldUnkeyedSystemFolderId()
            val oldProfilesId = cloudPreferences.getOldUnkeyedProfilesFolderId()
            val oldSpreadsheetId = cloudPreferences.getOldUnkeyedSpreadsheetId()
            val oldMediaFolderId = cloudPreferences.getOldUnkeyedMediaFolderId()

            // Verify old root exists in Drive
            val verifiedRootId = driveService.findFileByName("QRTix", "application/vnd.google-apps.folder")
            if (verifiedRootId == null) {
                // Root doesn't exist in Drive — nothing to migrate
                cloudPreferences.clearOldUnkeyedValues()
                cloudPreferences.setMigratedForEmail(email)
                return
            }

            // Check if System/ or Profiles/ exist as direct children of QRTix/ (old structure)
            val directSystemId = oldSystemId
                ?: driveService.findFileByName("System", "application/vnd.google-apps.folder", verifiedRootId)
            val directProfilesId = oldProfilesId
                ?: driveService.findFileByName("Profiles", "application/vnd.google-apps.folder", verifiedRootId)

            if (directSystemId == null && directProfilesId == null) {
                // No old structure to migrate
                cloudPreferences.clearOldUnkeyedValues()
                cloudPreferences.setMigratedForEmail(email)
                return
            }

            // Create QRTix/{email}/ folder
            val accountRootId = driveService.findFileByName(email, "application/vnd.google-apps.folder", verifiedRootId)
                ?: driveService.createFolder(email, verifiedRootId)

            // Move System/ into QRTix/{email}/
            if (directSystemId != null) {
                driveService.moveFile(directSystemId, accountRootId)
            }

            // Move Profiles/ into QRTix/{email}/
            if (directProfilesId != null) {
                driveService.moveFile(directProfilesId, accountRootId)
            }

            // Save migrated IDs under email-keyed preferences
            // (activeEmail is already set by caller before calling this)
            cloudPreferences.folderId = verifiedRootId
            cloudPreferences.accountRootFolderId = accountRootId
            if (directSystemId != null) cloudPreferences.systemFolderId = directSystemId
            if (directProfilesId != null) cloudPreferences.profilesFolderId = directProfilesId
            if (oldSpreadsheetId != null) cloudPreferences.spreadsheetId = oldSpreadsheetId
            if (oldMediaFolderId != null) cloudPreferences.mediaFolderId = oldMediaFolderId

            // Clean up old un-keyed values
            cloudPreferences.clearOldUnkeyedValues()
            cloudPreferences.setMigratedForEmail(email)

        } catch (e: Exception) {
            // Migration failure is non-fatal — the main flow will create new structure
            e.printStackTrace()
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
