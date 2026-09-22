package com.tkrz.qrtix.data.repository

import com.google.api.services.sheets.v4.model.DeleteDimensionRequest
import com.google.api.services.sheets.v4.model.DimensionRange
import com.google.api.services.sheets.v4.model.Request
import com.tkrz.qrtix.data.HistoryLog
import com.tkrz.qrtix.data.HistoryLogDao
import com.tkrz.qrtix.data.cloud.CloudPreferences
import com.tkrz.qrtix.data.cloud.GoogleSheetsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class HistoryLogRepository @Inject constructor(
    private val historyLogDao: HistoryLogDao,
    private val sheetsService: GoogleSheetsService,
    private val cloudPreferences: CloudPreferences
) {
    suspend fun getLogsForEvent(eventId: Long): List<HistoryLog> = historyLogDao.getLogsForEvent(eventId)

    suspend fun syncLogsFromCloud() = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: return@withContext
        val data = sheetsService.readRange(spreadsheetId, "HistoryLogs!A2:G")
        
        val logs = mutableListOf<HistoryLog>()
        if (data != null) {
            for (row in data) {
                if (row.isEmpty()) continue
                try {
                    val id = row.getOrNull(0)?.toString()?.toIntOrNull() ?: continue
                    val eventId = row.getOrNull(1)?.toString()?.toLongOrNull() ?: continue
                    val action = row.getOrNull(2)?.toString() ?: ""
                    val description = row.getOrNull(3)?.toString() ?: ""
                    val details = row.getOrNull(4)?.toString() ?: ""
                    val timestamp = row.getOrNull(5)?.toString()?.toLongOrNull() ?: System.currentTimeMillis()
                    val isUndone = row.getOrNull(6)?.toString()?.toBooleanStrictOrNull() ?: false
                    
                    logs.add(HistoryLog(id, eventId, action, description, details, timestamp, isUndone))
                } catch (e: Exception) {
                    // Skip malformed row
                }
            }
        }

        historyLogDao.deleteAllLogs()
        for (log in logs) {
            historyLogDao.insertLog(log)
        }
    }

    suspend fun insertLog(log: HistoryLog): Long = withContext(Dispatchers.IO) {
        var newLog = log
        try {
            val spreadsheetId = cloudPreferences.spreadsheetId
            if (spreadsheetId != null) {
                // Find next ID
                val data = sheetsService.readRange(spreadsheetId, "HistoryLogs!A2:A")
                var nextId = 1
                if (data != null) {
                    val maxId = data.mapNotNull { it.firstOrNull()?.toString()?.toIntOrNull() }.maxOrNull()
                    if (maxId != null) {
                        nextId = maxId + 1
                    }
                }
                newLog = log.copy(id = if (log.id == 0) nextId else log.id)
                
                val rowData = listOf(
                    newLog.id.toString(),
                    newLog.eventId.toString(),
                    newLog.action,
                    newLog.description,
                    newLog.details,
                    newLog.timestamp.toString(),
                    newLog.isUndone.toString()
                )
                sheetsService.appendRow(spreadsheetId, "HistoryLogs!A:G", rowData)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // It's acceptable to log locally only if sheet fails
        }
        
        historyLogDao.insertLog(newLog)
        newLog.id.toLong()
    }

    suspend fun markAsUndone(logId: Int) = withContext(Dispatchers.IO) {
        try {
            val spreadsheetId = cloudPreferences.spreadsheetId
            if (spreadsheetId != null) {
                val data = sheetsService.readRange(spreadsheetId, "HistoryLogs!A1:A")
                var rowIndex = -1
                if (data != null) {
                    for (i in data.indices) {
                        if (data[i].firstOrNull()?.toString() == logId.toString()) {
                            rowIndex = i + 1
                            break
                        }
                    }
                }
                if (rowIndex != -1) {
                    val rowData = listOf("true")
                    sheetsService.updateRow(spreadsheetId, "HistoryLogs!G$rowIndex:G$rowIndex", rowData)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        historyLogDao.markAsUndone(logId)
    }

    suspend fun deleteLogsForEvent(eventId: Long) = withContext(Dispatchers.IO) {
        try {
            val spreadsheetId = cloudPreferences.spreadsheetId
            if (spreadsheetId != null) {
                val sheetId = sheetsService.getSheetIdByName(spreadsheetId, "HistoryLogs")
                if (sheetId != null) {
                    val data = sheetsService.readRange(spreadsheetId, "HistoryLogs!A1:B")
                    val indicesToDelete = mutableListOf<Int>()
                    if (data != null) {
                        for (i in data.indices) {
                            if (data[i].size > 1 && data[i][1].toString() == eventId.toString()) {
                                indicesToDelete.add(i)
                            }
                        }
                    }
                    
                    if (indicesToDelete.isNotEmpty()) {
                        indicesToDelete.sortDescending()
                        val requests = indicesToDelete.map { index ->
                            Request().setDeleteDimension(
                                DeleteDimensionRequest().setRange(
                                    DimensionRange()
                                        .setSheetId(sheetId)
                                        .setDimension("ROWS")
                                        .setStartIndex(index)
                                        .setEndIndex(index + 1)
                                )
                            )
                        }
                        sheetsService.batchUpdate(spreadsheetId, requests)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        historyLogDao.deleteLogsForEvent(eventId)
    }
}
