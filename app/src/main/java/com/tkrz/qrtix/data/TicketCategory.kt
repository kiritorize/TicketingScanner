package com.tkrz.qrtix.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["categoryCode", "eventId"], unique = true)
    ]
)
data class TicketCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val categoryName: String,
    val categoryCode: String
)
