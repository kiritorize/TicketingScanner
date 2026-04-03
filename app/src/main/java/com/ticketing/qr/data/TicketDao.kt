package com.ticketing.qr.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

import androidx.room.Index

@Entity(tableName = "tickets", indices = [Index(value = ["qrContent"], unique = true)])
data class Ticket(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val qrContent: String,
    val ticketType: String,
    val isScanned: Boolean = false
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

    @Query("UPDATE tickets SET isScanned = 1 WHERE qrContent = :qrContent")
    suspend fun markAsScanned(qrContent: String)

    @Query("DELETE FROM tickets")
    suspend fun deleteAllTickets()
}
