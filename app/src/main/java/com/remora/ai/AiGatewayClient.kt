package com.remora.ai

import com.remora.BuildConfig
import com.remora.ai.model.*
import com.remora.data.local.entity.StoredEmbedding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt

class ApiException(val statusCode: Int, message: String, val retryAfterSeconds: Long? = null) : Exception(message) {
    val isNonRetryableClientError: Boolean get() = statusCode in 400..499 && statusCode !in setOf(408, 425, 429)
    val isRateLimited: Boolean get() = statusCode == 429
    val isServerError: Boolean get() = statusCode >= 500
}

class AiGatewayClient(
    baseUrl: String = BuildConfig.PROXY_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build(),
    private val sessionStore: SessionStore = MemorySessionStore(AiSession(baseUrl))
) {
    private val json = Json { ignoreUnknownKeys = true }
    val session = sessionStore.state

    suspend fun getValidToken(forceRefresh: Boolean = false): String? {
        if (forceRefresh) invalidateToken()
        return session.value.takeIf { it.isValid() }?.token
    }

    suspend fun signIn(endpoint: String, username: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val safeEndpoint = validateEndpoint(endpoint)
            require(username.isNotBlank() && password.isNotBlank()) { "CREDENTIALS_REQUIRED" }
            val request = Request.Builder().url("$safeEndpoint/auth/login-demo")
                .post(json.encodeToString(AuthLoginRequest(username.trim(), password)).toRequestBody(JSON_TYPE)).build()
            client.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) throw ApiException(response.code, "HTTP_${response.code}")
                val token = json.decodeFromString<AuthTokenResponse>(response.body?.string().orEmpty())
                require(token.access_token.isNotBlank() && token.expires_in in 1..604800) { "INVALID_SESSION" }
                sessionStore.save(AiSession(safeEndpoint, token.access_token,
                    System.currentTimeMillis() + token.expires_in * 1000))
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(safeError(error)) }
    }

    private suspend fun <T> execute(
        build: (AiSession) -> Request, parse: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        val current = session.value
        if (!current.isValid()) return@withContext Result.failure(ApiException(401, "SIGN_IN_REQUIRED"))
        try {
            client.newCall(build(current)).awaitResponse().use { response ->
                if (response.code == 401 && session.value == current) invalidateToken()
                if (!response.isSuccessful) throw ApiException(response.code, "HTTP_${response.code}",
                    response.header("Retry-After")?.toLongOrNull())
                Result.success(parse(response.body?.string().orEmpty()))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(safeError(error)) }
    }

    suspend fun extractCard(imageBytes: ByteArray, userIntent: String?): Result<ExtractCardResponseDto> {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", "screenshot.jpg", imageBytes.toRequestBody("image/jpeg".toMediaType()))
            .apply { if (!userIntent.isNullOrBlank()) addFormDataPart("user_intent", userIntent) }.build()
        return execute({ auth -> request(auth, "extract-card", body) }, CardResponseParser::parse)
    }

    suspend fun generateEmbedding(text: String, taskType: String = "RETRIEVAL_DOCUMENT"): Result<List<Float>> {
        val body = json.encodeToString(EmbeddingRequestDto(text, taskType)).toRequestBody(JSON_TYPE)
        return execute({ auth -> request(auth, "embeddings", body) }) { raw ->
            val response = json.decodeFromString<EmbeddingResponseDto>(raw)
            val values = response.embedding.toFloatArray()
            require(response.dimension == StoredEmbedding.DIMENSION && StoredEmbedding.isValid(values)) { "INVALID_VECTOR" }
            val norm = sqrt(values.sumOf { it.toDouble() * it.toDouble() })
            values.map { (it / norm).toFloat() }
        }
    }

    suspend fun evaluateResurfacing(batchRequest: ResurfaceBatchRequestDto): Result<ResurfaceBatchResponseDto> {
        val body = json.encodeToString(batchRequest).toRequestBody(JSON_TYPE)
        return execute({ auth -> request(auth, "resurface-batch", body) }) { json.decodeFromString(it) }
    }

    private fun request(auth: AiSession, route: String, body: RequestBody): Request = Request.Builder()
        .url("${auth.endpoint}/ai/$route").header("Authorization", "Bearer ${auth.token}").post(body).build()

    fun invalidateToken() { sessionStore.save(AiSession(session.value.endpoint)) }

    private fun safeError(error: Exception): Exception = when (error) {
        is ApiException -> error
        is IOException -> IOException("NETWORK_UNAVAILABLE")
        else -> IllegalStateException("INVALID_RESPONSE_OR_CONFIGURATION")
    }

    companion object {
        private val JSON_TYPE = "application/json".toMediaType()
        fun validateEndpoint(value: String): String {
            val url = value.trim().trimEnd('/').toHttpUrlOrNull() ?: error("INVALID_ENDPOINT")
            val local = url.host in setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")
            require(url.isHttps || (BuildConfig.DEBUG && local)) { "HTTPS_REQUIRED" }
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "INVALID_ENDPOINT" }
            val path = url.encodedPath.trimEnd('/')
            require(path.isEmpty() || path == "/v1") { "INVALID_ENDPOINT" }
            return url.newBuilder().encodedPath("/v1").build().toString().trimEnd('/')
        }
    }
}

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}

object CardResponseParser {
    fun parse(raw: String): ExtractCardResponseDto {
        val root = Json.parseToJsonElement(raw) as? JsonObject ?: error("INVALID_CARD")
        fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
        val tags = root["tags"] as? JsonObject
        val type = root["content_type"].text()?.takeIf {
            it in setOf("THEORY", "PROBLEM", "SOLUTION", "CODE", "NOTES", "UNKNOWN")
        } ?: "UNKNOWN"
        return ExtractCardResponseDto(
            title = root["title"].text()?.trim()?.take(80)?.ifBlank { null } ?: "Ảnh đã lưu",
            summary = root["summary"].text().orEmpty(),
            tags = CardTagsDto(tags?.get("subject").text()?.trim()?.ifBlank { null } ?: "chưa phân loại",
                (tags?.get("topics") as? JsonArray)?.mapNotNull { it.text()?.trim()?.ifBlank { null } }?.distinct() ?: emptyList()),
            content_type = type, ocr_text = root["ocr_text"].text().orEmpty()
        )
    }
}