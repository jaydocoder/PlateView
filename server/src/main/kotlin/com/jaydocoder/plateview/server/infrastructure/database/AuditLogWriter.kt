package com.jaydocoder.plateview.server.infrastructure.database

import java.sql.PreparedStatement
import javax.sql.DataSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal data class AuditEvent(
    val actorId: Long?,
    val actionType: String,
    val targetType: String,
    val targetId: Long?,
    val resultStatus: String,
    val requestId: String?,
    val metadata: JsonObject,
)

internal interface AuditLogWriter {
    fun write(event: AuditEvent)
}

internal class JdbcAuditLogWriter(
    private val dataSource: DataSource,
) : AuditLogWriter {
    override fun write(event: AuditEvent) {
        validate(event)

        dataSource.connection.use { connection ->
            connection.prepareStatement(INSERT_AUDIT_LOG).use { statement ->
                statement.setNullableLong(1, event.actorId)
                statement.setString(2, event.actionType)
                statement.setString(3, event.targetType)
                statement.setNullableLong(4, event.targetId)
                statement.setString(5, event.resultStatus)
                statement.setString(6, event.requestId)
                statement.setString(7, Json.encodeToString(event.metadata))
                statement.executeUpdate()
            }
        }
    }

    private fun validate(event: AuditEvent) {
        require(event.actionType.isNotBlank()) { "审计操作类型不能为空" }
        require(event.actionType.length <= 64) { "审计操作类型超出长度限制" }
        require(event.targetType.isNotBlank()) { "审计目标类型不能为空" }
        require(event.targetType.length <= 64) { "审计目标类型超出长度限制" }
        require(event.resultStatus in RESULT_STATUSES) { "审计结果状态无效" }
        require(event.requestId == null || event.requestId.length <= 64) { "请求标识超出长度限制" }
    }

    private fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) {
            setObject(index, null)
        } else {
            setLong(index, value)
        }
    }

    private companion object {
        val RESULT_STATUSES = setOf("SUCCESS", "FAILURE", "DENIED")

        const val INSERT_AUDIT_LOG = """
            INSERT INTO audit_logs (
                actor_id,
                action_type,
                target_type,
                target_id,
                result_status,
                request_id,
                metadata
            ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
        """
    }
}
