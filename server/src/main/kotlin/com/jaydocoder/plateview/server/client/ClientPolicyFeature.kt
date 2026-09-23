package com.jaydocoder.plateview.server.client

import com.jaydocoder.plateview.server.auth.isPrimaryAdministrator
import com.jaydocoder.plateview.server.infrastructure.database.DataSourceKey
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Connection
import java.security.MessageDigest
import java.time.Duration
import javax.sql.DataSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun Application.configureClientPolicyFeature() {
    val dataSource = attributes.getOrNull(DataSourceKey) ?: return
    val service = ClientPolicyService(dataSource)
    routing {
        authenticate("access-token") {
            get("/client/runtime-policy") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asLong()
                call.respond(service.runtimePolicy(userId))
            }
            post("/client/cache-reset/ack") {
                val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
                service.acknowledge(userId, call.receive())
                call.respond(HttpStatusCode.NoContent)
            }
            route("/admin/client-policy") {
                get {
                    val actorId = call.requirePrimaryAdministrator(service) ?: return@get
                    call.respond(service.adminPolicy(actorId))
                }
                put {
                    val actorId = call.requirePrimaryAdministrator(service) ?: return@put
                    call.respond(service.updatePolicy(actorId, call.receive()))
                }
                put("/limits") {
                    val actorId = call.requirePrimaryAdministrator(service) ?: return@put
                    call.respond(service.updateLimits(actorId, call.receive()))
                }
                put("/api-endpoint") {
                    val actorId = call.requirePrimaryAdministrator(service) ?: return@put
                    call.respond(service.updateApiEndpoint(actorId, call.receive()))
                }
                put("/update-endpoint") {
                    val actorId = call.requirePrimaryAdministrator(service) ?: return@put
                    call.respond(service.updateUpdateEndpoint(actorId, call.receive()))
                }
                post("/api-endpoint/test") {
                    call.requirePrimaryAdministrator(service) ?: return@post
                    val request = call.receive<EndpointTestRequest>()
                    call.respond(service.testApiEndpoint(request.baseUrl))
                }
                post("/update-endpoint/test") {
                    call.requirePrimaryAdministrator(service) ?: return@post
                    val request = call.receive<EndpointTestRequest>()
                    call.respond(service.testUpdateEndpoint(request.baseUrl))
                }
            }
            post("/admin/users/{userId}/cache-reset") {
                val actorId = call.requirePrimaryAdministrator(service) ?: return@post
                val userId = call.parameters["userId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("用户标识无效")
                call.respond(service.requestCacheReset(actorId, userId))
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requirePrimaryAdministrator(
    service: ClientPolicyService,
): Long? {
    val actorId = principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
    if (!service.isPrimaryAdministrator(actorId)) {
        respond(HttpStatusCode.Forbidden, mapOf("message" to "仅admin主管理员可以管理客户端策略"))
        return null
    }
    return actorId
}

internal class ClientPolicyService(private val dataSource: DataSource) {
    fun isPrimaryAdministrator(userId: Long): Boolean = dataSource.connection.use { it.isPrimaryAdministrator(userId) }

    fun runtimePolicy(userId: Long): ClientRuntimePolicyResponse = dataSource.connection.use { connection ->
        val policy = connection.readPolicy()
        val user = connection.prepareStatement("SELECT username, cache_reset_revision FROM users WHERE id = ?").use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { result ->
                if (!result.next()) throw IllegalArgumentException("用户不存在")
                result.getString("username") to result.getLong("cache_reset_revision")
            }
        }
        policy.toRuntime(user.first == "admin", user.second)
    }

    fun resultLimits(userId: Long): ClientResultLimits {
        val policy = runtimePolicy(userId)
        return ClientResultLimits(
            vehicle = policy.vehicleResultLimit,
            workOrder = policy.workOrderResultLimit,
            wechatMessage = policy.wechatMessageResultLimit,
        )
    }

    fun adminPolicy(actorId: Long): ClientPolicyResponse = dataSource.connection.use { connection ->
        check(connection.isPrimaryAdministrator(actorId))
        val policy = connection.readPolicy()
        val confirmation = connection.prepareStatement(
            "SELECT COUNT(*), COUNT(*) FILTER (WHERE applied_policy_revision >= ?), MAX(last_seen_at)::text FROM client_instance_state",
        ).use { statement ->
            statement.setLong(1, policy.revision)
            statement.executeQuery().use { result ->
                result.next()
                Triple(result.getInt(1), result.getInt(2), result.getString(3))
            }
        }
        val cacheResetStatuses = connection.prepareStatement(
            """
            SELECT u.id, u.cache_reset_revision, COUNT(c.client_instance_id),
                   COUNT(c.client_instance_id) FILTER (WHERE c.applied_cache_reset_revision >= u.cache_reset_revision),
                   MAX(c.last_seen_at)::text
            FROM users u LEFT JOIN client_instance_state c ON c.user_id = u.id
            WHERE u.cache_reset_requested_at IS NOT NULL
            GROUP BY u.id, u.cache_reset_revision
            ORDER BY u.id
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        val clients = result.getInt(3)
                        val completed = result.getInt(4)
                        add(
                            CacheResetStatusResponse(
                                result.getLong(1), result.getLong(2), clients, completed,
                                when {
                                    clients == 0 -> "WAITING"
                                    completed == 0 -> "SENT"
                                    completed < clients -> "PARTIAL"
                                    else -> "COMPLETED"
                                },
                                result.getString(5),
                            ),
                        )
                    }
                }
            }
        }
        policy.toResponse(confirmation.first, confirmation.second, confirmation.third, cacheResetStatuses)
    }

    fun updatePolicy(actorId: Long, request: ClientPolicyUpdateRequest): ClientPolicyResponse {
        require(request.vehicleResultLimit in 0..50) { "匹配车辆数量必须在0至50之间" }
        require(request.workOrderResultLimit in 0..50) { "微信车单数量必须在0至50之间" }
        require(request.wechatMessageResultLimit in 0..50) { "微信聊天数量必须在0至50之间" }
        val apiUrl = normalizeBaseUrl(request.apiBaseUrl)
        val updateUrl = normalizeBaseUrl(request.updateBaseUrl)
        val current = dataSource.connection.use { connection ->
            check(connection.isPrimaryAdministrator(actorId))
            connection.readPolicy()
        }
        if (current.apiBaseUrl != apiUrl) testApiEndpoint(apiUrl)
        if (current.updateBaseUrl != updateUrl) testUpdateEndpoint(updateUrl)
        dataSource.connection.use { connection ->
            check(connection.isPrimaryAdministrator(actorId))
            connection.prepareStatement(
                """
                UPDATE client_runtime_policy SET
                    revision = revision + 1,
                    vehicle_result_limit = ?, work_order_result_limit = ?, wechat_message_result_limit = ?,
                    previous_api_base_url = CASE WHEN active_api_base_url <> ? THEN active_api_base_url ELSE previous_api_base_url END,
                    active_api_base_url = ?,
                    previous_update_base_url = CASE WHEN active_update_base_url <> ? THEN active_update_base_url ELSE previous_update_base_url END,
                    active_update_base_url = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = 1
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, request.vehicleResultLimit)
                statement.setInt(2, request.workOrderResultLimit)
                statement.setInt(3, request.wechatMessageResultLimit)
                statement.setString(4, apiUrl)
                statement.setString(5, apiUrl)
                statement.setString(6, updateUrl)
                statement.setString(7, updateUrl)
                statement.setLong(8, actorId)
                statement.executeUpdate()
            }
            connection.writeAudit(actorId, "CLIENT_POLICY_UPDATE", "CLIENT_POLICY", 1)
        }
        return adminPolicy(actorId)
    }

    fun updateLimits(actorId: Long, request: ClientPolicyLimitsUpdateRequest): ClientPolicyResponse {
        require(request.vehicleResultLimit in 0..50) { "匹配车辆数量必须在0至50之间" }
        require(request.workOrderResultLimit in 0..50) { "微信车单数量必须在0至50之间" }
        require(request.wechatMessageResultLimit in 0..50) { "微信聊天数量必须在0至50之间" }
        dataSource.connection.use { connection ->
            check(connection.isPrimaryAdministrator(actorId))
            connection.prepareStatement(
                """
                UPDATE client_runtime_policy SET revision = revision + 1,
                    vehicle_result_limit = ?, work_order_result_limit = ?, wechat_message_result_limit = ?,
                    updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = 1
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, request.vehicleResultLimit)
                statement.setInt(2, request.workOrderResultLimit)
                statement.setInt(3, request.wechatMessageResultLimit)
                statement.setLong(4, actorId)
                statement.executeUpdate()
            }
            connection.writeAudit(actorId, "CLIENT_POLICY_LIMITS_UPDATE", "CLIENT_POLICY", 1)
        }
        return adminPolicy(actorId)
    }

    fun updateApiEndpoint(actorId: Long, request: ClientEndpointUpdateRequest): ClientPolicyResponse {
        val apiUrl = normalizeBaseUrl(request.baseUrl)
        dataSource.connection.use { connection ->
            check(connection.isPrimaryAdministrator(actorId))
            val current = connection.readPolicy()
            if (current.apiBaseUrl != apiUrl) testApiEndpoint(apiUrl)
            connection.prepareStatement(
                """
                UPDATE client_runtime_policy SET revision = revision + 1,
                    previous_api_base_url = CASE WHEN active_api_base_url <> ? THEN active_api_base_url ELSE previous_api_base_url END,
                    active_api_base_url = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, apiUrl)
                statement.setString(2, apiUrl)
                statement.setLong(3, actorId)
                statement.executeUpdate()
            }
            connection.writeAudit(actorId, "CLIENT_POLICY_API_ENDPOINT_UPDATE", "CLIENT_POLICY", 1)
        }
        return adminPolicy(actorId)
    }

    fun updateUpdateEndpoint(actorId: Long, request: ClientEndpointUpdateRequest): ClientPolicyResponse {
        val updateUrl = normalizeBaseUrl(request.baseUrl)
        dataSource.connection.use { connection ->
            check(connection.isPrimaryAdministrator(actorId))
            val current = connection.readPolicy()
            if (current.updateBaseUrl != updateUrl) testUpdateEndpoint(updateUrl)
            connection.prepareStatement(
                """
                UPDATE client_runtime_policy SET revision = revision + 1,
                    previous_update_base_url = CASE WHEN active_update_base_url <> ? THEN active_update_base_url ELSE previous_update_base_url END,
                    active_update_base_url = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, updateUrl)
                statement.setString(2, updateUrl)
                statement.setLong(3, actorId)
                statement.executeUpdate()
            }
            connection.writeAudit(actorId, "CLIENT_POLICY_UPDATE_ENDPOINT_UPDATE", "CLIENT_POLICY", 1)
        }
        return adminPolicy(actorId)
    }

    fun requestCacheReset(actorId: Long, userId: Long): CacheResetStatusResponse = dataSource.connection.use { connection ->
        check(connection.isPrimaryAdministrator(actorId))
        val revision = connection.prepareStatement(
            """
            UPDATE users SET cache_reset_revision = cache_reset_revision + 1,
                cache_reset_requested_at = CURRENT_TIMESTAMP, cache_reset_requested_by = ?
            WHERE id = ? RETURNING cache_reset_revision
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, actorId)
            statement.setLong(2, userId)
            statement.executeQuery().use { result ->
                if (!result.next()) throw IllegalArgumentException("用户不存在")
                result.getLong(1)
            }
        }
        connection.writeAudit(actorId, "CLIENT_CACHE_RESET_REQUEST", "USER", userId)
        connection.cacheResetStatus(userId, revision)
    }

    fun acknowledge(userId: Long, request: ClientPolicyAckRequest) {
        require(request.clientInstanceId.matches(Regex("[A-Za-z0-9_-]{8,64}"))) { "客户端实例编号无效" }
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO client_instance_state(user_id, client_instance_id, applied_cache_reset_revision,
                    applied_policy_revision, last_api_base_url, last_update_base_url, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(user_id, client_instance_id) DO UPDATE SET
                    applied_cache_reset_revision = GREATEST(client_instance_state.applied_cache_reset_revision, EXCLUDED.applied_cache_reset_revision),
                    applied_policy_revision = GREATEST(client_instance_state.applied_policy_revision, EXCLUDED.applied_policy_revision),
                    last_api_base_url = EXCLUDED.last_api_base_url,
                    last_update_base_url = EXCLUDED.last_update_base_url,
                    last_seen_at = CURRENT_TIMESTAMP
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, userId)
                statement.setString(2, request.clientInstanceId)
                statement.setLong(3, request.appliedCacheResetRevision)
                statement.setLong(4, request.appliedPolicyRevision)
                statement.setString(5, request.apiBaseUrl)
                statement.setString(6, request.updateBaseUrl)
                statement.executeUpdate()
            }
        }
    }

    fun testApiEndpoint(value: String): EndpointTestResponse {
        val baseUrl = normalizeBaseUrl(value)
        val response = httpGet(URI.create(baseUrl).resolve("health"))
        require(response.statusCode() in 200..299) { "新后台健康检查失败：HTTP ${response.statusCode()}" }
        return EndpointTestResponse(true, "后台服务连接正常")
    }

    fun testUpdateEndpoint(value: String): EndpointTestResponse {
        val baseUrl = normalizeBaseUrl(value)
        val manifest = httpGet(URI.create(baseUrl).resolve("latest.json"))
        require(manifest.statusCode() in 200..299) { "更新清单加载失败：HTTP ${manifest.statusCode()}" }
        val json = Json.parseToJsonElement(manifest.body()).jsonObject
        require(json["versionName"]?.jsonPrimitive?.content?.isNotBlank() == true) { "更新清单缺少版本号" }
        val legacyArtifact = json["apkPath"]?.jsonPrimitive?.content?.let { path ->
            mapOf("apkPath" to path,
                "sha256" to json["sha256"]?.jsonPrimitive?.content.orEmpty(),
                "sizeBytes" to json["sizeBytes"]?.jsonPrimitive?.content.orEmpty())
        }
        val artifacts = json["artifacts"]?.jsonObject?.mapValues { (_, value) ->
            val item = value.jsonObject
            mapOf(
                "apkPath" to item["apkPath"]?.jsonPrimitive?.content.orEmpty(),
                "sha256" to item["sha256"]?.jsonPrimitive?.content.orEmpty(),
                "sizeBytes" to item["sizeBytes"]?.jsonPrimitive?.content.orEmpty(),
            )
        }.orEmpty().ifEmpty { mapOf("universal" to checkNotNull(legacyArtifact) { "更新清单缺少APK路径" }) }
        listOf("arm64-v8a", "armeabi-v7a", "universal").forEach { architecture ->
            val artifact = artifacts[architecture] ?: throw IllegalArgumentException("更新清单缺少$architecture APK")
            validateUpdateArtifact(baseUrl, artifact)
        }
        return EndpointTestResponse(true, "更新服务连接正常")
    }

    private fun validateUpdateArtifact(baseUrl: String, artifact: Map<String, String>) {
        val apkPath = artifact["apkPath"].orEmpty()
        require(apkPath.isNotBlank() && !URI.create(apkPath).isAbsolute) { "更新清单必须提供相对APK路径" }
        val expectedSha256 = artifact["sha256"].orEmpty().lowercase()
        require(expectedSha256.matches(Regex("[a-f0-9]{64}"))) { "更新清单摘要无效" }
        val expectedSize = artifact["sizeBytes"]?.toLongOrNull()
        require(expectedSize != null && expectedSize > 0) { "更新清单文件大小无效" }
        val apkUri = URI.create(baseUrl).resolve(apkPath)
        val range = httpRange(apkUri)
        require(range.statusCode() in listOf(200, 206) && range.body().isNotEmpty()) { "APK文件不可下载" }
        require(!range.headers().firstValue("Content-Type").orElse("").contains("text/html", ignoreCase = true)) { "APK地址返回了网页内容" }
        val apk = httpStream(apkUri)
        require(apk.statusCode() in 200..299) { "APK文件不可下载" }
        val digest = MessageDigest.getInstance("SHA-256")
        var actualSize = 0L
        apk.body().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                actualSize += count
            }
        }
        require(actualSize == expectedSize) { "APK文件大小与清单不一致" }
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
        require(actualSha256 == expectedSha256) { "APK文件摘要与清单不一致" }
    }

    private fun httpGet(uri: URI): HttpResponse<String> = HTTP_CLIENT.send(
        HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun httpRange(uri: URI): HttpResponse<ByteArray> = HTTP_CLIENT.send(
        HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Range", "bytes=0-1023").GET().build(),
        HttpResponse.BodyHandlers.ofByteArray(),
    )

    private fun httpStream(uri: URI): HttpResponse<java.io.InputStream> = HTTP_CLIENT.send(
        HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build(),
        HttpResponse.BodyHandlers.ofInputStream(),
    )

    private fun Connection.readPolicy(): ClientPolicyRecord = prepareStatement(
        """
        SELECT revision, vehicle_result_limit, work_order_result_limit, wechat_message_result_limit,
               active_api_base_url, previous_api_base_url, active_update_base_url, previous_update_base_url,
               updated_at::text
        FROM client_runtime_policy WHERE id = 1
        """.trimIndent(),
    ).use { statement ->
        statement.executeQuery().use { result ->
            check(result.next()) { "客户端策略尚未初始化" }
            ClientPolicyRecord(
                result.getLong(1), result.getInt(2), result.getInt(3), result.getInt(4), result.getString(5),
                result.getString(6), result.getString(7), result.getString(8), result.getString(9),
            )
        }
    }

    private fun normalizeBaseUrl(value: String): String {
        val normalized = value.trim().let { if (it.endsWith('/')) it else "$it/" }
        val uri = URI.create(normalized)
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_HOSTS)) { "服务地址必须使用HTTPS" }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "服务地址格式无效" }
        return normalized
    }

    private fun Connection.cacheResetStatus(userId: Long, revision: Long): CacheResetStatusResponse = prepareStatement(
        """
        SELECT COUNT(*), COUNT(*) FILTER (WHERE applied_cache_reset_revision >= ?), MAX(last_seen_at)::text
        FROM client_instance_state WHERE user_id = ?
        """.trimIndent(),
    ).use { statement ->
        statement.setLong(1, revision)
        statement.setLong(2, userId)
        statement.executeQuery().use { result ->
            result.next()
            val clients = result.getInt(1)
            val completed = result.getInt(2)
            CacheResetStatusResponse(
                userId = userId,
                revision = revision,
                expectedClientCount = clients,
                completedClientCount = completed,
                status = when {
                    clients == 0 -> "WAITING"
                    completed == 0 -> "SENT"
                    completed < clients -> "PARTIAL"
                    else -> "COMPLETED"
                },
                lastConfirmedAt = result.getString(3),
            )
        }
    }

    private fun Connection.writeAudit(actorId: Long, action: String, targetType: String, targetId: Long) {
        prepareStatement(
            "INSERT INTO audit_logs(actor_id, action_type, target_type, target_id, result_status) VALUES (?, ?, ?, ?, 'SUCCESS')",
        ).use { statement ->
            statement.setLong(1, actorId)
            statement.setString(2, action)
            statement.setString(3, targetType)
            statement.setLong(4, targetId)
            statement.executeUpdate()
        }
    }

    private companion object {
        val HTTP_CLIENT: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build()
        val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2")
    }
}

private data class ClientPolicyRecord(
    val revision: Long,
    val vehicleLimit: Int,
    val workOrderLimit: Int,
    val wechatMessageLimit: Int,
    val apiBaseUrl: String,
    val previousApiBaseUrl: String?,
    val updateBaseUrl: String,
    val previousUpdateBaseUrl: String?,
    val updatedAt: String,
) {
    fun toRuntime(primaryAdmin: Boolean, cacheResetRevision: Long) = ClientRuntimePolicyResponse(
        revision,
        effectiveLimit(vehicleLimit, primaryAdmin),
        effectiveLimit(workOrderLimit, primaryAdmin),
        effectiveLimit(wechatMessageLimit, primaryAdmin),
        apiBaseUrl,
        updateBaseUrl,
        cacheResetRevision,
    )

    fun toResponse(
        clientCount: Int,
        appliedClientCount: Int,
        lastConfirmedAt: String?,
        cacheResetStatuses: List<CacheResetStatusResponse>,
    ) = ClientPolicyResponse(
        revision, vehicleLimit, workOrderLimit, wechatMessageLimit, apiBaseUrl, previousApiBaseUrl,
        updateBaseUrl, previousUpdateBaseUrl, updatedAt, clientCount, appliedClientCount, lastConfirmedAt, cacheResetStatuses,
    )
}

internal fun effectiveLimit(configured: Int, primaryAdmin: Boolean): Int = if (primaryAdmin && configured == 0) 8 else configured

internal data class ClientResultLimits(
    val vehicle: Int,
    val workOrder: Int,
    val wechatMessage: Int,
)

@Serializable data class ClientRuntimePolicyResponse(
    val policyRevision: Long,
    val vehicleResultLimit: Int,
    val workOrderResultLimit: Int,
    val wechatMessageResultLimit: Int,
    val apiBaseUrl: String,
    val updateBaseUrl: String,
    val cacheResetRevision: Long,
)

@Serializable data class ClientPolicyLimitsUpdateRequest(
    val vehicleResultLimit: Int,
    val workOrderResultLimit: Int,
    val wechatMessageResultLimit: Int,
)

@Serializable data class ClientEndpointUpdateRequest(val baseUrl: String)

@Serializable internal data class ClientPolicyResponse(
    val revision: Long,
    val vehicleResultLimit: Int,
    val workOrderResultLimit: Int,
    val wechatMessageResultLimit: Int,
    val apiBaseUrl: String,
    val previousApiBaseUrl: String?,
    val updateBaseUrl: String,
    val previousUpdateBaseUrl: String?,
    val updatedAt: String,
    val clientCount: Int,
    val appliedClientCount: Int,
    val lastConfirmedAt: String?,
    val cacheResetStatuses: List<CacheResetStatusResponse>,
)

@Serializable internal data class ClientPolicyUpdateRequest(
    val vehicleResultLimit: Int,
    val workOrderResultLimit: Int,
    val wechatMessageResultLimit: Int,
    val apiBaseUrl: String,
    val updateBaseUrl: String,
)

@Serializable private data class EndpointTestRequest(val baseUrl: String)
@Serializable internal data class EndpointTestResponse(val success: Boolean, val message: String)
@Serializable internal data class CacheResetStatusResponse(
    val userId: Long,
    val revision: Long,
    val expectedClientCount: Int,
    val completedClientCount: Int,
    val status: String,
    val lastConfirmedAt: String?,
)
@Serializable data class ClientPolicyAckRequest(
    val clientInstanceId: String,
    val appliedCacheResetRevision: Long,
    val appliedPolicyRevision: Long,
    val apiBaseUrl: String,
    val updateBaseUrl: String,
)
