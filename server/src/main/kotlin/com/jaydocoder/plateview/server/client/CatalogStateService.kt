package com.jaydocoder.plateview.server.client

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import io.ktor.server.application.Application
import io.ktor.util.AttributeKey
import com.jaydocoder.plateview.server.infrastructure.database.DataSourceKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

internal val CatalogStateServiceKey = AttributeKey<CatalogStateService>("catalogStateService")
private const val INACCESSIBLE_REVISION = -1L

internal fun Application.catalogStateService(dataSource: DataSource): CatalogStateService =
    attributes.getOrNull(CatalogStateServiceKey) ?: synchronized(attributes) {
        attributes.getOrNull(CatalogStateServiceKey) ?: CatalogStateService(dataSource).also {
            attributes.put(CatalogStateServiceKey, it)
        }
    }

internal fun Application.configureCatalogChangeLogMaintenance() {
    val dataSource = attributes.getOrNull(DataSourceKey) ?: return
    launch(Dispatchers.IO) {
        while (isActive) {
            runCatching { cleanupExpiredChangeLogs(dataSource) }
            delay(CHANGE_LOG_CLEANUP_INTERVAL_MILLIS)
        }
    }
}

internal class CatalogStateService(
    private val dataSource: DataSource,
    private val cacheLifetimeMillis: Long = 5_000L,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val snapshot = AtomicReference<CatalogRevisionSnapshot?>()

    fun state(userId: Long): CatalogStateResponse {
        val revisions = currentSnapshot()
        return dataSource.connection.use { connection ->
            val visibility = connection.prepareStatement(
                """
                SELECT u.username, u.role, u.other_long_term_access_enabled, u.resident_remarks_access_enabled,
                       u.wechat_work_order_access_enabled, p.revision, p.vehicle_result_limit,
                       p.work_order_result_limit, p.wechat_message_result_limit
                FROM users u CROSS JOIN client_runtime_policy p
                WHERE u.id = ? AND p.id = 1
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { result ->
                    require(result.next()) { "当前账号不存在" }
                    val primaryAdministrator = result.getString(1) == "admin" && result.getString(2) == "ADMIN"
                    CatalogVisibility(
                        vehicleBits = if (primaryAdministrator) 3L else {
                            (if (result.getBoolean(3)) 2L else 0L) + (if (result.getBoolean(4)) 1L else 0L)
                        },
                        policyRevision = result.getLong(6),
                        vehicleAllowed = effectiveLimit(result.getInt(7), primaryAdministrator) > 0,
                        wechatAccessEnabled = primaryAdministrator || result.getBoolean(5),
                        workOrderAllowedByPolicy = effectiveLimit(result.getInt(8), primaryAdministrator) > 0,
                        messageAllowedByPolicy = effectiveLimit(result.getInt(9), primaryAdministrator) > 0,
                    )
                }
            }
            revisions.visibleTo(visibility)
        }
    }

    fun invalidate() {
        snapshot.set(null)
    }

    private fun currentSnapshot(): CatalogRevisionSnapshot {
        val now = clockMillis()
        snapshot.get()?.takeIf { now - it.loadedAtMillis < cacheLifetimeMillis }?.let { return it }
        return synchronized(this) {
            snapshot.get()?.takeIf { now - it.loadedAtMillis < cacheLifetimeMillis } ?: loadSnapshot(now).also(snapshot::set)
        }
    }

    private fun loadSnapshot(loadedAtMillis: Long): CatalogRevisionSnapshot = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT vehicle_revision, work_order_revision, wechat_message_revision, attachment_manifest_revision, rebuild_generation
            FROM client_catalog_state WHERE id = 1
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { result ->
                check(result.next()) { "客户端目录状态尚未初始化" }
                CatalogRevisionSnapshot(
                    vehicleRevision = result.getLong(1),
                    workOrderRevision = result.getLong(2),
                    wechatMessageRevision = result.getLong(3),
                    attachmentManifestRevision = result.getLong(4),
                    rebuildGeneration = result.getLong(5),
                    loadedAtMillis = loadedAtMillis,
                )
            }
        }
    }

}

internal suspend fun cleanupExpiredChangeLogs(dataSource: DataSource) = withContext(Dispatchers.IO) {
    dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            CHANGE_LOG_TABLES.forEach { table ->
                connection.prepareStatement(
                    "DELETE FROM $table WHERE changed_at < CURRENT_TIMESTAMP - INTERVAL '90 days' " +
                        "AND revision < (SELECT MAX(revision) FROM $table)",
                ).use { it.executeUpdate() }
            }
            connection.commit()
        } catch (throwable: Throwable) {
            runCatching { connection.rollback() }
            throw throwable
        }
    }
}

private const val CHANGE_LOG_CLEANUP_INTERVAL_MILLIS = 6 * 60 * 60 * 1_000L
private val CHANGE_LOG_TABLES = listOf(
    "vehicle_catalog_changes",
    "work_order_catalog_changes",
    "wechat_message_catalog_changes",
)

@Serializable
data class CatalogStateResponse(
    val vehicleRevision: Long,
    val workOrderRevision: Long,
    val wechatMessageRevision: Long,
    val attachmentManifestRevision: Long,
    val rebuildGeneration: Long = 0,
    val policyRevision: Long,
    val serverTime: String,
)

internal data class CatalogVisibility(
    val vehicleBits: Long,
    val policyRevision: Long,
    val vehicleAllowed: Boolean,
    val wechatAccessEnabled: Boolean,
    val workOrderAllowedByPolicy: Boolean,
    val messageAllowedByPolicy: Boolean,
)

internal data class CatalogRevisionSnapshot(
    val vehicleRevision: Long,
    val workOrderRevision: Long,
    val wechatMessageRevision: Long,
    val attachmentManifestRevision: Long,
    val rebuildGeneration: Long = 0,
    val loadedAtMillis: Long,
)

internal fun CatalogRevisionSnapshot.visibleTo(
    visibility: CatalogVisibility,
    serverTime: Instant = Instant.now(),
): CatalogStateResponse {
    val workOrderAllowed = visibility.wechatAccessEnabled && visibility.workOrderAllowedByPolicy
    val messageAllowed = visibility.wechatAccessEnabled && visibility.messageAllowedByPolicy
    return CatalogStateResponse(
        vehicleRevision = if (visibility.vehicleAllowed) vehicleRevision * 4L + visibility.vehicleBits else INACCESSIBLE_REVISION,
        workOrderRevision = if (workOrderAllowed) workOrderRevision else INACCESSIBLE_REVISION,
        wechatMessageRevision = if (messageAllowed) wechatMessageRevision else INACCESSIBLE_REVISION,
        attachmentManifestRevision = when {
            !workOrderAllowed && !messageAllowed -> INACCESSIBLE_REVISION
            else -> attachmentManifestRevision * 4L +
                (if (workOrderAllowed) 2L else 0L) +
                (if (messageAllowed) 1L else 0L)
        },
        rebuildGeneration = rebuildGeneration,
        policyRevision = visibility.policyRevision,
        serverTime = serverTime.toString(),
    )
}
