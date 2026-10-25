package com.remora.capture

import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfacedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class ResurfacingNotificationContentTest {

    // --- 1. Safe Truncation Tests (Surrogate pairs & Vietnamese accents) ---

    @Test
    fun `truncateSafely preserves string shorter or equal to maxLength`() {
        val short = "Toán rời rạc"
        assertEquals(short, NotificationHelper.truncateSafely(short, 80))

        val exact80 = "A".repeat(80)
        assertEquals(exact80, NotificationHelper.truncateSafely(exact80, 80))

        assertEquals("", NotificationHelper.truncateSafely("", 80))
    }

    @Test
    fun `truncateSafely truncates long text and appends ellipsis within limit`() {
        val longText = "Phương trình vi phân cấp 2 thuần nhất là dạng phương trình toán học quan trọng trong giải tích"
        val truncated = NotificationHelper.truncateSafely(longText, 40)

        assertTrue("Truncated length must be <= 40, actual: ${truncated.length}", truncated.length <= 40)
        assertTrue("Must end with ellipsis", truncated.endsWith("…"))
        assertTrue("Must start with original prefix", longText.startsWith(truncated.removeSuffix("…")))
    }

    @Test
    fun `truncateSafely never splits surrogate pairs (emojis)`() {
        // Emoji 📚 is \uD83D\uDCDA (surrogate pair: high \uD83D, low \uDCDA)
        val prefix = "A".repeat(78)
        val textWithEmoji = "$prefix\uD83D\uDCDA và kiến thức toán"

        // Max length 80: index 79 would be between high and low surrogate if not handled safely
        val result = NotificationHelper.truncateSafely(textWithEmoji, 80)

        assertTrue("Result length must be <= 80", result.length <= 80)
        // Check that neither character of any surrogate pair is orphaned
        for (i in result.indices) {
            val ch = result[i]
            if (Character.isHighSurrogate(ch)) {
                assertTrue("High surrogate must be followed by low surrogate", i + 1 < result.length && Character.isLowSurrogate(result[i + 1]))
            }
            if (Character.isLowSurrogate(ch)) {
                assertTrue("Low surrogate must be preceded by high surrogate", i > 0 && Character.isHighSurrogate(result[i - 1]))
            }
        }
    }

    @Test
    fun `truncateSafely never splits Vietnamese decomposed diacritics`() {
        // "tiếng Việt" decomposed into NFD where 'ê' = 'e' + '\u0302' + '\u0301'
        val decomposed = Normalizer.normalize("Giải tích hàm: Đạo hàm riêng và vi phân toàn phần của hàm số nhiều biến số thực", Normalizer.Form.NFD)
        val truncated = NotificationHelper.truncateSafely(decomposed, 35)

        assertTrue(truncated.length <= 35)
        // Verify no orphan combining character at end
        val lastContentChar = truncated.removeSuffix("…").lastOrNull()
        if (lastContentChar != null) {
            val type = Character.getType(lastContentChar)
            assertFalse(
                "Last character before ellipsis must not be a combining mark",
                type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
            )
        }
    }

    // --- 2. Item Selection & Priority Tests (Best item per group first) ---

    @Test
    fun `selectTopAlertItems returns empty when input is empty`() {
        val result = NotificationHelper.selectTopAlertItems(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `selectTopAlertItems handles fewer than 3 items`() {
        val item1 = ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "Bài 1", "Lý do 1", confidence = 0.90f)
        val single = NotificationHelper.selectTopAlertItems(listOf(item1))
        assertEquals(1, single.size)
        assertEquals("Bài 1", single[0].candidateTitle)

        val item2 = ResurfacingAlertItem(ResurfaceGroup.COMPLEMENTARY, "Bài 2", "Lý do 2", confidence = 0.88f)
        val two = NotificationHelper.selectTopAlertItems(listOf(item1, item2))
        assertEquals(2, two.size)
        assertEquals("Bài 1", two[0].candidateTitle)
        assertEquals("Bài 2", two[1].candidateTitle)
    }

    @Test
    fun `selectTopAlertItems prioritizes highest confidence item of each group first`() {
        // Group SIMILAR has two items: 0.99 and 0.96
        // Group COMPLEMENTARY has one item: 0.92
        // Group ACTIONABLE has one item: 0.85
        // Standard sort by confidence alone would pick: [SIMILAR 0.99, SIMILAR 0.96, COMPLEMENTARY 0.92].
        // But rule says: "chọn mục confidence cao nhất mỗi nhóm trước".
        // Therefore, ACTIONABLE (0.85) MUST be picked over SIMILAR (0.96) because it represents its group!
        val s1 = ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "Similar High", "Reason S1", confidence = 0.99f)
        val s2 = ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "Similar Mid", "Reason S2", confidence = 0.96f)
        val c1 = ResurfacingAlertItem(ResurfaceGroup.COMPLEMENTARY, "Comp Item", "Reason C1", confidence = 0.92f)
        val a1 = ResurfacingAlertItem(ResurfaceGroup.ACTIONABLE, "Action Item", "Reason A1", confidence = 0.85f)

        val selected = NotificationHelper.selectTopAlertItems(listOf(s1, s2, c1, a1), maxLines = 3)

        assertEquals(3, selected.size)
        // Must contain top of all 3 groups
        val selectedTitles = selected.map { it.candidateTitle }
        assertTrue("Must include Similar High", selectedTitles.contains("Similar High"))
        assertTrue("Must include Comp Item", selectedTitles.contains("Comp Item"))
        assertTrue("Must include Action Item (best of group ACTIONABLE)", selectedTitles.contains("Action Item"))
        assertFalse("Must NOT take secondary Similar Mid when 3 distinct groups exist", selectedTitles.contains("Similar Mid"))

        // Must be ordered by confidence descending
        assertEquals(listOf("Similar High", "Comp Item", "Action Item"), selectedTitles)
    }

    @Test
    fun `selectTopAlertItems fills remaining slots with remaining highest confidence items when fewer than 3 groups present`() {
        // Only 2 groups present: SIMILAR (3 items) and COMPLEMENTARY (1 item)
        val s1 = ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "S1", "Reason", confidence = 0.95f)
        val s2 = ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "S2", "Reason", confidence = 0.91f)
        val s3 = ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "S3", "Reason", confidence = 0.82f)
        val c1 = ResurfacingAlertItem(ResurfaceGroup.COMPLEMENTARY, "C1", "Reason", confidence = 0.88f)

        val selected = NotificationHelper.selectTopAlertItems(listOf(s1, s2, s3, c1), maxLines = 3)

        assertEquals(3, selected.size)
        // Group bests: S1 (0.95), C1 (0.88). Remaining slot goes to S2 (0.91).
        val titles = selected.map { it.candidateTitle }
        assertEquals(listOf("S1", "S2", "C1"), titles)
    }

    // --- 3. Content Builder & BigTextStyle Formatting Tests ---

    @Test
    fun `buildResurfacingNotificationContent produces expected lines and BigText layout`() {
        val items = listOf(
            ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "Định lý Fermat", "Phương pháp tìm cực trị hàm số", confidence = 0.95f),
            ResurfacingAlertItem(ResurfaceGroup.COMPLEMENTARY, "Đạo hàm cấp cao", "Bổ trợ tính xấp xỉ Taylor", confidence = 0.90f),
            ResurfacingAlertItem(ResurfaceGroup.ACTIONABLE, "Bài tập vi phân 3", "Cần luyện tập trước kỳ thi", confidence = 0.86f)
        )

        val content = NotificationHelper.buildResurfacingNotificationContent(
            sourceCardTitle = "Giải tích 1: Cực trị",
            items = items
        )

        assertEquals("Giải tích 1: Cực trị", content.title)
        assertEquals(3, content.lines.size)

        assertEquals("tương đồng · Định lý Fermat — Phương pháp tìm cực trị hàm số", content.lines[0])
        assertEquals("bổ trợ · Đạo hàm cấp cao — Bổ trợ tính xấp xỉ Taylor", content.lines[1])
        assertEquals("có giá trị sử dụng · Bài tập vi phân 3 — Cần luyện tập trước kỳ thi", content.lines[2])

        // Verify BigText structure: first line is source card title, followed by up to 3 body lines
        val expectedBigText = """
            Giải tích 1: Cực trị
            tương đồng · Định lý Fermat — Phương pháp tìm cực trị hàm số
            bổ trợ · Đạo hàm cấp cao — Bổ trợ tính xấp xỉ Taylor
            có giá trị sử dụng · Bài tập vi phân 3 — Cần luyện tập trước kỳ thi
        """.trimIndent()

        assertEquals(expectedBigText, content.bigText)
    }

    @Test
    fun `buildResurfacingNotificationContent handles fewer than 3 items cleanly`() {
        val items = listOf(
            ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "Bài tập mẫu", "Ví dụ minh hoạ cách giải", confidence = 0.92f)
        )

        val content = NotificationHelper.buildResurfacingNotificationContent(
            sourceCardTitle = "Hình học không gian",
            items = items
        )

        assertEquals(1, content.lines.size)
        assertEquals("tương đồng · Bài tập mẫu — Ví dụ minh hoạ cách giải", content.lines[0])

        val expectedBigText = "Hình học không gian\ntương đồng · Bài tập mẫu — Ví dụ minh hoạ cách giải"
        assertEquals(expectedBigText, content.bigText)
    }

    @Test
    fun `buildResurfacingNotificationContent handles empty items gracefully`() {
        val content = NotificationHelper.buildResurfacingNotificationContent(
            sourceCardTitle = "Thẻ nguồn",
            items = emptyList()
        )

        assertTrue(content.lines.isEmpty())
        assertEquals("Thẻ nguồn", content.bigText)
    }

    @Test
    fun `buildResurfacingNotificationContent safely truncates long reasons`() {
        val veryLongReason = "Lý do liên kết này rất dài và chi tiết nhằm giải thích cặn kẽ tại sao hai bài học này lại bổ trợ cho nhau trong quá trình ôn tập chuẩn bị thi học kỳ 1 của sinh viên ngành công nghệ thông tin đại học bách khoa"
        val item = ResurfacingAlertItem(ResurfaceGroup.COMPLEMENTARY, "Đại số tuyến tính", veryLongReason, confidence = 0.89f)

        val content = NotificationHelper.buildResurfacingNotificationContent("Ma trận nghịch đảo", listOf(item))

        val line = content.lines[0]
        assertTrue("Line must start with group label and title", line.startsWith("bổ trợ · Đại số tuyến tính — "))
        assertTrue("Line must end with ellipsis because reason was truncated", line.endsWith("…"))
    }

    @Test
    fun `toAlertItem extension correctly converts domain ResurfacedItem`() {
        val dummyCard = MemoryCard(
            id = 42L,
            imageUri = "content://media/42",
            createdAt = 1000L,
            userIntent = "ôn tập",
            title = "Ma trận trực giao",
            status = ProcessingStatus.DONE
        )
        val domainItem = ResurfacedItem(
            candidateCard = dummyCard,
            group = ResurfaceGroup.ACTIONABLE,
            relation = ResurfaceRelation.SUPPLEMENT,
            reason = "Cần giải bài tập liên quan",
            confidence = 0.94f
        )

        val alertItem = domainItem.toAlertItem()

        assertEquals(ResurfaceGroup.ACTIONABLE, alertItem.group)
        assertEquals("Ma trận trực giao", alertItem.candidateTitle)
        assertEquals("Cần giải bài tập liên quan", alertItem.reason)
        assertEquals(0.94f, alertItem.confidence, 0.001f)
    }

    @Test
    fun `buildResurfacingNotificationContent handles blank source title, candidate title, and reason`() {
        val blankTitleItem = ResurfacingAlertItem(
            group = ResurfaceGroup.SIMILAR,
            candidateTitle = "   ",
            reason = "  ",
            confidence = 0.90f
        )

        val content = NotificationHelper.buildResurfacingNotificationContent(
            sourceCardTitle = "   ",
            items = listOf(blankTitleItem)
        )

        assertEquals("Bài học vừa lưu", content.title)
        assertEquals(1, content.lines.size)
        assertEquals("tương đồng · Thẻ liên quan", content.lines[0])
        assertEquals("Bài học vừa lưu\ntương đồng · Thẻ liên quan", content.bigText)
    }

    @Test
    fun `truncateSafely edge cases with non-positive limits or small limit`() {
        assertEquals("", NotificationHelper.truncateSafely("Bất kỳ chuỗi nào", maxLength = 0))
        assertEquals("", NotificationHelper.truncateSafely("Bất kỳ chuỗi nào", maxLength = -5))
        assertEquals("…", NotificationHelper.truncateSafely("Toán học", maxLength = 1))
    }

    @Test
    fun `truncateSafely never strips Vietnamese decomposed diacritics when cutoff lands on accent`() {
        // "ả" in NFD is 'a' (U+0061) + combining hook above (U+0309)
        val nfdText = Normalizer.normalize("ả bài học", Normalizer.Form.NFD)
        assertEquals(2, Normalizer.normalize("ả", Normalizer.Form.NFD).length)

        // When maxLength = 2 and ellipsis = "…", target = 1.
        // The first character "ả" takes 2 code units, so it does not fit in target = 1.
        // It must NOT truncate to "a…" (stripping the accent). It should return "…".
        val resultMax2 = NotificationHelper.truncateSafely(nfdText, maxLength = 2)
        assertEquals("…", resultMax2)

        // When maxLength = 3 and ellipsis = "…", target = 2.
        // "ả" fits in 2 code units and normalizes to NFC "ả".
        val resultMax3 = NotificationHelper.truncateSafely(nfdText, maxLength = 3)
        assertEquals("ả…", resultMax3)
    }

    @Test
    fun `buildResurfacingNotificationContent replaces newlines in titles and reason to keep single-line format`() {
        val item = ResurfacingAlertItem(
            group = ResurfaceGroup.SIMILAR,
            candidateTitle = "Định lý\nFermat",
            reason = "Phương pháp tìm cực trị.\nCần xem lại công thức.",
            confidence = 0.95f
        )

        val content = NotificationHelper.buildResurfacingNotificationContent(
            sourceCardTitle = "Giải tích 1:\nCực trị hàm số",
            items = listOf(item)
        )

        assertEquals("Giải tích 1: Cực trị hàm số", content.title)
        assertFalse("Title must not contain newline", content.title.contains("\n"))

        assertEquals(1, content.lines.size)
        val line = content.lines[0]
        assertFalse("Line must not contain newline", line.contains("\n"))
        assertTrue(line.contains("Định lý Fermat"))
        assertTrue(line.contains("Phương pháp tìm cực trị. Cần xem lại công thức."))

        // Total lines in BigText must be exactly 2 (title + 1 item line)
        val bigTextLines = content.bigText.split("\n")
        assertEquals(2, bigTextLines.size)
        assertEquals("Giải tích 1: Cực trị hàm số", bigTextLines[0])
        assertEquals(line, bigTextLines[1])
    }

    @Test
    fun `selectTopAlertItems handles all items belonging to a single group`() {
        val items = listOf(
            ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "S1", "Reason 1", confidence = 0.95f),
            ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "S2", "Reason 2", confidence = 0.91f),
            ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "S3", "Reason 3", confidence = 0.85f),
            ResurfacingAlertItem(ResurfaceGroup.SIMILAR, "S4", "Reason 4", confidence = 0.80f)
        )

        val selected = NotificationHelper.selectTopAlertItems(items, maxLines = 3)

        assertEquals(3, selected.size)
        assertEquals(listOf("S1", "S2", "S3"), selected.map { it.candidateTitle })
    }

    @Test
    fun `notification intent constants and request codes are well-configured`() {
        assertEquals("extra_open_resurfacing", NotificationHelper.EXTRA_OPEN_RESURFACING)
        assertEquals("extra_new_card_id", NotificationHelper.EXTRA_NEW_CARD_ID)
        assertEquals(200, NotificationHelper.RESURFACING_REQUEST_CODE)
        assertEquals(2001, NotificationHelper.RESURFACING_NOTIFICATION_ID)
        // Request code and notification ID must not clash with capture prompt
        assertTrue(NotificationHelper.RESURFACING_REQUEST_CODE != 100)
        assertTrue(NotificationHelper.RESURFACING_NOTIFICATION_ID != NotificationHelper.NOTIFICATION_ID)
    }
}
