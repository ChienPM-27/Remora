package com.remora.gateway

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
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
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Date
import java.util.UUID

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

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    val jwtSecret = System.getenv("JWT_SECRET") ?: "remora_super_secret_jwt_key_hackathon_2026"
    val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    install(CallLogging)

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Remora Gateway"
            verifier(
                JWT.require(jwtAlgorithm)
                    .withIssuer("remora-gateway")
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }

    routing {
        get("/v1/health") {
            call.respond(mapOf("status" to "UP", "service" to "gateway"))
        }

        route("/v1/auth") {
            post("/login-demo") {
                val req = call.receive<LoginDemoRequest>()
                if (req.username == "demo_student_01" && req.password == "password123") {
                    val token = JWT.create()
                        .withIssuer("remora-gateway")
                        .withClaim("userId", "usr_demo_student_01")
                        .withClaim("username", req.username)
                        .withExpiresAt(Date(System.currentTimeMillis() + 86400 * 1000L))
                        .sign(jwtAlgorithm)

                    call.respond(AuthTokenResponse(access_token = token))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "Sai tài khoản hoặc mật khẩu demo"))
                }
            }
        }

        authenticate("auth-jwt") {
            route("/v1/ai") {
                get("/quota") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString() ?: "unknown"
                    call.respond(mapOf(
                        "user_id" to userId,
                        "daily_limit" to 50,
                        "used_today" to 0,
                        "remaining" to 50
                    ))
                }
            }
        }
    }
}
