package com.example.roaddamagedetector

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "history_records")
data class HistoryRecord(
    @PrimaryKey
    val timestamp: Long,      // 记录时间戳
    val imagePath: String,    // 图片保存的路径 (现在存储的是 Uri 字符串)
    val className: String,    // 分类结果
    val confidence: Float,    // 置信度
    val processingTime: Long, // 处理时间（毫秒）
    val fps: Float?,          // 新增：分类推理速度 (FPS)，设为可空以兼容旧数据
    val recordType: String,
    val location: String?,
    val startLocation: String? = null, // 仅用于 VIDEO_SUMMARY
    val endLocation: String? = null    // 仅用于 VIDEO_SUMMARY
): Serializable
