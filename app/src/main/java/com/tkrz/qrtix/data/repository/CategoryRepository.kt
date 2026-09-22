package com.tkrz.qrtix.data.repository

import com.tkrz.qrtix.data.CategoryDao
import com.tkrz.qrtix.data.TicketCategory
import com.tkrz.qrtix.data.cloud.CloudPreferences
import com.tkrz.qrtix.data.cloud.GoogleSheetsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val sheetsService: GoogleSheetsService,
    private val cloudPreferences: CloudPreferences
) {
    fun getCategoriesForEventFlow(eventId: Long): Flow<List<TicketCategory>> =
        categoryDao.getCategoriesForEventFlow(eventId)

    suspend fun getCategoriesForEvent(eventId: Long): List<TicketCategory> =
        categoryDao.getCategoriesForEvent(eventId)

    suspend fun insertCategory(category: TicketCategory): Long = withContext(Dispatchers.IO) {
        val id = categoryDao.insertCategory(category)
        syncCategoriesToCloud(category.eventId)
        id
    }

    suspend fun deleteCategory(category: TicketCategory) = withContext(Dispatchers.IO) {
        categoryDao.deleteCategory(category.id)
        syncCategoriesToCloud(category.eventId)
    }

    suspend fun updateCategory(category: TicketCategory) = withContext(Dispatchers.IO) {
        categoryDao.updateCategory(category)
        syncCategoriesToCloud(category.eventId)
    }

    suspend fun syncCategoriesFromCloud(eventId: Long) = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: return@withContext
        try {
            val data = sheetsService.readRange(spreadsheetId, "Categories!A2:D") ?: return@withContext
            
            val cloudCategories = data.mapNotNull { row ->
                try {
                    val id = row.getOrNull(0)?.toString()?.toLongOrNull() ?: return@mapNotNull null
                    val rowEventId = row.getOrNull(1)?.toString()?.toLongOrNull() ?: return@mapNotNull null
                    val name = row.getOrNull(2)?.toString() ?: ""
                    val code = row.getOrNull(3)?.toString() ?: ""

                    if (rowEventId == eventId && name.isNotBlank() && code.isNotBlank()) {
                        TicketCategory(id, rowEventId, name, code)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            if (cloudCategories.isNotEmpty()) {
                categoryDao.deleteCategoriesForEvent(eventId)
                categoryDao.insertCategories(cloudCategories)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncCategoriesToCloud(eventId: Long) = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: return@withContext
        try {
            val localCategories = categoryDao.getCategoriesForEvent(eventId)
            
            // Read all existing to preserve other events' categories
            val data = sheetsService.readRange(spreadsheetId, "Categories!A2:D") ?: emptyList()
            
            val filteredData = data.filter { row ->
                val rowEventId = row.getOrNull(1)?.toString()?.toLongOrNull()
                rowEventId != eventId
            }

            val newRows = localCategories.map { cat ->
                listOf(
                    cat.id.toString(),
                    cat.eventId.toString(),
                    cat.categoryName,
                    cat.categoryCode
                )
            }

            val finalData = mutableListOf<List<Any>>()
            finalData.addAll(filteredData)
            finalData.addAll(newRows)

            // Clear data first
            sheetsService.clearRange(spreadsheetId, "Categories!A2:D")
            
            // Rewrite all
            if (finalData.isNotEmpty()) {
                sheetsService.appendRows(spreadsheetId, "Categories!A2", finalData)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun checkCategoryCodeExistsInCloud(eventId: Long, code: String): Boolean = withContext(Dispatchers.IO) {
        val spreadsheetId = cloudPreferences.spreadsheetId ?: return@withContext false
        try {
            val data = sheetsService.readRange(spreadsheetId, "Categories!A2:D") ?: return@withContext false
            data.any { row ->
                val rowEventId = row.getOrNull(1)?.toString()?.toLongOrNull()
                val rowCode = row.getOrNull(3)?.toString()
                rowEventId == eventId && rowCode.equals(code, ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }
}
