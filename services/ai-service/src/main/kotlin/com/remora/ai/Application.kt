package com.remora.ai

import io.ktor.utils.io.core.readBytes
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.isMultipart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.floatOrNull
import org.slf4j.LoggerFactory
import java.util.Base64
import kotlin.math.sqrt

private val logger = LoggerFactory.getLogger("AiService")

@Serializable
data class CardTagsDto(
    val subject: String = "chưa phân loại",
    val topics: List<String> = emptyList()
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
data class ExtractCardJsonRequest(
    val image_base64: String? = null,
    val user_intent: String? = null
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

val jsonSerializer = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

class GeminiClient(
    private val apiKey: String,
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(jsonSerializer)
        }
    }
) {
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"
    private val primaryVisionModel = System.getenv("GEMINI_VISION_MODEL") ?: "gemini-3.5-flash-lite"
    private val fallbackVisionModel = System.getenv("GEMINI_VISION_FALLBACK_MODEL") ?: "gemini-3.5-flash"
    private val embeddingModel = System.getenv("GEMINI_EMBEDDING_MODEL") ?: "gemini-embedding-2"

    private val cardResponseSchema = buildJsonObject {
        put("type", JsonPrimitive("OBJECT"))
        put("properties", buildJsonObject {
            put("title", buildJsonObject { put("type", JsonPrimitive("STRING")) })
            put("summary", buildJsonObject { put("type", JsonPrimitive("STRING")) })
            put("content_type", buildJsonObject {
                put("type", JsonPrimitive("STRING"))
                put("enum", buildJsonArray {
                    add(JsonPrimitive("THEORY"))
                    add(JsonPrimitive("PROBLEM"))
                    add(JsonPrimitive("SOLUTION"))
                    add(JsonPrimitive("CODE"))
                    add(JsonPrimitive("NOTES"))
                    add(JsonPrimitive("UNKNOWN"))
                })
            })
            put("tags", buildJsonObject {
                put("type", JsonPrimitive("OBJECT"))
                put("properties", buildJsonObject {
                    put("subject", buildJsonObject { put("type", JsonPrimitive("STRING")) })
                    put("topics", buildJsonObject {
                        put("type", JsonPrimitive("ARRAY"))
                        put("items", buildJsonObject { put("type", JsonPrimitive("STRING")) })
                    })
                })
                put("required", buildJsonArray {
                    add(JsonPrimitive("subject"))
                    add(JsonPrimitive("topics"))
                })
            })
            put("ocr_text", buildJsonObject { put("type", JsonPrimitive("STRING")) })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("title"))
            add(JsonPrimitive("summary"))
            add(JsonPrimitive("content_type"))
            add(JsonPrimitive("tags"))
            add(JsonPrimitive("ocr_text"))
        })
    }

    private val resurfaceResponseSchema = buildJsonObject {
        put("type", JsonPrimitive("OBJECT"))
        put("properties", buildJsonObject {
            put("results", buildJsonObject {
                put("type", JsonPrimitive("ARRAY"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("OBJECT"))
                    put("properties", buildJsonObject {
                        put("candidate_id", buildJsonObject { put("type", JsonPrimitive("INTEGER")) })
                        put("group", buildJsonObject {
                            put("type", JsonPrimitive("STRING"))
                            put("enum", buildJsonArray {
                                add(JsonPrimitive("SIMILAR"))
                                add(JsonPrimitive("COMPLEMENTARY"))
                                add(JsonPrimitive("ACTIONABLE"))
                            })
                        })
                        put("relation", buildJsonObject {
                            put("type", JsonPrimitive("STRING"))
                            put("enum", buildJsonArray {
                                add(JsonPrimitive("DUPLICATE"))
                                add(JsonPrimitive("SUPPLEMENT"))
                                add(JsonPrimitive("EXPLANATION"))
                                add(JsonPrimitive("CONTRADICTION"))
                            })
                        })
                        put("new_info", buildJsonObject { put("type", JsonPrimitive("STRING")) })
                        put("benefit", buildJsonObject { put("type", JsonPrimitive("STRING")) })
                        put("reason", buildJsonObject { put("type", JsonPrimitive("STRING")) })
                        put("confidence", buildJsonObject { put("type", JsonPrimitive("NUMBER")) })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("candidate_id"))
                        add(JsonPrimitive("group"))
                        add(JsonPrimitive("relation"))
                        add(JsonPrimitive("new_info"))
                        add(JsonPrimitive("benefit"))
                        add(JsonPrimitive("reason"))
                        add(JsonPrimitive("confidence"))
                    })
                })
            })
        })
        put("required", buildJsonArray { add(JsonPrimitive("results")) })
    }

    private suspend fun callGenerateContentWithFallback(
        payload: JsonObject,
        primaryModel: String,
        fallbackModel: String
    ): String {
        val primaryUrl = "$baseUrl/models/$primaryModel:generateContent"
        logger.info("Calling Gemini primary model: $primaryModel")
        
        val primaryResponse = httpClient.post(primaryUrl) {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        val primaryStatus = primaryResponse.status.value
        if (primaryStatus == 200) {
            return primaryResponse.bodyAsText()
        }

        // If rate-limited (429) or service unavailable (503/5xx), try fallback
        if (primaryStatus == 429 || primaryStatus == 503 || primaryStatus >= 500) {
            logger.warn("Primary model $primaryModel failed with HTTP $primaryStatus. Falling back to $fallbackModel...")
            delay(1000) // Brief backoff
            val fallbackUrl = "$baseUrl/models/$fallbackModel:generateContent"
            val fallbackResponse = httpClient.post(fallbackUrl) {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            if (fallbackResponse.status.value == 200) {
                return fallbackResponse.bodyAsText()
            }
            val errBody = fallbackResponse.bodyAsText()
            logger.error("Fallback model $fallbackModel failed with HTTP ${fallbackResponse.status.value}: $errBody")
            throw RuntimeException("Gemini API error ${fallbackResponse.status.value}: $errBody")
        } else {
            val errBody = primaryResponse.bodyAsText()
            logger.error("Primary model $primaryModel error HTTP $primaryStatus: $errBody")
            throw RuntimeException("Gemini API error $primaryStatus: $errBody")
        }
    }

    suspend fun extractCard(imageBytes: ByteArray, userIntent: String?): ExtractCardResponse {
        val base64Image = Base64.getEncoder().encodeToString(imageBytes)
        val promptText = buildString {
            append("Hãy đọc toàn bộ văn bản trong ảnh chụp màn hình bài học này, trích xuất cấu trúc thẻ ghi nhớ theo schema: ")
            append("giữ nguyên công thức toán lý, định lý, tên thuật ngữ và các con số.")
            if (!userIntent.isNullOrBlank()) {
                append("\nMục đích lưu/ghi chú của người học: \"$userIntent\"")
            }
        }

        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", JsonPrimitive("image/jpeg"))
                                put("data", JsonPrimitive(base64Image))
                            })
                        })
                        add(buildJsonObject {
                            put("text", JsonPrimitive(promptText))
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", JsonPrimitive("application/json"))
                put("responseSchema", cardResponseSchema)
            })
        }

        val responseJsonText = callGenerateContentWithFallback(payload, primaryVisionModel, fallbackVisionModel)
        val root = jsonSerializer.parseToJsonElement(responseJsonText).jsonObject
        val textPart = root["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Missing text content in Gemini response")

        val cardJson = jsonSerializer.parseToJsonElement(textPart).jsonObject
        val tagsObj = cardJson["tags"]?.jsonObject
        val subject = tagsObj?.get("subject")?.jsonPrimitive?.contentOrNull ?: "chưa phân loại"
        val topics = tagsObj?.get("topics")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

        return ExtractCardResponse(
            title = cardJson["title"]?.jsonPrimitive?.contentOrNull ?: "Thẻ bài học",
            summary = cardJson["summary"]?.jsonPrimitive?.contentOrNull ?: "",
            tags = CardTagsDto(subject = subject, topics = topics),
            content_type = cardJson["content_type"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN",
            ocr_text = cardJson["ocr_text"]?.jsonPrimitive?.contentOrNull ?: ""
        )
    }

    suspend fun embedText(text: String, taskType: String): List<Float> {
        val url = "$baseUrl/models/$embeddingModel:embedContent"
        val payload = buildJsonObject {
            put("content", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", JsonPrimitive(text)) })
                })
            })
            put("outputDimensionality", JsonPrimitive(768))
            put("taskType", JsonPrimitive(taskType))
        }

        val response = httpClient.post(url) {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        if (response.status.value != 200) {
            val err = response.bodyAsText()
            logger.error("Embedding call failed HTTP ${response.status.value}: $err")
            throw RuntimeException("Gemini embedding error ${response.status.value}: $err")
        }

        val root = jsonSerializer.parseToJsonElement(response.bodyAsText()).jsonObject
        val values = root["embedding"]?.jsonObject?.get("values")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.floatOrNull
        } ?: throw IllegalStateException("Missing embedding values from Gemini response")

        // L2-normalize vector to make cosine similarity strictly equal to dot product
        var sumSq = 0.0
        for (f in values) sumSq += (f * f)
        val norm = sqrt(sumSq).toFloat()
        return if (norm > 0f) values.map { it / norm } else values
    }

    suspend fun batchResurface(newCard: CandidateItemDto, candidates: List<CandidateItemDto>): List<ResurfaceResultItemDto> {
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val systemPrompt = """
            Bạn là trợ lý AI chuyên về Memory Resurfacing (gợi nhớ tri thức học tập liên quan) của Remora.
            NHIỆM VỤ: So sánh một thẻ kiến thức mới với danh sách các thẻ ứng viên (tối đa 15 thẻ) để tìm ra liên kết có giá trị học tập thực sự.

            QUY TẮC RÀNG BUỘC CỐT LÕI:
            1. CHỈ TRẢ VỀ khi có LỢI ÍCH HỌC TẬP CỤ THỂ (nhắc lại tiên đề cần nhớ, bài tập rèn luyện tương ứng, hoặc cảnh báo mâu thuẫn/khác biệt công thức).
            2. TUYỆT ĐỐI KHÔNG GƯỢNG ÉP LIÊN KẾT: Nếu các thẻ ứng viên không cùng chủ đề, không bổ trợ thực tế hoặc không mang lại giá trị học tập, KHÔNG ĐƯỢC trả về.
            3. CA ÂM TÍNH: Nếu không có thẻ nào thực sự liên quan, BẮT BUỘC trả về danh sách rỗng: {"results": []}.
            4. QUY TẮC KHỚP RELATION VÀ GROUP:
               - Nhóm SIMILAR: relation BẮT BUỘC là DUPLICATE (ảnh chụp trùng bài giảng) hoặc SUPPLEMENT (cùng chuyên đề, cùng dạng).
               - Nhóm COMPLEMENTARY: relation BẮT BUỘC là EXPLANATION (bài giải giải thích cho đề bài, hoặc lý thuyết nền) hoặc SUPPLEMENT (mở rộng hệ quả, khía cạnh bổ sung).
               - Nhóm ACTIONABLE: relation BẮT BUỘC là SUPPLEMENT (bài tập thực hành cho lý thuyết vừa học) hoặc CONTRADICTION (chỉ ra công thức trái ngược/điều kiện áp dụng khác nhau).
            5. candidate_id BẮT BUỘC là số nguyên (INTEGER) khớp chính xác với candidate_id trong danh sách đầu vào. Không được tự bịa ID.
            6. Mỗi group tối đa 2 thẻ.
            7. Đánh giá confidence trung thực từ 0.0 đến 1.0. Chỉ gợi ý khi confidence > 0.80.
        """.trimIndent()

        val newCardJson = jsonSerializer.encodeToString(CandidateItemDto.serializer(), newCard)
        val candidatesJson = jsonSerializer.encodeToString(kotlinx.serialization.builtins.ListSerializer(CandidateItemDto.serializer()), candidates)

        val fullPrompt = """
            $systemPrompt

            THẺ MỚI VỪA LƯU:
            $newCardJson

            DANH SÁCH ${candidates.size} ỨNG VIÊN CẦN ĐÁNH GIÁ:
            $candidatesJson

            Hãy xuất JSON theo đúng schema.
        """.trimIndent()

        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive(fullPrompt)) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", JsonPrimitive("application/json"))
                put("responseSchema", resurfaceResponseSchema)
            })
        }

        val responseJsonText = callGenerateContentWithFallback(payload, primaryVisionModel, fallbackVisionModel)
        val root = jsonSerializer.parseToJsonElement(responseJsonText).jsonObject
        val textPart = root["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?: return emptyList()

        val parsed = jsonSerializer.parseToJsonElement(textPart).jsonObject
        val resultsArray = parsed["results"]?.jsonArray ?: return emptyList()

        val allowedCandidateIds = candidates.map { it.candidate_id }.toSet()
        val validItems = mutableListOf<ResurfaceResultItemDto>()

        for (elem in resultsArray) {
            val obj = elem.jsonObject
            val candidateId = obj["candidate_id"]?.jsonPrimitive?.longOrNull ?: continue
            if (!allowedCandidateIds.contains(candidateId)) {
                logger.warn("Dropping invalid candidate_id $candidateId not in candidate list")
                continue
            }

            val group = obj["group"]?.jsonPrimitive?.contentOrNull ?: continue
            val relation = obj["relation"]?.jsonPrimitive?.contentOrNull ?: continue
            val confidence = obj["confidence"]?.jsonPrimitive?.floatOrNull ?: 0f

            // Filter strictly confidence > 0.80 per specification
            if (confidence <= 0.80f) continue

            // Conformance check between group and relation
            val isValidRelation = when (group) {
                "SIMILAR" -> relation == "DUPLICATE" || relation == "SUPPLEMENT"
                "COMPLEMENTARY" -> relation == "EXPLANATION" || relation == "SUPPLEMENT"
                "ACTIONABLE" -> relation == "SUPPLEMENT" || relation == "CONTRADICTION"
                else -> false
            }
            if (!isValidRelation) continue

            validItems.add(
                ResurfaceResultItemDto(
                    candidate_id = candidateId,
                    group = group,
                    relation = relation,
                    new_info = obj["new_info"]?.jsonPrimitive?.contentOrNull ?: "",
                    benefit = obj["benefit"]?.jsonPrimitive?.contentOrNull ?: "",
                    reason = obj["reason"]?.jsonPrimitive?.contentOrNull ?: "",
                    confidence = confidence
                )
            )
        }

        // Enforce max 2 results per group
        val groupCounts = mutableMapOf<String, Int>()
        val filteredResults = mutableListOf<ResurfaceResultItemDto>()
        for (item in validItems) {
            val currentCount = groupCounts.getOrDefault(item.group, 0)
            if (currentCount < 2) {
                filteredResults.add(item)
                groupCounts[item.group] = currentCount + 1
            }
        }

        return filteredResults
    }
}

fun Application.module() {
    install(ContentNegotiation) {
        json(jsonSerializer)
    }

    install(CallLogging)

    val expectedInternalKey = System.getenv("INTERNAL_SERVICE_KEY")
    val geminiApiKey = System.getenv("GEMINI_API_KEY") ?: ""
    val geminiClient = GeminiClient(apiKey = geminiApiKey)

    fun verifyInternalKey(call: ApplicationCall): Boolean {
        if (expectedInternalKey.isNullOrBlank()) {
            return true // If not configured in environment, allow local development
        }
        val headerKey = call.request.header("X-Internal-Service-Key")
            ?: call.request.header("X-Internal-Key")
        return headerKey == expectedInternalKey
    }

    routing {
        get("/v1/health") {
            call.respond(mapOf("status" to "UP", "service" to "ai-service"))
        }

        get("/v1/healthz") {
            call.respond(mapOf("status" to "UP", "service" to "ai-service"))
        }

        get("/v1/readyz") {
            if (geminiApiKey.isBlank()) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "DEGRADED", "error" to "GEMINI_API_KEY not set"))
            } else {
                call.respond(mapOf("status" to "READY", "service" to "ai-service"))
            }
        }

        route("/v1/ai") {
            post("/extract-card") {
                if (!verifyInternalKey(call)) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: invalid internal service key"))
                    return@post
                }

                if (geminiApiKey.isBlank()) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "GEMINI_API_KEY is not configured on server"))
                    return@post
                }

                var imageBytes: ByteArray? = null
                var userIntent: String? = null

                if (call.request.isMultipart()) {
                    val multipart = call.receiveMultipart()
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                imageBytes = part.provider().readBytes()
                            }
                            is PartData.FormItem -> {
                                if (part.name == "user_intent") {
                                    userIntent = part.value
                                }
                            }
                            else -> {}
                        }
                        part.dispose()
                    }
                } else {
                    val jsonReq = try { call.receive<ExtractCardJsonRequest>() } catch (_: Exception) { null }
                    if (jsonReq?.image_base64 != null) {
                        imageBytes = Base64.getDecoder().decode(jsonReq.image_base64)
                    }
                    userIntent = jsonReq?.user_intent
                }

                val rawBytes = imageBytes
                if (rawBytes == null || rawBytes.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing image part in multipart request"))
                    return@post
                }

                try {
                    val cardResponse = geminiClient.extractCard(rawBytes, userIntent)
                    call.respond(cardResponse)
                } catch (e: Exception) {
                    logger.error("Error in extract-card", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Extraction failed")))
                }
            }

            post("/embeddings") {
                if (!verifyInternalKey(call)) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: invalid internal service key"))
                    return@post
                }

                if (geminiApiKey.isBlank()) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "GEMINI_API_KEY is not configured on server"))
                    return@post
                }

                val req = call.receive<EmbeddingRequest>()
                if (req.text.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "text cannot be empty"))
                    return@post
                }

                try {
                    val embedding = geminiClient.embedText(req.text, req.task_type)
                    call.respond(EmbeddingResponse(embedding = embedding, dimension = 768))
                } catch (e: Exception) {
                    logger.error("Error in embeddings", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Embedding failed")))
                }
            }

            post("/resurface-batch") {
                if (!verifyInternalKey(call)) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: invalid internal service key"))
                    return@post
                }

                if (geminiApiKey.isBlank()) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "GEMINI_API_KEY is not configured on server"))
                    return@post
                }

                val req = call.receive<ResurfaceBatchRequest>()
                try {
                    val results = geminiClient.batchResurface(req.new_card, req.candidates)
                    call.respond(ResurfaceBatchResponse(results = results))
                } catch (e: Exception) {
                    logger.error("Error in resurface-batch", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Resurfacing failed")))
                }
            }
        }
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8082
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}
