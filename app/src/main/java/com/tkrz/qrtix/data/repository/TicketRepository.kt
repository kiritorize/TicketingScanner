package com.tkrz.qrtix.data.repository

import com.google.api.services.sheets.v4.model.DeleteDimensionRequest
import com.google.api.services.sheets.v4.model.DimensionRange
import com.google.api.services.sheets.v4.model.Request
import com.tkrz.qrtix.data.Ticket
import com.tkrz.qrtix.data.TicketDao
import com.tkrz.qrtix.data.cloud.CloudPreferences
import com.tkrz.qrtix.data.cloud.GoogleSheetsService
import com.tkrz.qrtix.data.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import javax.inject.Inject

/**
 * Result type for real-time online ticket validation via Google Sheets.
 */
sealed class OnlineScanResult {
    data class Success(val ticket: Ticket, val scannedAt: Long) : OnlineScanResult()
    data class AlreadyScanned(val ticket: Ticket, val scannedAt: Long) : OnlineScanResult()
    data class NotFound(val code: String) : OnlineScanResult()
    data class NetworkError(val message: String) : OnlineScanResult()
}

class TicketRepository @Inject constructor(
    private val databaseProvider: DatabaseProvider,
    private val sheetsService: GoogleSheetsService,
    private val cloudPreferences: CloudPreferences
) {
    private val ticketDao get() = databaseProvider.ticketDao
    companion object {
        private const val ONLINE_SCAN_TIMEOUT_MS = 5000L
    }

    private val insertMutex = Mutex()

    suspend fun getAllTickets(eventId: Long): List<Ticket> = ticketDao.getAllTickets(eventId)

    suspend fun getTicketByQr(qrContent: String, eventId: Long): Ticket? = ticketDao.getTicketByQr(qrContent, eventId)

    fun getCategories(eventId: Long): Flow<List<String>> = ticketDao.getCategories(eventId)

    suspend fun getTicketCount(eventId: Long): Int = ticketDao.getTicketCount(eventId)

    suspend fun getExistingCodes(codes: List<String>, eventId: Long): List<String> = 
        ticketDao.getExistingCodes(codes, eventId)

    /**
     * Validates and scans a ticket in real-time against Google Sheets.
     * 
     * Flow:
     * 1. Read all ticket rows from the Tickets sheet.
     * 2. Find the row matching qrContent + eventId (case-insensitive on qrContent).
     * 3. If not found → NotFound.
     * 4. If found and already scanned → AlreadyScanned.
     * 5. If found and not scanned → write isScanned=true + scannedAt to Sheets.
     * 6. Immediately re-read to confirm write success (conflict prevention).
     * 7. If confirmed, update local cache → Success. Otherwise → AlreadyScanned.
     * 
     * The entire operation is wrapped in a 5-second timeout.
     */
    suspend fun validateAndScanOnline(qrContent: String, eventId: Long): OnlineScanResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(ONLINE_SCAN_TIMEOUT_MS) {
                val spreadsheetId = cloudPreferences.spreadsheetId
                    ?: return@withTimeout OnlineScanResult.NetworkError("Cloud database belum siap")

                // Read all ticket data from Sheets
                val data = sheetsService.readRange(spreadsheetId, "Tickets!A2:H")

                // Find the matching row
                var matchedRowIndex = -1 // 1-based index in sheet (header is row 1, data starts at row 2)
                var matchedTicket: Ticket? = null

                if (data != null) {
                    for (i in data.indices) {
                        val row = data[i]
                        if (row.isEmpty()) continue
                        try {
                            val rowQr = row.getOrNull(1)?.toString() ?: continue
                            val rowEventId = row.getOrNull(7)?.toString()?.toLongOrNull() ?: continue

                            if (rowQr.equals(qrContent, ignoreCase = true) && rowEventId == eventId) {
                                // Found the matching ticket row
                                matchedRowIndex = i + 2 // +2 because data starts at row 2 (1-based), and i is 0-based
                                val id = row.getOrNull(0)?.toString()?.toIntOrNull() ?: 0
                                val ticketType = row.getOrNull(2)?.toString() ?: ""
                                val isScanned = row.getOrNull(3)?.toString()?.lowercase()?.toBooleanStrictOrNull() ?: false
                                val createdAt = row.getOrNull(4)?.toString()?.toLongOrNull() ?: System.currentTimeMillis()
                                val scannedAtStr = row.getOrNull(5)?.toString()
                                val scannedAt = if (scannedAtStr.isNullOrBlank() || scannedAtStr == "null") null else scannedAtStr.toLongOrNull()
                                val isModified = row.getOrNull(6)?.toString()?.lowercase()?.toBooleanStrictOrNull() ?: false

                                matchedTicket = Ticket(id, rowQr, ticketType, isScanned, createdAt, scannedAt, isModified, rowEventId)
                                break
                            }
                        } catch (e: Exception) {
                            // Skip malformed row
                        }
                    }
                }

                if (matchedTicket == null) {
                    return@withTimeout OnlineScanResult.NotFound(qrContent)
                }

                // Check if already scanned
                if (matchedTicket.isScanned) {
                    val scannedAt = matchedTicket.scannedAt ?: 0L
                    return@withTimeout OnlineScanResult.AlreadyScanned(matchedTicket, scannedAt)
                }

                // Mark as scanned: write isScanned=true and scannedAt to the Sheets row
                val currentTime = System.currentTimeMillis()
                val updatedRowData = listOf(
                    matchedTicket.id.toString(),
                    matchedTicket.qrContent,
                    matchedTicket.ticketType,
                    "true",
                    matchedTicket.createdAt.toString(),
                    currentTime.toString(),
                    matchedTicket.isModified.toString(),
                    matchedTicket.eventId.toString()
                )
                sheetsService.updateRow(spreadsheetId, "Tickets!A$matchedRowIndex:H$matchedRowIndex", updatedRowData)

                // Let concurrent writes settle before verification read
                delay(100)

                // Race condition check: re-read the scannedAt value after delay
                val verifyData = sheetsService.readRange(spreadsheetId, "Tickets!F$matchedRowIndex:F$matchedRowIndex")
                val readScannedAtStr = verifyData?.firstOrNull()?.firstOrNull()?.toString()
                val readScannedAt = if (readScannedAtStr.isNullOrBlank() || readScannedAtStr == "null") null else readScannedAtStr.toLongOrNull()

                if (readScannedAt != currentTime) {
                    // Another device scanned it first or the write was overwritten
                    val scannedAt = readScannedAt ?: 0L
                    return@withTimeout OnlineScanResult.AlreadyScanned(matchedTicket.copy(isScanned = true, scannedAt = scannedAt), scannedAt)
                }

                // Update local cache
                ticketDao.markAsScanned(qrContent, eventId, currentTime)

                val scannedTicket = matchedTicket.copy(isScanned = true, scannedAt = currentTime)
                OnlineScanResult.Success(scannedTicket, currentTime)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            OnlineScanResult.NetworkError("Koneksi timeout. Periksa jaringan internet Anda.")
        } catch (e: java.net.UnknownHostException) {
            OnlineScanResult.NetworkError("Tidak ada koneksi internet.")
        } catch (e: java.io.IOException) {
            OnlineScanResult.NetworkError("Gagal terhubung ke server: ${e.message}")
        } catch (e: Exception) {
            OnlineScanResult.NetworkError("Terjadi kesalahan: ${e.message}")
        }
    }

    /**
     * Syncs tickets from Google Sheets with a smart merge strategy.
     *
     * Instead of destructive delete-all-then-insert, this compares cloud data
     * with local data and keeps the freshest scan state for each ticket:
     * - Local scanned + cloud not scanned → keep local (scan hasn't propagated yet)
     * - Cloud scanned + local not scanned → keep cloud (scanned from another device)
     * - Both scanned → keep the one with the earlier timestamp (first-scan-wins)
     * - Default → trust cloud
     */
    suspend fun syncTicketsFromCloud(eventId: Long) = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: throw Exception("Cloud database belum siap")
        val data = sheetsService.readRange(spreadsheetId, "Tickets!A2:H")

        // Parse cloud tickets for this event
        val cloudTickets = mutableListOf<Ticket>()
        if (data != null) {
            for (row in data) {
                if (row.isEmpty()) continue
                try {
                    val id = row.getOrNull(0)?.toString()?.toIntOrNull() ?: continue
                    val rowEventId = row.getOrNull(7)?.toString()?.toLongOrNull() ?: continue

                    if (rowEventId == eventId) {
                        val qrContent = row.getOrNull(1)?.toString() ?: ""
                        val ticketType = row.getOrNull(2)?.toString() ?: ""
                        val isScanned = row.getOrNull(3)?.toString()?.lowercase()?.toBooleanStrictOrNull() ?: false
                        val createdAt = row.getOrNull(4)?.toString()?.toLongOrNull() ?: System.currentTimeMillis()
                        val scannedAtStr = row.getOrNull(5)?.toString()
                        val scannedAt = if (scannedAtStr.isNullOrBlank() || scannedAtStr == "null") null else scannedAtStr.toLongOrNull()
                        val isModified = row.getOrNull(6)?.toString()?.lowercase()?.toBooleanStrictOrNull() ?: false

                        cloudTickets.add(Ticket(id, qrContent, ticketType, isScanned, createdAt, scannedAt, isModified, rowEventId))
                    }
                } catch (e: Exception) {
                    // Skip malformed row
                }
            }
        }

        // Smart merge: compare cloud vs local, keep the freshest scan data
        val localTickets = ticketDao.getAllTickets(eventId)
        val localMap = localTickets.associateBy { it.qrContent.trim().uppercase() }

        val mergedTickets = cloudTickets.map { cloudTicket ->
            val localTicket = localMap[cloudTicket.qrContent.trim().uppercase()]
            when {
                // Local scanned, cloud not — local is fresher (scan hasn't propagated yet)
                localTicket != null && localTicket.isScanned && !cloudTicket.isScanned -> localTicket
                // Cloud scanned, local not — cloud is fresher (scanned from another device)
                cloudTicket.isScanned && (localTicket == null || !localTicket.isScanned) -> cloudTicket
                // Both scanned — keep whichever was scanned first (first-scan-wins)
                localTicket != null && cloudTicket.scannedAt != null && localTicket.scannedAt != null ->
                    if (localTicket.scannedAt!! <= cloudTicket.scannedAt!!) localTicket else cloudTicket
                // Default: trust cloud
                else -> cloudTicket
            }
        }

        // Replace local DB with merged result
        ticketDao.deleteAllTickets(eventId)
        if (mergedTickets.isNotEmpty()) {
            ticketDao.insertTickets(mergedTickets)
        }
    }

    suspend fun insertTickets(tickets: List<Ticket>) = withContext(Dispatchers.IO) {
        if (tickets.isEmpty()) return@withContext
        
        val spreadsheetId = cloudPreferences.spreadsheetId ?: throw Exception("Cloud database belum siap")
        
        insertMutex.withLock {
            var nextId = 1
            var retries = 3
            while (retries > 0) {
                val data = sheetsService.readRange(spreadsheetId, "Tickets!A2:A")
                var maxId = 0
                if (data != null) {
                    maxId = data.mapNotNull { it.firstOrNull()?.toString()?.toIntOrNull() }.maxOrNull() ?: 0
                }
                
                // Delay and verify to avoid multi-device race conditions
                delay((150..350).random().toLong())
                
                val verifyData = sheetsService.readRange(spreadsheetId, "Tickets!A2:A")
                val verifyMaxId = verifyData?.mapNotNull { it.firstOrNull()?.toString()?.toIntOrNull() }?.maxOrNull() ?: 0
                
                if (maxId == verifyMaxId) {
                    nextId = maxId + 1
                    break
                }
                retries--
                if (retries == 0) {
                    nextId = verifyMaxId + 1
                }
            }
            
            val newTickets = tickets.mapIndexed { index, ticket ->
                ticket.copy(id = nextId + index)
            }
            
            val rows = newTickets.map { ticket ->
                listOf(
                    ticket.id.toString(),
                    ticket.qrContent,
                    ticket.ticketType,
                    ticket.isScanned.toString(),
                    ticket.createdAt.toString(),
                    ticket.scannedAt?.toString() ?: "",
                    ticket.isModified.toString(),
                    ticket.eventId.toString()
                )
            }
            
            val chunks = rows.chunked(500)
            for (chunk in chunks) {
                sheetsService.appendRows(spreadsheetId, "Tickets!A:H", chunk)
            }
            
            // Post-insert verification
            delay(500)
            val verifyInsertData = sheetsService.readRange(spreadsheetId, "Tickets!A2:A")
            val finalMaxId = verifyInsertData?.mapNotNull { it.firstOrNull()?.toString()?.toIntOrNull() }?.maxOrNull() ?: 0
            val expectedMaxId = nextId + tickets.size - 1
            if (finalMaxId < expectedMaxId) {
                Log.e("TicketRepository", "Post-insert verification failed: expected maxId $expectedMaxId but got $finalMaxId")
                // In a robust implementation, we would retry missing rows here.
            }
            
            ticketDao.insertTickets(newTickets)
        }
    }

    suspend fun updateTicket(id: Int, newQr: String, newType: String, updatedAt: Long) = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: throw Exception("Cloud database belum siap")
        
        // Ambil tiket lama dari DB lokal untuk referensi nilai lain yang tidak berubah
        val oldTicket = ticketDao.getTicketById(id) ?: return@withContext
        
        val data = sheetsService.readRange(spreadsheetId, "Tickets!A1:A")
        var rowIndex = -1
        if (data != null) {
            for (i in data.indices) {
                if (data[i].firstOrNull()?.toString() == id.toString()) {
                    rowIndex = i + 1 
                    break
                }
            }
        }
        
        if (rowIndex != -1) {
            val updatedTicket = oldTicket.copy(
                qrContent = newQr,
                ticketType = newType,
                createdAt = updatedAt,
                isModified = true
            )
            
            val rowData = listOf(
                updatedTicket.id.toString(),
                updatedTicket.qrContent,
                updatedTicket.ticketType,
                updatedTicket.isScanned.toString(),
                updatedTicket.createdAt.toString(),
                updatedTicket.scannedAt?.toString() ?: "",
                updatedTicket.isModified.toString(),
                updatedTicket.eventId.toString()
            )
            sheetsService.updateRow(spreadsheetId, "Tickets!A$rowIndex:H$rowIndex", rowData)
        }
        
        ticketDao.updateTicket(id, newQr, newType, updatedAt)
    }

    suspend fun deleteTickets(ids: List<Int>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val spreadsheetId = cloudPreferences.spreadsheetId ?: throw Exception("Cloud database belum siap")
        val sheetId = sheetsService.getSheetIdByName(spreadsheetId, "Tickets") ?: throw Exception("Tickets sheet not found")
        
        val data = sheetsService.readRange(spreadsheetId, "Tickets!A1:A")
        val idSet = ids.map { it.toString() }.toSet()
        val rowIndicesToDelete = mutableListOf<Int>()
        
        if (data != null) {
            for (i in data.indices) {
                if (idSet.contains(data[i].firstOrNull()?.toString())) {
                    rowIndicesToDelete.add(i) 
                }
            }
        }
        
        // Hapus dari indeks terbesar ke terkecil agar indeks di bawahnya tidak bergeser saat proses penghapusan
        rowIndicesToDelete.sortDescending()
        
        if (rowIndicesToDelete.isNotEmpty()) {
            val deleteRequests = rowIndicesToDelete.map { rowIndex ->
                Request().setDeleteDimension(
                    DeleteDimensionRequest().setRange(
                        DimensionRange()
                            .setSheetId(sheetId)
                            .setDimension("ROWS")
                            .setStartIndex(rowIndex)
                            .setEndIndex(rowIndex + 1)
                    )
                )
            }
            // Kirim secara batch (chunking per 500 request juga jika ukurannya terlalu besar)
            deleteRequests.chunked(500).forEach { chunk ->
                sheetsService.batchUpdate(spreadsheetId, chunk)
            }
        }
        
        ticketDao.deleteTickets(ids)
    }

    suspend fun deleteAllTickets(eventId: Long) = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: throw Exception("Cloud database belum siap")
        val sheetId = sheetsService.getSheetIdByName(spreadsheetId, "Tickets") ?: throw Exception("Tickets sheet not found")
        
        // Kita butuh kolom H (eventId) untuk mengecek eventId-nya.
        // Download kolom A sampai H
        val data = sheetsService.readRange(spreadsheetId, "Tickets!A1:H")
        val rowIndicesToDelete = mutableListOf<Int>()
        
        if (data != null) {
            for (i in data.indices) {
                val rowEventId = data[i].getOrNull(7)?.toString()
                if (rowEventId == eventId.toString()) {
                    rowIndicesToDelete.add(i)
                }
            }
        }
        
        rowIndicesToDelete.sortDescending()
        
        if (rowIndicesToDelete.isNotEmpty()) {
            val deleteRequests = rowIndicesToDelete.map { rowIndex ->
                Request().setDeleteDimension(
                    DeleteDimensionRequest().setRange(
                        DimensionRange()
                            .setSheetId(sheetId)
                            .setDimension("ROWS")
                            .setStartIndex(rowIndex)
                            .setEndIndex(rowIndex + 1)
                    )
                )
            }
            deleteRequests.chunked(500).forEach { chunk ->
                sheetsService.batchUpdate(spreadsheetId, chunk)
            }
        }
        
        ticketDao.deleteAllTickets(eventId)
    }

    suspend fun resetSequence() = ticketDao.resetSequence()

    // Local-only fallback for marking tickets as scanned (reserved for future offline mode)
    suspend fun markAsScannedLocally(qrContent: String, eventId: Long, scannedAt: Long = System.currentTimeMillis()) = 
        ticketDao.markAsScanned(qrContent, eventId, scannedAt)
}
