package com.remora.data

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.AppSettingEntity
import com.remora.work.CardWorkScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class SaveCoordinatorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class Scheduler : CardWorkScheduler {
        val enqueued = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()
        override fun analyze(cardId: Long) { enqueued += cardId }
        override fun cancel(cardId: Long) { cancelled += cardId }
        override fun cancelAll() = Unit
    }

    private fun image(): File = File(context.cacheDir, "test-${UUID.randomUUID()}.png").apply {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun duplicateSaveKeepsOneOwnedImageAndOriginalNote() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RemoraDatabase::class.java).build()
        val scheduler = Scheduler()
        val coordinator = SaveCoordinator(context, db, scheduler)
        val source = image()
        try {
            db.appSettingDao().insertOrUpdate(AppSettingEntity(consentGranted = true))
            val first = coordinator.save(Uri.fromFile(source), "  My original reason  ")
            val second = coordinator.save(Uri.fromFile(source), "Different note")
            assertFalse(first.duplicate)
            assertTrue(second.duplicate)
            assertEquals(first.cardId, second.cardId)
            assertEquals(listOf(first.cardId), scheduler.enqueued)
            assertEquals(1, db.memoryCardDao().getCardCount())
            val card = requireNotNull(db.memoryCardDao().getCardById(first.cardId))
            assertEquals("My original reason", card.userIntent)
            assertEquals("PENDING", card.status)
            val owned = File(requireNotNull(Uri.parse(card.imageUri).path))
            assertTrue(owned.exists())
            coordinator.delete(first.cardId)
            assertFalse(owned.exists())
            assertTrue(source.exists())
            assertEquals(listOf(first.cardId), scheduler.cancelled)
            assertEquals(0, db.memoryCardDao().getCardCount())
        } finally { source.delete(); db.close() }
    }

    @Test
    fun consentIsRequiredAndBlankNotesStayAbsent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RemoraDatabase::class.java).build()
        val scheduler = Scheduler()
        val coordinator = SaveCoordinator(context, db, scheduler)
        val source = image()
        try {
            assertTrue(runCatching { coordinator.save(Uri.fromFile(source), null) }.isFailure)
            assertEquals(0, db.memoryCardDao().getCardCount())
            assertTrue(scheduler.enqueued.isEmpty())
            db.appSettingDao().insertOrUpdate(AppSettingEntity(consentGranted = true))
            val result = coordinator.save(Uri.fromFile(source), "   ")
            val card = requireNotNull(db.memoryCardDao().getCardById(result.cardId))
            assertNull(card.userIntent)
            source.delete()
            assertTrue(File(requireNotNull(Uri.parse(card.imageUri).path)).exists())
            coordinator.clear()
            assertFalse(File(requireNotNull(Uri.parse(card.imageUri).path)).exists())
        } finally { source.delete(); db.close() }
    }
}
