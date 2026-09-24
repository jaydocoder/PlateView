package com.jaydocoder.plateview.server.client

import java.time.Duration
import java.time.Instant
import javax.sql.DataSource
import kotlinx.serialization.Serializable

internal class WechatSyncHealthService(private val dataSource: DataSource) {
    fun health(allowed: Boolean): WechatSyncHealthResponse? {
        if (!allowed) return null
        val sources = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT collector_status, last_heartbeat_at, last_successful_sync_at FROM wechat_sources",
            ).use { statement ->
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                WechatSyncHealthSource(
                                    status = result.getString("collector_status"),
                                    lastHeartbeatAt = result.getTimestamp("last_heartbeat_at")?.toInstant(),
                                    lastSuccessfulSyncAt = result.getTimestamp("last_successful_sync_at")?.toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }
        return aggregateWechatSyncHealth(sources, Instant.now())
    }
}

internal data class WechatSyncHealthSource(
    val status: String,
    val lastHeartbeatAt: Instant?,
    val lastSuccessfulSyncAt: Instant?,
)

@Serializable
internal data class WechatSyncHealthResponse(
    val state: String,
    val lastSuccessfulSyncAt: String?,
    val lastHeartbeatAt: String?,
)

internal fun aggregateWechatSyncHealth(
    sources: List<WechatSyncHealthSource>,
    now: Instant,
): WechatSyncHealthResponse {
    val online = sources.filter { source ->
        source.lastHeartbeatAt?.let { Duration.between(it, now) <= Duration.ofSeconds(90) } == true
    }
    val state = when {
        online.any { it.status == "HEALTHY" } -> "ONLINE_HEALTHY"
        online.any { it.status == "CATCHING_UP" } -> "ONLINE_SYNCING"
        online.isNotEmpty() -> "ONLINE_ERROR"
        else -> "OFFLINE"
    }
    return WechatSyncHealthResponse(
        state = state,
        lastSuccessfulSyncAt = sources.mapNotNull { it.lastSuccessfulSyncAt }.maxOrNull()?.toString(),
        lastHeartbeatAt = sources.mapNotNull { it.lastHeartbeatAt }.maxOrNull()?.toString(),
    )
}
