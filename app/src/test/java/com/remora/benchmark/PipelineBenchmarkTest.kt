package com.remora.benchmark

import com.remora.core.model.entity.CardTagsCodec
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.core.model.resurface.ResurfaceValidator
import com.remora.search.LexicalMatcher
import com.remora.search.QueryParser
import com.remora.search.SearchableDoc
import com.remora.search.TimeResolver
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * T035 offline benchmark over a versioned, consent-safe fixture
 * (app/src/test/resources/benchmark/pipeline_benchmark_v1.json).
 *
 * Channels measurable deterministically without GEMINI_API_KEY:
 * lexical retrieval, temporal resolution, resurfacing validation, tags decoding.
 * Live-model channels (vision extraction, embedding semantic quality, batch LLM judgment)
 * are blocked in this environment; see docs/benchmark/report.md.
 *
 * Gates asserted here are product-derived (validator 100%, temporal 100%, no junk results on
 * no-relevant queries); retrieval precision/recall numbers are recorded as the v1 baseline.
 */
class PipelineBenchmarkTest {

    @Serializable
    private data class CardCase(
        val id: Long, val title: String, val subject: String, val topics: List<String> = emptyList(),
        val intent: String? = null, val summary: String = "", val ocr: String = "",
        val content_type: String = "THEORY", val days_ago: Long = 0
    )

    @Serializable
    private data class LexicalQuery(val name: String, val slice: String, val query: String, val relevant_ids: List<Long>)

    @Serializable
    private data class TimeCase(val name: String, val phrase: String, val kind: String, val start_days_ago: Long = 0, val end_days_ago: Long = 0)

    @Serializable
    private data class ResurfaceItemCase(val id: Long, val group: String, val relation: String, val confidence: Float)

    @Serializable
    private data class ResurfaceCase(val name: String, val items: List<ResurfaceItemCase>, val allowed: List<Long>, val expected: List<Long>)

    @Serializable
    private data class TagsCase(
        val name: String, val json: String, val expected_subject: String,
        val expected_topics: Int, val expected_course: String?, val expected_concepts: Int
    )

    @Serializable
    private data class Benchmark(
        val version: Int,
        val cards: List<CardCase>,
        val lexical_queries: List<LexicalQuery>,
        val time_cases: List<TimeCase>,
        val resurface_cases: List<ResurfaceCase>,
        val tags_cases: List<TagsCase>
    )

    private val fixture: Benchmark by lazy {
        val raw = javaClass.classLoader!!.getResourceAsStream("benchmark/pipeline_benchmark_v1.json")!!
            .bufferedReader().readText()
        // Fixture-only: lets the file carry an Infinity case. The production API parser stays
        // strict and rejects non-finite values at decode time, which is the behavior under test.
        Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }
            .decodeFromString(Benchmark.serializer(), raw)
    }

    private val fixedNow: Long = 1_700_000_000_000L
    private val dayMs = 24 * 60 * 60 * 1000L

    private fun doc(card: CardCase) = SearchableDoc(
        title = card.title,
        subject = card.subject,
        topics = card.topics,
        intent = card.intent.orEmpty(),
        summary = card.summary,
        ocr = card.ocr
    )

    @Test
    fun lexicalRetrievalBaseline() {
        val lines = mutableListOf<String>()
        var totalRelevant = 0
        var totalHit = 0
        var totalMatched = 0
        val bySlice = sortedMapOf<String, IntArray>() // [hits, relevant, matched]

        for (q in fixture.lexical_queries) {
            val parsed = QueryParser.parse(q.query)
            val matched = fixture.cards.filter { LexicalMatcher.score(parsed, doc(it)).isMatch }.map { it.id }.toSet()
            val relevant = q.relevant_ids.toSet()
            val hits = (matched intersect relevant).size
            val precision = if (matched.isEmpty()) 1f else hits.toFloat() / matched.size
            val recall = if (relevant.isEmpty()) 1f else hits.toFloat() / relevant.size
            lines += "${q.slice.padEnd(28)} ${q.name.padEnd(26)} P=${"%.2f".format(precision)} R=${"%.2f".format(recall)} matched=${matched.sorted()} relevant=${relevant.sorted()}"
            totalRelevant += relevant.size; totalHit += hits; totalMatched += matched.size
            val agg = bySlice.getOrPut(q.slice) { IntArray(3) }
            agg[0] += hits; agg[1] += relevant.size; agg[2] += matched.size
        }
        val microP = if (totalMatched == 0) 1f else totalHit.toFloat() / totalMatched
        val microR = if (totalRelevant == 0) 1f else totalHit.toFloat() / totalRelevant
        lines += "MICRO P=${"%.3f".format(microP)} R=${"%.3f".format(microR)} over ${fixture.lexical_queries.size} queries"
        bySlice.forEach { (slice, a) ->
            val p = if (a[2] == 0) 1f else a[0].toFloat() / a[2]
            val r = if (a[1] == 0) 1f else a[0].toFloat() / a[1]
            lines += "SLICE $slice P=${"%.2f".format(p)} R=${"%.2f".format(r)}"
        }
        println("=== LEXICAL BASELINE v${fixture.version} ===\n" + lines.joinToString("\n"))

        // Product gate: junk queries must return zero matches (never show junk results).
        fixture.lexical_queries.filter { it.relevant_ids.isEmpty() }.forEach { q ->
            val parsed = QueryParser.parse(q.query)
            val matched = fixture.cards.filter { LexicalMatcher.score(parsed, doc(it)).isMatch }
            assertTrue("Junk query '${q.query}' must match nothing but matched ${matched.map { it.id }}", matched.isEmpty())
        }
    }

    @Test
    fun temporalResolutionAccuracy() {
        var pass = 0
        val lines = mutableListOf<String>()
        for (tc in fixture.time_cases) {
            val r = TimeResolver.resolve(tc.phrase, now = fixedNow)
            val ok = when (tc.kind) {
                "none" -> r.startTimeMs == null && r.endTimeMs == null
                "rolling" -> r.startTimeMs == fixedNow - tc.start_days_ago * dayMs &&
                    r.endTimeMs == fixedNow - tc.end_days_ago * dayMs
                "today" -> {
                    val start = Calendar.getInstance().apply {
                        timeInMillis = fixedNow; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    r.startTimeMs == start && r.endTimeMs == fixedNow
                }
                "yesterday" -> {
                    val start = Calendar.getInstance().apply {
                        timeInMillis = fixedNow; add(Calendar.DAY_OF_YEAR, -1); set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    r.startTimeMs == start && r.endTimeMs == start + dayMs - 1
                }
                "previous_month" -> r.startTimeMs != null && r.endTimeMs != null && r.startTimeMs!! < r.endTimeMs!! && r.endTimeMs!! < fixedNow
                else -> false
            }
            lines += "${tc.name.padEnd(30)} ${if (ok) "PASS" else "FAIL"}"
            if (ok) pass++
        }
        println("=== TEMPORAL v${fixture.version} ===\n" + lines.joinToString("\n"))
        assertEquals("All temporal cases must pass (deterministic resolver)", fixture.time_cases.size, pass)
    }

    @Test
    fun resurfacingValidationAccuracy() {
        var pass = 0
        val lines = mutableListOf<String>()
        for (rc in fixture.resurface_cases) {
            val items = rc.items.mapNotNull { raw ->
                val group = try { ResurfaceGroup.valueOf(raw.group) } catch (_: Exception) { return@mapNotNull null }
                val relation = try { ResurfaceRelation.valueOf(raw.relation) } catch (_: Exception) { return@mapNotNull null }
                ResurfacedItem(
                    candidateCard = MemoryCard(id = raw.id, imageUri = "content://media/${raw.id}"),
                    group = group, relation = relation, confidence = raw.confidence
                )
            }
            val valid = ResurfaceValidator.filterValidItems(items, allowedCandidateIds = rc.allowed.toSet())
                .map { it.candidateCard.id }.sorted()
            val ok = valid == rc.expected.sorted()
            lines += "${rc.name.padEnd(30)} ${if (ok) "PASS" else "FAIL got=$valid want=${rc.expected.sorted()}"}"
            if (ok) pass++
        }
        println("=== RESURFACING VALIDATION v${fixture.version} ===\n" + lines.joinToString("\n"))
        assertEquals("All validation cases must pass (contract rules)", fixture.resurface_cases.size, pass)
    }

    @Test
    fun tagsDecodingAccuracy() {
        var pass = 0
        val lines = mutableListOf<String>()
        for (tc in fixture.tags_cases) {
            val tags = CardTagsCodec.decode(tc.json)
            val ok = tags.subject == tc.expected_subject && tags.topics.size == tc.expected_topics &&
                tags.course == tc.expected_course && tags.concepts.size == tc.expected_concepts
            lines += "${tc.name.padEnd(30)} ${if (ok) "PASS" else "FAIL subject=${tags.subject} course=${tags.course}"}"
            if (ok) pass++
        }
        println("=== TAGS DECODING v${fixture.version} ===\n" + lines.joinToString("\n"))
        assertEquals("All tags cases must pass", fixture.tags_cases.size, pass)
    }
}
