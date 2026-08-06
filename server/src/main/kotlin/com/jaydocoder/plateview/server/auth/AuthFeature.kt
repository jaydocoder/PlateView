package com.jaydocoder.plateview.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jaydocoder.plateview.server.infrastructure.database.AuditEvent
import com.jaydocoder.plateview.server.infrastructure.database.AuditLogWriterKey
import com.jaydocoder.plateview.server.infrastructure.database.DataSourceKey
import com.jaydocoder.plateview.server.infrastructure.web.ApiErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import javax.sql.DataSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.mindrot.jbcrypt.BCrypt

internal fun Application.configureAuthenticationFeature() {
    val dataSource = attributes.getOrNull(DataSourceKey) ?: return
    val settings = authenticationSettings()
    val service = AuthService(dataSource, settings)
    service.ensureInitialAdministrator()

    install(io.ktor.server.auth.Authentication) {
        jwt("access-token") {
            realm = "PlateView"
            verifier(settings.verifier)
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asLong()
                val user = userId?.let(service::findActiveUserById)
                if (user == null || credential.payload.getClaim("role").asString() != user.role) {
                    null
                } else {
                    JWTPrincipal(credential.payload)
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiErrorResponse("UNAUTHENTICATED", "登录状态已失效，请重新登录", call.callId),
                )
            }
        }
    }

    routing {
        route("/auth") {
            post("/login") {
                val request = call.receive<LoginRequest>()
                val result = service.login(request.username, request.password, call.callId)
                if (result == null) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("INVALID_CREDENTIALS", "账号或密码错误", call.callId))
                } else {
                    call.respond(result)
                }
            }
            post("/refresh") {
                val request = call.receive<RefreshRequest>()
                val result = service.refresh(request.refreshToken, call.callId)
                if (result == null) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("INVALID_REFRESH_TOKEN", "登录状态已失效，请重新登录", call.callId))
                } else {
                    call.respond(result)
                }
            }
            authenticate("access-token") {
                post("/logout") {
                    service.logout(call.receive<RefreshRequest>().refreshToken, call.callId)
                    call.respond(HttpStatusCode.NoContent)
                }
                get("/session") {
                    val user = service.currentUser(call.principal<JWTPrincipal>()!!)
                    call.respond(UserResponse(user.id, user.username, user.role))
                }
            }
        }
        authenticate("access-token") {
            get("/admin/session") {
                val actorId = call.requireAdministrator() ?: return@get
                val user = service.currentUser(call.principal<JWTPrincipal>()!!)
                call.respond(UserResponse(actorId, user.username, "ADMIN"))
            }
        }
    }
}

private fun Application.authenticationSettings(): AuthenticationSettings {
    val secret = environment.config.propertyOrNull("auth.jwtSecret")?.getString()
        ?: error("未配置JWT签名密钥")
    val initialPassword = environment.config.propertyOrNull("auth.initialAdminPassword")?.getString()
        ?: error("未配置初始管理员密码")
    require(secret.length >= 32) { "JWT签名密钥长度不足" }
    require(initialPassword.length >= 6) { "初始管理员密码长度不足" }
    val accessMinutes = environment.config.property("auth.accessTokenMinutes").getString().toLong()
    val refreshDays = environment.config.property("auth.refreshTokenDays").getString().toLong()
    return AuthenticationSettings(secret, initialPassword, Duration.ofMinutes(accessMinutes), Duration.ofDays(refreshDays))
}

internal suspend fun ApplicationCall.requireAdministrator(): Long? {
    val principal = principal<JWTPrincipal>() ?: return null
    val actorId = principal.payload.getClaim("userId").asLong()
    if (principal.payload.getClaim("role").asString() == "ADMIN") return actorId
    application.attributes.getOrNull(AuditLogWriterKey)?.write(
        AuditEvent(actorId, "ADMIN_ACCESS", "SESSION", null, "DENIED", callId, JsonObject(emptyMap())),
    )
    respond(HttpStatusCode.Forbidden, ApiErrorResponse("ADMIN_REQUIRED", "需要管理员权限", callId))
    return null
}

private data class AuthenticationSettings(
    val secret: String,
    val initialAdminPassword: String,
    val accessLifetime: Duration,
    val refreshLifetime: Duration,
) {
    val algorithm: Algorithm = Algorithm.HMAC256(secret)
    val verifier = JWT.require(algorithm).withIssuer(ISSUER).withAudience(AUDIENCE).build()
}

private class AuthService(private val dataSource: DataSource, private val settings: AuthenticationSettings) {
    fun ensureInitialAdministrator() {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT 1 FROM users WHERE username = 'admin'").use { query ->
                query.executeQuery().use { result ->
                    if (result.next()) return
                }
            }
            connection.prepareStatement("INSERT INTO users (username, password_hash, role) VALUES ('admin', ?, 'ADMIN')").use { statement ->
                statement.setString(1, BCrypt.hashpw(settings.initialAdminPassword, BCrypt.gensalt()))
                statement.executeUpdate()
            }
        }
    }

    fun login(username: String, password: String, requestId: String?): TokenResponse? {
        val user = findUserByUsername(username.trim()) ?: return null
        if (!BCrypt.checkpw(password, user.passwordHash)) return null
        return issueTokens(user, requestId)
    }

    fun refresh(refreshToken: String, requestId: String?): TokenResponse? {
        val user = findUserByRefreshHash(hash(refreshToken)) ?: return null
        revoke(refreshToken)
        return issueTokens(user, requestId)
    }

    fun logout(refreshToken: String, requestId: String?) {
        val user = findUserByRefreshHash(hash(refreshToken))
        revoke(refreshToken)
        if (user != null) audit(user.id, "LOGOUT", "SUCCESS", requestId)
    }

    fun findActiveUserById(userId: Long): UserAccount? = findUser("SELECT id, username, password_hash, role FROM users WHERE id = ? AND status = 'ACTIVE'", userId)

    fun currentUser(principal: JWTPrincipal): UserAccount = findActiveUserById(principal.payload.getClaim("userId").asLong())
        ?: error("当前账号不可用")

    private fun issueTokens(user: UserAccount, requestId: String?): TokenResponse {
        val refreshToken = randomToken()
        val expiresAt = Instant.now().plus(settings.refreshLifetime)
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO refresh_sessions (user_id, token_hash, expires_at) VALUES (?, ?, ?)").use { statement ->
                statement.setLong(1, user.id)
                statement.setString(2, hash(refreshToken))
                statement.setTimestamp(3, Timestamp.from(expiresAt))
                statement.executeUpdate()
            }
        }
        audit(user.id, "LOGIN", "SUCCESS", requestId)
        val accessExpiresAt = Instant.now().plus(settings.accessLifetime)
        val accessToken = JWT.create().withIssuer(ISSUER).withAudience(AUDIENCE).withClaim("userId", user.id).withClaim("role", user.role).withExpiresAt(Date.from(accessExpiresAt)).sign(settings.algorithm)
        return TokenResponse(accessToken, refreshToken, accessExpiresAt.toString(), UserResponse(user.id, user.username, user.role))
    }

    private fun revoke(refreshToken: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE refresh_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND revoked_at IS NULL").use { statement ->
                statement.setString(1, hash(refreshToken)); statement.executeUpdate()
            }
        }
    }

    private fun findUserByUsername(username: String): UserAccount? = findUser("SELECT id, username, password_hash, role FROM users WHERE username = ? AND status = 'ACTIVE'", username)
    private fun findUserByRefreshHash(tokenHash: String): UserAccount? = findUser("SELECT u.id, u.username, u.password_hash, u.role FROM refresh_sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > CURRENT_TIMESTAMP AND u.status = 'ACTIVE'", tokenHash)
    private fun findUser(sql: String, value: Any): UserAccount? = dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            when (value) { is Long -> statement.setLong(1, value); is String -> statement.setString(1, value) }
            statement.executeQuery().use { result -> if (result.next()) UserAccount(result.getLong("id"), result.getString("username"), result.getString("password_hash"), result.getString("role")) else null }
        }
    }
    private fun audit(userId: Long, action: String, status: String, requestId: String?) { dataSource.connection.use { connection -> connection.prepareStatement("INSERT INTO audit_logs (actor_id, action_type, target_type, result_status, request_id) VALUES (?, ?, 'AUTH', ?, ?)").use { s -> s.setLong(1, userId); s.setString(2, action); s.setString(3, status); s.setString(4, requestId); s.executeUpdate() } } }
}

@Serializable private data class LoginRequest(val username: String, val password: String)
@Serializable private data class RefreshRequest(val refreshToken: String)
@Serializable private data class TokenResponse(val accessToken: String, val refreshToken: String, val accessTokenExpiresAt: String, val user: UserResponse)
@Serializable private data class UserResponse(val id: Long, val username: String, val role: String)
private data class UserAccount(val id: Long, val username: String, val passwordHash: String, val role: String)
private const val ISSUER = "plateview"
private const val AUDIENCE = "plateview-mobile"
private fun randomToken(): String = ByteArray(48).also(SecureRandom()::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
