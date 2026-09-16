package com.tkrz.qrtix.data

data class BuyerData(
    val name: String,
    val email: String,
    val category: String,
    val requestedQty: Int
)

data class DistributionAssignment(
    val buyerName: String,
    val buyerEmail: String,
    val ticketCategory: String,
    val ticketCodes: List<String>,
    val emailStatus: String, // "PENDING", "SENT", "FAILED"
    val sentAt: String,
    val errorMessage: String
)

data class ValidationResult(
    val isValid: Boolean,
    val totalValidBuyers: Int,
    val totalTicketsToDistribute: Int,
    val categoryBreakdown: Map<String, Int>,
    val errors: List<String>,
    val assignments: List<DistributionAssignment>
)

data class SendingProgress(
    val total: Int = 0,
    val sent: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0
)

data class SendLogEntry(
    val timestamp: String,
    val email: String,
    val status: String,
    val message: String
)
