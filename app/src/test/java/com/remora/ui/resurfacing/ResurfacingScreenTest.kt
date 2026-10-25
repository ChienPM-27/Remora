package com.remora.ui.resurfacing

import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfacedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResurfacingScreenTest {

    private fun createTestCard(
        id: Long = 1L,
        title: String = "Test Card",
        status: ProcessingStatus = ProcessingStatus.DONE,
        createdAt: Long = 1000L
    ): MemoryCard {
        return MemoryCard(
            id = id,
            imageUri = "content://media/external/images/media/$id",
            createdAt = createdAt,
            userIntent = "Toán 12",
            title = title,
            summary = "Summary for $title",
            tags = CardTags(subject = "Toán", topics = listOf("Đại số")),
            contentType = ContentType.THEORY,
            ocrText = "OCR text",
            embedding = FloatArray(768) { 0.01f },
            status = status
        )
    }

    private fun createItem(
        id: Long,
        title: String,
        group: ResurfaceGroup,
        confidence: Float,
        reason: String = "Lý do $id",
        newInfo: String = "Thông tin mới $id",
        benefit: String = "Lợi ích $id"
    ): ResurfacedItem {
        return ResurfacedItem(
            candidateCard = createTestCard(id = id, title = title),
            group = group,
            relation = ResurfaceRelation.SUPPLEMENT,
            newInfo = newInfo,
            benefit = benefit,
            reason = reason,
            confidence = confidence
        )
    }

    // --- Tests for groupForDisplay ---

    @Test
    fun groupForDisplay_emptyList_returnsEmptyList() {
        val result = groupForDisplay(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun groupForDisplay_singleGroup_returnsOnlyThatGroupWithCorrectTitle() {
        val items = listOf(
            createItem(1L, "C1", ResurfaceGroup.SIMILAR, 0.85f),
            createItem(2L, "C2", ResurfaceGroup.SIMILAR, 0.90f)
        )

        val groups = groupForDisplay(items)
        assertEquals(1, groups.size)
        assertEquals(ResurfaceGroup.SIMILAR, groups[0].group)
        assertEquals("tương đồng", groups[0].title)
        assertEquals(2, groups[0].items.size)
    }

    @Test
    fun groupForDisplay_omitsEmptyGroups() {
        val items = listOf(
            createItem(1L, "C1", ResurfaceGroup.SIMILAR, 0.85f),
            createItem(2L, "C2", ResurfaceGroup.ACTIONABLE, 0.92f)
        )

        val groups = groupForDisplay(items)
        assertEquals(2, groups.size)
        assertEquals(listOf(ResurfaceGroup.SIMILAR, ResurfaceGroup.ACTIONABLE), groups.map { it.group })
        assertEquals(listOf("tương đồng", "có giá trị sử dụng"), groups.map { it.title })
    }

    @Test
    fun groupForDisplay_preservesFixedOrderRegardlessOfInput() {
        // Input has ACTIONABLE, then SIMILAR, then COMPLEMENTARY
        val items = listOf(
            createItem(1L, "C1", ResurfaceGroup.ACTIONABLE, 0.85f),
            createItem(2L, "C2", ResurfaceGroup.SIMILAR, 0.90f),
            createItem(3L, "C3", ResurfaceGroup.COMPLEMENTARY, 0.95f)
        )

        val groups = groupForDisplay(items)
        assertEquals(3, groups.size)
        assertEquals(ResurfaceGroup.SIMILAR, groups[0].group)
        assertEquals("tương đồng", groups[0].title)
        assertEquals(ResurfaceGroup.COMPLEMENTARY, groups[1].group)
        assertEquals("bổ trợ", groups[1].title)
        assertEquals(ResurfaceGroup.ACTIONABLE, groups[2].group)
        assertEquals("có giá trị sử dụng", groups[2].title)
    }

    @Test
    fun groupForDisplay_sortsItemsWithinGroupDescendingByConfidence() {
        val items = listOf(
            createItem(1L, "C1", ResurfaceGroup.COMPLEMENTARY, 0.82f),
            createItem(2L, "C2", ResurfaceGroup.COMPLEMENTARY, 0.96f),
            createItem(3L, "C3", ResurfaceGroup.COMPLEMENTARY, 0.89f)
        )

        val groups = groupForDisplay(items)
        assertEquals(1, groups.size)
        val sorted = groups[0].items
        assertEquals(3, sorted.size)
        assertEquals(2L, sorted[0].candidateCard.id)
        assertEquals(0.96f, sorted[0].confidence, 0.001f)
        assertEquals(3L, sorted[1].candidateCard.id)
        assertEquals(0.89f, sorted[1].confidence, 0.001f)
        assertEquals(1L, sorted[2].candidateCard.id)
        assertEquals(0.82f, sorted[2].confidence, 0.001f)
    }

    // --- Tests for resolveResurfacingUiState ---

    @Test
    fun resolveResurfacingUiState_noCards_returnsNoCardsEmptyState() {
        val state = resolveResurfacingUiState(
            sourceCard = null,
            items = emptyList(),
            latestCard = null
        )

        assertEquals(ResurfacingEmptyReason.NO_CARDS, state.emptyReason)
        assertEquals("chưa có bài học mới", state.headerTitle)
        assertTrue(state.items.isEmpty())
        assertNull(state.headerCard)
    }

    @Test
    fun resolveResurfacingUiState_latestPending_returnsLatestPendingEmptyState() {
        val pendingCard = createTestCard(id = 5L, title = "Đang phân tích toán 12", status = ProcessingStatus.PENDING)
        val previousSourceCard = createTestCard(id = 4L, title = "Toán 11")
        val previousItems = listOf(createItem(1L, "C1", ResurfaceGroup.SIMILAR, 0.85f))

        val state = resolveResurfacingUiState(
            sourceCard = previousSourceCard,
            items = previousItems,
            latestCard = pendingCard
        )

        assertEquals(ResurfacingEmptyReason.LATEST_PENDING, state.emptyReason)
        assertEquals("Đang phân tích toán 12", state.headerTitle)
        assertTrue(state.items.isEmpty())
        assertEquals(5L, state.headerCard?.id)
    }

    @Test
    fun resolveResurfacingUiState_newCardWithoutResults_doesNotShowOldResults() {
        val oldSourceCard = createTestCard(id = 1L, title = "Bài cũ", createdAt = 1000L)
        val oldItems = listOf(createItem(10L, "Liên kết cũ", ResurfaceGroup.SIMILAR, 0.85f))
        val newCardNoResults = createTestCard(id = 2L, title = "Bài mới không có liên kết", status = ProcessingStatus.DONE, createdAt = 2000L)

        val state = resolveResurfacingUiState(
            sourceCard = oldSourceCard,
            items = oldItems,
            latestCard = newCardNoResults
        )

        assertEquals(ResurfacingEmptyReason.NO_CONFIDENT_LINKS, state.emptyReason)
        assertEquals("Bài mới không có liên kết", state.headerTitle)
        assertTrue(state.items.isEmpty())
        assertEquals(2L, state.headerCard?.id)
    }

    @Test
    fun resolveResurfacingUiState_matchingSourceCardWithResults_returnsActiveState() {
        val sourceCard = createTestCard(id = 2L, title = "Định Lý Pythagoras: a² + b² = c²")
        val items = listOf(
            createItem(101L, "Tam giác vuông", ResurfaceGroup.SIMILAR, 0.95f),
            createItem(102L, "Hệ thức lượng", ResurfaceGroup.COMPLEMENTARY, 0.91f)
        )

        val state = resolveResurfacingUiState(
            sourceCard = sourceCard,
            items = items,
            latestCard = sourceCard
        )

        assertNull(state.emptyReason)
        assertEquals("Định Lý Pythagoras: a² + b² = c²", state.headerTitle)
        assertEquals(2, state.items.size)
        assertEquals(2L, state.headerCard?.id)
    }

    @Test
    fun resolveResurfacingUiState_sourceCardWithEmptyItems_returnsNoConfidentLinks() {
        val sourceCard = createTestCard(id = 3L, title = "Bài học đơn độc")

        val state = resolveResurfacingUiState(
            sourceCard = sourceCard,
            items = emptyList(),
            latestCard = sourceCard
        )

        assertEquals(ResurfacingEmptyReason.NO_CONFIDENT_LINKS, state.emptyReason)
        assertEquals("Bài học đơn độc", state.headerTitle)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun resolveResurfacingUiState_preservesOriginalCasingInHeaderTitle() {
        val sourceCard = createTestCard(id = 1L, title = "Java: public static void main(String[] args)")

        val state = resolveResurfacingUiState(
            sourceCard = sourceCard,
            items = emptyList(),
            latestCard = sourceCard
        )

        assertEquals("Java: public static void main(String[] args)", state.headerTitle)
    }

    @Test
    fun resolveResurfacingUiState_latestPendingWithUserIntent_prefersUserIntentWhenTitleIsDefault() {
        val pendingCard = createTestCard(
            id = 10L,
            title = "Đang xử lý...",
            status = ProcessingStatus.PENDING
        ).copy(userIntent = "Ôn tập giải tích 12")

        val state = resolveResurfacingUiState(
            sourceCard = null,
            items = emptyList(),
            latestCard = pendingCard
        )

        assertEquals(ResurfacingEmptyReason.LATEST_PENDING, state.emptyReason)
        assertEquals("Ôn tập giải tích 12", state.headerTitle)
        assertEquals(10L, state.headerCard?.id)
    }

    @Test
    fun resolveResurfacingUiState_orphanItemsWithoutSourceCard_doesNotAttributeToLatestCard() {
        val orphanItems = listOf(createItem(100L, "Liên kết mồ côi", ResurfaceGroup.SIMILAR, 0.90f))
        val latestCard = createTestCard(id = 20L, title = "Bài mới tinh", status = ProcessingStatus.DONE)

        val state = resolveResurfacingUiState(
            sourceCard = null,
            items = orphanItems,
            latestCard = latestCard
        )

        assertEquals(ResurfacingEmptyReason.NO_CONFIDENT_LINKS, state.emptyReason)
        assertEquals("Bài mới tinh", state.headerTitle)
        assertTrue(state.items.isEmpty())
        assertEquals(20L, state.headerCard?.id)
    }

    @Test
    fun resolveResurfacingUiState_sameTimestampTieBreak_correctlyIdentifiesNewerCard() {
        val oldSourceCard = createTestCard(id = 5L, title = "Bài cũ", createdAt = 5000L)
        val oldItems = listOf(createItem(10L, "Liên kết cũ", ResurfaceGroup.SIMILAR, 0.85f))
        val newCardNoResults = createTestCard(id = 6L, title = "Bài mới cùng timestamp", status = ProcessingStatus.DONE, createdAt = 5000L)

        val state = resolveResurfacingUiState(
            sourceCard = oldSourceCard,
            items = oldItems,
            latestCard = newCardNoResults
        )

        assertEquals(ResurfacingEmptyReason.NO_CONFIDENT_LINKS, state.emptyReason)
        assertEquals("Bài mới cùng timestamp", state.headerTitle)
        assertTrue(state.items.isEmpty())
        assertEquals(6L, state.headerCard?.id)
    }

    @Test
    fun resolveResurfacingUiState_latestCardFailed_returnsLatestFailedEmptyState() {
        val failedCard = createTestCard(
            id = 7L,
            title = "Bài học bị lỗi",
            status = ProcessingStatus.FAILED
        )

        val state = resolveResurfacingUiState(
            sourceCard = null,
            items = emptyList(),
            latestCard = failedCard
        )

        assertEquals(ResurfacingEmptyReason.LATEST_FAILED, state.emptyReason)
        assertEquals("Bài học bị lỗi", state.headerTitle)
        assertTrue(state.items.isEmpty())
        assertEquals(7L, state.headerCard?.id)
    }

    @Test
    fun resolveResurfacingUiState_sourceCardFailed_returnsLatestFailedEmptyState() {
        val failedCard = createTestCard(
            id = 8L,
            title = "Bài học thất bại",
            status = ProcessingStatus.FAILED
        )

        val state = resolveResurfacingUiState(
            sourceCard = failedCard,
            items = emptyList(),
            latestCard = failedCard
        )

        assertEquals(ResurfacingEmptyReason.LATEST_FAILED, state.emptyReason)
        assertEquals("Bài học thất bại", state.headerTitle)
        assertTrue(state.items.isEmpty())
        assertEquals(8L, state.headerCard?.id)
    }

    @Test
    fun resolveResurfacingUiState_doneCardWithin90sWindow_returnsLatestPending() {
        val baseTime = 1_000_000L
        val doneCard = createTestCard(
            id = 9L,
            title = "Bài học vừa phân tích xong",
            status = ProcessingStatus.DONE,
            createdAt = baseTime
        )

        // 30 seconds after card creation (< 90 seconds)
        val state = resolveResurfacingUiState(
            sourceCard = doneCard,
            items = emptyList(),
            latestCard = doneCard,
            nowMs = baseTime + 30_000L
        )

        assertEquals(ResurfacingEmptyReason.LATEST_PENDING, state.emptyReason)
        assertEquals("Bài học vừa phân tích xong", state.headerTitle)
        assertTrue(state.items.isEmpty())
        assertEquals(9L, state.headerCard?.id)
    }

    @Test
    fun resolveResurfacingUiState_doneCardOutside90sWindow_returnsNoConfidentLinks() {
        val baseTime = 1_000_000L
        val doneCard = createTestCard(
            id = 10L,
            title = "Bài học đã phân tích lâu rồi",
            status = ProcessingStatus.DONE,
            createdAt = baseTime
        )

        // 95 seconds after card creation (> 90 seconds)
        val state = resolveResurfacingUiState(
            sourceCard = doneCard,
            items = emptyList(),
            latestCard = doneCard,
            nowMs = baseTime + 95_000L
        )

        assertEquals(ResurfacingEmptyReason.NO_CONFIDENT_LINKS, state.emptyReason)
        assertEquals("Bài học đã phân tích lâu rồi", state.headerTitle)
        assertTrue(state.items.isEmpty())
        assertEquals(10L, state.headerCard?.id)
    }
}
