package com.remora.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.AppSettingEntity
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.ResurfacingResultEntity
import com.remora.work.AnalysisOutcome
import com.remora.work.CardAnalysisPipeline
import com.remora.work.CardAnalyzeWorker
import com.remora.work.CardWorkScheduler
import com.remora.work.ResurfaceWorker
import com.remora.work.WorkScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class SaveCoordinatorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class Scheduler : CardWorkScheduler {
        val enqueued = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()
        var cancelAllCalls = 0
        var onCancel: (Long) -> Unit = {}
        var onCancelAll: () -> Unit = {}
        override fun analyze(cardId: Long) { enqueued += cardId }
        override fun resurface(cardId: Long) = Unit
        override fun cancel(cardId: Long) { onCancel(cardId); cancelled += cardId }
        override fun cancelAll() { onCancelAll(); cancelAllCalls++ }
    }

    private fun image(color: Int = Color.TRANSPARENT): File = File(context.cacheDir, "test-${UUID.randomUUID()}.png").apply {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
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

    @Test
    fun deleteRemovesIncomingAndOutgoingLinksWithoutChangingOtherCards() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RemoraDatabase::class.java).build()
        val scheduler = Scheduler()
        val coordinator = SaveCoordinator(context, db, scheduler)
        val sources = listOf(image(Color.RED), image(Color.GREEN), image(Color.BLUE))
        try {
            db.appSettingDao().insertOrUpdate(AppSettingEntity(consentGranted = true))
            val cards = sources.map { source ->
                val saved = coordinator.save(Uri.fromFile(source), "Keep this note")
                requireNotNull(db.memoryCardDao().getCardById(saved.cardId))
            }
            val (deleted, retained, other) = cards
            val owned = File(requireNotNull(Uri.parse(deleted.imageUri).path))
            val links = db.resurfacingResultDao()
            links.insertResults(listOf(link(deleted.id, retained.id), link(other.id, deleted.id), link(retained.id, other.id)))
            scheduler.onCancel = { id -> assertEquals(deleted.id, id); assertTrue(owned.exists()) }

            coordinator.delete(deleted.id)

            assertEquals(listOf(deleted.id), scheduler.cancelled)
            assertNull(db.memoryCardDao().getCardById(deleted.id))
            assertFalse(owned.exists())
            assertEquals(retained, db.memoryCardDao().getCardById(retained.id))
            assertEquals(other, db.memoryCardDao().getCardById(other.id))
            assertTrue(links.getResultsForCard(deleted.id).isEmpty())
            assertTrue(links.getResultsForCard(other.id).isEmpty())
            assertEquals(other.id, links.getResultsForCard(retained.id).single().candidateCardId)
            assertEquals(1, links.getRecentResultsFlow().first().size)
            assertTrue(sources.all { it.exists() })
            assertTrue(cards.drop(1).all { File(requireNotNull(Uri.parse(it.imageUri).path)).exists() })
        } finally {
            coordinator.clear()
            sources.forEach { it.delete() }
            db.close()
        }
    }

    @Test
    fun clearCancelsBeforeCleanupAndPreservesOriginalAndLegacyImages() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RemoraDatabase::class.java).build()
        val scheduler = Scheduler()
        val coordinator = SaveCoordinator(context, db, scheduler)
        val source = image()
        val legacy = image(Color.RED)
        try {
            db.appSettingDao().insertOrUpdate(AppSettingEntity(consentGranted = true))
            val saved = coordinator.save(Uri.fromFile(source), null)
            val savedCard = requireNotNull(db.memoryCardDao().getCardById(saved.cardId))
            val owned = File(requireNotNull(Uri.parse(savedCard.imageUri).path))
            val legacyId = db.memoryCardDao().insertCard(MemoryCardEntity(
                imageUri = Uri.fromFile(legacy).toString(), createdAt = 1000))
            db.memoryCardDao().insertCard(MemoryCardEntity(
                imageUri = "content://media/external/images/media/700", createdAt = 1001))
            db.resurfacingResultDao().insertResults(listOf(link(legacyId, saved.cardId)))
            scheduler.onCancelAll = { assertTrue(owned.exists()) }

            coordinator.clear()

            assertEquals(1, scheduler.cancelAllCalls)
            assertEquals(0, db.memoryCardDao().getCardCount())
            assertTrue(db.resurfacingResultDao().getRecentResultsFlow().first().isEmpty())
            assertFalse(owned.exists())
            assertTrue(source.exists())
            assertTrue(legacy.exists())
        } finally {
            scheduler.onCancelAll = {}
            coordinator.clear()
            source.delete()
            legacy.delete()
            db.close()
        }
    }

    @Test
    fun retryPreservesExtractionAndDoesNotRestartCompletedOrDeletedCards() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RemoraDatabase::class.java).build()
        val scheduler = Scheduler()
        val coordinator = SaveCoordinator(context, db, scheduler)
        val source = image()
        try {
            db.appSettingDao().insertOrUpdate(AppSettingEntity(consentGranted = true))
            val saved = coordinator.save(Uri.fromFile(source), "Original intent")
            val dao = db.memoryCardDao()
            val failed = requireNotNull(dao.getCardById(saved.cardId)).copy(
                status = "FAILED", attemptCount = 3, errorCode = "NETWORK_UNAVAILABLE",
                extractionComplete = true, title = "Extracted title", summary = "Retained summary",
                ocrText = "Retained text", resurfacingStatus = "FAILED", resurfacingAttemptCount = 2)
            dao.updateCard(failed)
            scheduler.enqueued.clear()

            coordinator.retry(saved.cardId)

            assertEquals(failed.copy(status = "PENDING", attemptCount = 0, errorCode = null), dao.getCardById(saved.cardId))
            assertEquals(listOf(saved.cardId), scheduler.enqueued)
            dao.updateCard(failed.copy(status = "DONE"))
            coordinator.retry(saved.cardId)
            coordinator.delete(saved.cardId)
            coordinator.retry(saved.cardId)
            assertEquals(listOf(saved.cardId), scheduler.enqueued)
            assertNull(dao.getCardById(saved.cardId))
        } finally {
            coordinator.clear()
            source.delete()
            db.close()
        }
    }

    @Test
    fun lateAnalysisResultCannotRestoreADeletedCard() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RemoraDatabase::class.java).build()
        val scheduler = Scheduler()
        val coordinator = SaveCoordinator(context, db, scheduler)
        val source = image()
        try {
            db.appSettingDao().insertOrUpdate(AppSettingEntity(consentGranted = true))
            val saved = coordinator.save(Uri.fromFile(source), null)
            val dao = db.memoryCardDao()
            val card = requireNotNull(dao.getCardById(saved.cardId))
            dao.updateCard(card.copy(extractionComplete = true))
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val pipeline = CardAnalysisPipeline(load = dao::getCardById, persist = dao::updateCard,
                extract = { error("Cached extraction must be retained") }, embed = {
                    started.complete(Unit)
                    release.await()
                    FloatArray(768) { if (it == 0) 1f else 0f }
                })
            val analysis = async { pipeline.run(saved.cardId) }
            try {
                withTimeout(5000) { started.await() }
                coordinator.delete(saved.cardId)
                release.complete(Unit)
                assertEquals(AnalysisOutcome.COMPLETE, withTimeout(5000) { analysis.await() })
                assertNull(dao.getCardById(saved.cardId))
                assertFalse(File(requireNotNull(Uri.parse(card.imageUri).path)).exists())
                assertEquals(listOf(saved.cardId), scheduler.cancelled)
                assertTrue(source.exists())
            } finally { analysis.cancelAndJoin() }
        } finally {
            coordinator.clear()
            source.delete()
            db.close()
        }
    }

    @Test
    fun deleteCancelsBothCardJobsAndClearCancelsRemainingJobs() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, RemoraDatabase::class.java).build()
        val workManager = WorkManager.getInstance(context)
        val coordinator = SaveCoordinator(context, db, WorkScheduler(context))
        val deletedId = 1_000_000L + (UUID.randomUUID().mostSignificantBits and 0x0fffffff)
        val retainedId = deletedId + 1
        val analyze = OneTimeWorkRequestBuilder<CardAnalyzeWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .addTag(WorkScheduler.TAG).addTag(WorkScheduler.cardTag(deletedId)).build()
        val resurface = OneTimeWorkRequestBuilder<ResurfaceWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .addTag(WorkScheduler.TAG).addTag(WorkScheduler.cardTag(deletedId)).build()
        val retained = OneTimeWorkRequestBuilder<CardAnalyzeWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .addTag(WorkScheduler.TAG).addTag(WorkScheduler.cardTag(retainedId)).build()
        val requests = listOf(analyze, resurface, retained)
        try {
            db.memoryCardDao().insertCard(MemoryCardEntity(id = deletedId, imageUri = "content://media/1", createdAt = 1000))
            db.memoryCardDao().insertCard(MemoryCardEntity(id = retainedId, imageUri = "content://media/2", createdAt = 1001))
            workManager.enqueue(requests).result.get(10, TimeUnit.SECONDS)

            coordinator.delete(deletedId)

            withTimeout(10000) {
                workManager.getWorkInfoByIdFlow(analyze.id).first { it?.state == WorkInfo.State.CANCELLED }
                workManager.getWorkInfoByIdFlow(resurface.id).first { it?.state == WorkInfo.State.CANCELLED }
            }
            assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfoById(retained.id).get(10, TimeUnit.SECONDS)?.state)
            assertNotNull(db.memoryCardDao().getCardById(retainedId))

            coordinator.clear()

            withTimeout(10000) {
                workManager.getWorkInfoByIdFlow(retained.id).first { it?.state == WorkInfo.State.CANCELLED }
            }
            assertEquals(0, db.memoryCardDao().getCardCount())
        } finally {
            requests.forEach { workManager.cancelWorkById(it.id).result.get(10, TimeUnit.SECONDS) }
            db.close()
        }
    }

    private fun link(sourceId: Long, candidateId: Long) = ResurfacingResultEntity(
        newCardId = sourceId, candidateCardId = candidateId, group = "SIMILAR", relation = "SUPPLEMENT", confidence = 0.95f)
}
