package com.remora.search

import com.remora.ai.AiGatewayClient
import com.remora.core.model.contract.VectorMath
import com.remora.core.model.entity.MemoryCard
import com.remora.data.local.dao.MemoryCardDao
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import timber.log.Timber

enum class MatchKind {
    KEYWORD,   // matched words / abbreviations only
    SEMANTIC,  // matched by meaning only
    BOTH,
    TIME,      // query was only a time phrase ("hôm qua"), card is inside the range
    WEAK       // nothing confident; closest-by-meaning fallback
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
    /** True when nothing matched confidently and [results] are the closest-by-meaning fallbacks. */
    val weakOnly: Boolean = false,
    val indexedCount: Int = 0
)

/**
 * Hybrid search: keyword/abbreviation matching on-device + embedding similarity.
 *
 * The old implementation kept only cosine >= 0.65 and returned an empty list whenever the query
 * embedding failed, so short queries such as "pttk" (4 letters vs. a long document vector) never matched.
 */
class SemanticSearchEngine(
    private val dao: MemoryCardDao,
    private val aiClient: AiGatewayClient
) {
    // Small cache so refining filters / retrying does not burn the gateway's embedding quota.
    private val queryVectorCache = object : LinkedHashMap<String, FloatArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean = size > 24
    }

    suspend fun indexedCount(): Int = dao.getDoneCardCount()

    suspend fun search(rawQuery: String, now: Long = System.currentTimeMillis()): SearchOutcome {
        val resolved = TimeResolver.resolve(rawQuery, now)
        val parsed = QueryParser.parse(resolved.cleanQuery)
        val indexed = dao.getDoneCardCount()

        val entities = if (resolved.startTimeMs != null && resolved.endTimeMs != null) {
            dao.getDoneCardsInTimeRange(resolved.startTimeMs, resolved.endTimeMs)
        } else {
            dao.getAllDoneCardsWithEmbedding()
        }

        // Query was only a time phrase ("hôm qua"): list everything in that range, newest first.
        if (parsed.isBlank) {
            val results = if (resolved.startTimeMs != null) {
                entities.take(MAX_RESULTS).map { SearchResultCard(it.toDomainModel(), 0f, MatchKind.TIME) }
            } else emptyList()
            return SearchOutcome(results = results, timeLabel = resolved.label, indexedCount = indexed)
        }

        Timber.i("Search '${parsed.embeddingText}' over ${entities.size} cards (time=${resolved.label})")

        val queryVector = embedQuery(parsed.embeddingText)
        currentCoroutineContext().ensureActive()

        val scored = entities.map { entity ->
            val card = entity.toDomainModel()
            val lex = LexicalMatcher.score(parsed, card.toSearchableDoc())
            val sim = if (queryVector != null) {
                card.embedding?.takeIf { it.size == queryVector.size }?.let { VectorMath.dotProduct(queryVector, it) }
            } else null
            Candidate(card, lex, sim)
        }

        val strong = scored.filter { it.lex.isMatch || (it.sim ?: 0f) >= SEMANTIC_ACCEPT }
        val picked: List<Candidate>
        val weakOnly: Boolean
        if (strong.isNotEmpty()) {
            picked = strong
            weakOnly = false
        } else {
            picked = scored.filter { (it.sim ?: 0f) >= SEMANTIC_WEAK }
                .sortedByDescending { it.sim }
                .take(WEAK_RESULTS)
            weakOnly = picked.isNotEmpty()
        }

        Timber.i(
            "Search done: ${strong.size} strong, ${picked.size} shown, semantic=${queryVector != null}, " +
                "bestSim=${scored.mapNotNull { it.sim }.maxOrNull()}"
        )

        val results = picked
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
            weakOnly = weakOnly,
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
            semHit -> MatchKind.SEMANTIC
            else -> MatchKind.WEAK
        }
        val summaryShowsMatch = Snippets.highlightRanges(card.summary, parsed.highlightTerms).isNotEmpty()
        val snippet = if (lex.isMatch && !summaryShowsMatch) {
            Snippets.windowAround(card.ocrText, parsed.highlightTerms)
        } else null
        return SearchResultCard(card, sim ?: lex.score, kind, snippet)
    }

    private suspend fun embedQuery(text: String): FloatArray? {
        synchronized(queryVectorCache) { queryVectorCache[text] }?.let { return it }
        val result = aiClient.generateEmbedding(text, taskType = "RETRIEVAL_QUERY")
        val vector = result.getOrNull()?.toFloatArray()?.let { VectorMath.l2Normalize(it) }
        if (vector == null) {
            Timber.w(result.exceptionOrNull(), "Query embedding failed; falling back to keyword-only search")
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
        /**
         * Cosine thresholds for gemini-embedding-2 (768d). These are starting values, not measured ones:
         * the previous fixed 0.65 dropped short queries. Tune using the "bestSim" Timber log line above.
         */
        const val SEMANTIC_ACCEPT = 0.55f
        const val SEMANTIC_WEAK = 0.45f
        const val WEAK_RESULTS = 3
        const val MAX_RESULTS = 30
    }
}
