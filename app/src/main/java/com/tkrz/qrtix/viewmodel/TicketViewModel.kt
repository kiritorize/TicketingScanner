package com.tkrz.qrtix.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.tkrz.qrtix.data.Ticket
import com.tkrz.qrtix.data.TicketDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class ScanStatus {
    data class Success(val ticket: Ticket, val scanTimeString: String) : ScanStatus()
    data class AlreadyScanned(val ticket: Ticket, val scanTimeString: String) : ScanStatus()
    data class Invalid(val code: String) : ScanStatus()
    object Idle : ScanStatus()
}

@HiltViewModel
class TicketViewModel @Inject constructor(
    private val ticketDao: TicketDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _ticketCount = MutableStateFlow(0)
    val ticketCount: StateFlow<Int> = _ticketCount

    private val _scannedTicketCount = MutableStateFlow(0)
    val scannedTicketCount: StateFlow<Int> = _scannedTicketCount

    private val _scanResultStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanResultStatus: StateFlow<ScanStatus> = _scanResultStatus

    private val _ticketList = MutableStateFlow<List<Ticket>>(emptyList())
    val ticketList: StateFlow<List<Ticket>> = _ticketList

    // --- Filter & Sort States ---
    val searchQuery = MutableStateFlow("")
    val filterStatus = MutableStateFlow("Semua")
    val filterCategory = MutableStateFlow("Semua Kategori")
    val sortMode = MutableStateFlow("ID Tiket")
    val sortAscending = MutableStateFlow(true)

    val filteredList: StateFlow<List<Ticket>> = combine(
        _ticketList,
        combine(searchQuery, filterStatus, filterCategory) { search, status, category -> Triple(search, status, category) },
        combine(sortMode, sortAscending) { mode, asc -> Pair(mode, asc) }
    ) { list, filters, sortOptions ->
        val search = filters.first
        val status = filters.second
        val category = filters.third
        val mode = sortOptions.first
        val asc = sortOptions.second

        list.filter { ticket ->
            search.isBlank() ||
            ticket.qrContent.contains(search, ignoreCase = true) ||
            ticket.ticketType.contains(search, ignoreCase = true)
        }.filter { ticket ->
            when (status) {
                "Sudah Scan" -> ticket.isScanned
                "Belum Scan" -> !ticket.isScanned
                else -> true
            }
        }.filter { ticket ->
            category == "Semua Kategori" || ticket.ticketType == category
        }.sortedWith { t1, t2 ->
            val baseCompare = when (mode) {
                "ID Tiket" -> t1.id.compareTo(t2.id)
                "Waktu Dimodifikasi" -> t1.createdAt.compareTo(t2.createdAt)
                "Terakhir Discan" -> {
                    val s1 = t1.scannedAt
                    val s2 = t2.scannedAt
                    when {
                        s1 == null && s2 == null -> 0
                        s1 == null -> 1
                        s2 == null -> -1
                        else -> s1.compareTo(s2)
                    }
                }
                else -> t1.id.compareTo(t2.id)
            }
            if (asc) baseCompare else -baseCompare
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var recentlyDeletedTickets: List<Ticket>? = null

    init {
        refreshTicketCount()
    }

    private fun refreshTicketCount() {
        viewModelScope.launch {
            val tickets = ticketDao.getAllTickets()
            _ticketCount.value = tickets.size
            _ticketList.value = tickets
            _scannedTicketCount.value = tickets.count { it.isScanned }
        }
    }

    suspend fun addTicketsFromText(text: String): String {
        return withContext(Dispatchers.Default) {
            try {
                // Gunakan kotlin-csv agar lebih kebal error dari tanda kutip dan koma di dalam string
                val rows = csvReader().readAll(text)
                if (rows.isEmpty()) return@withContext "File CSV kosong atau tidak terbaca."

                val tickets = mutableListOf<Ticket>()
                var isFirstLine = true
                for (row in rows) {
                    if (row.size < 2) {
                        isFirstLine = false
                        continue
                    }
                    val qr = row[0].trim()
                    val type = row[1].trim()

                    if (isFirstLine && (qr.equals("ID", ignoreCase = true) || qr.equals("Kode QR", ignoreCase = true) || row.joinToString().contains("Tipe Tiket"))) {
                        isFirstLine = false
                        continue
                    }
                    isFirstLine = false

                    if (qr.isBlank() || type.isBlank()) continue

                    tickets.add(Ticket(qrContent = qr, ticketType = type, isScanned = false))
                }

                if (tickets.isEmpty()) return@withContext "Tidak ada data tiket valid di dalam file."

                // Mengecek ke database apakah ada baris duplikat sebelum insert
                val allCodes = tickets.map { it.qrContent }
                val existingCodes = ticketDao.getExistingCodes(allCodes).toSet()

                val internalCodesToRows = mutableMapOf<String, MutableList<Int>>()
                val uniqueInternalTickets = mutableListOf<Ticket>()

                // Filter duplikat di dalam file itu sendiri
                for (i in tickets.indices) {
                    val code = tickets[i].qrContent
                    internalCodesToRows.getOrPut(code) { mutableListOf() }.add(i)
                    if (internalCodesToRows[code]!!.size == 1) {
                        uniqueInternalTickets.add(tickets[i])
                    }
                }

                val newTickets = uniqueInternalTickets.filter { it.qrContent !in existingCodes }
                val duplicatesCount = tickets.size - newTickets.size

                if (newTickets.isNotEmpty()) {
                    ticketDao.insertTickets(newTickets)
                    refreshTicketCount()
                }

                if (duplicatesCount > 0) {
                    "Berhasil import ${newTickets.size} data.\n$duplicatesCount data duplikat dilewati."
                } else {
                    "Berhasil ditambahkan ke Database!"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "Gagal memproses file:\n${e.message}"
            }
        }
    }

    fun importCsvFromUri(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                val resultMsg = addTicketsFromText(text)
                onResult(resultMsg)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult("Gagal membaca file: ${e.message}")
            }
        }
    }

    fun clearAllTickets() {
        viewModelScope.launch {
            recentlyDeletedTickets = _ticketList.value
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
            recentlyDeletedTickets = _ticketList.value.filter { it.id in ids }
            ticketDao.deleteTickets(ids)
            refreshTicketCount()
        }
    }

    fun undoDelete() {
        recentlyDeletedTickets?.let { tickets ->
            if (tickets.isNotEmpty()) {
                viewModelScope.launch {
                    ticketDao.insertTickets(tickets)
                    refreshTicketCount()
                    recentlyDeletedTickets = null
                }
            }
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
        _scanResultStatus.value = ScanStatus.Idle
    }

    suspend fun processQrCode(qrContent: String): Int {
        val ticket = ticketDao.getTicketByQr(qrContent)
        
        return if (ticket != null) {
            val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
            if (ticket.isScanned) {
                val scanTime = ticket.scannedAt?.let { dateFormat.format(java.util.Date(it)) } ?: "Tidak diketahui"
                _scanResultStatus.value = ScanStatus.AlreadyScanned(ticket, scanTime)
                2
            } else {
                val currentTime = System.currentTimeMillis()
                ticketDao.markAsScanned(qrContent, currentTime)
                val scanTime = dateFormat.format(java.util.Date(currentTime))
                _scanResultStatus.value = ScanStatus.Success(ticket, scanTime)
                refreshTicketCount()
                1
            }
        } else {
            _scanResultStatus.value = ScanStatus.Invalid(qrContent)
            3
        }
    }

    fun exportDataToCsvUri(uri: Uri, onlyScanned: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    var tickets = ticketDao.getAllTickets()
                    if (onlyScanned) {
                        tickets = tickets.filter { it.isScanned }
                    }
                    val outputStream = context.contentResolver.openOutputStream(uri)
                    outputStream?.bufferedWriter()?.use { writer ->
                        writer.write("ID,Kode QR,Tipe Tiket,Status Scan,Waktu Ditambahkan,Waktu Discan\n")
                        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                        tickets.forEach { ticket ->
                            val dateStr = dateFormat.format(java.util.Date(ticket.createdAt))
                            val scanStr = if (ticket.isScanned) "Sudah" else "Belum"
                            val scannedAtStr = ticket.scannedAt?.let { dateFormat.format(java.util.Date(it)) } ?: "-"
                            
                            // Escape commas properly with string replaces for simple CSV formatting
                            val escapedQr = ticket.qrContent.replace("\"", "\"\"")
                            val escapedType = ticket.ticketType.replace("\"", "\"\"")

                            writer.write("${ticket.id},\"${escapedQr}\",\"${escapedType}\",\"$scanStr\",\"$dateStr\",\"$scannedAtStr\"\n")
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
