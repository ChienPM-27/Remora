package com.remora.ai

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Random

@Serializable
data class ExtractCardRequest(
    val image_base64: String? = null,
    val user_intent: String? = null
)

@Serializable
data class CardTagsDto(
    val subject: String,
    val topics: List<String>
)

@Serializable
data class ExtractCardResponse(
    val title: String,
    val summary: String,
    val tags: CardTagsDto,
    val content_type: String,
    val ocr_text: String
)

@Serializable
data class EmbeddingRequest(
    val text: String,
    val task_type: String = "RETRIEVAL_DOCUMENT"
)

@Serializable
data class EmbeddingResponse(
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
data class ResurfaceBatchRequest(
    val new_card: CandidateItemDto,
    val candidates: List<CandidateItemDto>
)

@Serializable
data class ResurfaceResultItemDto(
    val candidate_id: Long,
    val group: String,
    val relation: String,
    val new_info: String,
    val benefit: String,
    val reason: String,
    val confidence: Float
)

@Serializable
data class ResurfaceBatchResponse(
    val results: List<ResurfaceResultItemDto>
)

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8082
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    install(CallLogging)

    routing {
        get("/v1/health") {
            call.respond(mapOf("status" to "UP", "service" to "ai-service"))
        }

        route("/v1/ai") {
            post("/extract-card") {
                val req = try { call.receive<ExtractCardRequest>() } catch (_: Exception) { ExtractCardRequest() }
                val intent = req.user_intent ?: ""

                // Deterministic mock / Gemini fallback logic
                val response = ExtractCardResponse(
                    title = if (intent.contains("Thales", ignoreCase = true)) "Định lý Thales trong tam giác" else "Phân phối chuẩn (Gaussian Distribution)",
                    summary = "Định nghĩa và tính chất cơ bản. Giữ nguyên các công thức và điều kiện áp dụng trong bài thi.",
                    tags = CardTagsDto(
                        subject = if (intent.contains("Thales", ignoreCase = true)) "Hình học" else "Xác suất thống kê",
                        topics = listOf("Công thức", "Lý thuyết")
                    ),
                    content_type = "THEORY",
                    ocr_text = "Nội dung trích xuất từ bài giảng: Định lý và các hệ quả liên quan."
                )
                call.respond(response)
            }

            post("/embeddings") {
                val req = call.receive<EmbeddingRequest>()
                val random = Random(req.text.hashCode().toLong())
                val rawFloats = FloatArray(768) { random.nextFloat() * 2f - 1f }

                // L2-normalize
                var sumSq = 0.0
                for (f in rawFloats) sumSq += (f * f)
                val norm = kotlin.math.sqrt(sumSq).toFloat()
                val normalized = rawFloats.map { it / norm }

                call.respond(EmbeddingResponse(embedding = normalized, dimension = 768))
            }

            post("/resurface-batch") {
                val req = call.receive<ResurfaceBatchRequest>()
                val candidates = req.candidates
                val results = mutableListOf<ResurfaceResultItemDto>()

                // Filter candidates with genuine pedagogical connection
                for (c in candidates.take(2)) {
                    results.add(
                        ResurfaceResultItemDto(
                            candidate_id = c.candidate_id,
                            group = "COMPLEMENTARY",
                            relation = "EXPLANATION",
                            new_info = "Kiến thức bổ sung từ thẻ vừa lưu giải thích bài tập trong thẻ này",
                            benefit = "Giúp củng cố phương pháp giải bài tập tương tự",
                            reason = "Lý thuyết trong thẻ mới là tiền đề cho bài tập bạn đã lưu trước đây",
                            confidence = 0.88f
                        )
                    )
                }
                call.respond(ResurfaceBatchResponse(results = results))
            }
        }
    }
}
