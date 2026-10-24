package com.remora.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.remora.data.local.dao.AppSettingDao
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.dao.ResurfacingResultDao
import com.remora.data.local.entity.AppSettingEntity
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.ResurfacingResultEntity

@Database(
    entities = [
        MemoryCardEntity::class,
        ResurfacingResultEntity::class,
        AppSettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class RemoraDatabase : RoomDatabase() {

    abstract fun memoryCardDao(): MemoryCardDao
    abstract fun resurfacingResultDao(): ResurfacingResultDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var INSTANCE: RemoraDatabase? = null

        fun getInstance(context: Context): RemoraDatabase {
            return INSTANCE ?: synchronized(this) {
                // Production must use explicit migrations — xem docs/engineering/definition-of-done.md
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RemoraDatabase::class.java,
                    "remora.db"
                ).apply {
                    if (com.remora.BuildConfig.DEBUG) {
                        fallbackToDestructiveMigration()
                    }
                }.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
