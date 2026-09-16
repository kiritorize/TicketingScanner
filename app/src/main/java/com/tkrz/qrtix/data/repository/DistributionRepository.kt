package com.tkrz.qrtix.data.repository

import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.AddSheetResponse
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.SheetProperties
import com.tkrz.qrtix.data.BuyerData
import com.tkrz.qrtix.data.DistributionAssignment
import com.tkrz.qrtix.data.Ticket
import com.tkrz.qrtix.data.ValidationResult
import com.tkrz.qrtix.data.cloud.GoogleSheetsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DistributionRepository @Inject constructor(
    private val sheetsService: GoogleSheetsService,
    private val ticketRepository: TicketRepository
) {
    private val emailPattern = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")
    private val distributionSheetName = "Distribution"

    suspend fun getExistingDistributionData(spreadsheetId: String): List<DistributionAssignment> = withContext(Dispatchers.IO) {
        val sheetId = sheetsService.getSheetIdByName(spreadsheetId, distributionSheetName) ?: return@withContext emptyList()
        val range = "$distributionSheetName!A3:G" // Start from A3 (due to 2-row header)
        val data = sheetsService.readRange(spreadsheetId, range) ?: return@withContext emptyList()
        
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
        spreadsheetId: String // Pass the source spreadsheetId to check if distribution exists
    ): ValidationResult = withContext(Dispatchers.Default) {
        
        // Check for existing frozen mapping in Sheets
        val existingData = getExistingDistributionData(spreadsheetId)
        if (existingData.isNotEmpty()) {
            val totalTickets = existingData.sumOf { it.ticketCodes.size }
            val categoryBreakdown = existingData.groupBy { it.ticketCategory }
                .mapValues { entry -> entry.value.sumOf { it.ticketCodes.size } }
            
            return@withContext ValidationResult(
                isValid = true,
                totalValidBuyers = existingData.size,
                totalTicketsToDistribute = totalTickets,
                categoryBreakdown = categoryBreakdown,
                errors = emptyList(), // No validation errors if we are loading frozen data
                assignments = existingData
            )
        }

        // We don't have frozen data, let's validate and assign
        val errors = mutableListOf<String>()
        val validBuyers = mutableListOf<BuyerData>()

        // 1. Data Validation
        for ((index, buyer) in buyers.withIndex()) {
            val rowNum = index + 1 // Assuming 1-based indexing for user clarity, but form rows might vary. Just use logical index.
            if (buyer.name.isBlank()) {
                errors.add("Baris \$rowNum: Nama kosong.")
                continue
            }
            if (buyer.email.isBlank() || !emailPattern.matcher(buyer.email).matches()) {
                errors.add("Baris \$rowNum: Format email tidak valid (\${buyer.email}).")
                continue
            }
            if (buyer.requestedQty <= 0) {
                errors.add("Baris \$rowNum: Jumlah tiket untuk \${buyer.name} kurang dari atau sama dengan 0.")
                continue
            }
            if (buyer.category.isBlank()) {
                errors.add("Baris \$rowNum: Kategori tiket tidak dikenali untuk \${buyer.name}.")
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
                errors.add("Kategori '\$category': Diminta \$requestedQty, namun hanya tersedia \$availableQty.")
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
                // This shouldn't happen if quantity validation passed, but just in case
                errors.add("Kesalahan sistem: Tiket tidak cukup untuk \${buyer.name} (Kategori: \${buyer.category}).")
                continue
            }
            
            val assignedTickets = mutableListOf<Ticket>()
            for (i in 0 until buyer.requestedQty) {
                assignedTickets.add(queue.removeAt(0)) // Pop first available ticket
            }
            
            if (assignedTickets.size != buyer.requestedQty) {
                errors.add("Kesalahan sistem: Gagal mengalokasikan tiket untuk \${buyer.name}.")
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

    suspend fun saveDistributionMapping(
        spreadsheetId: String, 
        eventId: Long, 
        eventName: String, 
        assignments: List<DistributionAssignment>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if Distribution sheet exists, if not create it
            var sheetId = sheetsService.getSheetIdByName(spreadsheetId, distributionSheetName)
            if (sheetId == null) {
                val addSheetRequest = Request().setAddSheet(
                    AddSheetRequest().setProperties(
                        SheetProperties().setTitle(distributionSheetName)
                    )
                )
                sheetsService.batchUpdate(spreadsheetId, listOf(addSheetRequest))
                // Note: to get the ID, we'd need the response, but we can just query it again
                sheetId = sheetsService.getSheetIdByName(spreadsheetId, distributionSheetName)
            }

            // Write header and data
            // Header Row 1 & 2 as requested in Task 9.3 summary format
            val total = assignments.size
            val headerData = listOf(
                listOf("DISTRIBUSI TIKET — \$eventName", "", "", "", "", "", ""),
                listOf("Total: \$total", "Terkirim: 0", "Gagal: 0", "Menunggu: \$total", "Progres: 0.0%", "", ""),
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
            
            // Clear existing if any (simplistic approach: just write over it)
            // But usually this is a one-time write per event. We'll write to A1.
            val range = "\$distributionSheetName!A1"
            sheetsService.appendRows(spreadsheetId, range, allData) 
            // Wait, appendRows will add to the end of existing data.
            // But we know it's either empty (newly created) or we shouldn't be overwriting it anyway.
            // Actually, if it was just created, appendRows is fine.
            // Apply conditional formatting
            applyConditionalFormatting(spreadsheetId, sheetId)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateEmailStatus(
        spreadsheetId: String,
        rowIndex: Int,
        status: String,
        sentAt: String,
        errorMessage: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val range = "$distributionSheetName!E$rowIndex:G$rowIndex"
            val values = listOf<Any>(status, sentAt, errorMessage)
            sheetsService.updateRow(spreadsheetId, range, values)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateSummaryRow(
        spreadsheetId: String,
        total: Int,
        sent: Int,
        failed: Int,
        pending: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val progress = if (total > 0) (sent.toFloat() / total * 100) else 0f
            val formattedProgress = String.format("%.1f%%", progress)
            val range = "$distributionSheetName!A2:E2"
            val values = listOf<Any>("Total: $total", "Terkirim: $sent", "Gagal: $failed", "Menunggu: $pending", "Progres: $formattedProgress")
            sheetsService.updateRow(spreadsheetId, range, values)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun applyConditionalFormatting(spreadsheetId: String, sheetId: Int) {
        val requests = mutableListOf<Request>()

        // Helper to create rule
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
