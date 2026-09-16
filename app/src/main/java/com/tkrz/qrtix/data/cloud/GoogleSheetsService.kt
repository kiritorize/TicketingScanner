package com.tkrz.qrtix.data.cloud

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsService @Inject constructor(
    private val credentialManager: GoogleCredentialManager
) {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

    val sheetsService: Sheets
        get() = Sheets.Builder(httpTransport, jsonFactory, credentialManager.getCredential())
            .setApplicationName("QRTix")
            .build()

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var currentDelay = 1000L
        val maxRetries = 3
        var exception: Exception? = null

        for (i in 0 until maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                exception = e
                val msg = e.message ?: ""
                // 429 Too Many Requests
                if (msg.contains("429") || msg.contains("quota")) {
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    throw e
                }
            }
        }
        throw exception ?: Exception("Failed after retries")
    }

    suspend fun createSpreadsheet(title: String): String = withContext(Dispatchers.IO) {
        withRetry {
            val spreadsheet = Spreadsheet().setProperties(SpreadsheetProperties().setTitle(title))
            val created = sheetsService.spreadsheets().create(spreadsheet).execute()
            created.spreadsheetId
        }
    }

    suspend fun readRange(spreadsheetId: String, range: String): List<List<Any>>? = withContext(Dispatchers.IO) {
        withRetry {
            val response = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
            response.getValues()
        }
    }

    suspend fun getSpreadsheet(spreadsheetId: String): Spreadsheet = withContext(Dispatchers.IO) {
        withRetry {
            sheetsService.spreadsheets().get(spreadsheetId).execute()
        }
    }

    suspend fun getSheetIdByName(spreadsheetId: String, sheetName: String): Int? = withContext(Dispatchers.IO) {
        val spreadsheet = getSpreadsheet(spreadsheetId)
        spreadsheet.sheets.find { it.properties.title == sheetName }?.properties?.sheetId
    }

    suspend fun appendRow(spreadsheetId: String, range: String, values: List<Any>) = withContext(Dispatchers.IO) {
        withRetry {
            val valueRange = ValueRange().setValues(listOf(values))
            sheetsService.spreadsheets().values()
                .append(spreadsheetId, range, valueRange)
                .setValueInputOption("USER_ENTERED")
                .execute()
        }
    }

    suspend fun appendRows(spreadsheetId: String, range: String, values: List<List<Any>>) = withContext(Dispatchers.IO) {
        withRetry {
            val valueRange = ValueRange().setValues(values)
            sheetsService.spreadsheets().values()
                .append(spreadsheetId, range, valueRange)
                .setValueInputOption("USER_ENTERED")
                .execute()
        }
    }

    suspend fun clearRange(spreadsheetId: String, range: String) = withContext(Dispatchers.IO) {
        withRetry {
            sheetsService.spreadsheets().values()
                .clear(spreadsheetId, range, ClearValuesRequest())
                .execute()
        }
    }

    suspend fun updateRow(spreadsheetId: String, range: String, values: List<Any>) = withContext(Dispatchers.IO) {
        withRetry {
            val valueRange = ValueRange().setValues(listOf(values))
            sheetsService.spreadsheets().values()
                .update(spreadsheetId, range, valueRange)
                .setValueInputOption("USER_ENTERED")
                .execute()
        }
    }

    suspend fun deleteRow(spreadsheetId: String, sheetId: Int, rowIndex: Int) = withContext(Dispatchers.IO) {
        withRetry {
            val deleteRequest = Request().setDeleteDimension(
                DeleteDimensionRequest().setRange(
                    DimensionRange()
                        .setSheetId(sheetId)
                        .setDimension("ROWS")
                        .setStartIndex(rowIndex)
                        .setEndIndex(rowIndex + 1)
                )
            )
            batchUpdate(spreadsheetId, listOf(deleteRequest))
        }
    }

    suspend fun batchUpdate(spreadsheetId: String, requests: List<Request>) = withContext(Dispatchers.IO) {
        withRetry {
            val batchUpdateRequest = BatchUpdateSpreadsheetRequest().setRequests(requests)
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute()
        }
    }
}
