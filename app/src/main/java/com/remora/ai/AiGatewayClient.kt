package com.remora.ai

import com.remora.BuildConfig
import com.remora.ai.model.AuthLoginRequest
import com.remora.ai.model.AuthTokenResponse
import com.remora.ai.model.EmbeddingRequestDto
import com.remora.ai.model.EmbeddingResponseDto
import com.remora.ai.model.ExtractCardResponseDto
import com.remora.ai.model.ResurfaceBatchRequestDto
import com.remora.ai.model.ResurfaceBatchResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ApiException(
    val statusCode: Int,
    message: String,
    val retryAfterSeconds: Long? = null
) : Exception(message) {
    val isNonRetryableClientError: Boolean
        get() = statusCode in 400..404 || statusCode == 422
    val isRateLimited: Boolean
        get() = statusCode == 429
    val isServerError: Boolean
        get() = statusCode >= 500
}

class AiGatewayClient(
    private val baseUrl: String = BuildConfig.PROXY_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var cachedToken: String? = null
    private val authMutex = Mutex()

    suspend fun getValidToken(forceRefresh: Boolean = false): String? = authMutex.withLock {
        if (!forceRefresh && !cachedToken.isNullOrBlank()) {
            return cachedToken
        }

        val username = BuildConfig.DEMO_USERNAME
        val password = BuildConfig.DEMO_PASSWORD
        if (username.isBlank() || password.isBlank()) {
            Timber.e("No demo credentials configured in BuildConfig (DEMO_USERNAME/DEMO_PASSWORD)")
            return null
        }

        return withContext(Dispatchers.IO) {
            val loginPayload = json.encodeToString(AuthLoginRequest(username = username, password = password))
            val request = Request.Builder()
                .url("$baseUrl/auth/login-demo")
                .post(loginPayload.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val auth = json.decodeFromString<AuthTokenResponse>(body)
                        cachedToken = auth.access_token
                        auth.access_token
                    } else {
                        Timber.w("Failed to authenticate demo user: HTTP ${response.code}")
                        cachedToken = null
                        null
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during login-demo call")
                cachedToken = null
                null
            }
        }
    }

    private suspend fun <T> executeRequestWithAuth(
        buildRequest: (token: String) -> Request,
        parseResponse: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        // Enforce: Never send request without a valid token
        val initialToken = getValidToken(forceRefresh = false)
            ?: return@withContext Result.failure(
                ApiException(401, "No auth token: credentials missing or authentication failed")
            )

        var request = buildRequest(initialToken)
        try {
            var response = client.newCall(request).execute()

            // Enforce: Auto re-login once on 401 Unauthorized
            if (response.code == 401) {
                response.close()
                Timber.w("Received 401 Unauthorized. Refreshing token and retrying...")
                val refreshedToken = getValidToken(forceRefresh = true)
                    ?: return@withContext Result.failure(
                        ApiException(401, "Re-authentication failed after 401")
                    )
                request = buildRequest(refreshedToken)
                response = client.newCall(request).execute()
            }

            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    Result.success(parseResponse(body))
                } else {
                    val retryAfter = resp.header("Retry-After")?.toLongOrNull()
                    val errBody = resp.body?.string() ?: ""
                    Result.failure(
                        ApiException(resp.code, "Request failed HTTP ${resp.code}: $errBody", retryAfter)
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // never swallow cancellation (e.g. user typed a new search query)
        } catch (e: Exception) {
            Timber.e(e, "Network error during AI request")
            Result.failure(e)
        }
    }

    suspend fun extractCard(
        imageBytes: ByteArray,
        userIntent: String?
    ): Result<ExtractCardResponseDto> {
        val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                "screenshot.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            )

        if (!userIntent.isNullOrBlank()) {
            multipartBuilder.addFormDataPart("user_intent", userIntent)
        }

        val requestBody = multipartBuilder.build()

        return executeRequestWithAuth(
            buildRequest = { token ->
                Request.Builder()
                    .url("$baseUrl/ai/extract-card")
                    .header("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()
            },
            parseResponse = { body ->
                json.decodeFromString<ExtractCardResponseDto>(body)
            }
        )
    }

    suspend fun generateEmbedding(
        text: String,
        taskType: String = "RETRIEVAL_DOCUMENT"
    ): Result<List<Float>> {
        val payload = json.encodeToString(EmbeddingRequestDto(text = text, task_type = taskType))

        return executeRequestWithAuth(
            buildRequest = { token ->
                Request.Builder()
                    .url("$baseUrl/ai/embeddings")
                    .header("Authorization", "Bearer $token")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
            },
            parseResponse = { body ->
                json.decodeFromString<EmbeddingResponseDto>(body).embedding
            }
        )
    }

    suspend fun evaluateResurfacing(
        batchRequest: ResurfaceBatchRequestDto
    ): Result<ResurfaceBatchResponseDto> {
        val payload = json.encodeToString(batchRequest)

        return executeRequestWithAuth(
            buildRequest = { token ->
                Request.Builder()
                    .url("$baseUrl/ai/resurface-batch")
                    .header("Authorization", "Bearer $token")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
            },
            parseResponse = { body ->
                json.decodeFromString<ResurfaceBatchResponseDto>(body)
            }
        )
    }

    fun invalidateToken() {
        cachedToken = null
    }
}
