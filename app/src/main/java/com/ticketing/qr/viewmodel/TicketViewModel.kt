package com.ticketing.qr.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ticketing.qr.data.AppDatabase
import com.ticketing.qr.data.Ticket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TicketViewModel(application: Application) : AndroidViewModel(application) {
    private val ticketDao = AppDatabase.getDatabase(application).ticketDao()

    private val _ticketCount = MutableStateFlow(0)
    val ticketCount: StateFlow<Int> = _ticketCount

    private val _scanResultMsg = MutableStateFlow<String?>(null)
    val scanResultMsg: StateFlow<String?> = _scanResultMsg

    private val _ticketList = MutableStateFlow<List<Ticket>>(emptyList())
    val ticketList: StateFlow<List<Ticket>> = _ticketList

    init {
        refreshTicketCount()
    }

    private fun refreshTicketCount() {
        viewModelScope.launch {
            val tickets = ticketDao.getAllTickets()
            _ticketCount.value = tickets.size
            _ticketList.value = tickets
        }
    }

    fun addTicketsFromText(text: String): Boolean {
        val lines = text.split(Regex("[\n\r]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            
        if (lines.isEmpty()) return false
        
        val tickets = mutableListOf<Ticket>()
        for (line in lines) {
            val parts = line.split(",", limit = 2)
            if (parts.size != 2) return false
            
            val qr = parts[0].trim()
            val type = parts[1].trim()
            
            if (qr.isBlank() || type.isBlank()) return false
            
            tickets.add(Ticket(qrContent = qr, ticketType = type, isScanned = false))
        }
        
        viewModelScope.launch {
            ticketDao.insertTickets(tickets)
            refreshTicketCount()
        }
        return true
    }

    fun importCsvFromUri(uri: android.net.Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                    inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                val success = addTicketsFromText(text)
                onResult(success)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun clearAllTickets() {
        viewModelScope.launch {
            ticketDao.deleteAllTickets()
            try {
                ticketDao.resetSequence()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            refreshTicketCount()
        }
    }

    fun deleteTickets(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ticketDao.deleteTickets(ids)
            ticketDao.reassignIds()
            refreshTicketCount()
        }
    }

    fun updateTicket(id: Int, newQr: String, newType: String) {
        viewModelScope.launch {
            try {
                ticketDao.updateTicket(id, newQr, newType, System.currentTimeMillis())
                refreshTicketCount()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun addTicketsFromTwoBoxes(codesText: String, categoriesText: String): Pair<Boolean, String> {
        val codes = codesText.split("\n")
        val cats = categoriesText.split("\n")

        val codeLines = codes.dropLastWhile { it.isEmpty() }
        val catLines = cats.dropLastWhile { it.isEmpty() }

        if (codeLines.size != catLines.size) {
            return Pair(false, "Jumlah baris Kode (${codeLines.size}) berbeda dgn Kategori (${catLines.size})!")
        }

        if (codeLines.isEmpty()) return Pair(false, "Teks kosong.")

        // --- Phase 1: Check for empty rows (collect ALL errors) ---
        val emptyRowErrors = mutableListOf<String>()
        for (i in codeLines.indices) {
            val code = codeLines[i].trim()
            val cat = catLines[i].trim()
            if (code.isBlank() && cat.isBlank()) {
                emptyRowErrors.add("Baris ${i + 1}: Kode & Kategori kosong")
            } else if (code.isBlank()) {
                emptyRowErrors.add("Baris ${i + 1}: Kode kosong")
            } else if (cat.isBlank()) {
                emptyRowErrors.add("Baris ${i + 1}: Kategori kosong")
            }
        }
        if (emptyRowErrors.isNotEmpty()) {
            return Pair(false, "Ditemukan baris kosong:\n\n${emptyRowErrors.joinToString("\n")}\n\nPastikan semua baris terisi lengkap.")
        }

        // --- Phase 2: Build tickets and check internal duplicates ---
        val tickets = mutableListOf<Ticket>()
        val codeToRows = mutableMapOf<String, MutableList<Int>>()

        for (i in codeLines.indices) {
            val code = codeLines[i].trim()
            val cat = catLines[i].trim()
            codeToRows.getOrPut(code) { mutableListOf() }.add(i + 1)
            tickets.add(Ticket(qrContent = code, ticketType = cat, isScanned = false))
        }

        if (tickets.isEmpty()) return Pair(false, "Tidak ada data tiket valid.")

        val internalDuplicates = codeToRows.filter { it.value.size > 1 }
        if (internalDuplicates.isNotEmpty()) {
            val messages = internalDuplicates.map { (code, rows) ->
                "Kode \"$code\" duplikat di baris: ${rows.joinToString(", ")}"
            }
            return Pair(false, "Ditemukan kode duplikat di input:\n\n${messages.joinToString("\n")}")
        }

        // --- Phase 3: Check duplicates against existing database ---
        val allCodes = tickets.map { it.qrContent }
        val existingCodes = ticketDao.getExistingCodes(allCodes).toSet()
        if (existingCodes.isNotEmpty()) {
            val dbDuplicateRows = mutableListOf<String>()
            for (i in codeLines.indices) {
                val code = codeLines[i].trim()
                if (code in existingCodes) {
                    dbDuplicateRows.add("Baris ${i + 1}: \"$code\"")
                }
            }
            return Pair(false, "Kode sudah ada di database:\n\n${dbDuplicateRows.joinToString("\n")}")
        }

        ticketDao.insertTickets(tickets)
        refreshTicketCount()
        return Pair(true, "Ditambahkan!")
    }

    fun clearScanResult() {
        _scanResultMsg.value = null
    }

    // Returns: 1 (Success/Play Sound A), 2 (Already Scanned/Play Sound B), 3 (Not Registered/Play Sound B)
    suspend fun processQrCode(qrContent: String): Int {
        val ticket = ticketDao.getTicketByQr(qrContent)
        
        return if (ticket != null) {
            val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
            if (ticket.isScanned) {
                val scanTime = ticket.scannedAt?.let { dateFormat.format(java.util.Date(it)) } ?: "Tidak diketahui"
                _scanResultMsg.value = "SUDAH DISCAN:\n$qrContent\nTipe: ${ticket.ticketType}\nWaktu: $scanTime"
                2
            } else {
                val currentTime = System.currentTimeMillis()
                ticketDao.markAsScanned(qrContent, currentTime)
                val scanTime = dateFormat.format(java.util.Date(currentTime))
                _scanResultMsg.value = "BERHASIL:\n$qrContent\nTipe: ${ticket.ticketType}\nWaktu: $scanTime"
                refreshTicketCount()
                1
            }
        } else {
            _scanResultMsg.value = "TIDAK TERDAFTAR:\n$qrContent"
            3
        }
    }

    fun exportDataToCsvUri(uri: android.net.Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val tickets = ticketDao.getAllTickets()
                    val outputStream = getApplication<Application>().contentResolver.openOutputStream(uri)
                    outputStream?.bufferedWriter()?.use { writer ->
                        writer.write("ID,Kode QR,Tipe Tiket,Status Scan,Waktu Ditambahkan\n")
                        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                        tickets.forEach { ticket ->
                            val dateStr = dateFormat.format(java.util.Date(ticket.createdAt))
                            val scanStr = if (ticket.isScanned) "Sudah" else "Belum"
                            writer.write("${ticket.id},\"${ticket.qrContent}\",\"${ticket.ticketType}\",\"$scanStr\",\"$dateStr\"\n")
                        }
                    }
                }
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }
}
