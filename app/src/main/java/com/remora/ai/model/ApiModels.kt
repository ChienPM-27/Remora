package com.remora.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthLoginRequest(
    val username: String = "demo_student_01",
    val password: String = "password123"
)

@Serializable
data class AuthTokenResponse(
    val access_token: String,
    val token_type: String = "Bearer",
    val expires_in: Long = 86400L
)

@Serializable
data class CardTagsDto(
    val subject: String = "chưa phân loại",
    val topics: List<String> = emptyList()
)

@Serializable
data class ExtractCardResponseDto(
    val title: String,
    val summary: String,
    val tags: CardTagsDto = CardTagsDto(),
    val content_type: String = "UNKNOWN",
    val ocr_text: String = ""
)

@Serializable
data class EmbeddingRequestDto(
    val text: String,
    val task_type: String = "RETRIEVAL_DOCUMENT"
)

@Serializable
data class EmbeddingResponseDto(
    val embedding: List<Float>,
    val dimension: Int = 768
)

@Serializable
data class CandidateItemDto(
    val candidate_id: Long,
    val title: String,
    val summary: String,
    val tags: CardTagsDto? = null,
    val content_type: String = "UNKNOWN",
    val user_intent: String? = null
)

@Serializable
data class ResurfaceBatchRequestDto(
    val new_card: CandidateItemDto,
    val candidates: List<CandidateItemDto>
)

@Serializable
data class ResurfaceResultItemDto(
    val candidate_id: Long,
    val group: String,
    val relation: String,
    val new_info: String = "",
    val benefit: String = "",
    val reason: String = "",
    val confidence: Float
)

@Serializable
data class ResurfaceBatchResponseDto(
    val results: List<ResurfaceResultItemDto> = emptyList()
)
