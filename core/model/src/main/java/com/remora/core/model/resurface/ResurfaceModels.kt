package com.remora.core.model.resurface

import com.remora.core.model.entity.Screenshot
import kotlinx.serialization.Serializable

@Serializable
enum class RelationshipType {
    SIMILAR,         // Tương đồng: Cùng chủ đề, dạng bài tập tương tự
    COMPLEMENTARY,   // Bổ trợ: Lý thuyết nền tảng bổ trợ cho bài tập hoặc ngược lại
    ACTIONABLE,      // Có giá trị sử dụng: Lời giải mẫu, công thức có thể áp dụng trực tiếp
    EXTENSION,       // Mở rộng: Nâng cao hoặc phát triển thêm
    CONTRADICTION    // Mâu thuẫn / Cần lưu ý đặc biệt
}

@Serializable
data class ResurfacedItem(
    val screenshot: Screenshot,
    val relationshipType: RelationshipType,
    val explanation: String,      // Lý do AI liên kết (1-2 câu tiếng Việt)
    val actionableTip: String? = null, // Hướng dẫn vận dụng (nếu có)
    val confidenceScore: Float = 0.85f  // Độ tin cậy (0.0 đến 1.0)
)

@Serializable
data class ResurfaceResult(
    val newScreenshotId: Long,
    val similarItems: List<ResurfacedItem> = emptyList(),        // Nhóm 1: Tương đồng (tối đa 1-2)
    val complementaryItems: List<ResurfacedItem> = emptyList(),  // Nhóm 2: Bổ trợ (tối đa 1-2)
    val actionableItems: List<ResurfacedItem> = emptyList(),     // Nhóm 3: Có giá trị sử dụng (tối đa 1-2)
    val aiInsightSummary: String = ""                            // Tóm tắt tổng thể về mối liên kết
) {
    val totalCount: Int
        get() = similarItems.size + complementaryItems.size + actionableItems.size

    val hasMemories: Boolean
        get() = totalCount > 0
}
