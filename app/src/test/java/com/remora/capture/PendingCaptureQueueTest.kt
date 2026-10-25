package com.remora.capture

import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.entity.MemoryCardEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class PendingCaptureQueueTest {

    private class FakeMemoryCardDao : MemoryCardDao {
        val cards = mutableListOf<MemoryCardEntity>()
        private var nextId = 1L

        override fun getAllCardsFlow(): Flow<List<MemoryCardEntity>> = flowOf(cards)

        override suspend fun getCardById(id: Long): MemoryCardEntity? =
            cards.find { it.id == id }

        override suspend fun getCardByImageUri(imageUri: String): MemoryCardEntity? =
            cards.find { it.imageUri == imageUri }

        override suspend fun countCardsByImageUri(imageUri: String): Int =
            cards.count { it.imageUri == imageUri }
        override suspend fun getCardByImageHash(hash: String): MemoryCardEntity? = null

        override suspend fun insertCard(card: MemoryCardEntity): Long {
            val id = if (card.id == 0L) nextId++ else card.id
            val persisted = card.copy(id = id)
            cards.add(persisted)
            return id
        }

        override suspend fun updateCard(card: MemoryCardEntity) {
            val index = cards.indexOfFirst { it.id == card.id }
            if (index != -1) {
                cards[index] = card
            }
        }

        override suspend fun deleteCard(id: Long) {
            cards.removeAll { it.id == id }
        }

        override suspend fun getAllDoneCardsWithEmbedding(): List<MemoryCardEntity> =
            cards.filter { it.status == "DONE" && it.embedding != null }

        override suspend fun getCandidatesExcluding(excludeId: Long): List<MemoryCardEntity> =
            cards.filter { it.status == "DONE" && it.embedding != null && it.id != excludeId }

        override suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long): List<MemoryCardEntity> =
            cards.filter { it.status == "DONE" && it.embedding != null && it.createdAt in startTime..endTime }

        override suspend fun getCardCount(): Int = cards.size

        override suspend fun getDoneCardCount(): Int =
            cards.count { it.status == "DONE" && it.embedding != null }

        override suspend fun deleteAllCards() {
            cards.clear()
        }
    }

    // 1. thêm trùng uri không nhân đôi
    @Test
    fun add_duplicateUri_doesNotDuplicate() {
        val initial = emptyList<PendingCapture>()
        val uri = "content://media/external/images/media/100"

        val afterFirst = PendingCaptureQueue.add(initial, uri, now = 10_000L)
        assertEquals(1, afterFirst.size)
        assertEquals(uri, afterFirst[0].uriString)

        val afterSecond = PendingCaptureQueue.add(afterFirst, uri, now = 12_000L, prioritize = false)
        assertEquals(1, afterSecond.size)
        assertEquals(10_000L, afterSecond[0].timestamp)
    }

    // 2. resolve bỏ đúng mục
    @Test
    fun resolve_removesTargetUri_keepsOthers() {
        val uri1 = "content://media/external/images/media/1"
        val uri2 = "content://media/external/images/media/2"
        val uri3 = "content://media/external/images/media/3"

        val queue = listOf(
            PendingCapture(uri1, 3000L),
            PendingCapture(uri2, 2000L),
            PendingCapture(uri3, 1000L)
        )

        val resolved = PendingCaptureQueue.resolve(queue, uri2)
        assertEquals(2, resolved.size)
        assertEquals(listOf(uri1, uri3), resolved.map { it.uriString })
    }

    // 3. hết hạn 24 giờ bị loại
    @Test
    fun pruneExpired_olderThan24Hours_isDiscarded() {
        val now = TimeUnit.HOURS.toMillis(48)
        val validTime = now - TimeUnit.HOURS.toMillis(23)
        val expiredTime = now - TimeUnit.HOURS.toMillis(25)

        val queue = listOf(
            PendingCapture("content://media/valid", validTime),
            PendingCapture("content://media/expired", expiredTime)
        )

        val pruned = PendingCaptureQueue.pruneExpired(queue, now)
        assertEquals(1, pruned.size)
        assertEquals("content://media/valid", pruned[0].uriString)
    }

    // 4. vượt 5 mục thì bỏ mục cũ nhất
    @Test
    fun add_exceedingMaxItems_dropsOldest() {
        var queue = emptyList<PendingCapture>()
        for (i in 1..5) {
            queue = PendingCaptureQueue.add(queue, "content://media/$i", now = i * 1000L)
        }
        assertEquals(5, queue.size)
        // Add 6th item
        queue = PendingCaptureQueue.add(queue, "content://media/6", now = 6000L)
        assertEquals(5, queue.size)
        // 6 should be present, 1 (oldest) should be dropped
        assertEquals("content://media/6", queue.first().uriString)
        assertFalse(queue.any { it.uriString == "content://media/1" })
    }

    // 5. thứ tự mới nhất (newest first) & prioritize
    @Test
    fun add_ordering_newestFirst_andPrioritizeMovesToFront() {
        val q1 = PendingCaptureQueue.add(emptyList(), "content://media/1", now = 1000L)
        val q2 = PendingCaptureQueue.add(q1, "content://media/2", now = 2000L)
        val q3 = PendingCaptureQueue.add(q2, "content://media/3", now = 3000L)

        assertEquals(listOf("content://media/3", "content://media/2", "content://media/1"), q3.map { it.uriString })

        // Prioritize existing item "content://media/1"
        val q4 = PendingCaptureQueue.add(q3, "content://media/1", now = 4000L, prioritize = true)
        assertEquals(listOf("content://media/1", "content://media/3", "content://media/2"), q4.map { it.uriString })
        assertEquals(4000L, q4[0].timestamp)
    }

    // 6. receiver Save: card được thêm và uri bị resolve
    @Test
    fun receiver_saveAction_cardAddedAndUriResolved() = runTest {
        val fakeDao = FakeMemoryCardDao()
        val uri = "content://media/external/images/media/42"
        var resolvedUri: String? = null
        var enqueuedWorkerId: Long? = null

        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_SAVE,
            imageUriStr = uri,
            userIntent = "Toán giải tích",
            memoryCardDao = fakeDao,
            onEnqueueWorker = { enqueuedWorkerId = it },
            onResolve = { resolvedUri = it }
        )

        assertEquals(1, fakeDao.cards.size)
        val card = fakeDao.cards.first()
        assertEquals(uri, card.imageUri)
        assertEquals("Toán giải tích", card.userIntent)
        assertEquals("PENDING", card.status)
        assertEquals(card.id, enqueuedWorkerId)
        assertEquals(uri, resolvedUri)
    }

    // 7. receiver Skip: resolve
    @Test
    fun receiver_skipAction_resolvesUriWithoutAddingCard() = runTest {
        val fakeDao = FakeMemoryCardDao()
        val uri = "content://media/external/images/media/99"
        var resolvedUri: String? = null

        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_SKIP,
            imageUriStr = uri,
            userIntent = null,
            memoryCardDao = fakeDao,
            onResolve = { resolvedUri = it }
        )

        assertEquals(0, fakeDao.cards.size)
        assertEquals(uri, resolvedUri)
    }

    // 8. không có action (vuốt bỏ): vẫn pending
    @Test
    fun notificationSwipeAway_noReceiverAction_leavesUriPending() {
        val uri = "content://media/external/images/media/88"
        val initialQueue = listOf(PendingCapture(uri, 1000L))

        // When a user swipes away the notification without tapping Save, Skip, or Mute,
        // no broadcast intent is triggered to CaptureActionReceiver.
        // Thus resolve is never called, and the URI remains in the queue.
        val resolvedQueue = initialQueue // untouched
        assertEquals(1, resolvedQueue.size)
        assertEquals(uri, resolvedQueue[0].uriString)
    }

    // 9. hàm chọn mục cần prompt (nextPromptUri) bỏ qua uri đã có thẻ trong DB
    @Test
    fun nextPromptUri_skipsUriWithExistingCardInDb() {
        val uriInDb = "content://media/already_in_db"
        val uriPending = "content://media/fresh_capture"

        val queue = listOf(
            PendingCapture(uriInDb, 2000L),
            PendingCapture(uriPending, 1000L)
        )

        val next = PendingCaptureQueue.nextPromptUri(queue) { uri ->
            uri == uriInDb
        }

        assertEquals(uriPending, next)

        val allInDb = PendingCaptureQueue.nextPromptUri(queue) { true }
        assertNull(allInDb)
    }

    // 10. Mục 1: So sánh tuổi ảnh thuần (isWithinMaxAge)
    @Test
    fun isWithinMaxAge_within15Seconds_returnsTrue() {
        val now = 100_000L
        assertTrue(ScreenshotObserver.isWithinMaxAge(dateAddedSeconds = now, nowSeconds = now, maxAgeSeconds = 15L))
        assertTrue(ScreenshotObserver.isWithinMaxAge(dateAddedSeconds = now - 14L, nowSeconds = now, maxAgeSeconds = 15L))
        assertTrue(ScreenshotObserver.isWithinMaxAge(dateAddedSeconds = now - 15L, nowSeconds = now, maxAgeSeconds = 15L))
    }

    @Test
    fun isWithinMaxAge_olderThan15Seconds_returnsFalse() {
        val now = 100_000L
        assertFalse(ScreenshotObserver.isWithinMaxAge(dateAddedSeconds = now - 16L, nowSeconds = now, maxAgeSeconds = 15L))
        assertFalse(ScreenshotObserver.isWithinMaxAge(dateAddedSeconds = now - 3600L, nowSeconds = now, maxAgeSeconds = 15L))
    }

    @Test
    fun isWithinMaxAge_clockSkewTolerance() {
        val now = 100_000L
        // 5s into the future (skew within 10s) -> allowed
        assertTrue(ScreenshotObserver.isWithinMaxAge(dateAddedSeconds = now + 5L, nowSeconds = now, maxAgeSeconds = 15L))
        // 15s into the future (skew beyond 10s) -> rejected
        assertFalse(ScreenshotObserver.isWithinMaxAge(dateAddedSeconds = now + 15L, nowSeconds = now, maxAgeSeconds = 15L))
    }

    // 11. Mục 2: MUTE_1H và PAUSE với URI null vẫn gọi onApplyCaptureAction; có URI thì resolve
    @Test
    fun captureActionHandler_mute1h_withNullUri_appliesActionWithoutResolving() = runTest {
        val fakeDao = FakeMemoryCardDao()
        var appliedAction: String? = null
        var resolvedUri: String? = null

        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_MUTE_1H,
            imageUriStr = null,
            userIntent = null,
            memoryCardDao = fakeDao,
            onApplyCaptureAction = { appliedAction = it },
            onResolve = { resolvedUri = it }
        )

        assertEquals(NotificationHelper.ACTION_MUTE_1H, appliedAction)
        assertNull(resolvedUri)
    }

    @Test
    fun captureActionHandler_pause_withNullUri_appliesActionWithoutResolving() = runTest {
        val fakeDao = FakeMemoryCardDao()
        var appliedAction: String? = null
        var resolvedUri: String? = null

        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_PAUSE,
            imageUriStr = null,
            userIntent = null,
            memoryCardDao = fakeDao,
            onApplyCaptureAction = { appliedAction = it },
            onResolve = { resolvedUri = it }
        )

        assertEquals(NotificationHelper.ACTION_PAUSE, appliedAction)
        assertNull(resolvedUri)
    }

    @Test
    fun captureActionHandler_mute1h_withValidUri_appliesActionAndResolves() = runTest {
        val fakeDao = FakeMemoryCardDao()
        val uri = "content://media/external/images/media/55"
        var appliedAction: String? = null
        var resolvedUri: String? = null

        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_MUTE_1H,
            imageUriStr = uri,
            userIntent = null,
            memoryCardDao = fakeDao,
            onApplyCaptureAction = { appliedAction = it },
            onResolve = { resolvedUri = it }
        )

        assertEquals(NotificationHelper.ACTION_MUTE_1H, appliedAction)
        assertEquals(uri, resolvedUri)
    }

    @Test
    fun captureActionHandler_pause_withValidUri_appliesActionAndResolves() = runTest {
        val fakeDao = FakeMemoryCardDao()
        val uri = "content://media/external/images/media/56"
        var appliedAction: String? = null
        var resolvedUri: String? = null

        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_PAUSE,
            imageUriStr = uri,
            userIntent = null,
            memoryCardDao = fakeDao,
            onApplyCaptureAction = { appliedAction = it },
            onResolve = { resolvedUri = it }
        )

        assertEquals(NotificationHelper.ACTION_PAUSE, appliedAction)
        assertEquals(uri, resolvedUri)
    }

    @Test
    fun captureActionHandler_saveAndSkip_withNullUri_ignored() = runTest {
        val fakeDao = FakeMemoryCardDao()
        var saveSuccessCalled = false
        var resolvedUri: String? = null

        // SAVE with null URI
        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_SAVE,
            imageUriStr = null,
            userIntent = "test",
            memoryCardDao = fakeDao,
            onSaveSuccess = { saveSuccessCalled = true },
            onResolve = { resolvedUri = it }
        )
        assertFalse(saveSuccessCalled)
        assertNull(resolvedUri)
        assertEquals(0, fakeDao.cards.size)

        // SKIP with null URI
        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_SKIP,
            imageUriStr = null,
            userIntent = null,
            memoryCardDao = fakeDao,
            onResolve = { resolvedUri = it }
        )
        assertNull(resolvedUri)
    }

    @Test
    fun captureActionHandler_save_callsSaveSuccessOnInsertAndWhenCardExists() = runTest {
        val fakeDao = FakeMemoryCardDao()
        val uri = "content://media/external/images/media/77"
        var saveSuccessCount = 0
        var resolvedUri: String? = null

        // 1st call: inserts new card and triggers onSaveSuccess
        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_SAVE,
            imageUriStr = uri,
            userIntent = "Toán 10",
            memoryCardDao = fakeDao,
            onSaveSuccess = { saveSuccessCount++ },
            onResolve = { resolvedUri = it }
        )
        assertEquals(1, saveSuccessCount)
        assertEquals(uri, resolvedUri)
        assertEquals(1, fakeDao.cards.size)

        // 2nd call: card exists, skips duplicate insert, still calls onSaveSuccess and resolves
        resolvedUri = null
        CaptureActionHandler.handleAction(
            action = NotificationHelper.ACTION_SAVE,
            imageUriStr = uri,
            userIntent = "Toán 10",
            memoryCardDao = fakeDao,
            onSaveSuccess = { saveSuccessCount++ },
            onResolve = { resolvedUri = it }
        )
        assertEquals(2, saveSuccessCount)
        assertEquals(uri, resolvedUri)
        assertEquals(1, fakeDao.cards.size)
    }

    // 12. Mục 4: Prune khi RESUMED với danh sách có mục quá hạn (>24 giờ)
    @Test
    fun pruneExpired_whenResumedWithExpiredItems_removesExpiredAndKeepsValid() {
        val now = TimeUnit.HOURS.toMillis(100)
        val ttlMs = TimeUnit.HOURS.toMillis(24)

        val queue = listOf(
            PendingCapture("content://media/valid_1", now - TimeUnit.HOURS.toMillis(1)),
            PendingCapture("content://media/expired_1", now - TimeUnit.HOURS.toMillis(25)),
            PendingCapture("content://media/valid_2", now - TimeUnit.HOURS.toMillis(23)),
            PendingCapture("content://media/expired_2", now - TimeUnit.HOURS.toMillis(50)),
            PendingCapture("content://media/exact_boundary", now - ttlMs),
            PendingCapture("content://media/just_expired", now - ttlMs - 1L)
        )

        val pruned = PendingCaptureQueue.pruneExpired(queue, now)
        assertEquals(3, pruned.size)
        assertEquals(
            listOf("content://media/valid_1", "content://media/valid_2", "content://media/exact_boundary"),
            pruned.map { it.uriString }
        )
    }
}
