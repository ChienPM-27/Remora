package com.remora.capture

import com.remora.core.model.entity.MemoryCard
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.entity.MemoryCardEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Default-deny consent policy for the notification Save action:
 * without explicit consent no card may be persisted and no worker may be enqueued.
 */
class CaptureConsentGateTest {

    private class FakeMemoryCardDao : MemoryCardDao {
        val inserted = mutableListOf<MemoryCardEntity>()
        override suspend fun insertCard(card: MemoryCardEntity): Long {
            inserted.add(card)
            return inserted.size.toLong()
        }
        override suspend fun updateCard(card: MemoryCardEntity) {}
        override suspend fun deleteCard(id: Long) {}
        override suspend fun getCardById(id: Long): MemoryCardEntity? = null
        override suspend fun getCardByImageUri(imageUri: String): MemoryCardEntity? = null
        override suspend fun countCardsByImageUri(imageUri: String): Int = 0
        override fun getAllCardsFlow(): Flow<List<MemoryCardEntity>> = flowOf(emptyList())
        override suspend fun getAllDoneCardsWithEmbedding(): List<MemoryCardEntity> = emptyList()
        override suspend fun getCandidatesExcluding(excludeId: Long): List<MemoryCardEntity> = emptyList()
        override suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long): List<MemoryCardEntity> = emptyList()
        override suspend fun getCardCount(): Int = 0
        override suspend fun getDoneCardCount(): Int = 0
        override suspend fun deleteAllCards() {}
    }

    @Test
    fun saveAction_withoutConsent_doesNotInsertOrEnqueue() = runTest {
        val dao = FakeMemoryCardDao()
        var enqueued = 0
        var savedNotified = false
        val resolved = mutableListOf<String>()

        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_SAVE,
            imageUriStr = "content://media/42",
            userIntent = null,
            memoryCardDao = dao,
            consentGranted = false,
            onEnqueueWorker = { enqueued++ },
            onSaveSuccess = { savedNotified = true },
            onResolve = { resolved.add(it) }
        )

        assertEquals("No card may be persisted without consent", 0, dao.inserted.size)
        assertEquals("No worker may be enqueued without consent", 0, enqueued)
        assertFalse("No success notification without consent", savedNotified)
        assertEquals("Pending capture must still be resolved", listOf("content://media/42"), resolved)
    }

    @Test
    fun saveAction_withConsent_insertsPendingCardAndEnqueues() = runTest {
        val dao = FakeMemoryCardDao()
        var enqueued = 0

        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_SAVE,
            imageUriStr = "content://media/42",
            userIntent = "ôn tập",
            memoryCardDao = dao,
            consentGranted = true,
            onEnqueueWorker = { enqueued++ },
            onResolve = {}
        )

        assertEquals(1, dao.inserted.size)
        assertEquals(1, enqueued)
        val card: MemoryCard = dao.inserted[0].toDomainModel()
        assertEquals("content://media/42", card.imageUri)
    }

    @Test
    fun saveAction_withoutConsent_duplicateUriStillResolved() = runTest {
        val dao = FakeMemoryCardDao()
        val resolved = mutableListOf<String>()

        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_SAVE,
            imageUriStr = "content://media/42",
            userIntent = null,
            memoryCardDao = dao,
            consentGranted = false,
            onResolve = { resolved.add(it) }
        )

        assertEquals(0, dao.inserted.size)
        assertEquals(listOf("content://media/42"), resolved)
    }
}
