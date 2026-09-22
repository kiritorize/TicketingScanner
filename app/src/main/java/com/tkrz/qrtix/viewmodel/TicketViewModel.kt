package com.tkrz.qrtix.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.tkrz.qrtix.data.Event
import com.tkrz.qrtix.data.EventDao
import com.tkrz.qrtix.data.EventPreferences
import com.tkrz.qrtix.data.Ticket
import com.tkrz.qrtix.data.TicketDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class ScanStatus {
    data class Success(val ticket: Ticket, val scanTimeString: String) : ScanStatus()
    data class AlreadyScanned(val ticket: Ticket, val scanTimeString: String) : ScanStatus()
    data class Invalid(val code: String) : ScanStatus()
    data class CrossEventError(val code: String) : ScanStatus()
    data class NetworkError(val message: String) : ScanStatus()
    object Idle : ScanStatus()
}

@HiltViewModel
class TicketViewModel @Inject constructor(
    private val ticketRepository: com.tkrz.qrtix.data.repository.TicketRepository,
    private val eventRepository: com.tkrz.qrtix.data.repository.EventRepository,
    private val categoryRepository: com.tkrz.qrtix.data.repository.CategoryRepository,
    private val eventPreferences: EventPreferences,
    private val historyLogRepository: com.tkrz.qrtix.data.repository.HistoryLogRepository,
    private val mediaManager: com.tkrz.qrtix.data.cloud.MediaManager,
    private val eventBackupManager: com.tkrz.qrtix.data.cloud.EventBackupManager,
    private val sheetsService: com.tkrz.qrtix.data.cloud.GoogleSheetsService,
    private val driveFolderManager: com.tkrz.qrtix.data.cloud.DriveFolderManager,
    val backgroundUploadManager: com.tkrz.qrtix.data.cloud.BackgroundUploadManager,
    private val cloudPreferences: com.tkrz.qrtix.data.cloud.CloudPreferences,
    private val databaseTransferManager: com.tkrz.qrtix.data.transfer.DatabaseTransferManager,
    private val networkMonitor: com.tkrz.qrtix.utils.NetworkMonitor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val activeEventId: StateFlow<Long> = eventPreferences.activeEventId

    private val _historyLogs = MutableStateFlow<List<com.tkrz.qrtix.data.HistoryLog>>(emptyList())
    val historyLogs: StateFlow<List<com.tkrz.qrtix.data.HistoryLog>> = _historyLogs


    private val _activeEvent = MutableStateFlow<Event?>(null)
    val activeEvent: StateFlow<Event?> = _activeEvent

    val allEvents: StateFlow<List<Event>> = eventRepository.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _ticketCount = MutableStateFlow(0)
    val ticketCount: StateFlow<Int> = _ticketCount

    // Navigation Flag for Distribution
    var isNavigatingToStatus = false

    private val _scannedTicketCount = MutableStateFlow(0)
    val scannedTicketCount: StateFlow<Int> = _scannedTicketCount

    private val _scanResultStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanResultStatus: StateFlow<ScanStatus> = _scanResultStatus

    val isOnline: StateFlow<Boolean> = networkMonitor.isConnected

    private val _isScanLoading = MutableStateFlow(false)
    val isScanLoading: StateFlow<Boolean> = _isScanLoading

    private val _lastSyncTime = MutableStateFlow<Long>(System.currentTimeMillis())
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    private var syncJob: kotlinx.coroutines.Job? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ticketCategories: StateFlow<List<com.tkrz.qrtix.data.TicketCategory>> = activeEventId
        .flatMapLatest { eventId ->
            categoryRepository.getCategoriesForEventFlow(eventId)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<String>> = activeEventId
        .flatMapLatest { eventId ->
            ticketRepository.getCategories(eventId)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
        viewModelScope.launch(Dispatchers.IO) {
            activeEventId.collect { id ->
                try {
                    var ev = eventRepository.getEventById(id)
                    
                    // If the current event ID from preferences is invalid/deleted
                    if (ev == null) {
                        // Attempt to recover using the default event (ID 1)
                        val defaultEvent = eventRepository.getEventById(1L)
                        if (defaultEvent != null) {
                            ev = defaultEvent
                        }
                        
                        // Sync preferences back to a valid event ID
                        if (ev != null && id != ev.id) {
                            launch(Dispatchers.Main) {
                                eventPreferences.setActiveEventId(ev.id)
                            }
                            // The collector will be triggered again by the preference update
                            return@collect
                        }
                    }
                    
                    // Update the active event state and refresh the data
                    _activeEvent.value = ev
                    refreshTicketCount()
                } catch (e: Exception) {
                    e.printStackTrace()
                    // If a serious DB error occurs, at least ensure we don't crash the main thread
                }
            }
        }
    }

    fun syncTicketsFromCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val eventId = activeEventId.value
                categoryRepository.syncCategoriesFromCloud(eventId)
                ticketRepository.syncTicketsFromCloud(eventId)
                refreshTicketCount() // Refresh local list after sync
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Sinkronisasi berhasil", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorToast("Sinkronisasi gagal: ${e.message}")
            }
        }
    }

    fun addCategory(name: String, code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmedName = name.trim()
                val trimmedCode = code.trim().uppercase()
                if (trimmedName.isEmpty() || trimmedCode.isEmpty()) return@launch

                val currentEventId = activeEventId.value
                val existingCategories = categoryRepository.getCategoriesForEvent(currentEventId)

                if (existingCategories.any { it.categoryCode == trimmedCode }) {
                    launch(Dispatchers.Main) { showErrorToast("ID Kategori (Code) sudah digunakan!") }
                    return@launch
                }
                
                if (categoryRepository.checkCategoryCodeExistsInCloud(currentEventId, trimmedCode)) {
                    launch(Dispatchers.Main) { showErrorToast("ID Kategori (Code) sudah digunakan (Cloud)!") }
                    return@launch
                }

                categoryRepository.insertCategory(
                    com.tkrz.qrtix.data.TicketCategory(
                        eventId = currentEventId,
                        categoryName = trimmedName,
                        categoryCode = trimmedCode
                    )
                )
                
                historyLogRepository.insertLog(com.tkrz.qrtix.data.HistoryLog(
                    eventId = currentEventId,
                    action = "Tambah Kategori",
                    description = "Tambah kategori $trimmedName (ID: $trimmedCode)"
                ))
                
                val ev = eventRepository.getEventById(currentEventId)
                if (ev != null) {
                    val cats = categoryRepository.getCategoriesForEvent(currentEventId).map { it.categoryName }
                    eventBackupManager.backupEventToCloud(ev, cats)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorToast("Gagal menambah kategori: ${e.message}")
            }
        }
    }

    fun updateCategory(categoryId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentEventId = activeEventId.value
                val existingCategories = categoryRepository.getCategoriesForEvent(currentEventId)
                val categoryToUpdate = existingCategories.find { it.id == categoryId }
                
                if (categoryToUpdate != null) {
                    val updatedCategory = categoryToUpdate.copy(categoryName = newName.trim())
                    categoryRepository.updateCategory(updatedCategory)
                    
                    historyLogRepository.insertLog(com.tkrz.qrtix.data.HistoryLog(
                        eventId = currentEventId,
                        action = "Ubah Kategori",
                        description = "Ubah nama kategori ID ${categoryToUpdate.categoryCode} menjadi ${newName.trim()}"
                    ))
                    
                    val ev = eventRepository.getEventById(currentEventId)
                    if (ev != null) {
                        val cats = categoryRepository.getCategoriesForEvent(currentEventId).map { it.categoryName }
                        eventBackupManager.backupEventToCloud(ev, cats)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorToast("Gagal mengubah kategori: ${e.message}")
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentEventId = activeEventId.value
                val existingCategories = categoryRepository.getCategoriesForEvent(currentEventId)
                val categoryToDelete = existingCategories.find { it.id == categoryId }
                
                if (categoryToDelete != null) {
                    categoryRepository.deleteCategory(categoryToDelete)
                    
                    historyLogRepository.insertLog(com.tkrz.qrtix.data.HistoryLog(
                        eventId = currentEventId,
                        action = "Hapus Kategori",
                        description = "Hapus kategori ${categoryToDelete.categoryName} (ID: ${categoryToDelete.categoryCode})"
                    ))
                    
                    val ev = eventRepository.getEventById(currentEventId)
                    if (ev != null) {
                        val cats = categoryRepository.getCategoriesForEvent(currentEventId).map { it.categoryName }
                        eventBackupManager.backupEventToCloud(ev, cats)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorToast("Gagal menghapus kategori: ${e.message}")
            }
        }
    }

    fun createAndSwitchEvent(name: String, eventCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmedName = name.trim()
                val trimmedCode = eventCode.trim()
                if (trimmedName.isEmpty() || trimmedCode.isEmpty()) return@launch
                
                if (allEvents.value.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                    showErrorToast("Nama workspace sudah ada!")
                    return@launch
                }
                if (allEvents.value.any { it.eventCode.equals(trimmedCode, ignoreCase = true) }) {
                    launch(Dispatchers.Main) { showErrorToast("Kode event sudah ada!") }
                    return@launch
                }
                
                if (eventRepository.checkEventCodeExistsInCloud(trimmedCode)) {
                    launch(Dispatchers.Main) { showErrorToast("Kode event sudah digunakan oleh event lain (Cloud)!") }
                    return@launch
                }
                
                if (eventRepository.checkEventNameExistsInCloud(trimmedName)) {
                    launch(Dispatchers.Main) { showErrorToast("Nama event sudah digunakan di perangkat lain (Cloud)!") }
                    return@launch
                }
                
                val profilesFolderId = cloudPreferences.profilesFolderId
                if (profilesFolderId != null) {
                    if (driveFolderManager.checkFolderExists(trimmedName, profilesFolderId)) {
                        launch(Dispatchers.Main) { showErrorToast("Folder event sudah ada di Google Drive (Cloud)!") }
                        return@launch
                    }
                }

                val newId = eventRepository.insertEvent(Event(name = trimmedName, eventCode = trimmedCode))
                launch(Dispatchers.Main) {
                    eventPreferences.setActiveEventId(newId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorToast("Gagal membuat workspace: ${e.message}")
            }
        }
    }

    fun switchEvent(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val event = eventRepository.getEventById(id)
                if (event != null) {
                    eventRepository.updateEvent(event.copy(lastAccessedAt = System.currentTimeMillis()))
                    launch(Dispatchers.Main) {
                        eventPreferences.setActiveEventId(id)
                    }
                } else {
                    showErrorToast("Workspace tidak ditemukan!")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorToast("Gagal berpindah workspace: ${e.message}")
            }
        }
    }

    fun updateEventName(id: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmedName = newName.trim()
            if (allEvents.value.any { it.name.equals(trimmedName, ignoreCase = true) && it.id != id }) {
                showErrorToast("Nama workspace sudah ada!")
                return@launch
            }
            
            if (eventRepository.checkEventNameExistsInCloud(trimmedName)) {
                launch(Dispatchers.Main) { showErrorToast("Nama event sudah digunakan di perangkat lain (Cloud)!") }
                return@launch
            }
            
            val profilesFolderId = cloudPreferences.profilesFolderId
            if (profilesFolderId != null) {
                if (driveFolderManager.checkFolderExists(trimmedName, profilesFolderId)) {
                    launch(Dispatchers.Main) { showErrorToast("Folder event sudah ada di Google Drive (Cloud)!") }
                    return@launch
                }
            }
            
            val event = eventRepository.getEventById(id)
            if (event != null) {
                val oldName = event.name
                val updatedEvent = event.copy(name = trimmedName)
                eventRepository.updateEvent(updatedEvent)
                
                historyLogRepository.insertLog(com.tkrz.qrtix.data.HistoryLog(
                    eventId = id,
                    action = "Ubah Event",
                    description = "Ubah nama event dari $oldName menjadi $trimmedName"
                ))
                
                if (id == activeEventId.value) {
                    _activeEvent.value = updatedEvent
                }
                
                // Rename in Google Drive
                if (profilesFolderId != null) {
                    val folderId = driveFolderManager.getOrCreateFolder(oldName, profilesFolderId)
                    if (folderId != null) {
                        driveFolderManager.renameFolder(folderId, trimmedName)
                    }
                }
            }
        }
    }

    fun updateEventMedia(eventId: Long, logoPath: String?, bgPath: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val event = eventRepository.getEventById(eventId)
            if (event != null) {
                var finalLogoId = event.logoPath
                var finalBgId = event.bgPath
                
                // If the provided path is a local path (starts with /), upload it
                if (logoPath != null && logoPath.startsWith("/")) {
                    val uploadedId = mediaManager.uploadMedia(java.io.File(logoPath), event.name, true)
                    if (uploadedId != null) finalLogoId = uploadedId
                } else if (logoPath == null) {
                    finalLogoId = null
                }

                if (bgPath != null && bgPath.startsWith("/")) {
                    val uploadedId = mediaManager.uploadMedia(java.io.File(bgPath), event.name, false)
                    if (uploadedId != null) finalBgId = uploadedId
                } else if (bgPath == null) {
                    finalBgId = null
                }

                val updatedEvent = event.copy(logoPath = finalLogoId, bgPath = finalBgId)
                eventRepository.updateEvent(updatedEvent)
                if (eventId == activeEventId.value) {
                    _activeEvent.value = updatedEvent
                }
            }
        }
    }

    suspend fun resolveMedia(fileId: String?): String? {
        return mediaManager.getOrDownloadMedia(fileId)
    }

    fun updateEventQrTransform(eventId: Long, qrX: Float, qrY: Float, qrScale: Float, qrRotation: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val event = eventRepository.getEventById(eventId)
            if (event != null) {
                val updatedEvent = event.copy(qrX = qrX, qrY = qrY, qrScale = qrScale, qrRotation = qrRotation)
                eventRepository.updateEvent(updatedEvent)
                if (eventId == activeEventId.value) {
                    _activeEvent.value = updatedEvent
                }
            }
        }
    }

    private fun showErrorToast(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSuccessToast(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteEvent(id: Long) {
        if (id == activeEventId.value) {
            showErrorToast("Tidak bisa menghapus event yang sedang aktif. Pindah ke event lain terlebih dahulu.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            eventRepository.deleteEvent(id)
            ticketRepository.deleteAllTickets(id)
        }
    }

    private fun refreshTicketCount() {
        viewModelScope.launch {
            val evtId = activeEventId.value
            val tickets = ticketRepository.getAllTickets(evtId)
            _ticketCount.value = tickets.size
            _ticketList.value = tickets
            _scannedTicketCount.value = tickets.count { it.isScanned }
            _historyLogs.value = historyLogRepository.getLogsForEvent(evtId)
        }
    }

    suspend fun insertBatchTickets(
        tickets: List<Ticket>, 
        isGenerated: Boolean = false, 
        categoryForLog: String = "", 
        eventNameForLog: String = ""
    ): Int = withContext(Dispatchers.IO) {
        val evtId = activeEventId.value
        val existingTickets = ticketRepository.getAllTickets(evtId)
        val existingCodes = existingTickets.map { it.qrContent }.toSet()
        val newTickets = tickets.filter { it.qrContent !in existingCodes }
        if (newTickets.isNotEmpty()) {
            ticketRepository.insertTickets(newTickets)
            if (isGenerated) {
                historyLogRepository.insertLog(com.tkrz.qrtix.data.HistoryLog(
                    eventId = evtId,
                    action = "Generate",
                    description = "Generate ${newTickets.size} tiket $categoryForLog untuk event $eventNameForLog",
                    details = newTickets.joinToString("||") { "${it.qrContent}::${it.ticketType}" }
                ))
            }
            refreshTicketCount()
        }
        newTickets.size
    }

    fun clearAllTickets() {
        viewModelScope.launch {
            val evtId = activeEventId.value
            recentlyDeletedTickets = _ticketList.value
            ticketRepository.deleteAllTickets(evtId)
            try {
                ticketRepository.resetSequence()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val detailsStr = serializeTickets(recentlyDeletedTickets ?: emptyList())
            historyLogRepository.insertLog(com.tkrz.qrtix.data.HistoryLog(eventId = evtId, action = "Hapus Semua", description = "Penghapusan semua tiket", details = detailsStr))
            refreshTicketCount()
        }
    }

    fun deleteTickets(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            recentlyDeletedTickets = _ticketList.value.filter { it.id in ids }
            ticketRepository.deleteTickets(ids)
            val detailsStr = serializeTickets(recentlyDeletedTickets ?: emptyList())
            val desc = if (ids.size == 1) "Penghapusan pada kode: ${recentlyDeletedTickets?.firstOrNull()?.qrContent}" else "Penghapusan ${ids.size} tiket"
            historyLogRepository.insertLog(com.tkrz.qrtix.data.HistoryLog(eventId = activeEventId.value, action = "Hapus", description = desc, details = detailsStr))
            refreshTicketCount()
        }
    }

    fun undoDelete() {
        recentlyDeletedTickets?.let { tickets ->
            if (tickets.isNotEmpty()) {
                viewModelScope.launch {
                    ticketRepository.insertTickets(tickets)
                    // No need to insert "Batal Hapus" since we have a dedicated Undo button on History log now.
                    // Instead, the old implementation used this for Snackbar Undo.
                    // We can choose not to log Snackbar Undos, or use the undoHistoryLog method if passed.
                    refreshTicketCount()
                    recentlyDeletedTickets = null
                }
            }
        }
    }

    fun updateTicket(id: Int, newQr: String, newType: String) {
        viewModelScope.launch {
            try {
                val oldTicket = _ticketList.value.find { it.id == id }
                val oldQr = oldTicket?.qrContent ?: "Tidak diketahui"
                val oldCat = oldTicket?.ticketType ?: "Tidak diketahui"
                ticketRepository.updateTicket(id, newQr, newType, System.currentTimeMillis())
                historyLogRepository.insertLog(com.tkrz.qrtix.data.HistoryLog(
                    eventId = activeEventId.value, 
                    action = "Edit", 
                    description = "Pengeditan pada kode tiket $oldQr",
                    details = "Sebelumnya: $oldQr ($oldCat)||Setelah diedit: $newQr ($newType)"
                ))
                refreshTicketCount()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearScanResult() {
        _scanResultStatus.value = ScanStatus.Idle
    }

    suspend fun processQrCode(rawQr: String): Int {
        val qrContent = rawQr.uppercase().trim()
        val evtId = activeEventId.value
        val evt = activeEvent.value

        // Local pre-check: event code prefix validation (no network needed)
        if (evt != null && !qrContent.startsWith(evt.eventCode.uppercase())) {
            _scanResultStatus.value = ScanStatus.CrossEventError(qrContent)
            return 4 // Cross-Event Error
        }

        // Online validation via Google Sheets
        _isScanLoading.value = true
        val result = ticketRepository.validateAndScanOnline(qrContent, evtId)
        _isScanLoading.value = false

        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())

        return when (result) {
            is com.tkrz.qrtix.data.repository.OnlineScanResult.Success -> {

                val scanTime = dateFormat.format(java.util.Date(result.scannedAt))
                _scanResultStatus.value = ScanStatus.Success(result.ticket, scanTime)
                // Log scan action to history
                historyLogRepository.insertLog(com.tkrz.qrtix.data.HistoryLog(
                    eventId = evtId,
                    action = "Scan",
                    description = "Telah discan kode: $qrContent",
                    timestamp = result.scannedAt
                ))
                refreshTicketCount()
                1
            }
            is com.tkrz.qrtix.data.repository.OnlineScanResult.AlreadyScanned -> {

                val scanTime = if (result.scannedAt > 0) dateFormat.format(java.util.Date(result.scannedAt)) else "Tidak diketahui"
                _scanResultStatus.value = ScanStatus.AlreadyScanned(result.ticket, scanTime)
                2
            }
            is com.tkrz.qrtix.data.repository.OnlineScanResult.NotFound -> {

                _scanResultStatus.value = ScanStatus.Invalid(qrContent)
                3
            }
            is com.tkrz.qrtix.data.repository.OnlineScanResult.NetworkError -> {

                _scanResultStatus.value = ScanStatus.NetworkError(result.message)
                5 // Network Error
            }
        }
    }

    fun exportDataToCsvUri(uri: Uri, onlyScanned: Boolean, onResult: (Boolean) -> Unit) {
        val evtId = activeEventId.value
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    var tickets = ticketRepository.getAllTickets(evtId)
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

    private fun serializeTickets(tickets: List<Ticket>): String {
        return tickets.joinToString("||") { 
            "${it.id}::${it.qrContent}::${it.ticketType}::${it.isScanned}::${it.createdAt}::${it.scannedAt ?: -1}::${it.isModified}::${it.eventId}"
        }
    }

    private fun deserializeTickets(data: String): List<Ticket> {
        if (data.isBlank()) return emptyList()
        return data.split("||").mapNotNull {
            val parts = it.split("::")
            if (parts.size >= 8) {
                Ticket(
                    id = parts[0].toInt(),
                    qrContent = parts[1],
                    ticketType = parts[2],
                    isScanned = parts[3].toBoolean(),
                    createdAt = parts[4].toLongOrNull() ?: System.currentTimeMillis(),
                    scannedAt = parts[5].toLongOrNull().takeIf { time -> time != -1L },
                    isModified = parts[6].toBoolean(),
                    eventId = parts[7].toLongOrNull() ?: 1L
                )
            } else null
        }
    }

    suspend fun exportEventToQrtix(eventId: Long): android.content.Intent? {
        return databaseTransferManager.exportEvent(eventId)
    }

    suspend fun importEventFromQrtix(uri: android.net.Uri): Boolean {
        val success = databaseTransferManager.importEvent(uri)
        if (success) {
            refreshTicketCount() // Refresh event list
        }
        return success
    }

    fun undoHistoryLog(log: com.tkrz.qrtix.data.HistoryLog) {
        if ((log.action == "Hapus" || log.action == "Hapus Semua") && log.details.isNotBlank() && !log.isUndone) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val ticketsToRestore = deserializeTickets(log.details).filter { 
                        it.qrContent.isNotBlank() && it.ticketType.isNotBlank() 
                    }
                    if (ticketsToRestore.isNotEmpty()) {
                        ticketRepository.insertTickets(ticketsToRestore)
                        historyLogRepository.markAsUndone(log.id)
                        refreshTicketCount()
                        showSuccessToast("Berhasil membatalkan penghapusan ${ticketsToRestore.size} tiket")
                    } else {
                        showErrorToast("Data tiket tidak valid atau kosong.")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    showErrorToast("Gagal membatalkan penghapusan: ${e.message}")
                }
            }
        }
    }

    fun clearHistoryLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            historyLogRepository.deleteLogsForEvent(activeEventId.value)
            refreshTicketCount()
        }
    }

    fun startPeriodicSync() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                kotlinx.coroutines.delay(12000L) // 12 seconds
                try {
                    val eventId = activeEventId.value
                    ticketRepository.syncTicketsFromCloud(eventId)
                    _lastSyncTime.value = System.currentTimeMillis()
                    refreshTicketCount()
    
                } catch (e: Exception) {
                    // Silently fail and retry next interval
                }
            }
        }
    }

    fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
    }

    fun syncScannerData(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val eventId = activeEventId.value
                ticketRepository.syncTicketsFromCloud(eventId)
                _lastSyncTime.value = System.currentTimeMillis()
                refreshTicketCount()

                withContext(Dispatchers.Main) {
                    onResult(true, "Sinkronisasi berhasil")
                }
            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    onResult(false, "Gagal sinkronisasi: ${e.message}")
                }
            }
        }
    }

    /**
     * Helper to read data from an external Google Sheet (e.g., Google Form responses).
     * Used by DistributionScreen to preview and map columns.
     */
    suspend fun readExternalSheetData(spreadsheetId: String, range: String): List<List<Any>>? {
        return withContext(Dispatchers.IO) {
            try {
                sheetsService.readRange(spreadsheetId, range)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Helper to get distinct ticket categories currently available in the active event.
     */
    fun getActiveEventCategories(): List<String> {
        // We use the existing state flow's value. 
        // This is safe to call from Composable because it's synchronously available.
        return categories.value
    }
}
