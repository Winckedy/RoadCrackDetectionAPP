package com.example.roaddamagedetector

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HistoryRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: HistoryRecord)

    @Update
    suspend fun update(record: HistoryRecord)

    @Query("SELECT * FROM history_records ORDER BY timestamp DESC")
    fun getAllRecords(): LiveData<List<HistoryRecord>>

    @Query("SELECT * FROM history_records ORDER BY timestamp DESC")
    suspend fun getAllRecordsForExport(): List<HistoryRecord>

    @Query("SELECT * FROM history_records WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getRecordsInRangeForExport(startTime: Long, endTime: Long): List<HistoryRecord>

    @Query("SELECT * FROM history_records WHERE timestamp = :timestamp LIMIT 1")
    suspend fun getRecordByTimestamp(timestamp: Long): HistoryRecord?

    // 👇 修改点 1: 添加 : Int
    @Query("DELETE FROM history_records")
    suspend fun clearAll(): Int

    // 👇 修改点 2: 添加 : Int
    @Query("DELETE FROM history_records WHERE timestamp < :timestamp")
    suspend fun clearOlderThan(timestamp: Long): Int

    // 👇 修改点 3: 添加 : Int
    @Query("DELETE FROM history_records WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun clearInRange(startTime: Long, endTime: Long): Int
}