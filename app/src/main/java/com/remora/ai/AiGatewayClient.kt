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
    private val baseUrl: String = BuildConfig.PROXY_BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedToken: String? = null

    private suspend fun getOrRefreshToken(): String = withContext(Dispatchers.IO) {
        cachedToken?.let { return@withContext it }

        val loginPayload = json.encodeToString(AuthLoginRequest())
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
                    ""
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during login-demo call")
            ""
        }
    }

    suspend fun extractCard(
        imageBytes: ByteArray,
        userIntent: String?
    ): Result<ExtractCardResponseDto> = withContext(Dispatchers.IO) {
        val token = getOrRefreshToken()

        val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                "screenshot.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            )

        if (!userIntent.isNullOrBlank()) {
            multipartBuilder.addFormDataPart("user_intent", userIntent)
        }

        val request = Request.Builder()
            .url("$baseUrl/ai/extract-card")
            .apply {
                if (token.isNotEmpty()) header("Authorization", "Bearer $token")
            }
            .post(multipartBuilder.build())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    Result.success(json.decodeFromString<ExtractCardResponseDto>(body))
                } else {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    val errBody = response.body?.string() ?: ""
                    Result.failure(ApiException(response.code, "Extract card failed HTTP ${response.code}: $errBody", retryAfter))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Network error during extractCard")
            Result.failure(e)
        }
    }

    suspend fun generateEmbedding(
        text: String,
        taskType: String = "RETRIEVAL_DOCUMENT"
    ): Result<List<Float>> = withContext(Dispatchers.IO) {
        val token = getOrRefreshToken()
        val payload = json.encodeToString(EmbeddingRequestDto(text = text, task_type = taskType))

        val request = Request.Builder()
            .url("$baseUrl/ai/embeddings")
            .apply {
                if (token.isNotEmpty()) header("Authorization", "Bearer $token")
            }
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val result = json.decodeFromString<EmbeddingResponseDto>(body)
                    Result.success(result.embedding)
                } else {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    val errBody = response.body?.string() ?: ""
                    Result.failure(ApiException(response.code, "Embedding failed HTTP ${response.code}: $errBody", retryAfter))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Network error during generateEmbedding")
            Result.failure(e)
        }
    }

    suspend fun evaluateResurfacing(
        batchRequest: ResurfaceBatchRequestDto
    ): Result<ResurfaceBatchResponseDto> = withContext(Dispatchers.IO) {
        val token = getOrRefreshToken()
        val payload = json.encodeToString(batchRequest)

        val request = Request.Builder()
            .url("$baseUrl/ai/resurface-batch")
            .apply {
                if (token.isNotEmpty()) header("Authorization", "Bearer $token")
            }
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    Result.success(json.decodeFromString<ResurfaceBatchResponseDto>(body))
                } else {
                    Result.failure(Exception("Resurface batch failed with HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Network error during evaluateResurfacing")
            Result.failure(e)
        }
    }
}
