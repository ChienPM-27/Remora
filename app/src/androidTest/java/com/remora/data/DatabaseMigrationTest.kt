package com.remora.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.remora.data.local.RemoraDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DatabaseMigrationTest {
    @Test
    fun upgradeRetainsCardsLinksAndDisabledNotifications() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val schema = instrumentation.context.assets
            .open("com.remora.data.local.RemoraDatabase/1.json").bufferedReader().use { it.readText() }
        val entities = Json.parseToJsonElement(schema).jsonObject.getValue("database")
            .jsonObject.getValue("entities").jsonArray
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            entities.forEach { element ->
                val entity = element.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                legacy.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.getValue("indices").jsonArray.forEach { index ->
                    legacy.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            legacy.execSQL("INSERT INTO app_setting VALUES (1, 0, 1, 0)")
            for (id in 1..2) {
                legacy.execSQL("INSERT INTO memory_card (id, image_uri, created_at, title, summary, tags_json, content_type, ocr_text, status, attempt_count) VALUES (?, 'content://original', 123, 'Preserved', 'Summary', '{}', 'THEORY', 'Text', 'DONE', 0)", arrayOf(id))
            }
            legacy.execSQL("INSERT INTO resurfacing_result (id, new_card_id, candidate_card_id, `group`, relation, new_info, benefit, reason, confidence, created_at) VALUES (1, 2, 1, 'SIMILAR', 'SUPPLEMENT', 'Info', 'Benefit', 'Reason', 0.9, 123)")
            legacy.version = 1
        }
        val db = Room.databaseBuilder(context, RemoraDatabase::class.java, name)
            .addMigrations(RemoraDatabase.MIGRATION_1_2).build()
        try {
            assertEquals(2, db.memoryCardDao().getCardCount())
            val setting = requireNotNull(db.appSettingDao().getSetting())
            assertTrue(setting.consentGranted)
            assertFalse(setting.notificationsEnabled)
            assertTrue(setting.paused)
            val card = requireNotNull(db.memoryCardDao().getCardById(2))
            assertEquals("Preserved", card.title)
            assertEquals("content://original", card.imageUri)
            assertTrue(card.extractionComplete)
            assertEquals("DONE", card.resurfacingStatus)
            assertEquals(1, db.resurfacingResultDao().getResultsForCard(2).size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
