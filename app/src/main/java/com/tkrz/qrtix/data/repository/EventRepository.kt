package com.tkrz.qrtix.data.repository

import com.tkrz.qrtix.data.Event
import com.tkrz.qrtix.data.EventDao
import com.tkrz.qrtix.data.cloud.CloudPreferences
import com.tkrz.qrtix.data.cloud.GoogleSheetsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val sheetsService: GoogleSheetsService,
    private val cloudPreferences: CloudPreferences
) {
    fun getAllEvents(): Flow<List<Event>> = eventDao.getAllEvents()

    suspend fun getEventById(id: Long): Event? = eventDao.getEventById(id)

    suspend fun syncEventsFromCloud() = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: throw Exception("Cloud database belum siap")
        val data = sheetsService.readRange(spreadsheetId, "Events!A2:K")
        
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
                    
                    events.add(Event(id, name, createdAt, lastAccessedAt, logoPath, eventCode, bgPath, qrX, qrY, qrScale, qrRotation))
                } catch (e: Exception) {
                    // Skip malformed row
                }
            }
        }

        if (events.isEmpty()) {
            val defaultEvent = Event(id = 1, name = "Event Default")
            insertEvent(defaultEvent)
        } else {
            eventDao.deleteAllEvents()
            for (event in events) {
                eventDao.insertEvent(event)
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
            newEvent.qrRotation.toString()
        )
        
        sheetsService.appendRow(spreadsheetId, "Events!A1", rowData)
        eventDao.insertEvent(newEvent)
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
                event.qrRotation.toString()
            )
            sheetsService.updateRow(spreadsheetId, "Events!A$rowIndex:K$rowIndex", rowData)
        }
        
        eventDao.updateEvent(event)
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
}
