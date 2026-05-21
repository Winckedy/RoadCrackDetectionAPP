// AppDatabase.kt
package com.example.roaddamagedetector

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HistoryRecord::class], version = 1, exportSchema = false) // <-- 在这里添加
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): HistoryRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "road_damage_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
