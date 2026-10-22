package com.remora.core.model.entity

import kotlinx.serialization.Serializable

@Serializable
data class CardTags(
    val subject: String = "chưa phân loại",
    val topics: List<String> = emptyList()
)

@Serializable
data class MemoryCard(
    val id: Long = 0,
    val imageUri: String,                               // URI lưu ảnh trên máy (MediaStore content:// hoặc file://)
    val createdAt: Long = System.currentTimeMillis(),    // Thời điểm chụp (epoch ms)
    val userIntent: String? = null,                     // Ghi chú lý do người dùng lưu ảnh (ví dụ: "Ôn thi cuối kỳ")
    val title: String = "",                             // Tiêu đề ngắn gọn từ AI (<= 80 ký tự)
    val summary: String = "",                           // Tóm tắt 2-4 câu trọng tâm
    val tags: CardTags = CardTags(),                    // Nhãn môn học và danh sách chủ đề
    val contentType: ContentType = ContentType.UNKNOWN, // Định dạng nội dung học tập
    val ocrText: String = "",                           // Toàn bộ văn bản đọc được trong ảnh
    val embedding: FloatArray? = null,                  // Vector 768 chiều L2-normalized
    val status: ProcessingStatus = ProcessingStatus.PENDING,
    val attemptCount: Int = 0,                          // Số lần đã thử gọi AI
    val errorCode: String? = null                       // Mã lỗi khi thất bại (TIMEOUT, QUOTA_EXCEEDED, etc.)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemoryCard

        if (id != other.id) return false
        if (imageUri != other.imageUri) return false
        if (createdAt != other.createdAt) return false
        if (userIntent != other.userIntent) return false
        if (title != other.title) return false
        if (summary != other.summary) return false
        if (tags != other.tags) return false
        if (contentType != other.contentType) return false
        if (ocrText != other.ocrText) return false
        if (status != other.status) return false
        if (attemptCount != other.attemptCount) return false
        if (errorCode != other.errorCode) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + imageUri.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (userIntent?.hashCode() ?: 0)
        result = 31 * result + title.hashCode()
        result = 31 * result + summary.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + ocrText.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + attemptCount.hashCode()
        result = 31 * result + (errorCode?.hashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
