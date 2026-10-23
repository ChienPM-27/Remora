package com.remora.ai

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiServiceTest {

    @Test
    fun testHealthEndpoints() = testApplication {
        application {
            module()
        }

        val healthRes = client.get("/v1/health")
        assertEquals(HttpStatusCode.OK, healthRes.status)
        assertTrue(healthRes.bodyAsText().contains("UP"))

        val healthzRes = client.get("/v1/healthz")
        assertEquals(HttpStatusCode.OK, healthzRes.status)
        assertTrue(healthzRes.bodyAsText().contains("UP"))
    }

    @Test
    fun testInternalKeyProtectionWhenConfigured() = testApplication {
        // Test module behavior
        application {
            module()
        }

        // When GEMINI_API_KEY is not set or empty, request should return 500 configuration error, NOT mock
        val embedRes = client.post("/v1/ai/embeddings") {
            contentType(ContentType.Application.Json)
            setBody("""{"text": "Định lý Pythagoras", "task_type": "RETRIEVAL_DOCUMENT"}""")
        }
        // In test environment without GEMINI_API_KEY, expect 500 error since mocks are deleted
        assertEquals(HttpStatusCode.InternalServerError, embedRes.status)
        assertTrue(embedRes.bodyAsText().contains("GEMINI_API_KEY is not configured"))
    }

    @Test
    fun testBatchResurfaceNegativeCaseContract() {
        val candidates = listOf(
            CandidateItemDto(
                candidate_id = 101,
                title = "Hệ quả Thales",
                summary = "Tam giác đồng dạng",
                content_type = "THEORY"
            )
        )
        val newCard = CandidateItemDto(
            candidate_id = 999,
            title = "Thì hiện tại hoàn thành",
            summary = "Ngữ pháp tiếng Anh",
            content_type = "THEORY"
        )
        val req = ResurfaceBatchRequest(new_card = newCard, candidates = candidates)
        val json = jsonSerializer.encodeToString(ResurfaceBatchRequest.serializer(), req)
        assertTrue(json.contains("101"))
        assertTrue(json.contains("999"))
    }
}
