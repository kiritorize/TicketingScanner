package com.tkrz.qrtix.data.repository

import com.tkrz.qrtix.data.Event
import com.tkrz.qrtix.data.EventDao
import com.tkrz.qrtix.data.cloud.CloudPreferences
import com.tkrz.qrtix.data.cloud.GoogleSheetsService
import com.tkrz.qrtix.data.EventPreferences
import com.tkrz.qrtix.data.cloud.EventBackupManager
import com.tkrz.qrtix.data.CategoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val sheetsService: GoogleSheetsService,
    private val cloudPreferences: CloudPreferences,
    private val eventPreferences: EventPreferences,
    private val eventBackupManager: EventBackupManager,
    private val categoryDao: CategoryDao
) {
    fun getAllEvents(): Flow<List<Event>> = eventDao.getAllEvents()

    suspend fun getEventById(id: Long): Event? = eventDao.getEventById(id)

    suspend fun syncEventsFromCloud() = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: throw Exception("Cloud database belum siap")
        val data = sheetsService.readRange(spreadsheetId, "Events!A2:L")
        
        val events = mutableListOf<Event>()
        if (data != null) {
            for (row in data) {
                if (row.isEmpty()) continue
                try {
                    val id = row.getOrNull(0)?.toString()?.toLongOrNull() ?: continue
                    val name = row.getOrNull(1)?.toString() ?: ""
                    val createdAt = row.getOrNull(2)?.toString()?.toLongOrNull() ?: System.currentTimeMillis()
                    val lastAccessedAt = row.getOrNull(3)?.toString()?.toLongOrNull() ?: System.currentTimeMillis()
                    val logoPath = row.getOrNull(4)?.toString()?.takeIf { it.isNotBlank() }
                    val eventCode = row.getOrNull(5)?.toString() ?: "EVNT1"
                    val bgPath = row.getOrNull(6)?.toString()?.takeIf { it.isNotBlank() }
                    val qrX = row.getOrNull(7)?.toString()?.toFloatOrNull() ?: 0f
                    val qrY = row.getOrNull(8)?.toString()?.toFloatOrNull() ?: 0f
                    val qrScale = row.getOrNull(9)?.toString()?.toFloatOrNull() ?: 1f
                    val qrRotation = row.getOrNull(10)?.toString()?.toFloatOrNull() ?: 0f
                    val distributionSheetId = row.getOrNull(11)?.toString()?.takeIf { it.isNotBlank() }
                    
                    events.add(Event(id, name, createdAt, lastAccessedAt, logoPath, eventCode, bgPath, qrX, qrY, qrScale, qrRotation, distributionSheetId))
                } catch (e: Exception) {
                    // Skip malformed row
                }
            }
        }

        if (events.isEmpty()) {
            Log.w("EventRepository", "Cloud returned 0 events, skipping destructive sync")
            return@withContext
        } else {
            val localEvents = eventDao.getEventsList()
            val cloudEventIds = events.map { it.id }.toSet()
            
            // Delete events in local but not in cloud
            for (local in localEvents) {
                if (local.id !in cloudEventIds && local.id != 1L) {
                    eventDao.deleteEvent(local.id)
                }
            }
            
            // Insert / Update events from cloud
            for (event in events) {
                eventDao.insertEvent(event)
            }
            
            // Validate activeEventId
            val activeId = eventPreferences.getActiveEventId()
            val isActiveStillValid = events.any { it.id == activeId }
            if (!isActiveStillValid) {
                val fallbackId = events.firstOrNull()?.id ?: 1L
                eventPreferences.setActiveEventId(fallbackId)
            }
        }
    }

    suspend fun insertEvent(event: Event): Long = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: throw Exception("Cloud database belum siap")
        
        val data = sheetsService.readRange(spreadsheetId, "Events!A2:A")
        var nextId = 1L
        if (data != null) {
            val maxId = data.mapNotNull { it.firstOrNull()?.toString()?.toLongOrNull() }.maxOrNull()
            if (maxId != null) {
                nextId = maxId + 1
            }
        }
        
        val newEvent = event.copy(id = if (event.id == 0L) nextId else event.id)
        
        val rowData = listOf(
            newEvent.id.toString(),
            newEvent.name,
            newEvent.createdAt.toString(),
            newEvent.lastAccessedAt.toString(),
            newEvent.logoPath ?: "",
            newEvent.eventCode,
            newEvent.bgPath ?: "",
            newEvent.qrX.toString(),
            newEvent.qrY.toString(),
            newEvent.qrScale.toString(),
            newEvent.qrRotation.toString(),
            newEvent.distributionSheetId ?: ""
        )
        
        sheetsService.appendRow(spreadsheetId, "Events!A:L", rowData)
        eventDao.insertEvent(newEvent)
        
        CoroutineScope(Dispatchers.IO).launch {
            val categories = categoryDao.getCategoriesForEvent(newEvent.id).map { it.categoryName }
            eventBackupManager.backupEventToCloud(newEvent, categories)
        }
        
        newEvent.id
    }

    suspend fun updateEvent(event: Event) = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: throw Exception("Cloud database belum siap")
        val data = sheetsService.readRange(spreadsheetId, "Events!A1:A")
        
        var rowIndex = -1
        if (data != null) {
            for (i in data.indices) {
                if (data[i].firstOrNull()?.toString() == event.id.toString()) {
                    rowIndex = i + 1 
                    break
                }
            }
        }
        
        if (rowIndex != -1) {
            val rowData = listOf(
                event.id.toString(),
                event.name,
                event.createdAt.toString(),
                event.lastAccessedAt.toString(),
                event.logoPath ?: "",
                event.eventCode,
                event.bgPath ?: "",
                event.qrX.toString(),
                event.qrY.toString(),
                event.qrScale.toString(),
                event.qrRotation.toString(),
                event.distributionSheetId ?: ""
            )
            sheetsService.updateRow(spreadsheetId, "Events!A$rowIndex:L$rowIndex", rowData)
        }
        
        eventDao.updateEvent(event)
        
        CoroutineScope(Dispatchers.IO).launch {
            val categories = categoryDao.getCategoriesForEvent(event.id).map { it.categoryName }
            eventBackupManager.backupEventToCloud(event, categories)
        }
    }

    suspend fun deleteEvent(id: Long) = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: throw Exception("Cloud database belum siap")
        val sheetId = sheetsService.getSheetIdByName(spreadsheetId, "Events") ?: throw Exception("Events sheet not found")
        val data = sheetsService.readRange(spreadsheetId, "Events!A1:A")
        
        var rowIndex = -1
        if (data != null) {
            for (i in data.indices) {
                if (data[i].firstOrNull()?.toString() == id.toString()) {
                    rowIndex = i 
                    break
                }
            }
        }
        
        if (rowIndex != -1) {
            sheetsService.deleteRow(spreadsheetId, sheetId, rowIndex)
        }
        
        eventDao.deleteEvent(id)
    }

    suspend fun checkEventCodeExistsInCloud(eventCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val spreadsheetId = cloudPreferences.spreadsheetId ?: return@withContext false
            val data = sheetsService.readRange(spreadsheetId, "Events!F2:F")
            data?.any { it.firstOrNull()?.toString().equals(eventCode, ignoreCase = true) } == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkEventNameExistsInCloud(eventName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val spreadsheetId = cloudPreferences.spreadsheetId ?: return@withContext false
            val data = sheetsService.readRange(spreadsheetId, "Events!B2:B")
            data?.any { it.firstOrNull()?.toString().equals(eventName, ignoreCase = true) } == true
        } catch (e: Exception) {
            false
        }
    }
}
