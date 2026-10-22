package com.remora.resurfacing

import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfaceValidator
import com.remora.core.model.resurface.ResurfacedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResurfacingValidatorTest {

    private fun mockCard(id: Long) = MemoryCard(
        id = id,
        imageUri = "content://images/$id",
        title = "Card $id",
        status = ProcessingStatus.DONE
    )

    @Test
    fun testOnlyConfidenceStrictlyGreaterThanEightyAccepted() {
        val item1 = ResurfacedItem(mockCard(1), ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.80f)
        val item2 = ResurfacedItem(mockCard(2), ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.801f)
        val item3 = ResurfacedItem(mockCard(3), ResurfaceGroup.SIMILAR, ResurfaceRelation.DUPLICATE, confidence = 0.79f)

        val filtered = ResurfaceValidator.filterValidItems(listOf(item1, item2, item3))
        assertEquals(1, filtered.size)
        assertEquals(2L, filtered[0].candidateCard.id)
    }

    @Test
    fun testDropUnknownCandidateId() {
        val validCard = mockCard(101)
        val unknownCard = mockCard(999)

        val itemValid = ResurfacedItem(validCard, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.9f)
        val itemInvalid = ResurfacedItem(unknownCard, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION, confidence = 0.95f)

        val allowedPool = setOf(101L, 102L)
        val filtered = ResurfaceValidator.filterValidItems(listOf(itemValid, itemInvalid), allowedCandidateIds = allowedPool)

        assertEquals(1, filtered.size)
        assertEquals(101L, filtered[0].candidateCard.id)
    }

    @Test
    fun testNegativeCaseWithZeroConnectionsReturnsEmpty() {
        val emptyList = emptyList<ResurfacedItem>()
        val filtered = ResurfaceValidator.filterValidItems(emptyList)
        assertTrue(filtered.isEmpty())
    }
}
