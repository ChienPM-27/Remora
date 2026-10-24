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
    fun testDropsDuplicateCandidateIdKeepingFirstOccurrence() {
        val card = createTestCard(101L, "Same card")
        val items = listOf(
            ResurfacedItem(card, ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.95f),
            ResurfacedItem(card, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.99f),
            ResurfacedItem(createTestCard(102L, "Other"), ResurfaceGroup.SIMILAR, ResurfaceRelation.SUPPLEMENT, confidence = 0.85f)
        )

        val filtered = ResurfaceValidator.filterValidItems(items)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.candidateCard.id != 101L || it.confidence == 0.95f })
    }
}
