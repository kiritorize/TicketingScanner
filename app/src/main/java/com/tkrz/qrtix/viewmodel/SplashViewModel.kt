package com.tkrz.qrtix.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkrz.qrtix.data.AuthPreferences
import com.tkrz.qrtix.data.cloud.CloudPreferences
import com.tkrz.qrtix.data.cloud.MediaManager
import com.tkrz.qrtix.data.cloud.SpreadsheetManager
import com.tkrz.qrtix.data.repository.CategoryRepository
import com.tkrz.qrtix.data.repository.EventRepository
import com.tkrz.qrtix.data.repository.HistoryLogRepository
import com.tkrz.qrtix.data.repository.TicketRepository
import com.tkrz.qrtix.utils.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SyncStage(val label: String, val progress: Float) {
    CHECKING_NETWORK("Memeriksa koneksi internet...", 0.0f),
    CONNECTING_DRIVE("Menghubungkan ke Google Drive...", 0.15f),
    SYNCING_EVENTS("Sinkronisasi profil event...", 0.30f),
    SYNCING_CATEGORIES("Sinkronisasi kategori...", 0.50f),
    SYNCING_TICKETS("Sinkronisasi database tiket...", 0.70f),
    DOWNLOADING_MEDIA("Mengunduh media...", 0.85f),
    COMPLETE("Siap!", 1.0f)
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val authPreferences: AuthPreferences,
    private val spreadsheetManager: SpreadsheetManager,
    private val eventRepository: EventRepository,
    private val categoryRepository: CategoryRepository,
    private val ticketRepository: TicketRepository,
    private val historyLogRepository: HistoryLogRepository,
    private val mediaManager: MediaManager,
    private val eventPreferences: com.tkrz.qrtix.data.EventPreferences,
    private val cloudPreferences: CloudPreferences
) : ViewModel() {

    private val _currentStage = MutableStateFlow(SyncStage.CHECKING_NETWORK)
    val currentStage: StateFlow<SyncStage> = _currentStage.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _isOfflineBlocking = MutableStateFlow(false)
    val isOfflineBlocking: StateFlow<Boolean> = _isOfflineBlocking.asStateFlow()
    
    private val _isAuthMissing = MutableStateFlow(false)
    val isAuthMissing: StateFlow<Boolean> = _isAuthMissing.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    fun startSync() {
        viewModelScope.launch {
            _syncError.value = null
            _isOfflineBlocking.value = false
            _isAuthMissing.value = false
            _isFinished.value = false
            
            // Step 1: Check Network
            _currentStage.value = SyncStage.CHECKING_NETWORK
            delay(500) // Small delay for visual effect
            val isConnected = networkMonitor.checkConnectivity()
            if (!isConnected) {
                _isOfflineBlocking.value = true
                return@launch
            }

            // Step 2: Check Auth
            if (!authPreferences.isSignedIn.value) {
                _isAuthMissing.value = true
                return@launch
            }

            val email = authPreferences.userEmail
            if (email.isNullOrBlank()) {
                _isAuthMissing.value = true
                return@launch
            }
            cloudPreferences.activeEmail = email

            try {
                performSyncWithRetry(email)
                
                // Step 8: Complete
                _currentStage.value = SyncStage.COMPLETE
                delay(500) // Let user see the 100% completion
                _isFinished.value = true

            } catch (e: Exception) {
                _syncError.value = "Gagal sinkronisasi: ${e.message}"
            }
        }
    }

    private suspend fun performSyncWithRetry(email: String) {
        try {
            executeSyncStages(email)
        } catch (e: Exception) {
            if (isSpreadsheetNotFoundError(e)) {
                // Clear stale spreadsheet ID and re-run initialization and sync
                cloudPreferences.setSpreadsheetId(email, null)
                cloudPreferences.spreadsheetId = null

                _currentStage.value = SyncStage.CONNECTING_DRIVE
                val reInitResult = spreadsheetManager.initializeSpreadsheet(email)
                if (!reInitResult.isSuccess) {
                    val ex = reInitResult.exceptionOrNull()
                    throw ex ?: Exception("Gagal menyiapkan database cloud.")
                }
                executeSyncStages(email)
            } else {
                throw e
            }
        }
    }

    private suspend fun executeSyncStages(email: String) {
        // Step 3: Connect Drive/Sheets
        _currentStage.value = SyncStage.CONNECTING_DRIVE
        val initResult = spreadsheetManager.initializeSpreadsheet(email)
        if (!initResult.isSuccess) {
            val ex = initResult.exceptionOrNull()
            if (ex is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                _syncError.value = "Izin Google Account diperlukan."
            } else {
                _syncError.value = "Gagal menghubungkan ke cloud."
            }
            throw ex ?: Exception("Gagal menghubungkan ke cloud.")
        }
        
        // Step 4: Sync Events & Logs
        _currentStage.value = SyncStage.SYNCING_EVENTS
        eventRepository.syncEventsFromCloud()
        historyLogRepository.syncLogsFromCloud()

        // Step 5: Sync Categories (for all events)
        _currentStage.value = SyncStage.SYNCING_CATEGORIES
        val allEvents = eventRepository.getAllEvents().first()
        for (event in allEvents) {
            categoryRepository.syncCategoriesFromCloud(event.id)
        }
        
        // Step 6: Sync Tickets
        _currentStage.value = SyncStage.SYNCING_TICKETS
        val activeEventId = eventPreferences.activeEventId.value
        ticketRepository.syncTicketsFromCloud(activeEventId)

        // Step 7: Download Media
        _currentStage.value = SyncStage.DOWNLOADING_MEDIA
        for (event in allEvents) {
            if (!event.logoPath.isNullOrBlank()) {
                mediaManager.getOrDownloadMedia(event.logoPath)
            }
            if (!event.bgPath.isNullOrBlank()) {
                mediaManager.getOrDownloadMedia(event.bgPath)
            }
        }
    }

    private fun isSpreadsheetNotFoundError(e: Throwable): Boolean {
        if (e is com.google.api.client.googleapis.json.GoogleJsonResponseException && e.statusCode == 404) {
            return true
        }
        val msg = e.message ?: ""
        return msg.contains("404") || msg.contains("Not Found", ignoreCase = true) || msg.contains("Requested entity was not found", ignoreCase = true)
    }
}
