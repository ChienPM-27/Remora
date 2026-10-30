package com.remora.ui.gallery

import com.remora.core.model.contract.MemoryCardRepository
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.dao.ResurfacingResultDao
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.ResurfacingResultEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val insertedCards = mutableListOf<MemoryCard>()
    private val updatedCards = mutableListOf<MemoryCard>()
    private val deletedCardIds = mutableListOf<Long>()
    private val storedCards = MutableStateFlow<List<MemoryCard>>(emptyList())

    private val fakeRepo = object : MemoryCardRepository {
        override fun getAllCards(): Flow<List<MemoryCard>> = storedCards
        override suspend fun getCardById(id: Long): MemoryCard? = insertedCards.find { it.id == id }
        override suspend fun insertCard(card: MemoryCard): Long {
            insertedCards.add(card)
            return (insertedCards.size).toLong()
        }
        override suspend fun updateCard(card: MemoryCard) {
            updatedCards.add(card)
        }
        override suspend fun deleteCard(id: Long) {
            deletedCardIds.add(id)
        }
        override suspend fun getAllWithEmbeddings(): List<MemoryCard> = emptyList()
        override suspend fun searchHybrid(query: String, limit: Int): List<MemoryCard> = emptyList()
    }

    private val fakeResurfaceDao = object : ResurfacingResultDao {
        override fun getResultsForCardFlow(newCardId: Long): Flow<List<ResurfacingResultEntity>> = flowOf(emptyList())
        override fun getRecentResultsFlow(): Flow<List<ResurfacingResultEntity>> = flowOf(emptyList())
        override suspend fun insertResults(results: List<ResurfacingResultEntity>) {}
        override suspend fun getResultsForCard(newCardId: Long): List<ResurfacingResultEntity> = emptyList()
        override suspend fun deleteResultsForCard(newCardId: Long) {}
        override suspend fun deleteAllResults() {}
        override suspend fun getExistingCardIds(ids: List<Long>) = emptyList<Long>()
        override suspend fun beginAttempt(cardId: Long, maxAttempts: Int) = 0
        override suspend fun markCompleted(cardId: Long) = 0
        override suspend fun markFailed(cardId: Long) = 0
        override suspend fun resetFailed(cardId: Long) = 0
    }

    private val fakeCardDao = object : MemoryCardDao {
        override suspend fun getCardByHash(hash: String): MemoryCardEntity? = null
        override suspend fun getPendingCards(): List<MemoryCardEntity> = emptyList()
        override suspend fun getPendingResurfacing(): List<MemoryCardEntity> = emptyList()
        override suspend fun resetAuthenticationFailures() = Unit
        override suspend fun insertCard(card: MemoryCardEntity): Long = 1L
        override suspend fun updateCard(card: MemoryCardEntity) {}
        override suspend fun deleteCard(id: Long) {}
        override suspend fun getCardById(id: Long): MemoryCardEntity? = null
        override fun getAllCardsFlow(): Flow<List<MemoryCardEntity>> = flowOf(emptyList())
        override suspend fun getAllDoneCardsWithEmbedding(): List<MemoryCardEntity> = emptyList()
        override suspend fun getCandidatesExcluding(excludeId: Long): List<MemoryCardEntity> = emptyList()
        override suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long): List<MemoryCardEntity> = emptyList()
        override suspend fun getCardCount(): Int = 0
        override suspend fun getDoneCardCount(): Int = 0
        override suspend fun deleteAllCards() {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun importCardCreatesPendingCardWithTrimmedIntent() = runTest {
        val viewModel = GalleryViewModel(fakeRepo, fakeResurfaceDao, fakeCardDao)
        var capturedCardId: Long? = null

        viewModel.importCard(
            imageUri = "file:///data/user/0/com.remora/files/imported_images/test.jpg",
            userIntent = "  ôn thi giữa kỳ toán  "
        ) { id ->
            capturedCardId = id
        }

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, insertedCards.size)
        val created = insertedCards.first()
        assertEquals("file:///data/user/0/com.remora/files/imported_images/test.jpg", created.imageUri)
        assertEquals("ôn thi giữa kỳ toán", created.userIntent)
        assertEquals(ProcessingStatus.PENDING, created.status)
        assertEquals("Đang xử lý...", created.title)
        assertEquals(1L, capturedCardId)
    }

    @Test
    fun importCardWithBlankIntentStoresNull() = runTest {
        val viewModel = GalleryViewModel(fakeRepo, fakeResurfaceDao, fakeCardDao)

        viewModel.importCard(
            imageUri = "file:///data/user/0/com.remora/files/imported_images/test2.jpg",
            userIntent = "   "
        ) {}

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, insertedCards.size)
        assertNull(insertedCards.first().userIntent)
    }

    @Test
    fun selectedDetailTracksUpdatesAndDisappearsAfterExternalDeletion() = runTest {
        val viewModel = GalleryViewModel(fakeRepo, fakeResurfaceDao, fakeCardDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedCard.collect() }
        val pending = MemoryCard(id = 42, imageUri = "file:///owned.png", createdAt = 1000)
        storedCards.value = listOf(pending)
        viewModel.showCard(pending.id)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(pending, viewModel.selectedCard.value)

        val completed = pending.copy(status = ProcessingStatus.DONE, title = "Latest title", summary = "Latest summary")
        storedCards.value = listOf(completed)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(completed, viewModel.selectedCard.value)

        storedCards.value = emptyList()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.selectedCard.value)
    }

    @Test
    fun retryDelegatesByIdWithoutOverwritingStoredFields() = runTest {
        val viewModel = GalleryViewModel(fakeRepo, fakeResurfaceDao, fakeCardDao)
        val retried = mutableListOf<Long>()
        viewModel.retryCard(42) { retried += it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(42L), retried)
        assertTrue(updatedCards.isEmpty())
        assertFalse(viewModel.actionInProgress.value)
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun connectionRetryCoalescesActionsAndReportsOnlyASafeError() = runTest {
        val viewModel = GalleryViewModel(fakeRepo, fakeResurfaceDao, fakeCardDao)
        val release = CompletableDeferred<Unit>()
        val retried = mutableListOf<Long>()
        viewModel.retryConnections(42) { retried += it; release.await(); error("Private provider response") }
        viewModel.retryConnections(42) { retried += it }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(42L), retried)
        assertTrue(viewModel.actionInProgress.value)

        release.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.actionInProgress.value)
        assertEquals("Chưa thể thử lại gợi nhớ. Hãy kiểm tra kết nối AI rồi thử lại.", viewModel.actionError.value)
        assertTrue(updatedCards.isEmpty())
    }

    @Test
    fun repeatedActionsAreIgnoredUntilTheCurrentActionCompletes() = runTest {
        val viewModel = GalleryViewModel(fakeRepo, fakeResurfaceDao, fakeCardDao)
        val release = CompletableDeferred<Unit>()
        var calls = 0
        viewModel.retryCard(42) { calls++; release.await() }
        viewModel.retryCard(42) { calls++ }
        viewModel.deleteCard(42) { calls++ }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, calls)
        assertTrue(viewModel.actionInProgress.value)
        release.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.actionInProgress.value)
    }

    @Test
    fun detailCanCloseDuringAnActionAndOpenAnotherCardAfterward() = runTest {
        val viewModel = GalleryViewModel(fakeRepo, fakeResurfaceDao, fakeCardDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedCard.collect() }
        val first = MemoryCard(id = 42, imageUri = "file:///first.png", createdAt = 1000)
        val second = first.copy(id = 43)
        storedCards.value = listOf(first, second)
        viewModel.showCard(first.id)
        val release = CompletableDeferred<Unit>()
        viewModel.retryCard(first.id) { release.await() }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissCard()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.selectedCard.value)
        assertTrue(viewModel.actionInProgress.value)

        release.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.showCard(second.id)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(second, viewModel.selectedCard.value)
    }

    @Test
    fun deletionFailureKeepsDetailOpenAndAllowsAnotherAttempt() = runTest {
        val viewModel = GalleryViewModel(fakeRepo, fakeResurfaceDao, fakeCardDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedCard.collect() }
        val card = MemoryCard(id = 42, imageUri = "file:///owned.png", createdAt = 1000)
        storedCards.value = listOf(card)
        viewModel.showCard(card.id)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteCard(card.id) { error("Storage unavailable") }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(card, viewModel.selectedCard.value)
        assertNotNull(viewModel.actionError.value)
        assertFalse(viewModel.actionInProgress.value)

        var deleted: Long? = null
        viewModel.deleteCard(card.id) { deleted = it; storedCards.value = emptyList() }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(card.id, deleted)
        assertNull(viewModel.selectedCard.value)
        assertNull(viewModel.actionError.value)
        assertTrue(deletedCardIds.isEmpty())
    }

    @Test
    fun clearReportsSuccessOnlyAfterTheCoordinatorCompletes() = runTest {
        val viewModel = GalleryViewModel(fakeRepo, fakeResurfaceDao, fakeCardDao)
        var completed = false
        viewModel.clearCards(clear = { error("Storage unavailable") }, onCleared = { completed = true })
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(completed)
        assertNotNull(viewModel.actionError.value)

        val release = CompletableDeferred<Unit>()
        viewModel.clearCards(clear = { release.await() }, onCleared = { completed = true })
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(completed)
        assertTrue(viewModel.actionInProgress.value)
        release.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(completed)
        assertFalse(viewModel.actionInProgress.value)
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun cancellationDoesNotBecomeAnActionError() = runTest {
        val viewModel = GalleryViewModel(fakeRepo, fakeResurfaceDao, fakeCardDao)
        viewModel.retryCard(42) {
            throw CancellationException("Screen closed")
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.actionInProgress.value)
        assertNull(viewModel.actionError.value)
    }
}
