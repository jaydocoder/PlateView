package com.jaydocoder.plateview.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jaydocoder.plateview.server.infrastructure.database.AuditEvent
import com.jaydocoder.plateview.server.infrastructure.database.AuditLogWriterKey
import com.jaydocoder.plateview.server.infrastructure.database.DataSourceKey
import com.jaydocoder.plateview.server.infrastructure.web.ApiErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
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
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
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
                if (
                    user == null ||
                    credential.payload.getClaim("role").asString() != user.role ||
                    credential.payload.getClaim("authVersion").asLong() != user.authVersion
                ) {
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
                    call.respond(user.toResponse())
                }
                route("/profile") {
                    get {
                        call.respond(service.currentUser(call.principal<JWTPrincipal>()!!).toProfileResponse())
                    }
                    post {
                        val actor = service.currentUser(call.principal<JWTPrincipal>()!!)
                        service.updateProfile(actor.id, call.receive<ProfileUpdateRequest>())
                        call.respond(HttpStatusCode.NoContent)
                    }
                    post("/avatar") {
                        val actor = service.currentUser(call.principal<JWTPrincipal>()!!)
                        val avatar = call.receiveAvatarUpload()
                        call.respond(service.updateAvatar(actor.id, avatar).toProfileResponse())
                    }
                    get("/avatar") {
                        val avatar = service.currentUser(call.principal<JWTPrincipal>()!!).avatar
                        if (avatar == null) {
                            call.respond(HttpStatusCode.NotFound)
                        } else {
                            call.response.headers.append(HttpHeaders.ContentType, avatar.contentType)
                            call.respondBytes(avatar.content)
                        }
                    }
                    post("/avatar/delete") {
                        val actor = service.currentUser(call.principal<JWTPrincipal>()!!)
                        call.respond(service.deleteAvatar(actor.id).toProfileResponse())
                    }
                }
            }
        }
        authenticate("access-token") {
            get("/admin/session") {
                val actorId = call.requireAdministrator() ?: return@get
                val user = service.currentUser(call.principal<JWTPrincipal>()!!)
                call.respond(user.toResponse())
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

    fun findActiveUserById(userId: Long): UserAccount? = findUser(
        "SELECT id, username, password_hash, role, auth_version, avatar_version, avatar_content, avatar_content_type, ((username = 'admin' AND role = 'ADMIN') OR EXISTS (SELECT 1 FROM schedule_participants p WHERE p.account_id = users.id)) AS schedule_enabled FROM users WHERE id = ? AND status = 'ACTIVE'",
        userId,
    )

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
        val accessToken = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim("userId", user.id)
            .withClaim("role", user.role)
            .withClaim("authVersion", user.authVersion)
            .withExpiresAt(Date.from(accessExpiresAt))
            .sign(settings.algorithm)
        return TokenResponse(accessToken, refreshToken, accessExpiresAt.toString(), user.toResponse())
    }

    private fun revoke(refreshToken: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE refresh_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND revoked_at IS NULL").use { statement ->
                statement.setString(1, hash(refreshToken)); statement.executeUpdate()
            }
        }
    }

    fun updateProfile(userId: Long, request: ProfileUpdateRequest) {
        val username = request.username?.trim()?.takeIf(String::isNotEmpty)
        val password = request.password?.takeIf(String::isNotBlank)
        require(username != null || password != null) { "请至少修改一项账号资料" }
        if (username != null) require(username.length <= 64) { "用户名不能超过64个字符" }
        if (password != null) {
            require(password.length >= 6) { "密码至少需要6个字符" }
            val currentPassword = request.currentPassword?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("修改密码需要输入当前密码")
            val user = checkNotNull(findActiveUserById(userId))
            require(BCrypt.checkpw(currentPassword, user.passwordHash)) { "当前密码不正确" }
        }
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                if (username != null) {
                    connection.prepareStatement("SELECT 1 FROM users WHERE username = ? AND id <> ?").use { statement ->
                        statement.setString(1, username)
                        statement.setLong(2, userId)
                        statement.executeQuery().use { result -> if (result.next()) throw ProfileConflictException("用户名已被使用") }
                    }
                }
                connection.prepareStatement(
                    "UPDATE users SET username = COALESCE(?, username), password_hash = COALESCE(?, password_hash), auth_version = auth_version + 1 WHERE id = ?",
                ).use { statement ->
                    statement.setString(1, username)
                    statement.setString(2, password?.let { BCrypt.hashpw(it, BCrypt.gensalt()) })
                    statement.setLong(3, userId)
                    statement.executeUpdate()
                }
                connection.prepareStatement("UPDATE refresh_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeUpdate()
                }
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    fun updateAvatar(userId: Long, avatar: AvatarUpload): UserAccount {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE users SET avatar_content = ?, avatar_content_type = ?, avatar_version = avatar_version + 1 WHERE id = ?",
            ).use { statement ->
                statement.setBytes(1, avatar.content)
                statement.setString(2, avatar.contentType)
                statement.setLong(3, userId)
                statement.executeUpdate()
            }
        }
        return checkNotNull(findActiveUserById(userId))
    }

    fun deleteAvatar(userId: Long): UserAccount {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE users SET avatar_content = NULL, avatar_content_type = NULL, avatar_version = avatar_version + 1 WHERE id = ?",
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeUpdate()
            }
        }
        return checkNotNull(findActiveUserById(userId))
    }

    private fun findUserByUsername(username: String): UserAccount? = findUser(
        "SELECT id, username, password_hash, role, auth_version, avatar_version, avatar_content, avatar_content_type, ((username = 'admin' AND role = 'ADMIN') OR EXISTS (SELECT 1 FROM schedule_participants p WHERE p.account_id = users.id)) AS schedule_enabled FROM users WHERE username = ? AND status = 'ACTIVE'",
        username,
    )
    private fun findUserByRefreshHash(tokenHash: String): UserAccount? = findUser(
        "SELECT u.id, u.username, u.password_hash, u.role, u.auth_version, u.avatar_version, u.avatar_content, u.avatar_content_type, ((u.username = 'admin' AND u.role = 'ADMIN') OR EXISTS (SELECT 1 FROM schedule_participants p WHERE p.account_id = u.id)) AS schedule_enabled FROM refresh_sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > CURRENT_TIMESTAMP AND u.status = 'ACTIVE'",
        tokenHash,
    )
    private fun findUser(sql: String, value: Any): UserAccount? = dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            when (value) { is Long -> statement.setLong(1, value); is String -> statement.setString(1, value) }
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                UserAccount(
                    id = result.getLong("id"),
                    username = result.getString("username"),
                    passwordHash = result.getString("password_hash"),
                    role = result.getString("role"),
                    authVersion = result.getLong("auth_version"),
                    avatarVersion = result.getLong("avatar_version"),
                    avatar = result.getBytes("avatar_content")?.let { AvatarContent(it, result.getString("avatar_content_type")) },
                    scheduleEnabled = result.getBoolean("schedule_enabled"),
                )
            }
        }
    }
    private fun audit(userId: Long, action: String, status: String, requestId: String?) { dataSource.connection.use { connection -> connection.prepareStatement("INSERT INTO audit_logs (actor_id, action_type, target_type, result_status, request_id) VALUES (?, ?, 'AUTH', ?, ?)").use { s -> s.setLong(1, userId); s.setString(2, action); s.setString(3, status); s.setString(4, requestId); s.executeUpdate() } } }
}

@Serializable private data class LoginRequest(val username: String, val password: String)
@Serializable private data class RefreshRequest(val refreshToken: String)
@Serializable private data class ProfileUpdateRequest(
    val username: String? = null,
    val password: String? = null,
    val currentPassword: String? = null,
)
@Serializable private data class TokenResponse(val accessToken: String, val refreshToken: String, val accessTokenExpiresAt: String, val user: UserResponse)
@Serializable private data class UserResponse(val id: Long, val username: String, val role: String, val avatarVersion: Long, val scheduleEnabled: Boolean)
@Serializable private data class ProfileResponse(val id: Long, val username: String, val role: String, val avatarVersion: Long, val hasAvatar: Boolean, val scheduleEnabled: Boolean)
private data class UserAccount(
    val id: Long,
    val username: String,
    val passwordHash: String,
    val role: String,
    val authVersion: Long,
    val avatarVersion: Long,
    val avatar: AvatarContent?,
    val scheduleEnabled: Boolean,
)
private data class AvatarContent(val content: ByteArray, val contentType: String)
internal data class AvatarUpload(val content: ByteArray, val contentType: String)
internal class ProfileConflictException(message: String) : RuntimeException(message)

private fun UserAccount.toResponse() = UserResponse(id, username, role, avatarVersion, scheduleEnabled)
private fun UserAccount.toProfileResponse() = ProfileResponse(id, username, role, avatarVersion, avatar != null, scheduleEnabled)

internal suspend fun ApplicationCall.receiveAvatarUpload(): AvatarUpload {
    val declaredSize = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    require(declaredSize == null || declaredSize <= MAXIMUM_AVATAR_SIZE_BYTES) { "头像文件不能超过10MiB" }
    val multipart = receiveMultipart()
    var upload: AvatarUpload? = null
    while (true) {
        val part = multipart.readPart() ?: break
        try {
            if (part is PartData.FileItem && part.name == "avatar") {
                require(upload == null) { "一次只能上传一个头像文件" }
                val bytes = part.provider().readRemaining(MAXIMUM_AVATAR_SIZE_BYTES.toLong() + 1).readBytes()
                require(bytes.size <= MAXIMUM_AVATAR_SIZE_BYTES) { "头像文件不能超过10MiB" }
                val contentType = bytes.detectAvatarContentType()
                    ?: throw IllegalArgumentException("仅支持JPEG、PNG、WebP、GIF或BMP格式的头像")
                upload = AvatarUpload(bytes, contentType)
            }
        } finally {
            part.dispose()
        }
    }
    return requireNotNull(upload) { "缺少头像文件" }
}

private fun ByteArray.detectAvatarContentType(): String? = when {
    size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte() -> "image/jpeg"
    size >= 8 && copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
    size >= 6 && (copyOfRange(0, 6).decodeToString() == "GIF87a" || copyOfRange(0, 6).decodeToString() == "GIF89a") -> "image/gif"
    size >= 2 && this[0] == 0x42.toByte() && this[1] == 0x4D.toByte() -> "image/bmp"
    size >= 12 && copyOfRange(0, 4).decodeToString() == "RIFF" && copyOfRange(8, 12).decodeToString() == "WEBP" -> "image/webp"
    else -> null
}

private const val MAXIMUM_AVATAR_SIZE_BYTES = 10 * 1024 * 1024
private const val ISSUER = "plateview"
private const val AUDIENCE = "plateview-mobile"
private fun randomToken(): String = ByteArray(48).also(SecureRandom()::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
