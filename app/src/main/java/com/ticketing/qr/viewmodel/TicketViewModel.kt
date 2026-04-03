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

    init {
        refreshTicketCount()
    }

    private fun refreshTicketCount() {
        viewModelScope.launch {
            _ticketCount.value = ticketDao.getTicketCount()
        }
    }

    fun addTicketsFromText(text: String, separator: String = "\n") {
        viewModelScope.launch {
            val lines = text.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
            val tickets = lines.map { Ticket(qrContent = it, isScanned = false) }
            ticketDao.insertTickets(tickets)
            refreshTicketCount()
        }
    }

    fun clearAllTickets() {
        viewModelScope.launch {
            ticketDao.deleteAllTickets()
            refreshTicketCount()
        }
    }

    fun clearScanResult() {
        _scanResultMsg.value = null
    }

    // Returns: 1 (Success/Play Sound A), 2 (Already Scanned/Play Sound B), 3 (Not Registered/Play Sound B)
    suspend fun processQrCode(qrContent: String): Int {
        val ticket = ticketDao.getTicketByQr(qrContent)
        
        return if (ticket != null) {
            if (ticket.isScanned) {
                _scanResultMsg.value = "SUDAH DISCAN: \$qrContent"
                2
            } else {
                ticketDao.markAsScanned(qrContent)
                _scanResultMsg.value = "BERHASIL: \$qrContent"
                1
            }
        } else {
            _scanResultMsg.value = "TIDAK TERDAFTAR: \$qrContent"
            3
        }
    }
}
