package com.remora.ui.gallery

import com.remora.core.model.contract.MemoryCardRepository
import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.dao.ResurfacingResultDao
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.ResurfacingResultEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val insertedCards = mutableListOf<MemoryCard>()
    private val updatedCards = mutableListOf<MemoryCard>()
    private val deletedCardIds = mutableListOf<Long>()

    private val fakeRepo = object : MemoryCardRepository {
        override fun getAllCards(): Flow<List<MemoryCard>> = flowOf(emptyList())
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
    fun retryCardResetsAttemptsAndSetsPending() = runTest {
        val viewModel = GalleryViewModel(fakeRepo, fakeResurfaceDao, fakeCardDao)
        val failedCard = MemoryCard(
            id = 42L,
            imageUri = "content://media/1",
            createdAt = 1000L,
            userIntent = "intent",
            title = "Test",
            summary = "",
            tags = CardTags("Toán", emptyList()),
            contentType = ContentType.THEORY,
            ocrText = "",
            status = ProcessingStatus.FAILED,
            attemptCount = 3,
            errorCode = "TIMEOUT"
        )

        var enqueuedId: Long? = null
        viewModel.retryCard(failedCard) { id ->
            enqueuedId = id
        }

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, updatedCards.size)
        val updated = updatedCards.first()
        assertEquals(ProcessingStatus.PENDING, updated.status)
        assertEquals(0, updated.attemptCount)
        assertNull(updated.errorCode)
        assertEquals(42L, enqueuedId)
    }
}
