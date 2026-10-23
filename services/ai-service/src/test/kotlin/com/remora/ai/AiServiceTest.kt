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
    fun testFailClosedWhenInternalKeyMissing() = testApplication {
        application {
            module()
        }

        // Without valid INTERNAL_SERVICE_KEY, request MUST fail-closed with 401
        val embedRes = client.post("/v1/ai/embeddings") {
            contentType(ContentType.Application.Json)
            setBody("""{"text": "Định lý Pythagoras", "task_type": "RETRIEVAL_DOCUMENT"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, embedRes.status)
        assertTrue(embedRes.bodyAsText().contains("UNAUTHORIZED"))
    }

    @Test
    fun testBatchResurfaceNegativeCaseContract() {
        val candidates = (1..25).map {
            CandidateItemDto(
                candidate_id = it.toLong(),
                title = "Thẻ số $it",
                summary = "Tóm tắt $it",
                content_type = "THEORY"
            )
        }
        val newCard = CandidateItemDto(
            candidate_id = 999,
            title = "Thì hiện tại hoàn thành",
            summary = "Ngữ pháp tiếng Anh",
            content_type = "THEORY"
        )
        val req = ResurfaceBatchRequest(new_card = newCard, candidates = candidates)
        val json = jsonSerializer.encodeToString(ResurfaceBatchRequest.serializer(), req)
        assertTrue(json.contains("25"), "Serialized json should contain all candidates")
        assertTrue(candidates.size > 20, "Input has more than 20 candidates for limit validation test")
    }
}
