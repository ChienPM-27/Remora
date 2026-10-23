package com.remora.gateway

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val logger = LoggerFactory.getLogger("Gateway")

@Serializable
data class LoginDemoRequest(
    val username: String,
    val password: String
)

@Serializable
data class AuthTokenResponse(
    val access_token: String,
    val token_type: String = "Bearer",
    val expires_in: Long = 86400L
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String
)

@Serializable
data class QuotaResponse(
    val user_id: String,
    val daily_limit: Int,
    val used_today: Int,
    val remaining: Int
)

class IpRateLimiter(
    private val maxAttempts: Int = 5,
    private val windowMs: Long = 60_000L
) {
    private val attempts = ConcurrentHashMap<String, MutableList<Long>>()

    fun isAllowed(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val list = attempts.computeIfAbsent(ip) { mutableListOf() }
        synchronized(list) {
            list.removeAll { it < now - windowMs }
            if (list.size >= maxAttempts) {
                return false
            }
            list.add(now)
            return true
        }
    }
}

class UserQuotaManager(
    private val defaultDailyLimit: Int = 50
) {
    private val userUsage = ConcurrentHashMap<String, AtomicInteger>()

    fun getRemaining(userId: String): Int {
        val used = userUsage.computeIfAbsent(userId) { AtomicInteger(0) }.get()
        return (defaultDailyLimit - used).coerceAtLeast(0)
    }

    fun getUsed(userId: String): Int {
        return userUsage.computeIfAbsent(userId) { AtomicInteger(0) }.get()
    }

    fun tryConsume(userId: String): Boolean {
        val counter = userUsage.computeIfAbsent(userId) { AtomicInteger(0) }
        while (true) {
            val current = counter.get()
            if (current >= defaultDailyLimit) {
                return false
            }
            if (counter.compareAndSet(current, current + 1)) {
                return true
            }
        }
    }
}

fun Application.module() {
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: throw IllegalStateException("Missing required environment variable: JWT_SECRET")
    val demoUsername = System.getenv("DEMO_USERNAME")
        ?: throw IllegalStateException("Missing required environment variable: DEMO_USERNAME")
    val demoPassword = System.getenv("DEMO_PASSWORD")
        ?: throw IllegalStateException("Missing required environment variable: DEMO_PASSWORD")

    val aiServiceUrl = System.getenv("AI_SERVICE_URL") ?: "http://localhost:8082"
    val internalServiceKey = System.getenv("INTERNAL_SERVICE_KEY") ?: ""

    val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)
    val loginRateLimiter = IpRateLimiter(maxAttempts = 5, windowMs = 60_000L)
    val quotaManager = UserQuotaManager(defaultDailyLimit = 50)

    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    install(CallLogging)

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Remora Gateway"
            verifier(
                JWT.require(jwtAlgorithm)
                    .withIssuer("remora-gateway")
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                if (!userId.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }

    fun getClientIp(call: ApplicationCall): String {
        return call.request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: call.request.origin.remoteHost
    }

    routing {
        get("/v1/health") {
            call.respond(mapOf("status" to "UP", "service" to "gateway"))
        }

        get("/v1/healthz") {
            call.respond(mapOf("status" to "UP", "service" to "gateway"))
        }

        get("/v1/readyz") {
            call.respond(mapOf("status" to "READY", "service" to "gateway"))
        }

        route("/v1/auth") {
            post("/login-demo") {
                val ip = getClientIp(call)
                if (!loginRateLimiter.isAllowed(ip)) {
                    logger.warn("Rate limit exceeded for login attempts from IP: $ip")
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("RATE_LIMITED", "Quá nhiều lần thử đăng nhập từ IP của bạn. Vui lòng thử lại sau 1 phút.")
                    )
                    return@post
                }

                val req = call.receive<LoginDemoRequest>()
                if (req.username == demoUsername && req.password == demoPassword) {
                    val token = JWT.create()
                        .withIssuer("remora-gateway")
                        .withClaim("userId", "usr_demo_student_01")
                        .withClaim("username", req.username)
                        .withExpiresAt(Date(System.currentTimeMillis() + 86400 * 1000L))
                        .sign(jwtAlgorithm)

                    call.respond(AuthTokenResponse(access_token = token))
                } else {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("UNAUTHORIZED", "Sai tài khoản hoặc mật khẩu demo")
                    )
                }
            }
        }

        authenticate("auth-jwt") {
            route("/v1/ai") {
                get("/quota") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString() ?: "unknown"
                    val used = quotaManager.getUsed(userId)
                    val remaining = quotaManager.getRemaining(userId)
                    call.respond(
                        QuotaResponse(
                            user_id = userId,
                            daily_limit = 50,
                            used_today = used,
                            remaining = remaining
                        )
                    )
                }

                post("/extract-card") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString() ?: "unknown"

                    if (!quotaManager.tryConsume(userId)) {
                        call.respond(
                            HttpStatusCode.TooManyRequests,
                            ErrorResponse("QUOTA_EXCEEDED", "Bạn đã dùng hết hạn ngạch 50 lượt gọi AI trong ngày.")
                        )
                        return@post
                    }

                    val requestBytes = call.receive<ByteArray>()
                    val incomingContentType = call.request.contentType()

                    val targetUrl = "$aiServiceUrl/v1/ai/extract-card"
                    logger.info("Forwarding /v1/ai/extract-card to ai-service: $targetUrl")

                    try {
                        val response = httpClient.post(targetUrl) {
                            if (internalServiceKey.isNotBlank()) {
                                header("X-Internal-Service-Key", internalServiceKey)
                            }
                            header(HttpHeaders.ContentType, incomingContentType.toString())
                            setBody(requestBytes)
                        }

                        val responseBytes = response.readBytes()
                        val responseContentType = response.contentType() ?: ContentType.Application.Json
                        call.respondBytes(
                            bytes = responseBytes,
                            contentType = responseContentType,
                            status = response.status
                        )
                    } catch (e: Exception) {
                        logger.error("Failed to forward extract-card to ai-service", e)
                        call.respond(HttpStatusCode.BadGateway, ErrorResponse("BAD_GATEWAY", "ai-service is unreachable: ${e.message}"))
                    }
                }

                post("/embeddings") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString() ?: "unknown"

                    if (!quotaManager.tryConsume(userId)) {
                        call.respond(
                            HttpStatusCode.TooManyRequests,
                            ErrorResponse("QUOTA_EXCEEDED", "Bạn đã dùng hết hạn ngạch 50 lượt gọi AI trong ngày.")
                        )
                        return@post
                    }

                    val requestBytes = call.receive<ByteArray>()
                    val targetUrl = "$aiServiceUrl/v1/ai/embeddings"
                    logger.info("Forwarding /v1/ai/embeddings to ai-service: $targetUrl")

                    try {
                        val response = httpClient.post(targetUrl) {
                            if (internalServiceKey.isNotBlank()) {
                                header("X-Internal-Service-Key", internalServiceKey)
                            }
                            contentType(ContentType.Application.Json)
                            setBody(requestBytes)
                        }

                        val responseBytes = response.readBytes()
                        call.respondBytes(
                            bytes = responseBytes,
                            contentType = response.contentType() ?: ContentType.Application.Json,
                            status = response.status
                        )
                    } catch (e: Exception) {
                        logger.error("Failed to forward embeddings to ai-service", e)
                        call.respond(HttpStatusCode.BadGateway, ErrorResponse("BAD_GATEWAY", "ai-service is unreachable: ${e.message}"))
                    }
                }

                post("/resurface-batch") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString() ?: "unknown"

                    if (!quotaManager.tryConsume(userId)) {
                        call.respond(
                            HttpStatusCode.TooManyRequests,
                            ErrorResponse("QUOTA_EXCEEDED", "Bạn đã dùng hết hạn ngạch 50 lượt gọi AI trong ngày.")
                        )
                        return@post
                    }

                    val requestBytes = call.receive<ByteArray>()
                    val targetUrl = "$aiServiceUrl/v1/ai/resurface-batch"
                    logger.info("Forwarding /v1/ai/resurface-batch to ai-service: $targetUrl")

                    try {
                        val response = httpClient.post(targetUrl) {
                            if (internalServiceKey.isNotBlank()) {
                                header("X-Internal-Service-Key", internalServiceKey)
                            }
                            contentType(ContentType.Application.Json)
                            setBody(requestBytes)
                        }

                        val responseBytes = response.readBytes()
                        call.respondBytes(
                            bytes = responseBytes,
                            contentType = response.contentType() ?: ContentType.Application.Json,
                            status = response.status
                        )
                    } catch (e: Exception) {
                        logger.error("Failed to forward resurface-batch to ai-service", e)
                        call.respond(HttpStatusCode.BadGateway, ErrorResponse("BAD_GATEWAY", "ai-service is unreachable: ${e.message}"))
                    }
                }
            }
        }
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}
