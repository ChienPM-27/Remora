package com.remora.core.model.contract

import com.remora.core.model.entity.ContentType
import kotlinx.serialization.Serializable

@Serializable
data class VisionAnalysisResult(
    val title: String,
    val summary: String,
    val subject: String,
    val tags: List<String>,
    val contentType: ContentType,
    val ocrText: String
)

interface VisionAnalyzer {
    /**
     * Phân tích ảnh và trích xuất cấu trúc dữ liệu qua Gemini Flash Lite (hoặc Mock)
     */
    suspend fun analyze(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        userIntent: String? = null
    ): Result<VisionAnalysisResult>
}

interface TextEmbedder {
    /**
     * Tạo vector 768 chiều từ văn bản tổng hợp
     */
    suspend fun embed(text: String): Result<FloatArray>
}
