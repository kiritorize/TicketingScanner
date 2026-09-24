package com.tkrz.qrtix.data.repository

import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.SheetProperties
import com.tkrz.qrtix.data.BuyerData
import com.tkrz.qrtix.data.DistributionAssignment
import com.tkrz.qrtix.data.Ticket
import com.tkrz.qrtix.data.ValidationResult
import com.tkrz.qrtix.data.cloud.CloudPreferences
import com.tkrz.qrtix.data.cloud.GoogleSheetsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DistributionRepository @Inject constructor(
    private val sheetsService: GoogleSheetsService,
    private val ticketRepository: TicketRepository,
    private val cloudPreferences: CloudPreferences
) {
    private val emailPattern = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")

    /**
     * Derives a Google Sheets tab name from the event name.
     * Sanitizes characters that are invalid in sheet tab names.
     * Google Sheets tab names cannot contain: ' * ? : [ ] / \
     */
    private fun getDistributionTabName(eventName: String): String {
        val sanitized = eventName.replace(Regex("[*?:\\[\\]/\\\\']"), "").trim().take(80)
        return "Distribusi_${sanitized.ifEmpty { "Event" }}"
    }

    /**
     * Returns the internal QRTix_Data spreadsheet ID.
     * Throws if not available (cloud not initialized).
     */
    private fun getInternalSpreadsheetId(): String {
        return cloudPreferences.spreadsheetId
            ?: throw IllegalStateException("Cloud database belum siap. Pastikan koneksi internet tersedia.")
    }

    /**
     * Validates that the app can read from an external spreadsheet (Google Forms response sheet).
     * Returns true if readable. Throws a descriptive exception on failure.
     */
    suspend fun validateExternalSheetAccess(externalSheetId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            sheetsService.readRange(externalSheetId, "Sheet1!A1:A5")
            true
        } catch (e: Exception) {
            val msg = e.message ?: ""
            when {
                msg.contains("403") || msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                    throw PermissionDeniedException(
                        "Akses ditolak. Minta pemilik spreadsheet untuk membagikan akses baca ke email Anda, atau atur izin ke 'Anyone with the link'."
                    )
                msg.contains("404") || msg.contains("not found", ignoreCase = true) ->
                    throw IllegalArgumentException(
                        "Spreadsheet tidak ditemukan. Pastikan URL yang dimasukkan benar."
                    )
                else -> throw e
            }
        }
    }

    /**
     * Reads existing distribution data from the internal QRTix_Data spreadsheet
     * for the given event.
     */
    suspend fun getExistingDistributionData(eventName: String): List<DistributionAssignment> = withContext(Dispatchers.IO) {
        val internalId = try { getInternalSpreadsheetId() } catch (e: Exception) { return@withContext emptyList() }
        val tabName = getDistributionTabName(eventName)
        val sheetId = try {
            sheetsService.getSheetIdByName(internalId, tabName)
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        if (sheetId == null) return@withContext emptyList()

        val range = "$tabName!A3:G" // Start from A3 (due to 2-row header)
        val data = try {
            sheetsService.readRange(internalId, range)
        } catch (e: Exception) {
            return@withContext emptyList()
        } ?: return@withContext emptyList()

        data.map { row ->
            DistributionAssignment(
                buyerName = row.getOrNull(0)?.toString() ?: "",
                buyerEmail = row.getOrNull(1)?.toString() ?: "",
                ticketCategory = row.getOrNull(2)?.toString() ?: "",
                ticketCodes = row.getOrNull(3)?.toString()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                emailStatus = row.getOrNull(4)?.toString() ?: "PENDING",
                sentAt = row.getOrNull(5)?.toString() ?: "",
                errorMessage = row.getOrNull(6)?.toString() ?: ""
            )
        }
    }

    suspend fun processDistributionData(
        buyers: List<BuyerData>,
        eventId: Long,
        eventName: String
    ): ValidationResult = withContext(Dispatchers.Default) {

        // Check for existing frozen mapping in the internal spreadsheet
        val existingData = getExistingDistributionData(eventName)
        if (existingData.isNotEmpty()) {
            val totalTickets = existingData.sumOf { it.ticketCodes.size }
            val categoryBreakdown = existingData.groupBy { it.ticketCategory }
                .mapValues { entry -> entry.value.sumOf { it.ticketCodes.size } }

            return@withContext ValidationResult(
                isValid = true,
                totalValidBuyers = existingData.size,
                totalTicketsToDistribute = totalTickets,
                categoryBreakdown = categoryBreakdown,
                errors = emptyList(),
                assignments = existingData
            )
        }

        // We don't have frozen data, let's validate and assign
        val errors = mutableListOf<String>()
        val validBuyers = mutableListOf<BuyerData>()

        // 1. Data Validation
        for ((index, buyer) in buyers.withIndex()) {
            val rowNum = index + 1
            if (buyer.name.isBlank()) {
                errors.add("Baris $rowNum: Nama kosong.")
                continue
            }
            if (buyer.email.isBlank() || !emailPattern.matcher(buyer.email).matches()) {
                errors.add("Baris $rowNum: Format email tidak valid (${buyer.email}).")
                continue
            }
            if (buyer.requestedQty <= 0) {
                errors.add("Baris $rowNum: Jumlah tiket untuk ${buyer.name} kurang dari atau sama dengan 0.")
                continue
            }
            if (buyer.category.isBlank()) {
                errors.add("Baris $rowNum: Kategori tiket tidak dikenali untuk ${buyer.name}.")
                continue
            }
            validBuyers.add(buyer)
        }

        // Fetch local tickets
        val allLocalTickets = ticketRepository.getAllTickets(eventId)

        // 2. Quantity Validation
        val requestedByCategory = validBuyers.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.requestedQty } }

        val availableTicketsByCategory = allLocalTickets.groupBy { it.ticketType }.toMutableMap()

        for ((category, requestedQty) in requestedByCategory) {
            val availableQty = availableTicketsByCategory[category]?.size ?: 0
            if (requestedQty > availableQty) {
                errors.add("Kategori '$category': Diminta $requestedQty, namun hanya tersedia $availableQty.")
            }
        }

        // If validation errors exist, halt assignment
        if (errors.isNotEmpty()) {
            return@withContext ValidationResult(
                isValid = false,
                totalValidBuyers = validBuyers.size,
                totalTicketsToDistribute = validBuyers.sumOf { it.requestedQty },
                categoryBreakdown = requestedByCategory,
                errors = errors,
                assignments = emptyList()
            )
        }

        // 3. Match Validation & Assignment
        val assignments = mutableListOf<DistributionAssignment>()
        val ticketQueues = availableTicketsByCategory.mapValues { it.value.toMutableList() }

        for (buyer in validBuyers) {
            val queue = ticketQueues[buyer.category]
            if (queue == null || queue.size < buyer.requestedQty) {
                errors.add("Kesalahan sistem: Tiket tidak cukup untuk ${buyer.name} (Kategori: ${buyer.category}).")
                continue
            }

            val assignedTickets = mutableListOf<Ticket>()
            for (i in 0 until buyer.requestedQty) {
                assignedTickets.add(queue.removeAt(0))
            }

            if (assignedTickets.size != buyer.requestedQty) {
                errors.add("Kesalahan sistem: Gagal mengalokasikan tiket untuk ${buyer.name}.")
                continue
            }

            assignments.add(
                DistributionAssignment(
                    buyerName = buyer.name,
                    buyerEmail = buyer.email,
                    ticketCategory = buyer.category,
                    ticketCodes = assignedTickets.map { it.qrContent },
                    emailStatus = "PENDING",
                    sentAt = "",
                    errorMessage = ""
                )
            )
        }

        ValidationResult(
            isValid = errors.isEmpty(),
            totalValidBuyers = validBuyers.size,
            totalTicketsToDistribute = validBuyers.sumOf { it.requestedQty },
            categoryBreakdown = requestedByCategory,
            errors = errors,
            assignments = assignments
        )
    }

    /**
     * Saves the distribution mapping to the internal QRTix_Data spreadsheet
     * in an event-specific tab.
     */
    suspend fun saveDistributionMapping(
        eventId: Long,
        eventName: String,
        assignments: List<DistributionAssignment>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val internalId = getInternalSpreadsheetId()
            val tabName = getDistributionTabName(eventName)

            // Check if distribution tab exists, if not create it
            var sheetId = sheetsService.getSheetIdByName(internalId, tabName)
            if (sheetId == null) {
                val addSheetRequest = Request().setAddSheet(
                    AddSheetRequest().setProperties(
                        SheetProperties().setTitle(tabName)
                    )
                )
                sheetsService.batchUpdate(internalId, listOf(addSheetRequest))
                sheetId = sheetsService.getSheetIdByName(internalId, tabName)
            }

            // Write header and data
            val total = assignments.size
            val headerData = listOf(
                listOf("DISTRIBUSI TIKET — $eventName", "", "", "", "", "", ""),
                listOf("Total: $total", "Terkirim: 0", "Gagal: 0", "Menunggu: $total", "Progres: 0.0%", "", ""),
                listOf("Nama Pembeli", "Email", "Kategori Tiket", "Kode Tiket (Dipisahkan koma)", "Status Email", "Waktu Terkirim", "Pesan Error")
            )

            val rowData = assignments.map {
                listOf(
                    it.buyerName,
                    it.buyerEmail,
                    it.ticketCategory,
                    it.ticketCodes.joinToString(","),
                    it.emailStatus,
                    it.sentAt,
                    it.errorMessage
                )
            }

            val allData = headerData + rowData

            val range = "$tabName!A1"
            sheetsService.appendRows(internalId, range, allData)

            // Apply conditional formatting
            sheetId?.let { applyConditionalFormatting(internalId, it) }

            true
        } catch (e: Exception) {
            android.util.Log.e("Distribution", "Failed to save distribution", e)
            throw e
        }
    }

    suspend fun updateEmailStatus(
        eventName: String,
        rowIndex: Int,
        status: String,
        sentAt: String,
        errorMessage: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val internalId = getInternalSpreadsheetId()
            val tabName = getDistributionTabName(eventName)
            val range = "$tabName!E$rowIndex:G$rowIndex"
            val values = listOf<Any>(status, sentAt, errorMessage)
            sheetsService.updateRow(internalId, range, values)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateSummaryRow(
        eventName: String,
        total: Int,
        sent: Int,
        failed: Int,
        pending: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val internalId = getInternalSpreadsheetId()
            val tabName = getDistributionTabName(eventName)
            val progress = if (total > 0) (sent.toFloat() / total * 100) else 0f
            val formattedProgress = String.format("%.1f%%", progress)
            val range = "$tabName!A2:E2"
            val values = listOf<Any>("Total: $total", "Terkirim: $sent", "Gagal: $failed", "Menunggu: $pending", "Progres: $formattedProgress")
            sheetsService.updateRow(internalId, range, values)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun applyConditionalFormatting(spreadsheetId: String, sheetId: Int) {
        val requests = mutableListOf<Request>()

        fun createRule(status: String, red: Float, green: Float, blue: Float): Request {
            return Request().setAddConditionalFormatRule(
                com.google.api.services.sheets.v4.model.AddConditionalFormatRuleRequest().setRule(
                    com.google.api.services.sheets.v4.model.ConditionalFormatRule()
                        .setRanges(listOf(
                            com.google.api.services.sheets.v4.model.GridRange()
                                .setSheetId(sheetId)
                                .setStartColumnIndex(4) // Column E
                                .setEndColumnIndex(5)
                                .setStartRowIndex(3) // Row 4
                        ))
                        .setBooleanRule(
                            com.google.api.services.sheets.v4.model.BooleanRule()
                                .setCondition(
                                    com.google.api.services.sheets.v4.model.BooleanCondition()
                                        .setType("TEXT_EQ")
                                        .setValues(listOf(
                                            com.google.api.services.sheets.v4.model.ConditionValue().setUserEnteredValue(status)
                                        ))
                                )
                                .setFormat(
                                    com.google.api.services.sheets.v4.model.CellFormat()
                                        .setBackgroundColor(
                                            com.google.api.services.sheets.v4.model.Color()
                                                .setRed(red)
                                                .setGreen(green)
                                                .setBlue(blue)
                                        )
                                )
                        )
                ).setIndex(0)
            )
        }

        // SENT -> Green
        requests.add(createRule("SENT", 0.85f, 0.93f, 0.83f))
        // FAILED -> Red
        requests.add(createRule("FAILED", 0.96f, 0.8f, 0.8f))
        // PENDING -> Yellow
        requests.add(createRule("PENDING", 0.99f, 0.95f, 0.8f))

        try {
            sheetsService.batchUpdate(spreadsheetId, requests)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Specific exception for permission denied errors when accessing external spreadsheets.
 */
class PermissionDeniedException(message: String) : Exception(message)
