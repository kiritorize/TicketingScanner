package com.tkrz.qrtix.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val logoPath: String? = null,
    val eventCode: String = "EVNT1",
    val bgPath: String? = null,
    val qrX: Float = 0f,
    val qrY: Float = 0f,
    val qrScale: Float = 1f,
    val qrRotation: Float = 0f,
    val distributionSheetId: String? = null
)
