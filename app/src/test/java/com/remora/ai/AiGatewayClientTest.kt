package com.remora.ai

import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AiGatewayClientTest {
    private fun response(request: Request, code: Int, body: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test")
        .body(body.toResponseBody("application/json".toMediaType())).build()

    @Test
    fun noSessionNeverSendsAnImage() = runTest {
        val requests = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet(); response(chain.request(), 500, "private provider detail")
        }.build()
        val client = AiGatewayClient("http://localhost/v1", http)
        val result = client.extractCard(byteArrayOf(1), "private note")
        assertEquals(401, (result.exceptionOrNull() as ApiException).statusCode)
        assertEquals(0, requests.get())
    }

    @Test
    fun unauthorizedClearsSessionWithoutPersistedPasswordOrAutomaticLogin() = runTest {
        val logins = AtomicInteger()
        val uploads = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath.contains("login")) {
                logins.incrementAndGet()
                response(chain.request(), 200, """{"access_token":"test-token","expires_in":3600}""")
            } else {
                uploads.incrementAndGet()
                response(chain.request(), 401, "private provider detail")
            }
        }.build()
        val client = AiGatewayClient("http://localhost/v1", http)
        assertTrue(client.signIn("http://localhost/v1", "test-user", "test-password").isSuccess)
        val error = client.extractCard(byteArrayOf(1), null).exceptionOrNull()
        assertEquals("HTTP_401", error?.message)
        assertNull(client.getValidToken())
        client.extractCard(byteArrayOf(2), null)
        assertEquals(1, logins.get())
        assertEquals(1, uploads.get())
    }

    @Test
    fun embeddingsRequireTheContractDimensionAndAreNormalized() = runTest {
        var size = 768
        val store = MemorySessionStore(AiSession("http://localhost/v1", "test-token", Long.MAX_VALUE))
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            response(chain.request(), 200, """{"embedding":[${List(size) { "1.0" }.joinToString(",")}],"dimension":$size}""")
        }.build()
        val client = AiGatewayClient("http://localhost/v1", http, store)
        val vector = client.generateEmbedding("test").getOrThrow()
        assertEquals(768, vector.size)
        assertEquals(1.0, vector.sumOf { it.toDouble() * it }, 0.0001)
        size = 3
        assertTrue(client.generateEmbedding("test").isFailure)
        assertTrue(client.session.value.isValid())
    }

    @Test
    fun externalPlaintextAndEmbeddedCredentialsAreRejected() {
        listOf("http://example.com/v1", "https://user:pass@example.com/v1", "https://example.com/v1?key=value")
            .forEach { assertTrue(runCatching { AiGatewayClient.validateEndpoint(it) }.isFailure) }
        assertEquals("https://example.com/v1", AiGatewayClient.validateEndpoint("https://example.com"))
    }

    @Test
    fun invalidCardFieldsKeepValidText() {
        val card = CardResponseParser.parse("""{"title":"Retained","summary":"Summary","tags":{"subject":null,"topics":["Math",{},2]},"content_type":"INVALID","ocr_text":"Original"}""")
        assertEquals("Retained", card.title)
        assertEquals("Summary", card.summary)
        assertEquals("chưa phân loại", card.tags.subject)
        assertEquals(listOf("Math"), card.tags.topics)
        assertEquals("UNKNOWN", card.content_type)
        assertEquals("Original", card.ocr_text)
        assertTrue(runCatching { CardResponseParser.parse("{") }.isFailure)
    }
}