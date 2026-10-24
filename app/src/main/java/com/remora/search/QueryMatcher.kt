package com.remora.search

import java.text.Normalizer

/**
 * Plain-text view of a card used by the keyword matcher.
 * Keeps this file free of Android / Room / serialization types so it is trivially unit-testable.
 */
data class SearchableDoc(
    val title: String,
    val subject: String,
    val topics: List<String>,
    val intent: String,
    val summary: String,
    val ocr: String
)

object TextFolding {
    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Lowercase + strip Vietnamese diacritics, mapping 1 char -> 1 char.
     * Because the length never changes, an index found in the folded text is valid in the original text
     * (used for highlighting and snippets).
     */
    fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            if (ch.code < 128) {
                sb.append(ch.lowercaseChar())
                continue
            }
            val lower = ch.lowercaseChar()
            if (lower == 'đ') {
                sb.append('d')
                continue
            }
            sb.append(Normalizer.normalize(lower.toString(), Normalizer.Form.NFD)[0])
        }
        return sb.toString()
    }

    fun tokens(foldedText: String): List<String> =
        foldedText.split(TOKEN_SPLIT).filter { it.isNotEmpty() }
}

/** Student shorthand that a pure embedding model cannot resolve from 4 letters alone. */
object Abbreviations {
    private val MAP: Map<String, String> = mapOf(
        "pttk" to "phân tích thiết kế hệ thống",
        "pttkht" to "phân tích thiết kế hệ thống",
        "xstk" to "xác suất thống kê",
        "cnpm" to "công nghệ phần mềm",
        "ctdl" to "cấu trúc dữ liệu",
        "csdl" to "cơ sở dữ liệu",
        "mmt" to "mạng máy tính",
        "hdh" to "hệ điều hành",
        "oop" to "lập trình hướng đối tượng",
        "dstt" to "đại số tuyến tính",
        "ktlt" to "kỹ thuật lập trình",
        "ml" to "machine learning",
        "dl" to "deep learning",
        "nlp" to "xử lý ngôn ngữ tự nhiên"
    )

    /** @param foldedToken already lowercase / diacritic-free. */
    fun lookup(foldedToken: String): String? = MAP[foldedToken]
}

/** [text] is folded; [expansion] keeps Vietnamese diacritics so it can be shown in the UI. */
data class QueryTerm(val text: String, val expansion: String? = null)

data class ParsedQuery(
    val original: String,
    val terms: List<QueryTerm>,
    /** Text sent to the embedding model: the user's words plus any abbreviation expansions. */
    val embeddingText: String
) {
    val isBlank: Boolean get() = terms.isEmpty()

    /** (abbreviation, expansion) pairs, for the "how I understood your query" chip. */
    val expansions: List<Pair<String, String>>
        get() = terms.mapNotNull { t -> t.expansion?.let { t.text to it } }

    /** Folded terms worth highlighting in result text. */
    val highlightTerms: List<String>
        get() = terms.flatMap { t ->
            listOf(t.text) + (t.expansion?.let { TextFolding.tokens(TextFolding.fold(it)) } ?: emptyList())
        }.filter { it.length >= 3 }.distinct()
}

object QueryParser {
    /** Filler words people type around the real subject of a search. */
    private val STOPWORDS = setOf(
        "cai", "ve", "la", "cua", "va", "nhung", "cac", "nao", "o", "trong", "toi", "minh",
        "chup", "anh", "hinh", "tim", "giup", "co", "khong", "do", "ay", "nay", "mot", "cho", "voi"
    )

    fun parse(cleanQuery: String): ParsedQuery {
        val folded = TextFolding.fold(cleanQuery)
        val tokens = TextFolding.tokens(folded)
        val content = tokens.filter { it !in STOPWORDS }
        // If the user typed only filler words, still search for them rather than returning nothing.
        val chosen = (if (content.isEmpty()) tokens else content).distinct()
        val terms = chosen.map { QueryTerm(it, Abbreviations.lookup(it)) }
        val embeddingText = buildString {
            append(cleanQuery.trim())
            terms.forEach { t -> t.expansion?.let { append(' ').append(it) } }
        }
        return ParsedQuery(cleanQuery.trim(), terms, embeddingText)
    }
}

data class LexicalResult(val score: Float, val matchedTerms: Int, val totalTerms: Int) {
    /**
     * Coverage gate, tightened from >=1/2 after the v1 benchmark showed junk queries like
     * "công thức nấu ăn" matching math cards through the generic term "công thức" (2 of 4 terms).
     * Short queries (<= 2 terms) keep the >=1/2 rule so single-word abbreviations still match;
     * longer queries must cover at least 2/3 of their meaningful terms.
     */
    val isMatch: Boolean get() = totalTerms > 0 &&
        (if (totalTerms <= 2) matchedTerms * 2 >= totalTerms else matchedTerms * 3 >= totalTerms * 2)
}

object LexicalMatcher {
    private const val W_TITLE = 3f
    private const val W_TAGS = 2.5f
    private const val W_INTENT = 2f
    private const val W_SUMMARY = 1.5f
    private const val W_OCR = 1f

    private val PHRASE_STOP = setOf("va", "cua", "ve", "la", "the", "and", "of", "in", "trong", "cho", "cac", "nhung", "voi")

    private class Field(val weight: Float, val tokens: Set<String>, val acronyms: List<String>)

    private fun field(weight: Float, phrases: List<String>, withAcronyms: Boolean): Field {
        val tokens = HashSet<String>()
        val acronyms = ArrayList<String>()
        for (p in phrases) {
            val toks = TextFolding.tokens(TextFolding.fold(p))
            tokens.addAll(toks)
            if (withAcronyms) {
                val a = toks.filter { it !in PHRASE_STOP }.map { it[0] }.joinToString("")
                if (a.length >= 3) acronyms.add(a)
            }
        }
        return Field(weight, tokens, acronyms)
    }

    private fun tokenMatch(term: String, tokens: Set<String>): Boolean =
        if (term.length <= 2) term in tokens else tokens.any { it.startsWith(term) }

    private fun matches(term: QueryTerm, f: Field): Boolean {
        if (tokenMatch(term.text, f.tokens)) return true
        // "pttk" ⊂ initials of "phân tích thiết kế hệ thống" = "pttkht"
        if (term.text.length >= 3 && f.acronyms.any { it.contains(term.text) }) return true
        val exp = term.expansion
        if (exp != null) {
            val expTokens = TextFolding.tokens(TextFolding.fold(exp)).filter { it !in PHRASE_STOP }
            if (expTokens.isNotEmpty() && expTokens.all { tokenMatch(it, f.tokens) }) return true
        }
        return false
    }

    fun score(query: ParsedQuery, doc: SearchableDoc): LexicalResult {
        if (query.terms.isEmpty()) return LexicalResult(0f, 0, 0)
        val fields = listOf(
            field(W_TITLE, listOf(doc.title), withAcronyms = true),
            field(W_TAGS, listOf(doc.subject) + doc.topics, withAcronyms = true),
            field(W_INTENT, listOf(doc.intent), withAcronyms = true),
            field(W_SUMMARY, listOf(doc.summary), withAcronyms = false),
            field(W_OCR, listOf(doc.ocr), withAcronyms = false)
        )
        var matched = 0
        var total = 0f
        for (term in query.terms) {
            var best = 0f
            for (f in fields) {
                if (f.weight > best && matches(term, f)) best = f.weight
            }
            if (best > 0f) {
                matched++
                total += best / W_TITLE
            }
        }
        return LexicalResult(total / query.terms.size, matched, query.terms.size)
    }
}

object Snippets {
    /** Ranges (in the ORIGINAL text) where any folded [terms] occur; overlapping ranges are merged. */
    fun highlightRanges(text: String, terms: List<String>): List<IntRange> {
        if (text.isEmpty() || terms.isEmpty()) return emptyList()
        val folded = TextFolding.fold(text)
        if (folded.length != text.length) return emptyList()
        val raw = ArrayList<IntRange>()
        for (term in terms) {
            if (term.length < 3) continue
            var from = 0
            while (true) {
                val idx = folded.indexOf(term, from)
                if (idx < 0) break
                raw.add(idx until idx + term.length)
                from = idx + term.length
            }
        }
        if (raw.isEmpty()) return emptyList()
        raw.sortBy { it.first }
        val merged = ArrayList<IntRange>()
        var cur = raw[0]
        for (i in 1 until raw.size) {
            val r = raw[i]
            if (r.first <= cur.last + 1) {
                cur = cur.first..maxOf(cur.last, r.last)
            } else {
                merged.add(cur)
                cur = r
            }
        }
        merged.add(cur)
        return merged
    }

    /** A short single-line excerpt around the first occurrence of any term, or null if none occurs. */
    fun windowAround(text: String, terms: List<String>, radius: Int = 70): String? {
        val first = highlightRanges(text, terms).firstOrNull() ?: return null
        val start = (first.first - radius).coerceAtLeast(0)
        val end = (first.last + 1 + radius).coerceAtMost(text.length)
        val body = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
        return (if (start > 0) "…" else "") + body + (if (end < text.length) "…" else "")
    }
}
