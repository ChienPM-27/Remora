package com.remora.search

import com.remora.ai.AiGatewayClient
import com.remora.core.model.contract.VectorMath
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.dao.MemoryCardDao
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

enum class MatchKind {
    KEYWORD,   // matched words / abbreviations only
    SEMANTIC,  // matched by meaning only
    BOTH,
    TIME       // query was only a time phrase ("hôm qua"), card is inside the range
}

data class SearchResultCard(
    val card: MemoryCard,
    val similarityScore: Float,
    val matchKind: MatchKind = MatchKind.SEMANTIC,
    /** OCR excerpt around the match, only when the summary itself does not show the match. */
    val snippet: String? = null
)

data class SearchOutcome(
    val results: List<SearchResultCard>,
    /** e.g. "hôm bữa · trong 3 tuần qua"; null when the query had no time phrase. */
    val timeLabel: String? = null,
    /** (abbreviation, expansion) pairs, e.g. "pttk" -> "phân tích thiết kế hệ thống". */
    val expansions: List<Pair<String, String>> = emptyList(),
    /** Folded terms to highlight in result text. */
    val highlightTerms: List<String> = emptyList(),
    /** False when the query could not be embedded (offline / quota / auth): results are keyword-only. */
    val semanticAvailable: Boolean = true,
    val indexedCount: Int = 0
)

/**
 * Hybrid search: keyword/abbreviation matching on-device + embedding similarity.
 *
 * Local text remains searchable without a vector. Semantic-only results must meet the accepted
 * relevance threshold; weak matches never substitute for an honest empty result.
 */
class SemanticSearchEngine(
    private val dao: MemoryCardDao,
    private val generateEmbedding: suspend (String) -> Result<List<Float>>
) {
    constructor(dao: MemoryCardDao, aiClient: AiGatewayClient) : this(
        dao, { text -> aiClient.generateEmbedding(text, taskType = "RETRIEVAL_QUERY") }
    )

    // Small cache so refining filters / retrying does not burn the gateway's embedding quota.
    private val queryVectorCache = object : LinkedHashMap<String, FloatArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean = size > 24
    }

    fun observeLibrary(): Flow<List<MemoryCard>> = dao.getAllCardsFlow()
        .map { entities -> entities.map { it.toDomainModel() } }

    fun indexedCount(cards: List<MemoryCard>): Int = cards.count {
        it.status == ProcessingStatus.DONE && it.embedding != null
    }

    suspend fun search(
        rawQuery: String,
        now: Long = System.currentTimeMillis(),
        cards: List<MemoryCard>? = null
    ): SearchOutcome {
        val resolved = TimeResolver.resolve(rawQuery, now)
        val parsed = QueryParser.parse(resolved.cleanQuery)
        val library = cards ?: observeLibrary().first()
        val indexed = indexedCount(library)
        val candidates = library.filter { card ->
            (resolved.startTimeMs == null || card.createdAt >= resolved.startTimeMs) &&
                (resolved.endTimeMs == null || card.createdAt <= resolved.endTimeMs)
        }

        // Query was only a time phrase ("hôm qua"): list everything in that range, newest first.
        if (parsed.isBlank) {
            val results = if (resolved.startTimeMs != null) {
                candidates.sortedByDescending { it.createdAt }.take(MAX_RESULTS)
                    .map { SearchResultCard(it, 0f, MatchKind.TIME) }
            } else emptyList()
            return SearchOutcome(results = results, timeLabel = resolved.label, indexedCount = indexed)
        }

        val queryVector = embedQuery(parsed.embeddingText)
        currentCoroutineContext().ensureActive()

        val scored = candidates.map { card ->
            val lex = LexicalMatcher.score(parsed, card.toSearchableDoc())
            val sim = if (queryVector != null && card.status == ProcessingStatus.DONE) {
                card.embedding?.takeIf { it.size == queryVector.size }?.let { VectorMath.dotProduct(queryVector, it) }
                    ?.takeIf { it.isFinite() }
            } else null
            Candidate(card, lex, sim)
        }

        val strong = scored.filter { it.lex.isMatch || (it.sim ?: 0f) >= SEMANTIC_ACCEPT }
        Timber.i(
            "Search completed: candidates=${scored.size}, matches=${strong.size}, semantic=${queryVector != null}"
        )

        val results = strong
            .map { it to rank(it) }
            .sortedWith(
                compareByDescending<Pair<Candidate, Float>> { it.second }
                    .thenByDescending { it.first.card.createdAt }
            )
            .take(MAX_RESULTS)
            .map { it.first.toResult(parsed) }

        return SearchOutcome(
            results = results,
            timeLabel = resolved.label,
            expansions = parsed.expansions,
            highlightTerms = parsed.highlightTerms,
            semanticAvailable = queryVector != null,
            indexedCount = indexed
        )
    }

    private class Candidate(val card: MemoryCard, val lex: LexicalResult, val sim: Float?)

    private fun rank(c: Candidate): Float {
        val lexScore = if (c.lex.isMatch) c.lex.score else 0f
        val semScore = c.sim?.let { ((it - 0.40f) / 0.35f).coerceIn(0f, 1f) } ?: 0f
        return maxOf(lexScore, semScore) + 0.25f * minOf(lexScore, semScore)
    }

    private fun Candidate.toResult(parsed: ParsedQuery): SearchResultCard {
        val semHit = (sim ?: 0f) >= SEMANTIC_ACCEPT
        val kind = when {
            lex.isMatch && semHit -> MatchKind.BOTH
            lex.isMatch -> MatchKind.KEYWORD
            else -> MatchKind.SEMANTIC
        }
        val summaryShowsMatch = Snippets.highlightRanges(card.summary, parsed.highlightTerms).isNotEmpty()
        val snippet = if (lex.isMatch && !summaryShowsMatch) {
            Snippets.windowAround(card.ocrText, parsed.highlightTerms)
        } else null
        return SearchResultCard(card, sim ?: lex.score, kind, snippet)
    }

    private suspend fun embedQuery(text: String): FloatArray? {
        synchronized(queryVectorCache) { queryVectorCache[text] }?.let { return it }
        val result = generateEmbedding(text)
        val vector = result.getOrNull()?.toFloatArray()?.let { VectorMath.l2Normalize(it) }
        if (vector == null) {
            Timber.w("Query embedding unavailable; using local keyword search")
            return null
        }
        synchronized(queryVectorCache) { queryVectorCache[text] = vector }
        return vector
    }

    private fun MemoryCard.toSearchableDoc() = SearchableDoc(
        title = title,
        subject = tags.subject,
        topics = tags.topics,
        intent = userIntent.orEmpty(),
        summary = summary,
        ocr = ocrText
    )

    companion object {
        /** Existing product threshold; lexical evidence can qualify independently of cosine. */
        const val SEMANTIC_ACCEPT = 0.55f
        const val MAX_RESULTS = 30
    }
}
