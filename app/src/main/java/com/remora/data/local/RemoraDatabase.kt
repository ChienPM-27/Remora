package com.remora.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = true
)
abstract class RemoraDatabase : RoomDatabase() {

    abstract fun memoryCardDao(): MemoryCardDao
    abstract fun resurfacingResultDao(): ResurfacingResultDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_setting ADD COLUMN consent_granted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE app_setting SET consent_granted = 1")
                db.execSQL("ALTER TABLE memory_card ADD COLUMN image_hash TEXT")
                db.execSQL("CREATE UNIQUE INDEX index_memory_card_image_hash ON memory_card(image_hash)")
                db.execSQL("ALTER TABLE memory_card ADD COLUMN extraction_complete INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE memory_card SET extraction_complete = 1 WHERE status = 'DONE'")
                db.execSQL("ALTER TABLE memory_card ADD COLUMN resurfacing_status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE memory_card ADD COLUMN resurfacing_attempt_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE memory_card SET resurfacing_status = 'DONE' WHERE id IN (SELECT new_card_id FROM resurfacing_result)")
            }
        }

        @Volatile
        private var INSTANCE: RemoraDatabase? = null

        fun getInstance(context: Context): RemoraDatabase {
            return INSTANCE ?: synchronized(this) {
                // Production must use explicit migrations — xem docs/engineering/definition-of-done.md
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RemoraDatabase::class.java,
                    "remora.db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
