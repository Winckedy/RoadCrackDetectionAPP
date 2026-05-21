package com.example.roaddamagedetector

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "history_records")
data class HistoryRecord(
    @PrimaryKey
    val timestamp: Long,
    val imagePath: String,
    val className: String,
    val confidence: Float,
    val processingTime: Long,
    val fps: Float?,
    val recordType: String,
    val location: String?,
    val startLocation: String? = null,
    val endLocation: String? = null
): Serializable
