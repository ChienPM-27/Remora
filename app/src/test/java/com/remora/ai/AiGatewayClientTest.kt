package com.remora.ai

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AiGatewayClientTest {

    @Test
    fun testAutoReloginOn401Unauthorized() = runTest {
        val loginCount = AtomicInteger(0)
        val embeddingCount = AtomicInteger(0)

        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()

            when {
                url.contains("/auth/login-demo") -> {
                    val count = loginCount.incrementAndGet()
                    val token = if (count == 1) "expired_token_1" else "valid_token_2"
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"access_token":"$token","token_type":"Bearer","expires_in":3600}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                url.contains("/ai/embeddings") -> {
                    val count = embeddingCount.incrementAndGet()
                    val authHeader = request.header("Authorization")
                    if (count == 1) {
                        assertEquals("Bearer expired_token_1", authHeader)
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(401)
                            .message("Unauthorized")
                            .body("""{"error":"token_expired"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    } else {
                        assertEquals("Bearer valid_token_2", authHeader)
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"embedding":[0.1, 0.2, 0.3],"dimension":3}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                }
                else -> {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody(null))
                        .build()
                }
            }
        }

        val testHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = AiGatewayClient(baseUrl = "http://localhost:8080/v1", client = testHttpClient, demoUsername = "demo", demoPassword = "demo")

        val result = client.generateEmbedding("test text", "RETRIEVAL_QUERY")
        assertTrue("Request should succeed after auto-relogin retry", result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
        assertEquals(2, loginCount.get()) // 1 initial + 1 refresh
        assertEquals(2, embeddingCount.get()) // 1 401 failure + 1 retry
    }

    @Test
    fun testReusesCachedTokenWithoutReauthenticating() = runTest {
        val loginCount = AtomicInteger(0)
        val embeddingCount = AtomicInteger(0)

        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()

            when {
                url.contains("/auth/login-demo") -> {
                    loginCount.incrementAndGet()
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"access_token":"cached_token_abc","token_type":"Bearer","expires_in":3600}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                url.contains("/ai/embeddings") -> {
                    embeddingCount.incrementAndGet()
                    assertEquals("Bearer cached_token_abc", request.header("Authorization"))
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"embedding":[0.5, 0.5],"dimension":2}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                else -> {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody(null))
                        .build()
                }
            }
        }

        val testHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = AiGatewayClient(baseUrl = "http://localhost:8080/v1", client = testHttpClient, demoUsername = "demo", demoPassword = "demo")

        val result1 = client.generateEmbedding("first query")
        val result2 = client.generateEmbedding("second query")

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        assertEquals("Login should only be called once", 1, loginCount.get())
        assertEquals("Two embedding requests executed", 2, embeddingCount.get())
    }

    @Test
    fun testFailsWhenLoginFailsWithoutSendingAiRequest() = runTest {
        val loginCount = AtomicInteger(0)
        val embeddingCount = AtomicInteger(0)

        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()

            when {
                url.contains("/auth/login-demo") -> {
                    loginCount.incrementAndGet()
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Invalid credentials")
                        .body("""{"error":"invalid_credentials"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                url.contains("/ai/embeddings") -> {
                    embeddingCount.incrementAndGet()
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{}".toResponseBody(null))
                        .build()
                }
                else -> {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody(null))
                        .build()
                }
            }
        }

        val testHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = AiGatewayClient(baseUrl = "http://localhost:8080/v1", client = testHttpClient, demoUsername = "demo", demoPassword = "demo")

        val result = client.generateEmbedding("test text")
        assertTrue("Call must fail", result.isFailure)
        val exception = result.exceptionOrNull() as? ApiException
        assertNotNull(exception)
        assertEquals(401, exception?.statusCode)
        assertEquals("Login called once", 1, loginCount.get())
        assertEquals("Ai request must NOT be sent when auth token is missing", 0, embeddingCount.get())
    }
}
