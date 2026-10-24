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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        override fun getLatestSourceCardIdFlow(): Flow<Long?> = flowOf(null)
        override suspend fun getLatestSourceCardId(): Long? = null
        override fun getRecentResultsFlow(): Flow<List<ResurfacingResultEntity>> = flowOf(emptyList())
        override suspend fun insertResults(results: List<ResurfacingResultEntity>) {}
        override suspend fun getResultsForCard(newCardId: Long): List<ResurfacingResultEntity> = emptyList()
        override suspend fun deleteResultsForCard(newCardId: Long) {}
        override suspend fun deleteAllResults() {}
    }

    private val fakeCardDao = object : MemoryCardDao {
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

    @Test
    fun viewModelSelectsLatestSourceCardAndSwitchesWhenNewResultsArrive() = runTest {
        val latestSourceIdFlow = MutableStateFlow<Long?>(null)
        val resultsMap = mutableMapOf<Long, MutableStateFlow<List<ResurfacingResultEntity>>>()
        val cardsMap = mutableMapOf<Long, MemoryCardEntity>()

        // 3 cards in repository/cardDao
        val card1 = MemoryCardEntity(id = 1L, imageUri = "uri1", title = "Card 1", createdAt = 1000L)
        val card2 = MemoryCardEntity(id = 2L, imageUri = "uri2", title = "Card 2", createdAt = 2000L)
        val card3 = MemoryCardEntity(id = 3L, imageUri = "uri3", title = "Card 3", createdAt = 3000L)
        cardsMap[1L] = card1
        cardsMap[2L] = card2
        cardsMap[3L] = card3

        val reactiveResurfaceDao = object : ResurfacingResultDao {
            override fun getResultsForCardFlow(newCardId: Long): Flow<List<ResurfacingResultEntity>> {
                return resultsMap.getOrPut(newCardId) { MutableStateFlow(emptyList()) }
            }
            override fun getLatestSourceCardIdFlow(): Flow<Long?> = latestSourceIdFlow
            override suspend fun getLatestSourceCardId(): Long? = latestSourceIdFlow.value
            override fun getRecentResultsFlow(): Flow<List<ResurfacingResultEntity>> = flowOf(emptyList())
            override suspend fun insertResults(results: List<ResurfacingResultEntity>) {}
            override suspend fun getResultsForCard(newCardId: Long): List<ResurfacingResultEntity> =
                resultsMap[newCardId]?.value ?: emptyList()
            override suspend fun deleteResultsForCard(newCardId: Long) {
                resultsMap.remove(newCardId)
            }
            override suspend fun deleteAllResults() {
                resultsMap.clear()
                latestSourceIdFlow.value = null
            }
        }

        val reactiveCardDao = object : MemoryCardDao {
            override suspend fun insertCard(card: MemoryCardEntity): Long = card.id
            override suspend fun updateCard(card: MemoryCardEntity) { cardsMap[card.id] = card }
            override suspend fun deleteCard(id: Long) { cardsMap.remove(id) }
            override suspend fun getCardById(id: Long): MemoryCardEntity? = cardsMap[id]
            override fun getAllCardsFlow(): Flow<List<MemoryCardEntity>> = flowOf(cardsMap.values.toList())
            override suspend fun getAllDoneCardsWithEmbedding(): List<MemoryCardEntity> = emptyList()
            override suspend fun getCandidatesExcluding(excludeId: Long): List<MemoryCardEntity> = emptyList()
            override suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long): List<MemoryCardEntity> = emptyList()
            override suspend fun getCardCount(): Int = cardsMap.size
            override suspend fun getDoneCardCount(): Int = cardsMap.size
            override suspend fun deleteAllCards() { cardsMap.clear() }
        }

        val viewModel = GalleryViewModel(fakeRepo, reactiveResurfaceDao, reactiveCardDao, testDispatcher)

        // Start collecting in backgroundScope so StateFlow sharing starts
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.resurfacedState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Initially, no resurfacing results
        assertNull(viewModel.resurfacedState.value.sourceCard)
        assertTrue(viewModel.resurfacedState.value.items.isEmpty())

        // 2. Resurfacing results arrive for Card 1 (with candidate Card 2)
        val entity1 = ResurfacingResultEntity(
            newCardId = 1L,
            candidateCardId = 2L,
            group = "SIMILAR",
            relation = "DUPLICATE",
            confidence = 0.95f
        )
        resultsMap.getOrPut(1L) { MutableStateFlow(emptyList()) }.value = listOf(entity1)
        latestSourceIdFlow.value = 1L
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1L, viewModel.resurfacedState.value.sourceCard?.id)
        assertEquals("Card 1", viewModel.resurfacedState.value.sourceCard?.title)
        assertEquals(1, viewModel.resurfacedState.value.items.size)
        assertEquals(2L, viewModel.resurfacedState.value.items[0].candidateCard.id)

        // 3. New resurfacing results arrive for Card 3 (with candidate Card 1)
        val entity2 = ResurfacingResultEntity(
            newCardId = 3L,
            candidateCardId = 1L,
            group = "COMPLEMENTARY",
            relation = "EXPLANATION",
            confidence = 0.92f
        )
        resultsMap.getOrPut(3L) { MutableStateFlow(emptyList()) }.value = listOf(entity2)
        latestSourceIdFlow.value = 3L
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify: Switched to Card 3, and results are NOT mixed with Card 1
        assertEquals(3L, viewModel.resurfacedState.value.sourceCard?.id)
        assertEquals("Card 3", viewModel.resurfacedState.value.sourceCard?.title)
        assertEquals(1, viewModel.resurfacedState.value.items.size)
        assertEquals(1L, viewModel.resurfacedState.value.items[0].candidateCard.id)

        // 4. Results cleared (e.g. all results deleted or reset)
        latestSourceIdFlow.value = null
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.resurfacedState.value.sourceCard)
        assertTrue(viewModel.resurfacedState.value.items.isEmpty())
    }

    @Test
    fun testDeletedSourceCardDoesNotCrashViewModel() = runTest {
        val latestSourceIdFlow = MutableStateFlow<Long?>(10L)
        val resultsFlow = MutableStateFlow(
            listOf(
                ResurfacingResultEntity(
                    newCardId = 10L,
                    candidateCardId = 20L,
                    group = "SIMILAR",
                    relation = "DUPLICATE",
                    confidence = 0.90f
                )
            )
        )
        val cardsMap = mutableMapOf<Long, MemoryCardEntity>()
        // Candidate card exists, but source card 10L does NOT exist (e.g. was deleted)
        cardsMap[20L] = MemoryCardEntity(id = 20L, imageUri = "uri20", title = "Candidate 20", createdAt = 2000L)

        val reactiveResurfaceDao = object : ResurfacingResultDao {
            override fun getResultsForCardFlow(newCardId: Long): Flow<List<ResurfacingResultEntity>> = resultsFlow
            override fun getLatestSourceCardIdFlow(): Flow<Long?> = latestSourceIdFlow
            override suspend fun getLatestSourceCardId(): Long? = latestSourceIdFlow.value
            override fun getRecentResultsFlow(): Flow<List<ResurfacingResultEntity>> = flowOf(emptyList())
            override suspend fun insertResults(results: List<ResurfacingResultEntity>) {}
            override suspend fun getResultsForCard(newCardId: Long): List<ResurfacingResultEntity> = resultsFlow.value
            override suspend fun deleteResultsForCard(newCardId: Long) {}
            override suspend fun deleteAllResults() {}
        }

        val reactiveCardDao = object : MemoryCardDao {
            override suspend fun insertCard(card: MemoryCardEntity): Long = card.id
            override suspend fun updateCard(card: MemoryCardEntity) {}
            override suspend fun deleteCard(id: Long) {}
            override suspend fun getCardById(id: Long): MemoryCardEntity? = cardsMap[id]
            override fun getAllCardsFlow(): Flow<List<MemoryCardEntity>> = flowOf(emptyList())
            override suspend fun getAllDoneCardsWithEmbedding(): List<MemoryCardEntity> = emptyList()
            override suspend fun getCandidatesExcluding(excludeId: Long): List<MemoryCardEntity> = emptyList()
            override suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long): List<MemoryCardEntity> = emptyList()
            override suspend fun getCardCount(): Int = 0
            override suspend fun getDoneCardCount(): Int = 0
            override suspend fun deleteAllCards() {}
        }

        val viewModel = GalleryViewModel(fakeRepo, reactiveResurfaceDao, reactiveCardDao, testDispatcher)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.resurfacedState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Must not crash; sourceCard is null, candidate item is present
        assertNull(viewModel.resurfacedState.value.sourceCard)
        assertEquals(1, viewModel.resurfacedState.value.items.size)
        assertEquals(20L, viewModel.resurfacedState.value.items[0].candidateCard.id)
    }

    @Test
    fun testDeletedCandidateCardGracefullyFilteredOut() = runTest {
        val latestSourceIdFlow = MutableStateFlow<Long?>(10L)
        val resultsFlow = MutableStateFlow(
            listOf(
                ResurfacingResultEntity(
                    newCardId = 10L,
                    candidateCardId = 999L, // Does not exist in cardsMap
                    group = "SIMILAR",
                    relation = "DUPLICATE",
                    confidence = 0.90f
                )
            )
        )
        val cardsMap = mutableMapOf<Long, MemoryCardEntity>()
        cardsMap[10L] = MemoryCardEntity(id = 10L, imageUri = "uri10", title = "Source 10", createdAt = 1000L)

        val reactiveResurfaceDao = object : ResurfacingResultDao {
            override fun getResultsForCardFlow(newCardId: Long): Flow<List<ResurfacingResultEntity>> = resultsFlow
            override fun getLatestSourceCardIdFlow(): Flow<Long?> = latestSourceIdFlow
            override suspend fun getLatestSourceCardId(): Long? = latestSourceIdFlow.value
            override fun getRecentResultsFlow(): Flow<List<ResurfacingResultEntity>> = flowOf(emptyList())
            override suspend fun insertResults(results: List<ResurfacingResultEntity>) {}
            override suspend fun getResultsForCard(newCardId: Long): List<ResurfacingResultEntity> = resultsFlow.value
            override suspend fun deleteResultsForCard(newCardId: Long) {}
            override suspend fun deleteAllResults() {}
        }

        val reactiveCardDao = object : MemoryCardDao {
            override suspend fun insertCard(card: MemoryCardEntity): Long = card.id
            override suspend fun updateCard(card: MemoryCardEntity) {}
            override suspend fun deleteCard(id: Long) {}
            override suspend fun getCardById(id: Long): MemoryCardEntity? = cardsMap[id]
            override fun getAllCardsFlow(): Flow<List<MemoryCardEntity>> = flowOf(emptyList())
            override suspend fun getAllDoneCardsWithEmbedding(): List<MemoryCardEntity> = emptyList()
            override suspend fun getCandidatesExcluding(excludeId: Long): List<MemoryCardEntity> = emptyList()
            override suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long): List<MemoryCardEntity> = emptyList()
            override suspend fun getCardCount(): Int = 0
            override suspend fun getDoneCardCount(): Int = 0
            override suspend fun deleteAllCards() {}
        }

        val viewModel = GalleryViewModel(fakeRepo, reactiveResurfaceDao, reactiveCardDao, testDispatcher)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.resurfacedState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Source card found, candidate 999 dropped by mapNotNull
        assertEquals(10L, viewModel.resurfacedState.value.sourceCard?.id)
        assertTrue(viewModel.resurfacedState.value.items.isEmpty())
    }

    @Test
    fun testDeleteCardDeletesFromRepositoryAndResurfacingDao() = runTest {
        var deletedFromResurfacingId: Long? = null
        val customResurfaceDao = object : ResurfacingResultDao {
            override fun getResultsForCardFlow(newCardId: Long): Flow<List<ResurfacingResultEntity>> = flowOf(emptyList())
            override fun getLatestSourceCardIdFlow(): Flow<Long?> = flowOf(null)
            override suspend fun getLatestSourceCardId(): Long? = null
            override fun getRecentResultsFlow(): Flow<List<ResurfacingResultEntity>> = flowOf(emptyList())
            override suspend fun insertResults(results: List<ResurfacingResultEntity>) {}
            override suspend fun getResultsForCard(newCardId: Long): List<ResurfacingResultEntity> = emptyList()
            override suspend fun deleteResultsForCard(newCardId: Long) {
                deletedFromResurfacingId = newCardId
            }
            override suspend fun deleteAllResults() {}
        }

        val viewModel = GalleryViewModel(fakeRepo, customResurfaceDao, fakeCardDao)
        viewModel.deleteCard(77L)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(77L), deletedCardIds)
        assertEquals(77L, deletedFromResurfacingId)
    }

    @Test
    fun testSelectSourceCardOverridesLatestSourceCardIdFlow() = runTest {
        val latestSourceIdFlow = MutableStateFlow<Long?>(10L)
        val resultsMap = mutableMapOf<Long, List<ResurfacingResultEntity>>()
        resultsMap[10L] = listOf(
            ResurfacingResultEntity(
                newCardId = 10L,
                candidateCardId = 101L,
                group = "SIMILAR",
                relation = "DUPLICATE",
                confidence = 0.85f
            )
        )
        resultsMap[50L] = listOf(
            ResurfacingResultEntity(
                newCardId = 50L,
                candidateCardId = 202L,
                group = "ACTIONABLE",
                relation = "SUPPLEMENT",
                confidence = 0.95f
            )
        )

        val cardsMap = mutableMapOf<Long, MemoryCardEntity>()
        cardsMap[10L] = MemoryCardEntity(id = 10L, imageUri = "uri10", title = "Source 10", createdAt = 1000L)
        cardsMap[50L] = MemoryCardEntity(id = 50L, imageUri = "uri50", title = "Source 50", createdAt = 5000L)
        cardsMap[101L] = MemoryCardEntity(id = 101L, imageUri = "uri101", title = "Candidate 101", createdAt = 500L)
        cardsMap[202L] = MemoryCardEntity(id = 202L, imageUri = "uri202", title = "Candidate 202", createdAt = 600L)

        val reactiveResurfaceDao = object : ResurfacingResultDao {
            override fun getResultsForCardFlow(newCardId: Long): Flow<List<ResurfacingResultEntity>> =
                flowOf(resultsMap[newCardId] ?: emptyList())
            override fun getLatestSourceCardIdFlow(): Flow<Long?> = latestSourceIdFlow
            override suspend fun getLatestSourceCardId(): Long? = latestSourceIdFlow.value
            override fun getRecentResultsFlow(): Flow<List<ResurfacingResultEntity>> = flowOf(emptyList())
            override suspend fun insertResults(results: List<ResurfacingResultEntity>) {}
            override suspend fun getResultsForCard(newCardId: Long): List<ResurfacingResultEntity> =
                resultsMap[newCardId] ?: emptyList()
            override suspend fun deleteResultsForCard(newCardId: Long) {}
            override suspend fun deleteAllResults() {}
        }

        val reactiveCardDao = object : MemoryCardDao {
            override suspend fun insertCard(card: MemoryCardEntity): Long = card.id
            override suspend fun updateCard(card: MemoryCardEntity) {}
            override suspend fun deleteCard(id: Long) {}
            override suspend fun getCardById(id: Long): MemoryCardEntity? = cardsMap[id]
            override fun getAllCardsFlow(): Flow<List<MemoryCardEntity>> = flowOf(emptyList())
            override suspend fun getAllDoneCardsWithEmbedding(): List<MemoryCardEntity> = emptyList()
            override suspend fun getCandidatesExcluding(excludeId: Long): List<MemoryCardEntity> = emptyList()
            override suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long): List<MemoryCardEntity> = emptyList()
            override suspend fun getCardCount(): Int = 0
            override suspend fun getDoneCardCount(): Int = 0
            override suspend fun deleteAllCards() {}
        }

        val viewModel = GalleryViewModel(fakeRepo, reactiveResurfaceDao, reactiveCardDao, testDispatcher)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.resurfacedState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially follows latestSourceCardIdFlow (10L)
        assertEquals(10L, viewModel.resurfacedState.value.sourceCard?.id)
        assertEquals(1, viewModel.resurfacedState.value.items.size)
        assertEquals(101L, viewModel.resurfacedState.value.items[0].candidateCard.id)

        // Explicitly select 50L (e.g. from notification deep link)
        viewModel.selectSourceCard(50L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(50L, viewModel.resurfacedState.value.sourceCard?.id)
        assertEquals(1, viewModel.resurfacedState.value.items.size)
        assertEquals(202L, viewModel.resurfacedState.value.items[0].candidateCard.id)

        // Reset explicit selection (pass null) -> falls back to latestSourceIdFlow
        viewModel.selectSourceCard(null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(10L, viewModel.resurfacedState.value.sourceCard?.id)
        assertEquals(101L, viewModel.resurfacedState.value.items[0].candidateCard.id)
    }
}
