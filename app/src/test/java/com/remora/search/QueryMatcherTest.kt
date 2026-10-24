package com.remora.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryMatcherTest {

    // The real card from the bug report: "Ôn tập PTTK Hệ thống".
    private val pttkCard = SearchableDoc(
        title = "Ôn tập PTTK Hệ thống",
        subject = "Phân tích và Thiết kế Hệ thống",
        topics = listOf("Khái niệm cơ bản", "Quy trình phát triển hệ thống", "Phương pháp thu thập yêu cầu"),
        intent = "ôn thi giữa kỳ",
        summary = "Tài liệu ôn tập lý thuyết về Phân tích Thiết kế Hệ thống thông tin.",
        ocr = "LÝ THUYẾT: 1. Các khái niệm: Dữ liệu, Thông tin, Hệ thống, Hệ thống thông tin."
    )

    private val calculusCard = SearchableDoc(
        title = "Đạo hàm riêng",
        subject = "Giải tích",
        topics = listOf("Đạo hàm"),
        intent = "làm bài tập",
        summary = "Công thức đạo hàm riêng của hàm nhiều biến.",
        ocr = "f(x,y) = x^2 + y^2"
    )

    @Test
    fun foldKeepsLengthAndStripsDiacritics() {
        val s = "Đạo hàm Ôn tập Phương pháp"
        val folded = TextFolding.fold(s)
        assertEquals(s.length, folded.length)
        assertEquals("dao ham on tap phuong phap", folded)
    }

    @Test
    fun abbreviationIsExpandedForEmbedding() {
        val q = QueryParser.parse("pttk")
        assertEquals(1, q.terms.size)
        assertEquals("pttk phân tích thiết kế hệ thống", q.embeddingText)
        assertEquals(listOf("pttk" to "phân tích thiết kế hệ thống"), q.expansions)
    }

    @Test
    fun pttkQueryMatchesTheReportedCard() {
        val r = LexicalMatcher.score(QueryParser.parse("pttk"), pttkCard)
        assertTrue(r.isMatch)
        assertTrue(r.score > 0.9f) // literal hit in the title
    }

    @Test
    fun pttkMatchesByAcronymWhenLiteralAbbreviationIsAbsent() {
        val doc = pttkCard.copy(title = "Quy trình phát triển hệ thống", intent = "", summary = "", ocr = "")
        assertTrue(LexicalMatcher.score(QueryParser.parse("pttk"), doc).isMatch)
    }

    @Test
    fun pttkDoesNotMatchUnrelatedCard() {
        assertFalse(LexicalMatcher.score(QueryParser.parse("pttk"), calculusCard).isMatch)
    }

    @Test
    fun matchingIgnoresDiacriticsAndCase() {
        assertTrue(LexicalMatcher.score(QueryParser.parse("DAO HAM"), calculusCard).isMatch)
        assertTrue(LexicalMatcher.score(QueryParser.parse("giai tich"), calculusCard).isMatch)
    }

    @Test
    fun fillerWordsAreIgnored() {
        val q = QueryParser.parse("cái slide về phân phối chuẩn chụp")
        assertEquals(listOf("slide", "phan", "phoi", "chuan"), q.terms.map { it.text })
    }

    @Test
    fun queryMadeOnlyOfFillerStillSearches() {
        assertEquals(1, QueryParser.parse("cái").terms.size)
        assertTrue(QueryParser.parse("   ").isBlank)
    }

    @Test
    fun shortTermsDoNotMatchInsideOtherWords() {
        // "ai" must not match "hai" / "bai"
        val doc = calculusCard.copy(title = "bai hai", subject = "", topics = emptyList(), intent = "", summary = "", ocr = "")
        assertFalse(LexicalMatcher.score(QueryParser.parse("ai"), doc).isMatch)
    }

    @Test
    fun partialCoverageNeedsAtLeastHalfOfTerms() {
        val q = QueryParser.parse("đạo hàm xác suất kinh tế") // 6 folded terms, only 2 present in the card
        assertFalse(LexicalMatcher.score(q, calculusCard).isMatch)
    }

    @Test
    fun highlightRangesAreMappedToOriginalText() {
        val text = "Phân phối chuẩn và phân phối nhị thức"
        val ranges = Snippets.highlightRanges(text, listOf("phan", "phoi"))
        assertEquals(4, ranges.size)
        assertEquals("Phân", text.substring(ranges[0].first, ranges[0].last + 1))
        assertEquals("phối", text.substring(ranges[1].first, ranges[1].last + 1))
    }

    @Test
    fun overlappingHighlightRangesAreMerged() {
        val ranges = Snippets.highlightRanges("Ôn tập PTTK", listOf("ptt", "pttk"))
        assertEquals(1, ranges.size)
        assertEquals(7..10, ranges[0])
    }

    @Test
    fun windowAroundReturnsExcerptOrNull() {
        val ocr = "a ".repeat(100) + "PTTK là môn học " + "b ".repeat(100)
        val w = Snippets.windowAround(ocr, listOf("pttk"))
        assertNotNull(w)
        assertTrue(w!!.contains("PTTK"))
        assertTrue(w.startsWith("…") && w.endsWith("…"))
        assertNull(Snippets.windowAround("không liên quan", listOf("pttk")))
    }
}
