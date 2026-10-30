package com.remora.search

import com.remora.core.model.contract.VectorMath
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.StoredEmbedding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import timber.log.Timber
import kotlin.math.sqrt

class SemanticSearchEngineTest {

    private val dao = SearchTestDao()
    private val queryVector = searchVector(1f).toList()

    @Test
    fun weakOnlySemanticMatchesReturnAnEmptyOutcome() = runTest {
        dao.cards.value = listOf(searchCard(1, 0.50f), searchCard(2, 0.5499f), searchCard(3, -0.5f))
        val outcome = SemanticSearchEngine(dao) { Result.success(queryVector) }.search("quasar")
        assertTrue(outcome.results.isEmpty())
        assertTrue(outcome.semanticAvailable)
        assertEquals(3, outcome.indexedCount)
    }

    @Test
    fun thresholdIsInclusiveAndLexicalEvidenceQualifiesIndependently() = runTest {
        dao.cards.value = listOf(
            searchCard(1, 0.55f), searchCard(2, 0.5499f), searchCard(3, 0.10f).copy(userIntent = "quasar"),
            searchCard(4, 0.90f).copy(title = "quasar"), searchCard(5, 0.85f)
        )
        val outcome = SemanticSearchEngine(dao) { Result.success(queryVector) }.search("quasar")
        assertEquals(setOf(1L, 3L, 4L, 5L), outcome.results.map { it.card.id }.toSet())
        assertEquals(4L, outcome.results.first().card.id)
        assertEquals(MatchKind.SEMANTIC, outcome.results.single { it.card.id == 1L }.matchKind)
        assertEquals(MatchKind.KEYWORD, outcome.results.single { it.card.id == 3L }.matchKind)
        assertEquals(MatchKind.BOTH, outcome.results.single { it.card.id == 4L }.matchKind)
    }

    @Test
    fun offlineSearchRetainsNotesWithoutVectorsAndTextWithCorruptVectors() = runTest {
        dao.cards.value = listOf(
            searchCard(1).copy(status = "PENDING", embedding = null, userIntent = "Ôn thi giữa kỳ"),
            searchCard(2).copy(embedding = byteArrayOf(1, 2), ocrText = "Bài ôn thi giữa kỳ cần đọc"), searchCard(3)
        )
        val outcome = SemanticSearchEngine(dao) { Result.failure(IllegalStateException("offline")) }.search("on thi giua ky")
        assertEquals(setOf(1L, 2L), outcome.results.map { it.card.id }.toSet())
        assertFalse(outcome.semanticAvailable)
        assertTrue(outcome.results.all { it.matchKind == MatchKind.KEYWORD })
        assertEquals("Ôn thi giữa kỳ", outcome.results.single { it.card.id == 1L }.card.userIntent)
        assertNotNull(outcome.results.single { it.card.id == 2L }.snippet)
        assertEquals(1, outcome.indexedCount)
    }

    @Test
    fun abbreviationExpansionAndHighlightsSurviveOfflineFallback() = runTest {
        dao.cards.value = listOf(searchCard(1).copy(title = "Phân tích thiết kế hệ thống"))
        var embeddedText: String? = null
        val outcome = SemanticSearchEngine(dao) { embeddedText = it; Result.failure(IllegalStateException("offline")) }.search("pttk")
        assertEquals("pttk phân tích thiết kế hệ thống", embeddedText)
        assertEquals(listOf("pttk" to "phân tích thiết kế hệ thống"), outcome.expansions)
        assertTrue(outcome.highlightTerms.containsAll(listOf("pttk", "phan", "tich", "thiet")))
        assertEquals(MatchKind.KEYWORD, outcome.results.single().matchKind)
    }

    @Test
    fun timeOnlyQueryUsesInclusiveBoundsAndNewestFirstWithoutEmbedding() = runTest {
        val now = 1_800_000_000_000L
        val range = TimeResolver.resolve("hôm qua", now)
        val start = range.startTimeMs!!
        val end = range.endTimeMs!!
        dao.cards.value = listOf(
            searchCard(1).copy(createdAt = start, embedding = null, status = "PENDING"),
            searchCard(2).copy(createdAt = start - 1), searchCard(3).copy(createdAt = end), searchCard(4).copy(createdAt = end + 1)
        )
        val engine = SemanticSearchEngine(dao) { error("A time-only query must not embed") }
        val outcome = engine.search("hôm qua", now)
        assertEquals(listOf(3L, 1L), outcome.results.map { it.card.id })
        assertTrue(outcome.results.all { it.matchKind == MatchKind.TIME })
        assertEquals("hôm qua", outcome.timeLabel)
        assertTrue(engine.search("", now).results.isEmpty())
    }

    @Test
    fun timeAndAbbreviationQueryFiltersBothLexicalAndSemanticCandidates() = runTest {
        val now = 1_800_000_000_000L
        val range = TimeResolver.resolve("hôm bữa", now)
        dao.cards.value = listOf(
            searchCard(1, 0.1f).copy(title = "Phân tích thiết kế hệ thống", createdAt = now - 1),
            searchCard(2, 0.9f).copy(createdAt = range.startTimeMs!! - 1),
            searchCard(3, 0.9f).copy(createdAt = now + 1), searchCard(4, 0.9f).copy(createdAt = range.startTimeMs!!)
        )
        val outcome = SemanticSearchEngine(dao) { Result.success(queryVector) }.search("pttk hôm bữa", now)
        assertEquals(setOf(1L, 4L), outcome.results.map { it.card.id }.toSet())
        assertEquals(range.label, outcome.timeLabel)
        assertEquals(listOf("pttk" to "phân tích thiết kế hệ thống"), outcome.expansions)
    }

    @Test
    fun libraryRefreshReusesQueryVectorAndReadsCurrentCards() = runTest {
        var calls = 0
        val engine = SemanticSearchEngine(dao) { calls++; Result.success(queryVector) }
        dao.cards.value = listOf(searchCard(1, 0.9f))
        assertEquals(1L, engine.search("quasar").results.single().card.id)
        dao.cards.value = listOf(searchCard(2, 0.9f).copy(title = "Latest title"))
        val refreshed = engine.search("quasar")
        assertEquals(2L, refreshed.results.single().card.id)
        assertEquals("Latest title", refreshed.results.single().card.title)
        dao.cards.value = emptyList()
        assertTrue(engine.search("quasar").results.isEmpty())
        assertEquals(1, calls)
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingKeywordFallback() = runTest {
        val engine = SemanticSearchEngine(dao) { throw CancellationException("Cancelled") }
        try { engine.search("quasar"); fail("Cancellation must propagate") }
        catch (_: CancellationException) { }
    }

    @Test
    fun diagnosticsExcludeQueryAndEmbeddingExceptionText() = runTest {
        val messages = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) { messages += message; assertNull(t) }
        }
        Timber.plant(tree)
        try {
            SemanticSearchEngine(dao) { Result.failure(IllegalStateException("private response body")) }.search("private study query")
            assertTrue(messages.isNotEmpty())
            assertTrue(messages.none { it.contains("private") })
        } finally { Timber.uproot(tree) }
    }
}

internal fun searchVector(cosine: Float): FloatArray = FloatArray(StoredEmbedding.DIMENSION).apply {
    this[0] = cosine
    this[1] = sqrt(1f - cosine * cosine)
}

internal fun searchCard(id: Long, cosine: Float = 0f): MemoryCardEntity = MemoryCardEntity(
    id = id, imageUri = "file:///owned/$id.png", createdAt = id, title = "Physics",
    status = "DONE", extractionComplete = true, embedding = VectorMath.toByteArray(searchVector(cosine))
)

/** Both suites exercise the real engine against the same observable library. */
internal class SearchTestDao : MemoryCardDao {
    val cards = MutableStateFlow<List<MemoryCardEntity>>(emptyList())
    override fun getAllCardsFlow(): Flow<List<MemoryCardEntity>> = cards
    override suspend fun getCardById(id: Long): MemoryCardEntity? = cards.value.find { it.id == id }
    override suspend fun getCardCount(): Int = cards.value.size
    override suspend fun getDoneCardCount(): Int = cards.value.count { it.status == "DONE" && it.embedding != null }
    override suspend fun insertCard(card: MemoryCardEntity): Long = error("Not used by search")
    override suspend fun updateCard(card: MemoryCardEntity): Unit = error("Not used by search")
    override suspend fun deleteCard(id: Long): Unit = error("Not used by search")
    override suspend fun deleteAllCards(): Unit = error("Not used by search")
    override suspend fun getAllDoneCardsWithEmbedding(): List<MemoryCardEntity> = error("Search must use the snapshot")
    override suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long): List<MemoryCardEntity> = error("Search must filter the snapshot")
    override suspend fun getCandidatesExcluding(excludeId: Long): List<MemoryCardEntity> = error("Not used by search")
    override suspend fun getCardByHash(hash: String): MemoryCardEntity? = error("Not used by search")
    override suspend fun getPendingCards(): List<MemoryCardEntity> = error("Not used by search")
    override suspend fun getPendingResurfacing(): List<MemoryCardEntity> = error("Not used by search")
    override suspend fun resetAuthenticationFailures(): Unit = error("Not used by search")
}
