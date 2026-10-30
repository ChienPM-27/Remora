package com.remora.resurfacing

import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfaceValidator
import com.remora.core.model.resurface.ResurfacedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResurfacingValidatorTest {

    private fun mockCard(id: Long) = MemoryCard(
        id = id,
        imageUri = "content://images/$id",
        title = "Card $id",
        status = ProcessingStatus.DONE
    )

    private fun item(
        id: Long,
        group: ResurfaceGroup = ResurfaceGroup.SIMILAR,
        relation: ResurfaceRelation = ResurfaceRelation.SUPPLEMENT,
        confidence: Float = 0.9f
    ) = ResurfacedItem(
        mockCard(id), group, relation,
        reason = "Revisit the theorem used in this problem",
        benefit = "Apply the theorem to solve the new problem",
        confidence = confidence
    )

    @Test
    fun testOnlyConfidenceStrictlyGreaterThanEightyAccepted() {
        val item1 = item(1, confidence = 0.80f)
        val item2 = item(2, confidence = 0.801f)
        val item3 = item(3, confidence = 0.79f)

        val filtered = ResurfaceValidator.filterValidItems(listOf(item1, item2, item3))
        assertEquals(1, filtered.size)
        assertEquals(2L, filtered[0].candidateCard.id)
    }

    @Test
    fun testDropUnknownCandidateId() {
        val itemValid = item(101, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION)
        val itemInvalid = item(999, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION)

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

    @Test
    fun rejectsNonFiniteAndOutOfRangeConfidenceButAcceptsOne() {
        val invalid = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 0f, 0.80f, 1.001f)
        val items = invalid.mapIndexed { index, confidence -> item(index.toLong(), confidence = confidence) } +
            item(100, confidence = 1f)

        assertEquals(listOf(100L), ResurfaceValidator.filterValidItems(items).map { it.candidateCard.id })
    }

    @Test
    fun aCandidateAppearsOnlyOnceAcrossAllGroups() {
        val first = item(101)
        val duplicate = item(101, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION)
        val other = item(102, ResurfaceGroup.ACTIONABLE, ResurfaceRelation.CONTRADICTION)

        assertEquals(listOf(first, other), ResurfaceValidator.filterValidItems(listOf(first, duplicate, other)))
    }

    @Test
    fun everyGroupHasAtMostTwoUniqueCandidates() {
        val items = ResurfaceGroup.entries.flatMapIndexed { groupIndex, group ->
            (1..3).map { item((groupIndex * 10 + it).toLong(), group) }
        }
        val filtered = ResurfaceValidator.filterValidItems(items)

        assertEquals(6, filtered.size)
        ResurfaceGroup.entries.forEach { group -> assertEquals(2, filtered.count { it.group == group }) }
        assertEquals(6, filtered.map { it.candidateCard.id }.distinct().size)
    }

    @Test
    fun incompatibleRelationsAndUnexplainedLinksAreRejected() {
        val invalid = listOf(
            item(1, ResurfaceGroup.SIMILAR, ResurfaceRelation.EXPLANATION),
            item(2, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.CONTRADICTION),
            item(3, ResurfaceGroup.ACTIONABLE, ResurfaceRelation.DUPLICATE),
            item(4).copy(reason = "  "),
            item(5).copy(benefit = "")
        )

        assertTrue(ResurfaceValidator.filterValidItems(invalid).isEmpty())
    }

    @Test
    fun rejectedEntriesDoNotConsumeCandidateIdentityOrGroupCapacity() {
        val invalid = item(101, confidence = Float.NaN)
        val valid = item(101)
        val other = item(102)

        assertEquals(listOf(valid, other), ResurfaceValidator.filterValidItems(listOf(invalid, valid, other)))
    }
}
