package com.remora.core.model.resurface

import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResurfaceModelsTest {

    private fun createTestCard(id: Long, title: String): MemoryCard {
        return MemoryCard(
            id = id,
            imageUri = "content://media/external/images/media/$id",
            title = title,
            summary = "Summary $id",
            tags = CardTags(subject = "Toán", topics = listOf("ĐạiSố")),
            contentType = ContentType.THEORY,
            status = ProcessingStatus.DONE
        )
    }

    @Test
    fun testGroupRelationCompatibilityRules() {
        // SIMILAR: DUPLICATE hoặc SUPPLEMENT
        assertTrue(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE))
        assertTrue(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.SIMILAR, ResurfaceRelation.SUPPLEMENT))
        assertFalse(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.SIMILAR, ResurfaceRelation.EXPLANATION))
        assertFalse(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.SIMILAR, ResurfaceRelation.CONTRADICTION))

        // COMPLEMENTARY: EXPLANATION hoặc SUPPLEMENT
        assertTrue(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION))
        assertTrue(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.SUPPLEMENT))
        assertFalse(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.DUPLICATE))
        assertFalse(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.CONTRADICTION))

        // ACTIONABLE: SUPPLEMENT hoặc CONTRADICTION
        assertTrue(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.ACTIONABLE, ResurfaceRelation.SUPPLEMENT))
        assertTrue(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.ACTIONABLE, ResurfaceRelation.CONTRADICTION))
        assertFalse(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.ACTIONABLE, ResurfaceRelation.DUPLICATE))
        assertFalse(ResurfaceValidator.isValidRelationForGroup(ResurfaceGroup.ACTIONABLE, ResurfaceRelation.EXPLANATION))
    }

    @Test
    fun testConfidenceThresholdStrictlyGreaterThanEighty() {
        val card1 = createTestCard(101L, "Card 1")
        val card2 = createTestCard(102L, "Card 2")
        val card3 = createTestCard(103L, "Card 3")

        val items = listOf(
            ResurfacedItem(card1, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.80f), // Rejected (<= 0.80)
            ResurfacedItem(card2, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.79f), // Rejected
            ResurfacedItem(card3, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.81f)  // Accepted (> 0.80)
        )

        val filtered = ResurfaceValidator.filterValidItems(items)
        assertEquals(1, filtered.size)
        assertEquals(103L, filtered[0].candidateCard.id)
    }

    @Test
    fun testDropCandidateIdNotInAllowedSet() {
        val card1 = createTestCard(101L, "Card 1")
        val card2 = createTestCard(999L, "Unknown Card")

        val items = listOf(
            ResurfacedItem(card1, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.90f),
            ResurfacedItem(card2, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.95f)
        )

        val allowedIds = setOf(101L, 102L, 103L)
        val filtered = ResurfaceValidator.filterValidItems(items, allowedCandidateIds = allowedIds)

        assertEquals(1, filtered.size)
        assertEquals(101L, filtered[0].candidateCard.id)
    }

    @Test
    fun testMaxTwoItemsPerGroup() {
        val items = listOf(
            ResurfacedItem(createTestCard(1L, "C1"), ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.95f),
            ResurfacedItem(createTestCard(2L, "C2"), ResurfaceGroup.SIMILAR, ResurfaceRelation.SUPPLEMENT, confidence = 0.90f),
            ResurfacedItem(createTestCard(3L, "C3"), ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.85f),
            ResurfacedItem(createTestCard(4L, "C4"), ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.92f)
        )

        val filtered = ResurfaceValidator.filterValidItems(items)
        // Group SIMILAR has max 2 items, COMPLEMENTARY has 1 item -> Total 3 items
        assertEquals(3, filtered.size)
        val similarCount = filtered.count { it.group == ResurfaceGroup.SIMILAR }
        val compCount = filtered.count { it.group == ResurfaceGroup.COMPLEMENTARY }
        assertEquals(2, similarCount)
        assertEquals(1, compCount)
    }

    @Test
    fun testNegativeCaseReturnsEmptyList() {
        val items = emptyList<ResurfacedItem>()
        val filtered = ResurfaceValidator.filterValidItems(items)
        assertTrue(filtered.isEmpty())

        val result = ResurfaceResult(newCardId = 10L, items = filtered)
        assertFalse(result.hasMemories)
        assertEquals(0, result.totalCount)
    }

    @Test
    fun testRejectsNonFiniteOrOutOfRangeConfidence() {
        val items = listOf(
            ResurfacedItem(createTestCard(1L, "C1"), ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = Float.NaN),
            ResurfacedItem(createTestCard(2L, "C2"), ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = Float.POSITIVE_INFINITY),
            ResurfacedItem(createTestCard(3L, "C3"), ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 1.2f),
            ResurfacedItem(createTestCard(4L, "C4"), ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = -0.9f),
            ResurfacedItem(createTestCard(5L, "C5"), ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 1.0f)
        )

        val filtered = ResurfaceValidator.filterValidItems(items)
        assertEquals(1, filtered.size)
        assertEquals(5L, filtered[0].candidateCard.id)
    }

    @Test
    fun testDedupeAcrossDifferentGroupsKeepsHigherConfidence() {
        val card = createTestCard(101L, "Thales")
        val items = listOf(
            ResurfacedItem(card, ResurfaceGroup.SIMILAR, ResurfaceRelation.SUPPLEMENT, confidence = 0.85f),
            ResurfacedItem(card, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.92f)
        )

        val filtered = ResurfaceValidator.filterValidItems(items)
        assertEquals(1, filtered.size)
        assertEquals(ResurfaceGroup.COMPLEMENTARY, filtered[0].group)
        assertEquals(0.92f, filtered[0].confidence, 0.001f)
    }

    @Test
    fun testDropsDuplicateCandidateIdKeepingHighestConfidence() {
        val card = createTestCard(101L, "Same card")
        val items = listOf(
            ResurfacedItem(card, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.95f),
            ResurfacedItem(card, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.99f),
            ResurfacedItem(createTestCard(102L, "Other"), ResurfaceGroup.SIMILAR, ResurfaceRelation.SUPPLEMENT, confidence = 0.85f)
        )

        val filtered = ResurfaceValidator.filterValidItems(items)
        assertEquals(2, filtered.size)
        // The duplicate card keeps only its highest-confidence entry (0.99 in COMPLEMENTARY)
        assertTrue(filtered.all { it.candidateCard.id != 101L || it.confidence == 0.99f })
        assertEquals(1, filtered.count { it.candidateCard.id == 101L })
    }

    @Test
    fun testTieBreakActionableOverComplementaryOverSimilar() {
        val card1 = createTestCard(101L, "Card 1")
        val card2 = createTestCard(102L, "Card 2")
        val card3 = createTestCard(103L, "Card 3")

        val items = listOf(
            // Card 1: ACTIONABLE vs COMPLEMENTARY (tie 0.85) -> ACTIONABLE
            ResurfacedItem(card1, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.85f),
            ResurfacedItem(card1, ResurfaceGroup.ACTIONABLE, ResurfaceRelation.CONTRADICTION, confidence = 0.85f),

            // Card 2: COMPLEMENTARY vs SIMILAR (tie 0.85) -> COMPLEMENTARY
            ResurfacedItem(card2, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.85f),
            ResurfacedItem(card2, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.85f),

            // Card 3: ACTIONABLE vs SIMILAR (tie 0.85) -> ACTIONABLE
            ResurfacedItem(card3, ResurfaceGroup.SIMILAR, ResurfaceRelation.SUPPLEMENT, confidence = 0.85f),
            ResurfacedItem(card3, ResurfaceGroup.ACTIONABLE, ResurfaceRelation.SUPPLEMENT, confidence = 0.85f)
        )

        val filtered = ResurfaceValidator.filterValidItems(items).associateBy { it.candidateCard.id }
        assertEquals(3, filtered.size)
        assertEquals(ResurfaceGroup.ACTIONABLE, filtered[101L]?.group)
        assertEquals(ResurfaceGroup.COMPLEMENTARY, filtered[102L]?.group)
        assertEquals(ResurfaceGroup.ACTIONABLE, filtered[103L]?.group)

        // Card 4: all 3 tie 0.85 -> ACTIONABLE (tested separately to not exceed group cap of 2)
        val card4 = createTestCard(104L, "Card 4")
        val items4 = listOf(
            ResurfacedItem(card4, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.85f),
            ResurfacedItem(card4, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.85f),
            ResurfacedItem(card4, ResurfaceGroup.ACTIONABLE, ResurfaceRelation.CONTRADICTION, confidence = 0.85f)
        )
        val filtered4 = ResurfaceValidator.filterValidItems(items4)
        assertEquals(1, filtered4.size)
        assertEquals(ResurfaceGroup.ACTIONABLE, filtered4[0].group)
    }

    @Test
    fun testConfidenceDescendingOrder() {
        val card1 = createTestCard(1L, "C1")
        val card2 = createTestCard(2L, "C2")
        val card3 = createTestCard(3L, "C3")

        val items = listOf(
            ResurfacedItem(card1, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.82f),
            ResurfacedItem(card2, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.96f),
            ResurfacedItem(card3, ResurfaceGroup.ACTIONABLE, ResurfaceRelation.CONTRADICTION, confidence = 0.89f)
        )

        val filtered = ResurfaceValidator.filterValidItems(items)
        assertEquals(3, filtered.size)
        assertEquals(2L, filtered[0].candidateCard.id)
        assertEquals(0.96f, filtered[0].confidence, 0.001f)
        assertEquals(3L, filtered[1].candidateCard.id)
        assertEquals(0.89f, filtered[1].confidence, 0.001f)
        assertEquals(1L, filtered[2].candidateCard.id)
        assertEquals(0.82f, filtered[2].confidence, 0.001f)
    }

    @Test
    fun testCapTwoAppliedAfterDeduplication() {
        val c1 = createTestCard(1L, "C1")
        val c2 = createTestCard(2L, "C2")
        val c3 = createTestCard(3L, "C3")
        val c4 = createTestCard(4L, "C4")

        // C1 appears in SIMILAR (0.98) and COMPLEMENTARY (0.95).
        // If deduplication happens first, C1 goes to SIMILAR (0.98).
        // Then COMPLEMENTARY still has 2 slots available for C2 (0.90) and C3 (0.85).
        val items = listOf(
            ResurfacedItem(c1, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.98f),
            ResurfacedItem(c1, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.95f),
            ResurfacedItem(c2, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.90f),
            ResurfacedItem(c3, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.SUPPLEMENT, confidence = 0.85f),
            ResurfacedItem(c4, ResurfaceGroup.SIMILAR, ResurfaceRelation.SUPPLEMENT, confidence = 0.88f)
        )

        val filtered = ResurfaceValidator.filterValidItems(items)
        assertEquals(4, filtered.size)

        val compItems = filtered.filter { it.group == ResurfaceGroup.COMPLEMENTARY }
        val simItems = filtered.filter { it.group == ResurfaceGroup.SIMILAR }

        assertEquals(2, compItems.size)
        assertEquals(2, simItems.size)
        assertEquals(setOf(2L, 3L), compItems.map { it.candidateCard.id }.toSet())
        assertEquals(setOf(1L, 4L), simItems.map { it.candidateCard.id }.toSet())
    }

    @Test
    fun testCapTwoPerGroupPicksTopTwoConfidenceWhenOverflown() {
        val c1 = createTestCard(1L, "C1")
        val c2 = createTestCard(2L, "C2")
        val c3 = createTestCard(3L, "C3")
        val c4 = createTestCard(4L, "C4")

        // LLM returns 4 candidates in SIMILAR in random confidence order
        val items = listOf(
            ResurfacedItem(c1, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.82f),
            ResurfacedItem(c2, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.95f),
            ResurfacedItem(c3, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.88f),
            ResurfacedItem(c4, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.91f)
        )

        val filtered = ResurfaceValidator.filterValidItems(items)
        assertEquals(2, filtered.size)
        assertEquals(2L, filtered[0].candidateCard.id) // 0.95
        assertEquals(4L, filtered[1].candidateCard.id) // 0.91
    }

    @Test
    fun testDropInvalidRelations() {
        val c1 = createTestCard(1L, "C1")
        val c2 = createTestCard(2L, "C2")

        val items = listOf(
            // Invalid: SIMILAR with EXPLANATION
            ResurfacedItem(c1, ResurfaceGroup.SIMILAR, ResurfaceRelation.EXPLANATION, confidence = 0.95f),
            // Invalid: ACTIONABLE with DUPLICATE
            ResurfacedItem(c2, ResurfaceGroup.ACTIONABLE, ResurfaceRelation.DUPLICATE, confidence = 0.95f)
        )

        val filtered = ResurfaceValidator.filterValidItems(items)
        assertTrue(filtered.isEmpty())
    }
}
