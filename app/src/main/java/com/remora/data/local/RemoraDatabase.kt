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
        @Volatile
        private var INSTANCE: RemoraDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Default-deny: upgrading users keep consent_granted = 0 from the column
                // default. Consent is only granted by an explicit user action.
                db.execSQL("ALTER TABLE app_setting ADD COLUMN consent_granted INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): RemoraDatabase {
            return INSTANCE ?: synchronized(this) {
                // Production must use explicit migrations — xem docs/engineering/definition-of-done.md
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RemoraDatabase::class.java,
                    "remora.db"
                ).addMigrations(MIGRATION_1_2)
                .apply {
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
