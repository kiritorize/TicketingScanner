package com.tkrz.qrtix.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "history_logs")
data class HistoryLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val eventId: Long,
    val action: String,
    val description: String,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isUndone: Boolean = false
)

@Dao
interface HistoryLogDao {
    @Query("SELECT * FROM history_logs WHERE eventId = :eventId ORDER BY timestamp DESC")
    suspend fun getLogsForEvent(eventId: Long): List<HistoryLog>

    @Insert
    suspend fun insertLog(log: HistoryLog): Long

    @Query("UPDATE history_logs SET isUndone = 1 WHERE id = :logId")
    suspend fun markAsUndone(logId: Int)

    @Query("DELETE FROM history_logs WHERE eventId = :eventId")
    suspend fun deleteLogsForEvent(eventId: Long)
}
