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

    private val ResurfaceGroup.priority: Int
        get() = when (this) {
            ResurfaceGroup.ACTIONABLE -> 3
            ResurfaceGroup.COMPLEMENTARY -> 2
            ResurfaceGroup.SIMILAR -> 1
        }

    /**
     * Bộ lọc chuẩn hóa kết quả Resurfacing:
     * 1. Lọc theo confidence > 0.80 (dùng > thay vì >=), id hợp lệ, relation khớp group.
     * 2. Sắp theo confidence giảm dần; hoà thì ưu tiên ACTIONABLE > COMPLEMENTARY > SIMILAR.
     * 3. Mỗi candidateId giữ một mục (confidence cao nhất, hoà thì theo thứ tự ưu tiên nhóm).
     *    Đây là cách hiện thực "gộp khi trùng" (docs yêu cầu gộp khi Bổ trợ và Có giá trị sử dụng trùng).
     * 4. Áp giới hạn tối đa 2 mục mỗi nhóm sau khi đã dedupe.
     */
    fun filterValidItems(
        items: List<ResurfacedItem>,
        allowedCandidateIds: Set<Long>? = null
    ): List<ResurfacedItem> {
        // 1. Lọc điều kiện cơ bản: confidence > 0.80, allowed IDs, quan hệ hợp lệ
        val validItems = items.filter { item ->
            item.confidence > 0.80f &&
                (allowedCandidateIds == null || allowedCandidateIds.contains(item.candidateCard.id)) &&
                isValidRelationForGroup(item.group, item.relation)
        }

        // 2. Sắp theo confidence giảm dần; hoà thì ưu tiên ACTIONABLE > COMPLEMENTARY > SIMILAR
        val sortedItems = validItems.sortedWith(
            compareByDescending<ResurfacedItem> { it.confidence }
                .thenByDescending { it.group.priority }
        )

        // 3. Mỗi candidateId giữ đúng 1 mục có confidence cao nhất (hoà ưu tiên ACTIONABLE > COMPLEMENTARY > SIMILAR).
        // Comment: Đây là cách hiện thực "gộp khi trùng" theo tài liệu thiết kế (tránh việc một thẻ xuất hiện ở nhiều nhóm).
        val dedupedItems = mutableListOf<ResurfacedItem>()
        val seenCandidateIds = mutableSetOf<Long>()
        for (item in sortedItems) {
            if (seenCandidateIds.add(item.candidateCard.id)) {
                dedupedItems.add(item)
            }
        }

        // 4. Áp giới hạn tối đa 2 mục mỗi nhóm sau khi đã dedupe
        val groupCounts = mutableMapOf<ResurfaceGroup, Int>()
        val result = mutableListOf<ResurfacedItem>()
        for (item in dedupedItems) {
            val count = groupCounts.getOrDefault(item.group, 0)
            if (count < 2) {
                groupCounts[item.group] = count + 1
                result.add(item)
            }
        }

        return result
    }
}
