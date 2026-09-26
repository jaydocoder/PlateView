package com.jaydocoder.plateview.server.workorder

import java.sql.Connection
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.io.File
import java.security.MessageDigest
import javax.sql.DataSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray

internal class WechatRebuildLockedException(message: String) : IllegalStateException(message)

internal class WechatRebuildService(private val dataSource: DataSource) {
    fun listBackups(): List<WechatBackupResponse> {
        val root = File(System.getenv("PLATEVIEW_BACKUP_DIR") ?: "./backups").canonicalFile
        return root.listFiles { file -> file.isFile && file.extension == "dump" }
            ?.sortedByDescending { it.lastModified() }
            ?.take(3)
            ?.map { file ->
                val digest = MessageDigest.getInstance("SHA-256").digest(file.inputStream().use { it.readBytes() })
                    .joinToString("") { "%02x".format(it) }
                WechatBackupResponse(
                    id = file.name,
                    createdAt = Instant.ofEpochMilli(file.lastModified()).toString(),
                    sizeBytes = file.length(),
                    sha256 = digest,
                    verified = runCatching {
                        val sidecar = File("${file.absolutePath}.sha256")
                        val sidecarMatches = sidecar.takeIf { it.isFile }?.readText()?.trim()?.split(Regex("\\s+"))?.firstOrNull()
                            ?.equals(digest, ignoreCase = true) == true
                        sidecarMatches || (ProcessBuilder("pg_restore", "--list", file.absolutePath).redirectErrorStream(true).start().waitFor() == 0)
                    }.getOrDefault(false),
                )
            }
            ?: emptyList()
    }

    fun requestBackupRestore(actorId: Long, backupId: String): WechatBackupRestoreResponse {
        val root = File(System.getenv("PLATEVIEW_BACKUP_DIR") ?: "./backups").canonicalFile
        require(backupId.matches(Regex("[A-Za-z0-9._-]+\\.dump"))) { "备份标识无效" }
        val file = File(root, backupId).canonicalFile
        require(file.parentFile == root && file.isFile) { "备份文件不存在或不在允许目录" }
        val backup = listBackups().firstOrNull { it.id == backupId } ?: throw IllegalArgumentException("只允许恢复最近三个备份")
        require(backup.verified) { "备份尚未通过归档校验" }
        expireStaleRuns()
        val active = current()
        require(active == null) { "当前存在微信重构批次，请先完成或取消后再恢复" }
        val runId = UUID.randomUUID()
        transaction { connection ->
            connection.prepareStatement(
                "INSERT INTO wechat_rebuild_runs(run_id, actor_id, status, expires_at, backup_path, backup_sha256, preview_report) VALUES (?, ?, 'LOCKED', CURRENT_TIMESTAMP + INTERVAL '60 minutes', ?, ?, ?::jsonb)",
            ).use { statement ->
                statement.setObject(1, runId)
                statement.setLong(2, actorId)
                statement.setString(3, file.absolutePath)
                statement.setString(4, backup.sha256)
                statement.setString(5, "{\"restore\":1}")
                statement.executeUpdate()
            }
        }
        val script = System.getenv("PLATEVIEW_RESTORE_SCRIPT")?.trim().orEmpty()
        if (script.isBlank()) {
            transaction { connection -> connection.prepareStatement("UPDATE wechat_rebuild_runs SET status = 'FAILED', last_error = ? WHERE run_id = ?").use { it.setString(1, "服务端未配置数据库恢复脚本"); it.setObject(2, runId); it.executeUpdate() } }
            throw IllegalArgumentException("服务端未配置数据库恢复脚本，已拒绝执行恢复")
        }
        return try {
            val process = ProcessBuilder(script, file.absolutePath, backup.sha256).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            require(exitCode == 0) { "数据库恢复脚本失败：${output.take(500)}" }
            transaction { connection ->
                connection.prepareStatement("UPDATE wechat_rebuild_runs SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP, last_error = NULL WHERE status IN ('BACKUP_VERIFYING', 'LOCKED', 'CLEANING', 'REBUILDING', 'VERIFYING')").use { it.executeUpdate() }
            }
            WechatBackupRestoreResponse(backupId, true, "恢复脚本已完成，客户端需要重新确认微信目录")
        } catch (error: Throwable) {
            runCatching { transaction { connection -> connection.prepareStatement("UPDATE wechat_rebuild_runs SET status = 'FAILED', last_error = ? WHERE run_id = ?").use { it.setString(1, error.message?.take(500)); it.setObject(2, runId); it.executeUpdate() } } }
            throw error
        }
    }
    fun assertUploadAllowed() {
        dataSource.connection.use { connection ->
            val active = connection.prepareStatement(
                "SELECT run_id, status FROM wechat_rebuild_runs WHERE status IN ('BACKUP_VERIFYING','LOCKED','CLEANING','REBUILDING','VERIFYING') AND expires_at > CURRENT_TIMESTAMP LIMIT 1",
            ).use { statement ->
                statement.executeQuery().use { result ->
                    if (!result.next()) null else result.getString("run_id") to result.getString("status")
                }
            }
            active?.let { (runId, status) ->
                throw WechatRebuildLockedException("微信数据正在重建（批次$runId，状态$status），暂时拒绝上传")
            }
        }
    }

    fun preview(actorId: Long, expiresAt: Instant): WechatRebuildRunResponse = transaction { connection ->
        expireStaleRuns(connection)
        val active = connection.prepareStatement(
            "SELECT run_id FROM wechat_rebuild_runs WHERE status IN ('BACKUP_VERIFYING','LOCKED','CLEANING','REBUILDING','VERIFYING') AND expires_at > CURRENT_TIMESTAMP ORDER BY created_at DESC LIMIT 1",
        ).use { statement -> statement.executeQuery().use { result -> if (result.next()) result.getObject("run_id", UUID::class.java) else null } }
        if (active != null) return@transaction readRun(connection, active)!!
        val runId = UUID.randomUUID()
        val report = connection.countPreview()
        connection.prepareStatement(
            "INSERT INTO wechat_rebuild_runs(run_id, actor_id, expires_at, preview_report) VALUES (?, ?, ?, ?::jsonb)",
        ).use { statement ->
            statement.setObject(1, runId)
            statement.setLong(2, actorId)
            statement.setObject(3, java.sql.Timestamp.from(expiresAt))
            statement.setString(4, report.toString())
            statement.executeUpdate()
        }
        readRun(connection, runId)!!
    }

    fun lock(actorId: Long, runId: UUID, confirmation: String): WechatRebuildRunResponse = transaction { connection ->
        require(confirmation == runId.toString()) { "确认标识必须等于重建批次编号" }
        val current = readRunForUpdate(connection, runId) ?: throw IllegalArgumentException("重建批次不存在")
        require(current.actorId == actorId) { "只能操作自己创建的重建批次" }
        require(current.status == "PREVIEW") { "只有预览状态的批次可以加锁" }
        require(OffsetDateTime.parse(current.expiresAt).toInstant().isAfter(Instant.now())) { "重建批次预览已过期" }
        connection.prepareStatement(
            "UPDATE wechat_rebuild_runs SET status = 'BACKUP_VERIFYING', locked_at = CURRENT_TIMESTAMP WHERE run_id = ?",
        ).use { statement ->
            statement.setObject(1, runId)
            statement.executeUpdate()
        }
        readRun(connection, runId)!!
    }

    fun backupVerify(actorId: Long, runId: UUID, backupPath: String?, expectedSha256: String?): WechatRebuildRunResponse = transaction { connection ->
        val current = readRunForUpdate(connection, runId) ?: throw IllegalArgumentException("重建批次不存在")
        require(current.actorId == actorId) { "只能操作自己创建的重建批次" }
        require(current.status == "PREVIEW" || current.status == "BACKUP_VERIFYING") { "当前批次不在备份验证阶段" }
        val configured = backupPath?.trim().orEmpty()
        val automatic = System.getenv("WECHAT_REBUILD_BACKUP_PATH")?.trim().orEmpty()
        val backupDirectory = File(System.getenv("PLATEVIEW_BACKUP_DIR") ?: "./backups").canonicalFile
        val latest = backupDirectory.listFiles { file -> file.isFile && (file.extension == "dump" || file.name.endsWith(".sql")) }
            ?.maxByOrNull { it.lastModified() }?.absolutePath.orEmpty()
        val file = File(configured.ifBlank { automatic.ifBlank { latest } }).canonicalFile
        require(file.isFile) { "备份文件不存在" }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.inputStream().use { it.readBytes() }).joinToString("") { "%02x".format(it) }
        require(expectedSha256.isNullOrBlank() || expectedSha256.equals(digest, ignoreCase = true)) { "备份校验值不匹配" }
        connection.prepareStatement("UPDATE wechat_rebuild_runs SET status = 'LOCKED', backup_path = ?, backup_sha256 = ?, locked_at = CURRENT_TIMESTAMP WHERE run_id = ?").use { statement ->
            statement.setString(1, file.absolutePath)
            statement.setString(2, digest)
            statement.setObject(3, runId)
            statement.executeUpdate()
        }
        readRun(connection, runId)!!
    }


    fun clean(actorId: Long, runId: UUID, confirmation: String): WechatRebuildRunResponse = transaction { connection ->
        require(confirmation == runId.toString()) { "确认标识必须等于重建批次编号" }
        val current = readRunForUpdate(connection, runId) ?: throw IllegalArgumentException("重建批次不存在")
        require(current.actorId == actorId) { "只能操作自己创建的重建批次" }
        if (current.status == "REBUILDING" || current.status == "VERIFYING" || current.status == "COMPLETED") {
            return@transaction current
        }
        require(current.status == "LOCKED") { "重建批次必须先完成备份验证" }
        require(!current.backupSha256.isNullOrBlank()) { "必须先完成备份验证" }
        require(OffsetDateTime.parse(current.expiresAt).toInstant().isAfter(Instant.now())) { "重建维护锁已过期" }
        connection.prepareStatement("UPDATE wechat_rebuild_runs SET status = 'CLEANING' WHERE run_id = ?").use { it.setObject(1, runId); it.executeUpdate() }
        val fileReport = connection.attachmentFileReport()
        val deleted = connection.cleanWechatData()
        connection.prepareStatement(
            "UPDATE wechat_rebuild_runs SET status = 'REBUILDING', deleted_counts = ?::jsonb, file_report = ?::jsonb WHERE run_id = ?",
        ).use { statement ->
            statement.setString(1, deleted.toString())
            statement.setString(2, fileReport.toString())
            statement.setObject(3, runId)
            statement.executeUpdate()
        }
        readRun(connection, runId)!!
    }

    fun cleanupAttachmentFiles(actorId: Long, runId: UUID): WechatRebuildRunResponse = transaction { connection ->
        val current = readRunForUpdate(connection, runId) ?: throw IllegalArgumentException("重建批次不存在")
        require(current.actorId == actorId) { "只能操作自己创建的重建批次" }
        require(current.status == "REBUILDING" || current.status == "FAILED") { "当前批次不在附件清理阶段" }
        val paths = current.fileReport["paths"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
        val root = File(System.getenv("WORK_ORDER_IMAGE_DIR") ?: "./data/work-order-images").canonicalFile
        var deleted = 0
        var missing = 0
        var rejected = 0
        val remaining = mutableListOf<JsonPrimitive>()
        paths.forEach { raw ->
            val file = runCatching {
                val candidate = File(raw)
                if (candidate.isAbsolute) candidate.canonicalFile else File(root, raw).canonicalFile
            }.getOrNull()
            if (file == null || !file.path.startsWith(root.path + File.separator)) { rejected++; remaining += JsonPrimitive(raw); return@forEach }
            if (!file.exists()) { missing++; return@forEach }
            if (file.delete()) deleted++ else { rejected++; remaining += JsonPrimitive(raw) }
        }
        val report = buildJsonObject {
            put("paths", JsonArray(remaining))
            put("listed", JsonPrimitive(paths.size))
            put("deleted", JsonPrimitive(deleted))
            put("missing", JsonPrimitive(missing))
            put("rejected", JsonPrimitive(rejected))
        }
        connection.prepareStatement("UPDATE wechat_rebuild_runs SET file_report = ?::jsonb, status = ? WHERE run_id = ?").use { statement ->
            statement.setString(1, report.toString())
            statement.setString(2, if (rejected == 0) "REBUILDING" else "FAILED")
            statement.setObject(3, runId)
            statement.executeUpdate()
        }
        readRun(connection, runId)!!
    }

    fun verify(actorId: Long, runId: UUID): WechatRebuildRunResponse = transaction { connection ->
        val current = readRunForUpdate(connection, runId) ?: throw IllegalArgumentException("重建批次不存在")
        require(current.actorId == actorId) { "只能操作自己创建的重建批次" }
        val retryableFalsePositive = current.status == "FAILED" && current.lastError == "微信业务表仍有残留数据"
        require(current.status == "REBUILDING" || current.status == "VERIFYING" || retryableFalsePositive) { "当前批次不在重建阶段" }
        // 清理事务在进入 REBUILDING 前已经完成。之后采集器可以立即写入新数据，
        // 因此不能再用“业务表必须为空”判断清理是否成功，否则正常重传会被误报为残留。
        connection.prepareStatement(
            "UPDATE wechat_rebuild_runs SET status = 'VERIFYING', last_error = NULL WHERE run_id = ?",
        ).use { statement ->
            statement.setObject(1, runId)
            statement.executeUpdate()
        }
        readRun(connection, runId)!!
    }

    fun unlock(actorId: Long, runId: UUID, success: Boolean): WechatRebuildRunResponse = transaction { connection ->
        val current = readRunForUpdate(connection, runId) ?: throw IllegalArgumentException("重建批次不存在")
        require(current.actorId == actorId) { "只能操作自己创建的重建批次" }
        require(current.status != "COMPLETED" && current.status != "CANCELLED") { "重建批次已经结束" }
        connection.prepareStatement(
            "UPDATE wechat_rebuild_runs SET status = ?, completed_at = CURRENT_TIMESTAMP WHERE run_id = ?",
        ).use { statement ->
            statement.setString(1, if (success) "COMPLETED" else "CANCELLED")
            statement.setObject(2, runId)
            statement.executeUpdate()
        }
        readRun(connection, runId)!!
    }

    fun current(): WechatRebuildRunResponse? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT run_id FROM wechat_rebuild_runs WHERE status IN ('BACKUP_VERIFYING','LOCKED','CLEANING','REBUILDING','VERIFYING') AND expires_at > CURRENT_TIMESTAMP ORDER BY created_at DESC LIMIT 1",
        ).use { statement ->
            statement.executeQuery().use { result ->
                if (!result.next()) null else readRun(connection, result.getObject("run_id", UUID::class.java))
            }
        }
    }

    fun collectorStatus(): WechatCollectorRebuildStatus = dataSource.connection.use { connection ->
        val generation = connection.prepareStatement("SELECT rebuild_generation FROM client_catalog_state WHERE id = 1").use { statement ->
            statement.executeQuery().use { result -> if (result.next()) result.getLong(1) else 0L }
        }
        val active = connection.prepareStatement("SELECT status FROM wechat_rebuild_runs WHERE status IN ('BACKUP_VERIFYING','LOCKED','CLEANING','REBUILDING','VERIFYING') AND expires_at > CURRENT_TIMESTAMP ORDER BY created_at DESC LIMIT 1").use { statement ->
            statement.executeQuery().use { result -> if (result.next()) result.getString(1) else null }
        }
        WechatCollectorRebuildStatus(generation, active)
    }

    private fun Connection.countPreview(): JsonObject = buildJsonObject {
        COUNT_TABLES.forEach { table ->
            prepareStatement("SELECT COUNT(*) FROM $table").use { statement ->
                statement.executeQuery().use { result ->
                    result.next()
                    put(table, JsonPrimitive(result.getLong(1)))
                }
            }
        }
    }

    private fun Connection.cleanWechatData(): JsonObject = buildJsonObject {
        // 批量销毁微信业务数据时跳过归并触发器，避免删除命令与替代主记录触发器互相修改同一行。
        prepareStatement("ALTER TABLE work_order_records DISABLE TRIGGER USER").use { it.executeUpdate() }
        prepareStatement("ALTER TABLE wechat_messages DISABLE TRIGGER USER").use { it.executeUpdate() }
        val statements = listOf(
            "client_attachment_cache_status",
            "client_attachment_cache_summary",
            "work_order_people",
            "work_order_vehicles",
            "work_order_images",
            "wechat_attachment_upload_tasks",
            "wechat_sync_watermarks",
            "wechat_sync_batches",
            "wechat_sync_runs",
            "work_order_records",
            "wechat_messages",
            "work_order_catalog_changes",
            "wechat_message_catalog_changes",
        )
        statements.forEach { table ->
            val deleted = prepareStatement("DELETE FROM $table").use { it.executeUpdate().toLong() }
            put(table, JsonPrimitive(deleted))
        }
        prepareStatement("ALTER TABLE work_order_records ENABLE TRIGGER USER").use { it.executeUpdate() }
        prepareStatement("ALTER TABLE wechat_messages ENABLE TRIGGER USER").use { it.executeUpdate() }
        prepareStatement("UPDATE wechat_sources SET latest_message_at = NULL, last_uploaded_at = NULL, collector_status = 'OFFLINE', backlog_count = 0, error_code = NULL").use { it.executeUpdate() }
        prepareStatement("UPDATE work_order_catalog_state SET revision = revision + 1 WHERE id = 1").use { it.executeUpdate() }
        prepareStatement("UPDATE work_order_attachment_manifest_state SET revision = revision + 1 WHERE id = 1").use { it.executeUpdate() }
        prepareStatement("UPDATE client_catalog_state SET rebuild_generation = rebuild_generation + 1 WHERE id = 1").use { it.executeUpdate() }
    }

    private fun Connection.attachmentFileReport(): JsonObject = buildJsonObject {
        val paths = mutableListOf<JsonPrimitive>()
        prepareStatement("SELECT original_path, preview_path, thumbnail_path FROM work_order_images").use { statement ->
            statement.executeQuery().use { result ->
                while (result.next()) listOf(1, 2, 3).mapNotNull { index -> result.getString(index) }.forEach { paths += JsonPrimitive(it) }
            }
        }
        put("paths", JsonArray(paths))
        put("listed", JsonPrimitive(paths.size))
    }

    private fun readRun(connection: Connection, runId: UUID): WechatRebuildRunResponse? = connection.prepareStatement(
        "SELECT run_id, actor_id, status, created_at, locked_at, expires_at, completed_at, backup_path, backup_sha256, preview_report, deleted_counts, file_report, last_error FROM wechat_rebuild_runs WHERE run_id = ?",
    ).use { statement ->
        statement.setObject(1, runId)
        statement.executeQuery().use { result -> if (!result.next()) null else result.toResponse() }
    }

    private fun readRunForUpdate(connection: Connection, runId: UUID): WechatRebuildRunResponse? {
        connection.prepareStatement("SELECT run_id FROM wechat_rebuild_runs WHERE run_id = ? FOR UPDATE").use { statement ->
            statement.setObject(1, runId)
            statement.executeQuery().use { if (!it.next()) return null }
        }
        return readRun(connection, runId)
    }

    private fun java.sql.ResultSet.toResponse(): WechatRebuildRunResponse = WechatRebuildRunResponse(
        runId = getObject("run_id", UUID::class.java).toString(),
        actorId = getLong("actor_id"),
        status = getString("status"),
        createdAt = getTimestamp("created_at").toInstant().toString(),
        lockedAt = getTimestamp("locked_at")?.toInstant()?.toString(),
        expiresAt = getTimestamp("expires_at").toInstant().toString(),
        completedAt = getTimestamp("completed_at")?.toInstant()?.toString(),
        backupPath = getString("backup_path"),
        backupSha256 = getString("backup_sha256"),
        previewReport = Json.parseToJsonElement(getString("preview_report")).jsonObject,
        deletedCounts = Json.parseToJsonElement(getString("deleted_counts")).jsonObject,
        fileReport = Json.parseToJsonElement(getString("file_report")).jsonObject,
        lastError = getString("last_error"),
    )

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (error: Throwable) {
            runCatching { connection.rollback() }
            throw error
        }
    }

    private fun expireStaleRuns() = transaction { connection -> expireStaleRuns(connection) }

    private fun expireStaleRuns(connection: Connection) {
        connection.prepareStatement(
            "UPDATE wechat_rebuild_runs SET status = 'FAILED', last_error = '维护批次已过期，已自动释放活动锁' WHERE status IN ('BACKUP_VERIFYING','LOCKED','CLEANING','REBUILDING','VERIFYING') AND expires_at <= CURRENT_TIMESTAMP",
        ).use { it.executeUpdate() }
    }

    companion object {
        private val COUNT_TABLES = listOf(
            "wechat_messages",
            "work_order_records",
            "work_order_people",
            "work_order_vehicles",
            "work_order_images",
            "wechat_attachment_upload_tasks",
            "wechat_sync_batches",
            "wechat_sync_runs",
            "wechat_sync_watermarks",
            "work_order_catalog_changes",
            "wechat_message_catalog_changes",
            "client_attachment_cache_status",
            "client_attachment_cache_summary",
        )
    }
}

@Serializable
internal data class WechatRebuildRunResponse(
    val runId: String,
    val actorId: Long,
    val status: String,
    val createdAt: String,
    val lockedAt: String? = null,
    val expiresAt: String,
    val completedAt: String? = null,
    val backupPath: String? = null,
    val backupSha256: String? = null,
    val previewReport: JsonObject = buildJsonObject { },
    val deletedCounts: JsonObject = buildJsonObject { },
    val fileReport: JsonObject = buildJsonObject { },
    val lastError: String? = null,
)

@Serializable
internal data class WechatCollectorRebuildStatus(val rebuildGeneration: Long, val rebuildState: String? = null)

@Serializable
internal data class WechatBackupResponse(
    val id: String,
    val createdAt: String,
    val sizeBytes: Long,
    val sha256: String,
    val verified: Boolean,
)

@Serializable
internal data class WechatBackupRestoreResponse(
    val backupId: String,
    val accepted: Boolean,
    val message: String,
)
