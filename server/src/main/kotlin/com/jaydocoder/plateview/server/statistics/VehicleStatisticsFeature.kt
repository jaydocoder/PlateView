package com.jaydocoder.plateview.server.statistics

import com.jaydocoder.plateview.server.infrastructure.database.DataSourceKey
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource
import kotlinx.serialization.Serializable

internal fun Application.configureVehicleStatisticsFeature() {
    val dataSource = attributes.getOrNull(DataSourceKey) ?: return
    val service = VehicleStatisticsService(dataSource)
    routing {
        authenticate("access-token") {
            route("/statistics") {
                get {
                    val principal = call.principal<JWTPrincipal>()!!
                    val actorId = principal.payload.getClaim("userId").asLong()
                        ?: throw IllegalArgumentException("登录状态无效")
                    val filter = StatisticsFilter.fromRequest(
                        range = call.request.queryParameters["range"],
                        category = call.request.queryParameters["category"],
                        scope = call.request.queryParameters["scope"],
                        actorId = actorId,
                        administrator = principal.payload.getClaim("role").asString() == "ADMIN",
                    )
                    call.respond(service.query(filter))
                }
                get("/events") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val actorId = principal.payload.getClaim("userId").asLong()
                        ?: throw IllegalArgumentException("登录状态无效")
                    val filter = StatisticsFilter.fromRequest(
                        range = call.request.queryParameters["range"],
                        category = call.request.queryParameters["category"],
                        scope = call.request.queryParameters["scope"],
                        actorId = actorId,
                        administrator = principal.payload.getClaim("role").asString() == "ADMIN",
                    )
                    call.respond(service.queryHistory(filter))
                }
                post("/events") {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
                        ?: throw IllegalArgumentException("登录状态无效")
                    val request = call.receive<QueryEventSyncRequest>()
                    call.respond(QueryEventSyncResponse(service.synchronize(actorId, request.events)))
                }
            }
        }
    }
}

private class VehicleStatisticsService(private val dataSource: DataSource) {
    fun query(filter: StatisticsFilter): StatisticsResponse = dataSource.connection.use { connection ->
        val criteria = filter.toCriteria()
        val summary = connection.prepareStatement(
            "SELECT COUNT(*) AS total, COUNT(DISTINCT vehicle_id) AS plates, COUNT(DISTINCT actor_id) AS actors FROM vehicle_query_events $criteria",
        ).use { statement ->
            statement.bind(filter)
            statement.executeQuery().use { result ->
                result.next()
                StatisticsSummary(result.getLong("total"), result.getLong("plates"), result.getLong("actors"))
            }
        }
        val bucketExpression = if (filter.range == StatisticsRange.TODAY) {
            "TO_CHAR(queried_at AT TIME ZONE 'Asia/Shanghai', 'HH24:00')"
        } else {
            "TO_CHAR(queried_at AT TIME ZONE 'Asia/Shanghai', 'MM-DD')"
        }
        val trend = connection.prepareStatement(
            "SELECT $bucketExpression AS bucket, COUNT(*) AS total FROM vehicle_query_events $criteria GROUP BY bucket ORDER BY bucket",
        ).use { statement ->
            statement.bind(filter)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(StatisticsTrendPoint(result.getString("bucket"), result.getLong("total")))
                }
            }
        }
        val categories = connection.prepareStatement(
            "SELECT category, COUNT(*) AS total FROM vehicle_query_events $criteria GROUP BY category ORDER BY total DESC, category",
        ).use { statement ->
            statement.bind(filter)
            statement.executeQuery().use { result ->
                buildList { while (result.next()) add(StatisticsCategoryPoint(result.getString("category"), result.getLong("total"))) }
            }
        }
        StatisticsResponse(filter.range.name, summary, trend, categories)
    }

    fun synchronize(actorId: Long, events: List<QueryEventUpload>): List<String> {
        require(events.isNotEmpty()) { "查询事件不能为空" }
        require(events.size <= MAX_SYNC_BATCH_SIZE) { "单次查询事件不能超过${MAX_SYNC_BATCH_SIZE}条" }
        events.forEach { event ->
            UUID.fromString(event.eventId)
            require(event.vehicleId > 0) { "车辆标识无效" }
            require(event.occurredAtEpochMillis > 0) { "查询时间无效" }
        }
        dataSource.connection.use { connection ->
            val originalAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement("SELECT category FROM vehicles WHERE id = ?").use { vehicleStatement ->
                    connection.prepareStatement(
                        "INSERT INTO vehicle_query_events (event_id, actor_id, vehicle_id, category, queried_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING",
                    ).use { insertStatement ->
                        events.forEach { event ->
                            vehicleStatement.setLong(1, event.vehicleId)
                            val category = vehicleStatement.executeQuery().use { result ->
                                if (!result.next()) throw IllegalArgumentException("车辆档案不存在")
                                result.getString("category")
                            }
                            insertStatement.setObject(1, UUID.fromString(event.eventId))
                            insertStatement.setLong(2, actorId)
                            insertStatement.setLong(3, event.vehicleId)
                            insertStatement.setString(4, category)
                            insertStatement.setTimestamp(5, Timestamp.from(Instant.ofEpochMilli(event.occurredAtEpochMillis)))
                            insertStatement.addBatch()
                        }
                        insertStatement.executeBatch()
                    }
                }
                connection.commit()
            } catch (throwable: Throwable) {
                connection.rollback()
                throw throwable
            } finally {
                connection.autoCommit = originalAutoCommit
            }
        }
        return events.map(QueryEventUpload::eventId)
    }

    fun queryHistory(filter: StatisticsFilter): StatisticsHistoryResponse = dataSource.connection.use { connection ->
        val criteria = filter.toCriteria(tableAlias = "event")
        connection.prepareStatement(
            """
            SELECT event.vehicle_id, vehicle.plate_number, event.category,
                CAST(EXTRACT(EPOCH FROM event.queried_at) * 1000 AS BIGINT) AS occurred_at_epoch_millis
            FROM vehicle_query_events event
            JOIN vehicles vehicle ON vehicle.id = event.vehicle_id
            $criteria
            ORDER BY event.queried_at DESC, event.id DESC
            LIMIT $HISTORY_LIMIT
            """.trimIndent(),
        ).use { statement ->
            statement.bind(filter)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            StatisticsHistoryItem(
                                vehicleId = result.getLong("vehicle_id"),
                                plateNumber = result.getString("plate_number"),
                                category = result.getString("category"),
                                occurredAtEpochMillis = result.getLong("occurred_at_epoch_millis"),
                            ),
                        )
                    }
                }
            }
        }.let(::StatisticsHistoryResponse)
    }

    private companion object {
        const val MAX_SYNC_BATCH_SIZE = 200
        const val HISTORY_LIMIT = 50
    }
}

private data class StatisticsFilter(
    val range: StatisticsRange,
    val category: String?,
    val actorId: Long?,
) {
    fun toCriteria(tableAlias: String? = null): String = buildString {
        val prefix = tableAlias?.let { "$it." }.orEmpty()
        append("WHERE ${prefix}queried_at >= ?")
        if (category != null) append(" AND ${prefix}category = ?")
        if (actorId != null) append(" AND ${prefix}actor_id = ?")
    }

    companion object {
        fun fromRequest(
            range: String?,
            category: String?,
            scope: String?,
            actorId: Long,
            administrator: Boolean,
        ): StatisticsFilter {
            val parsedScope = scope?.uppercase()?.takeIf { it.isNotBlank() } ?: "ME"
            require(parsedScope in setOf("ME", "ALL")) { "统计范围无效" }
            require(parsedScope != "ALL" || administrator) { "仅管理员可以查看全员统计" }
            return StatisticsFilter(
                range = StatisticsRange.fromRequest(range),
                category = category?.trim()?.takeIf { it.isNotEmpty() },
                actorId = if (parsedScope == "ALL") null else actorId,
            )
        }
    }
}

private fun PreparedStatement.bind(filter: StatisticsFilter) {
    var index = 1
    setTimestamp(index++, Timestamp.from(filter.range.startAt()))
    filter.category?.let { setString(index++, it) }
    filter.actorId?.let { setLong(index, it) }
}

private enum class StatisticsRange {
    TODAY,
    SEVEN_DAYS,
    THIRTY_DAYS;

    fun startAt(): Instant = when (this) {
        TODAY -> ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.DAYS).toInstant()
        SEVEN_DAYS -> Instant.now().minus(7, ChronoUnit.DAYS)
        THIRTY_DAYS -> Instant.now().minus(30, ChronoUnit.DAYS)
    }

    companion object {
        fun fromRequest(value: String?): StatisticsRange = entries.firstOrNull { it.name == value?.uppercase() } ?: TODAY
    }
}

@Serializable private data class StatisticsResponse(
    val range: String,
    val summary: StatisticsSummary,
    val trend: List<StatisticsTrendPoint>,
    val categories: List<StatisticsCategoryPoint>,
)
@Serializable private data class StatisticsSummary(val totalQueries: Long, val distinctPlates: Long, val activeUsers: Long)
@Serializable private data class StatisticsTrendPoint(val bucket: String, val queryCount: Long)
@Serializable private data class StatisticsCategoryPoint(val category: String, val queryCount: Long)
@Serializable private data class StatisticsHistoryResponse(val items: List<StatisticsHistoryItem>)
@Serializable private data class StatisticsHistoryItem(
    val vehicleId: Long,
    val plateNumber: String,
    val category: String,
    val occurredAtEpochMillis: Long,
)
@Serializable private data class QueryEventSyncRequest(val events: List<QueryEventUpload>)
@Serializable private data class QueryEventUpload(val eventId: String, val vehicleId: Long, val occurredAtEpochMillis: Long)
@Serializable private data class QueryEventSyncResponse(val acceptedEventIds: List<String>)
