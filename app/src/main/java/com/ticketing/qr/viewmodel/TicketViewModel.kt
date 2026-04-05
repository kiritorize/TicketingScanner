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

    fun addTicketsFromTwoBoxes(codesText: String, categoriesText: String): Pair<Boolean, String> {
        val codes = codesText.split("\n")
        val cats = categoriesText.split("\n")

        val codeLines = codes.dropLastWhile { it.isEmpty() }
        val catLines = cats.dropLastWhile { it.isEmpty() }

        if (codeLines.size != catLines.size) {
            return Pair(false, "Jumlah baris Kode (${codeLines.size}) berbeda dgn Kategori (${catLines.size})!")
        }

        if (codeLines.isEmpty()) return Pair(false, "Teks kosong.")

        val tickets = mutableListOf<Ticket>()
        for (i in codeLines.indices) {
            val code = codeLines[i].trim()
            val cat = catLines[i].trim()

            if (code.isBlank() && cat.isBlank()) continue
            if (code.isBlank()) return Pair(false, "Baris ${i + 1}: Kode kosong!")
            if (cat.isBlank()) return Pair(false, "Baris ${i + 1}: Kategori kosong!")

            tickets.add(Ticket(qrContent = code, ticketType = cat, isScanned = false))
        }

        if (tickets.isEmpty()) return Pair(false, "Tidak ada data tiket valid.")

        viewModelScope.launch {
            ticketDao.insertTickets(tickets)
            refreshTicketCount()
        }
        return Pair(true, "Ditambahkan!")
    }

    fun clearScanResult() {
        _scanResultMsg.value = null
    }

    // Returns: 1 (Success/Play Sound A), 2 (Already Scanned/Play Sound B), 3 (Not Registered/Play Sound B)
    suspend fun processQrCode(qrContent: String): Int {
        val ticket = ticketDao.getTicketByQr(qrContent)
        
        return if (ticket != null) {
            if (ticket.isScanned) {
                _scanResultMsg.value = "SUDAH DISCAN:\n$qrContent\nTipe: ${ticket.ticketType}"
                2
            } else {
                ticketDao.markAsScanned(qrContent)
                _scanResultMsg.value = "BERHASIL:\n$qrContent\nTipe: ${ticket.ticketType}"
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
