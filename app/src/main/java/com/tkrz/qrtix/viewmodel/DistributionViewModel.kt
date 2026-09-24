package com.tkrz.qrtix.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkrz.qrtix.data.BuyerData
import com.tkrz.qrtix.data.ValidationResult
import com.tkrz.qrtix.data.cloud.CloudPreferences
import com.tkrz.qrtix.data.repository.DistributionRepository
import com.tkrz.qrtix.data.repository.PermissionDeniedException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter

@HiltViewModel
class DistributionViewModel @Inject constructor(
    private val distributionRepository: DistributionRepository,
    private val gmailService: com.tkrz.qrtix.data.cloud.GmailService,
    private val eventRepository: com.tkrz.qrtix.data.repository.EventRepository,
    private val historyLogRepository: com.tkrz.qrtix.data.repository.HistoryLogRepository,
    private val eventPreferences: com.tkrz.qrtix.data.EventPreferences,
    private val cloudPreferences: CloudPreferences
) : ViewModel() {

    private val _validationResult = MutableStateFlow<ValidationResult?>(null)
    val validationResult: StateFlow<ValidationResult?> = _validationResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Validates that the app can read from an external spreadsheet.
     * Should be called in Step 1 before fetching data.
     */
    suspend fun validateExternalSheetAccess(externalSheetId: String): Boolean {
        return try {
            distributionRepository.validateExternalSheetAccess(externalSheetId)
        } catch (e: PermissionDeniedException) {
            _errorMessage.value = e.message
            false
        } catch (e: IllegalArgumentException) {
            _errorMessage.value = e.message
            false
        } catch (e: Exception) {
            _errorMessage.value = "Gagal mengakses spreadsheet: ${e.message}"
            false
        }
    }

    fun processDistributionData(buyers: List<BuyerData>, eventId: Long, eventName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = distributionRepository.processDistributionData(buyers, eventId, eventName)
                _validationResult.value = result
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Terjadi kesalahan saat memproses data distribusi."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveDistributionMapping(eventId: Long, eventName: String) {
        val result = _validationResult.value ?: return
        if (!result.isValid && result.errors.isNotEmpty()) {
            _errorMessage.value = "Terdapat error validasi, tidak bisa menyimpan."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val success = distributionRepository.saveDistributionMapping(
                    eventId = eventId,
                    eventName = eventName,
                    assignments = result.assignments
                )
                if (success) {
                    _saveSuccess.value = true
                    // Save the internal spreadsheet ID as distributionSheetId to mark distribution as done
                    val internalSpreadsheetId = cloudPreferences.spreadsheetId
                    val event = eventRepository.getEventById(eventId)
                    if (event != null && internalSpreadsheetId != null) {
                        eventRepository.updateEvent(event.copy(distributionSheetId = internalSpreadsheetId))
                    }
                } else {
                    _errorMessage.value = "Gagal menyimpan data distribusi ke Google Sheets tanpa error spesifik."
                }
            } catch (e: Exception) {
                android.util.Log.e("Distribution", "ViewModel save failed", e)
                val msg = e.message ?: ""
                val userMsg = when {
                    msg.contains("403") || msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                        "Akses ditolak. Pastikan cloud database sudah diinisialisasi."
                    msg.contains("not found", ignoreCase = true) || msg.contains("404") ->
                        "Spreadsheet internal tidak ditemukan. Pastikan cloud database sudah diinisialisasi."
                    msg.contains("Quota exceeded", ignoreCase = true) || msg.contains("429") ->
                        "Limit API Google Sheets terlampaui. Coba lagi nanti."
                    msg.contains("timeout", ignoreCase = true) ->
                        "Koneksi timeout. Periksa internet Anda."
                    msg.contains("already exists", ignoreCase = true) ->
                        "Sheet distribusi sudah ada. Data sebelumnya akan ditampilkan."
                    msg.contains("Cloud database belum siap", ignoreCase = true) ->
                        msg
                    else -> "Gagal menyimpan: ${e.message}"
                }
                _errorMessage.value = userMsg
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearState() {
        _validationResult.value = null
        _saveSuccess.value = false
        _errorMessage.value = null
    }

    private val _sendingProgress = MutableStateFlow(com.tkrz.qrtix.data.SendingProgress())
    val sendingProgress: StateFlow<com.tkrz.qrtix.data.SendingProgress> = _sendingProgress.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _sendLogs = MutableStateFlow<List<com.tkrz.qrtix.data.SendLogEntry>>(emptyList())
    val sendLogs: StateFlow<List<com.tkrz.qrtix.data.SendLogEntry>> = _sendLogs.asStateFlow()

    fun startSending(eventName: String) {
        if (_isSending.value) return
        _isSending.value = true
        _isPaused.value = false

        viewModelScope.launch {
            try {
                // Fetch current assignments from internal spreadsheet
                val assignments = distributionRepository.getExistingDistributionData(eventName)

                var total = assignments.size
                var sent = assignments.count { it.emailStatus == "SENT" }
                var failed = assignments.count { it.emailStatus == "FAILED" }
                var pending = total - sent - failed

                _sendingProgress.value = com.tkrz.qrtix.data.SendingProgress(total, sent, failed, pending)
                distributionRepository.updateSummaryRow(eventName, total, sent, failed, pending)

                if (pending == 0) {
                    _isSending.value = false
                    return@launch
                }

                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())

                for ((index, assignment) in assignments.withIndex()) {
                    if (_isPaused.value) {
                        break
                    }
                    if (assignment.emailStatus == "SENT") {
                        continue
                    }

                    // Attempt sending
                    val rowIndex = index + 4 // Row 1-2 headers, Row 3 column headers, Row 4 data starts

                    val attachments = assignment.ticketCodes.map { code ->
                        val bytes = com.tkrz.qrtix.utils.QrHelper.generateSimpleQrBytes(code)
                        Pair(bytes, "QRTix_${code.replace("-", "_")}.png")
                    }

                    // Construct email body
                    val subject = "$eventName — Tiket Anda"
                    val htmlBody = """
                        <div style="font-family: sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px;">
                            <h2 style="color: #4F46E5;">Halo ${assignment.buyerName},</h2>
                            <p>Terima kasih telah memesan tiket untuk <b>$eventName</b>.</p>
                            <p>Berikut adalah detail pesanan Anda:</p>
                            <table style="width: 100%; border-collapse: collapse; margin-bottom: 20px;">
                                <tr style="background-color: #f9f9f9;">
                                    <td style="padding: 8px; border: 1px solid #ddd;"><b>Kategori</b></td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${assignment.ticketCategory}</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px; border: 1px solid #ddd;"><b>Jumlah Tiket</b></td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${assignment.ticketCodes.size}</td>
                                </tr>
                            </table>
                            <p>QR Code tiket Anda telah dilampirkan pada email ini. Harap tunjukkan QR Code tersebut pada saat memasuki area acara.</p>
                            <br>
                            <p>Salam hangat,</p>
                            <p><b>Tim QRTix</b></p>
                        </div>
                    """.trimIndent()

                    val timestamp = sdf.format(java.util.Date())
                    var newStatus = "FAILED"
                    var newErrorMsg = ""

                    try {
                        val success = gmailService.sendEmailWithAttachment(
                            to = assignment.buyerEmail,
                            subject = subject,
                            htmlBody = htmlBody,
                            attachments = attachments
                        )
                        if (success) {
                            newStatus = "SENT"
                            sent++
                            pending--
                        } else {
                            newErrorMsg = "Gagal mengirim email (Tidak diketahui)"
                            failed++
                            pending--
                        }
                    } catch (e: Exception) {
                        newErrorMsg = e.message ?: "Gagal mengirim email"
                        failed++
                        pending--
                    }

                    // Update internal Sheets
                    distributionRepository.updateEmailStatus(eventName, rowIndex, newStatus, timestamp, newErrorMsg)

                    // Update Local State
                    _sendingProgress.value = com.tkrz.qrtix.data.SendingProgress(total, sent, failed, pending)

                    // Add Log
                    val newLog = com.tkrz.qrtix.data.SendLogEntry(timestamp, assignment.buyerEmail, newStatus, newErrorMsg)
                    _sendLogs.value = listOf(newLog) + _sendLogs.value.take(49) // Keep last 50

                    // Update summary row every 5 emails or at the end
                    if ((sent + failed) % 5 == 0 || pending == 0) {
                        distributionRepository.updateSummaryRow(eventName, total, sent, failed, pending)
                    }

                    // Rate limiting (unless it's the last item or we are pausing)
                    if (pending > 0 && !_isPaused.value) {
                        kotlinx.coroutines.delay(7500) // ~7.5 seconds delay to stay under quota (480/hour limit)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Terjadi kesalahan sistem saat mengirim."
            } finally {
                _isSending.value = false
                if (_sendingProgress.value.pending == 0 && (_sendingProgress.value.sent > 0 || _sendingProgress.value.failed > 0)) {
                    val s = _sendingProgress.value.sent
                    val f = _sendingProgress.value.failed
                    historyLogRepository.insertLog(com.tkrz.qrtix.data.HistoryLog(
                        eventId = eventPreferences.activeEventId.value,
                        action = "Distribusi",
                        description = "Kirim tiket ke ${s + f} penerima via email",
                        details = "Berhasil: $s, Gagal: $f"
                    ))
                }
            }
        }
    }

    fun pauseSending() {
        _isPaused.value = true
    }

    fun resumeSending(eventName: String) {
        startSending(eventName)
    }

    fun loadStatus(eventName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val assignments = distributionRepository.getExistingDistributionData(eventName)

                if (assignments.isEmpty()) {
                    _errorMessage.value = "Data distribusi tidak ditemukan. Silakan mulai proses distribusi baru."
                    _isLoading.value = false
                    return@launch
                }

                val total = assignments.size
                val sent = assignments.count { it.emailStatus == "SENT" }
                val failed = assignments.count { it.emailStatus == "FAILED" }
                val pending = total - sent - failed

                _sendingProgress.value = com.tkrz.qrtix.data.SendingProgress(total, sent, failed, pending)

                val initialLogs = assignments.filter { it.emailStatus != "PENDING" }
                    .takeLast(10)
                    .map { com.tkrz.qrtix.data.SendLogEntry(it.sentAt, it.buyerEmail, it.emailStatus, it.errorMessage) }
                    .reversed()

                _sendLogs.value = initialLogs

            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Gagal memuat status distribusi."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retryFailed(eventName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val assignments = distributionRepository.getExistingDistributionData(eventName)
                for ((index, assignment) in assignments.withIndex()) {
                    if (assignment.emailStatus == "FAILED") {
                        val rowIndex = index + 4
                        distributionRepository.updateEmailStatus(eventName, rowIndex, "PENDING", "", "")
                    }
                }
                startSending(eventName)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Gagal menyiapkan retry."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportReport(context: Context, eventName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val assignments = distributionRepository.getExistingDistributionData(eventName)

                // Create Event folder
                val sanitizedEventName = eventName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "QRTix")
                val eventDir = File(appDir, sanitizedEventName)
                if (!eventDir.exists()) {
                    eventDir.mkdirs()
                }

                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val reportFile = File(eventDir, "Laporan_Distribusi_${sanitizedEventName}_${timestamp}.csv")

                FileWriter(reportFile).use { writer ->
                    writer.append("Nama Pembeli,Email,Kategori,Jumlah Tiket,Status,Waktu,Pesan Error\n")
                    for (assignment in assignments) {
                        val name = assignment.buyerName.replace(",", " ")
                        val email = assignment.buyerEmail.replace(",", " ")
                        val category = assignment.ticketCategory.replace(",", " ")
                        val ticketsCount = assignment.ticketCodes.size
                        val status = assignment.emailStatus
                        val time = assignment.sentAt.replace(",", " ")
                        val errorMsg = assignment.errorMessage.replace(",", " ")
                        writer.append("$name,$email,$category,$ticketsCount,$status,$time,$errorMsg\n")
                    }
                }
                _errorMessage.value = "Laporan berhasil diekspor ke ${reportFile.absolutePath}"
            } catch (e: Exception) {
                _errorMessage.value = "Gagal mengekspor laporan: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
