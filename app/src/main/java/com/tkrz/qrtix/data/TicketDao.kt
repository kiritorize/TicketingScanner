package com.tkrz.qrtix.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity(tableName = "tickets", indices = [Index(value = ["qrContent"], unique = true)])
data class Ticket(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val qrContent: String,
    val ticketType: String,
    val isScanned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val scannedAt: Long? = null,
    val isModified: Boolean = false
)

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets ORDER BY id ASC")
    suspend fun getAllTickets(): List<Ticket>

    @Query("SELECT * FROM tickets WHERE qrContent = :qrContent LIMIT 1")
    suspend fun getTicketByQr(qrContent: String): Ticket?

    @Query("SELECT COUNT(*) FROM tickets")
    suspend fun getTicketCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTickets(tickets: List<Ticket>)

    @Query("UPDATE tickets SET isScanned = 1, scannedAt = :scannedAt WHERE qrContent = :qrContent")
    suspend fun markAsScanned(qrContent: String, scannedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM tickets")
    suspend fun deleteAllTickets()

    @Query("DELETE FROM sqlite_sequence WHERE name = 'tickets'")
    suspend fun resetSequence()

    @Query("DELETE FROM tickets WHERE id IN (:ids)")
    suspend fun deleteTickets(ids: List<Int>)

    @Query("UPDATE tickets SET qrContent = :newQr, ticketType = :newType, createdAt = :updatedAt, isModified = 1 WHERE id = :id")
    suspend fun updateTicket(id: Int, newQr: String, newType: String, updatedAt: Long)

    @Query("SELECT qrContent FROM tickets WHERE qrContent IN (:codes)")
    suspend fun getExistingCodes(codes: List<String>): List<String>
}
