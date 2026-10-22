package com.remora.core.model.resurface

import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import kotlinx.serialization.Serializable

@Serializable
data class ResurfacedItem(
    val candidateCard: MemoryCard,
    val group: ResurfaceGroup,
    val relation: ResurfaceRelation,
    val newInfo: String = "",
    val benefit: String = "",
    val reason: String = "",
    val confidence: Float = 0.85f
)

@Serializable
data class ResurfaceResult(
    val newCardId: Long,
    val items: List<ResurfacedItem> = emptyList()
) {
    val similarItems: List<ResurfacedItem>
        get() = items.filter { it.group == ResurfaceGroup.SIMILAR }

    val complementaryItems: List<ResurfacedItem>
        get() = items.filter { it.group == ResurfaceGroup.COMPLEMENTARY }

    val actionableItems: List<ResurfacedItem>
        get() = items.filter { it.group == ResurfaceGroup.ACTIONABLE }

    val totalCount: Int
        get() = items.size

    val hasMemories: Boolean
        get() = items.isNotEmpty()
}

object ResurfaceValidator {
    /**
     * Quy tắc khớp relation với group (AGENTS.md mục 4):
     * - SIMILAR: DUPLICATE hoặc SUPPLEMENT
     * - COMPLEMENTARY: EXPLANATION hoặc SUPPLEMENT
     * - ACTIONABLE: SUPPLEMENT hoặc CONTRADICTION
     */
    fun isValidRelationForGroup(group: ResurfaceGroup, relation: ResurfaceRelation): Boolean {
        return when (group) {
            ResurfaceGroup.SIMILAR -> relation == ResurfaceRelation.DUPLICATE || relation == ResurfaceRelation.SUPPLEMENT
            ResurfaceGroup.COMPLEMENTARY -> relation == ResurfaceRelation.EXPLANATION || relation == ResurfaceRelation.SUPPLEMENT
            ResurfaceGroup.ACTIONABLE -> relation == ResurfaceRelation.SUPPLEMENT || relation == ResurfaceRelation.CONTRADICTION
        }
    }

    /**
     * Bộ lọc chuẩn hóa kết quả Resurfacing:
     * 1. candidate_id khớp input hợp lệ (nếu truyền allowedCandidateIds)
     * 2. confidence > 0.80 (dùng > thay vì >=)
     * 3. relation phải khớp group
     * 4. Tối đa 2 kết quả mỗi group
     */
    fun filterValidItems(
        items: List<ResurfacedItem>,
        allowedCandidateIds: Set<Long>? = null
    ): List<ResurfacedItem> {
        val grouped = mutableMapOf<ResurfaceGroup, MutableList<ResurfacedItem>>()
        for (item in items) {
            if (item.confidence <= 0.80f) continue
            if (allowedCandidateIds != null && !allowedCandidateIds.contains(item.candidateCard.id)) continue
            if (!isValidRelationForGroup(item.group, item.relation)) continue

            val list = grouped.getOrPut(item.group) { mutableListOf() }
            if (list.size < 2) {
                list.add(item)
            }
        }
        return grouped.values.flatten()
    }
}
