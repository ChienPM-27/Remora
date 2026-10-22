package com.remora.core.model.entity

import kotlinx.serialization.Serializable

@Serializable
data class Screenshot(
    val id: Long = 0,
    val imageUri: String,                       // URI lưu ảnh trên máy (MediaStore content:// hoặc file://)
    val capturedAt: Long = System.currentTimeMillis(), // Thời điểm chụp
    val userIntent: String? = null,             // Mục đích lưu do người dùng gõ (ví dụ: "Ôn thi cuối kỳ")
    val rawOcrText: String = "",                // Kết quả OCR thô (ML Kit)
    val title: String = "Đang xử lý...",       // Tiêu đề ngắn gọn từ Gemini
    val summary: String = "",                   // Tóm tắt 2-3 câu trọng tâm
    val subject: String = "Chưa phân loại",    // Môn học (Xác suất thống kê, Giải tích...)
    val tags: List<String> = emptyList(),       // Các nhãn tag (#Chuong3, #CongThuc)
    val contentType: ContentType = ContentType.UNKNOWN,
    val embedding: FloatArray? = null,          // Vector 768 chiều từ text-embedding-004
    val status: ProcessingStatus = ProcessingStatus.PENDING,
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Screenshot

        if (id != other.id) return false
        if (imageUri != other.imageUri) return false
        if (capturedAt != other.capturedAt) return false
        if (userIntent != other.userIntent) return false
        if (rawOcrText != other.rawOcrText) return false
        if (title != other.title) return false
        if (summary != other.summary) return false
        if (subject != other.subject) return false
        if (tags != other.tags) return false
        if (contentType != other.contentType) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (status != other.status) return false
        if (errorMessage != other.errorMessage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + imageUri.hashCode()
        result = 31 * result + capturedAt.hashCode()
        result = 31 * result + (userIntent?.hashCode() ?: 0)
        result = 31 * result + rawOcrText.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + summary.hashCode()
        result = 31 * result + subject.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + status.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }
}
