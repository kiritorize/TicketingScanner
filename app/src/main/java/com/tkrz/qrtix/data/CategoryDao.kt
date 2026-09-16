package com.tkrz.qrtix.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE eventId = :eventId ORDER BY id ASC")
    fun getCategoriesForEventFlow(eventId: Long): Flow<List<TicketCategory>>
    
    @Query("SELECT * FROM categories WHERE eventId = :eventId ORDER BY id ASC")
    suspend fun getCategoriesForEvent(eventId: Long): List<TicketCategory>

    @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
    suspend fun getCategoryById(categoryId: Long): TicketCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: TicketCategory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<TicketCategory>)

    @Update
    suspend fun updateCategory(category: TicketCategory)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: Long)

    @Query("DELETE FROM categories WHERE eventId = :eventId")
    suspend fun deleteCategoriesForEvent(eventId: Long)
}
