package com.jaydocoder.plateview.data.statistics

import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
import com.jaydocoder.plateview.feature.auth.AuthSession
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class StatisticsResponseDto(
    val range: String,
    val summary: StatisticsSummaryDto,
    val trend: List<StatisticsTrendPointDto>,
    val categories: List<StatisticsCategoryPointDto>,
    val topPlates: List<StatisticsTopPlatePointDto>,
)

data class StatisticsSummaryDto(val totalQueries: Long, val distinctPlates: Long, val activeUsers: Long)
data class StatisticsTrendPointDto(val bucket: String, val queryCount: Long)
data class StatisticsCategoryPointDto(val category: String, val queryCount: Long)
data class StatisticsTopPlatePointDto(val plateNumber: String, val queryCount: Long)
data class StatisticsHistoryResponseDto(
    val items: List<StatisticsHistoryItemDto>,
    val total: Int? = null,
)
data class StatisticsHistoryItemDto(
    val vehicleId: Long,
    val plateNumber: String,
    val category: String,
    val occurredAtEpochMillis: Long,
)
data class QueryEventSyncRequestDto(val events: List<QueryEventUploadDto>)
data class QueryEventUploadDto(val eventId: String, val vehicleId: Long, val occurredAtEpochMillis: Long)
data class QueryEventSyncResponseDto(val acceptedEventIds: List<String>)

interface StatisticsApi {
    @GET("statistics")
    suspend fun getStatistics(
        @Header("Authorization") authorization: String,
        @Query("range") range: String,
        @Query("category") category: String?,
        @Query("scope") scope: String,
    ): StatisticsResponseDto

    @GET("statistics/events")
    suspend fun getHistory(
        @Header("Authorization") authorization: String,
        @Query("range") range: String,
        @Query("category") category: String?,
        @Query("scope") scope: String,
        @Query("query") query: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): StatisticsHistoryResponseDto

    @POST("statistics/events")
    suspend fun synchronizeEvents(
        @Header("Authorization") authorization: String,
        @Body request: QueryEventSyncRequestDto,
    ): QueryEventSyncResponseDto
}

data class VehicleStatistics(
    val totalQueries: Long,
    val distinctPlates: Long,
    val activeUsers: Long,
    val trend: List<VehicleTrendPoint>,
    val categories: List<VehicleCategoryPoint>,
    val topPlates: List<VehicleTopPlatePoint>,
)

data class VehicleTrendPoint(val bucket: String, val queryCount: Long)
data class VehicleCategoryPoint(val category: String, val queryCount: Long)
data class VehicleTopPlatePoint(val plateNumber: String, val queryCount: Long)
data class VehicleQueryHistoryItem(
    val vehicleId: Long,
    val plateNumber: String?,
    val category: String,
    val occurredAtEpochMillis: Long,
)

data class VehicleQueryHistoryPage(
    val items: List<VehicleQueryHistoryItem>,
    val total: Int,
)

@Singleton
class StatisticsRepository @Inject constructor(
    private val api: StatisticsApi,
    private val queryEventDao: QueryEventDao,
) {
    suspend fun recordQuery(session: AuthSession, vehicle: VehicleDetail) {
        queryEventDao.insert(
            LocalQueryEventEntity(
                eventId = UUID.randomUUID().toString(),
                accountId = session.userId,
                vehicleId = vehicle.id,
                plateNumber = vehicle.plateNumber,
                category = vehicle.category,
                occurredAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun localStatistics(
        session: AuthSession,
        range: String,
        category: String?,
    ): VehicleStatistics {
        val startAtEpochMillis = rangeStartAtEpochMillis(range)
        val summary = queryEventDao.summary(session.userId, startAtEpochMillis, category)
        val trend = if (range == TODAY_RANGE) {
            queryEventDao.hourlyTrend(session.userId, startAtEpochMillis, category)
        } else {
            queryEventDao.dailyTrend(session.userId, startAtEpochMillis, category)
        }
        val categories = queryEventDao.categories(session.userId, startAtEpochMillis, category)
        val topPlates = queryEventDao.topPlates(
            accountId = session.userId,
            startAtEpochMillis = startAtEpochMillis,
            category = category,
            limit = TOP_PLATE_LIMIT,
        )
        return VehicleStatistics(
            totalQueries = summary.totalQueries,
            distinctPlates = summary.distinctPlates,
            activeUsers = summary.activeUsers,
            trend = trend.map { VehicleTrendPoint(it.bucket, it.queryCount) },
            categories = categories.map { VehicleCategoryPoint(it.category, it.queryCount) },
            topPlates = topPlates.map { VehicleTopPlatePoint(it.plateNumber, it.queryCount) },
        )
    }

    suspend fun serverStatistics(
        session: AuthSession,
        range: String,
        category: String?,
        scope: String,
    ): VehicleStatistics = api.getStatistics(bearer(session), range, category, scope).let { response ->
        VehicleStatistics(
            totalQueries = response.summary.totalQueries,
            distinctPlates = response.summary.distinctPlates,
            activeUsers = response.summary.activeUsers,
            trend = response.trend.map { VehicleTrendPoint(it.bucket, it.queryCount) },
            categories = response.categories.map { VehicleCategoryPoint(it.category, it.queryCount) },
            topPlates = response.topPlates.map { VehicleTopPlatePoint(it.plateNumber, it.queryCount) },
        )
    }

    suspend fun localHistoryPage(
        session: AuthSession,
        range: String,
        category: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): VehicleQueryHistoryPage {
        val startAtEpochMillis = rangeStartAtEpochMillis(range)
        val normalizedQuery = query.normalizedHistoryQuery()
        val items = queryEventDao.history(
            accountId = session.userId,
            startAtEpochMillis = startAtEpochMillis,
            category = category,
            query = normalizedQuery,
            limit = limit,
            offset = offset,
        ).map { item ->
            VehicleQueryHistoryItem(item.vehicleId, item.plateNumber, item.category, item.occurredAtEpochMillis)
        }
        return VehicleQueryHistoryPage(
            items = items,
            total = queryEventDao.historyCount(session.userId, startAtEpochMillis, category, normalizedQuery),
        )
    }

    suspend fun serverHistoryPage(
        session: AuthSession,
        range: String,
        category: String?,
        scope: String,
        query: String?,
        limit: Int,
        offset: Int,
    ): VehicleQueryHistoryPage = api.getHistory(
        bearer(session),
        range,
        category,
        scope,
        query.normalizedHistoryQuery(),
        limit,
        offset,
    ).let { response ->
        val items = response.items.map { item ->
            VehicleQueryHistoryItem(item.vehicleId, item.plateNumber, item.category, item.occurredAtEpochMillis)
        }
        VehicleQueryHistoryPage(items, response.total ?: offset + items.size)
    }

    suspend fun synchronizePendingEvents(session: AuthSession) {
        while (true) {
            val events = queryEventDao.pendingSync(session.userId, SYNC_BATCH_SIZE)
            if (events.isEmpty()) return
            val response = api.synchronizeEvents(
                bearer(session),
                QueryEventSyncRequestDto(
                    events.map { event ->
                        QueryEventUploadDto(event.eventId, event.vehicleId, event.occurredAtEpochMillis)
                    },
                ),
            )
            check(response.acceptedEventIds.toSet() == events.map(LocalQueryEventEntity::eventId).toSet()) {
                "查询记录同步响应不完整"
            }
            queryEventDao.markSynced(response.acceptedEventIds, System.currentTimeMillis())
        }
    }

    suspend fun pendingSyncCount(session: AuthSession): Long = queryEventDao.pendingSyncCount(session.userId)

    private fun bearer(session: AuthSession): String = "Bearer ${session.accessToken}"

    private fun rangeStartAtEpochMillis(range: String): Long {
        val now = Instant.now()
        return when (range) {
            TODAY_RANGE -> ZonedDateTime.now(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant()
                .toEpochMilli()

            SEVEN_DAYS_RANGE -> now.minus(7, ChronoUnit.DAYS).toEpochMilli()
            THIRTY_DAYS_RANGE -> now.minus(30, ChronoUnit.DAYS).toEpochMilli()
            ALL_TIME_RANGE -> 0L
            else -> throw IllegalArgumentException("统计范围无效")
        }
    }

    private companion object {
        const val TODAY_RANGE = "TODAY"
        const val SEVEN_DAYS_RANGE = "SEVEN_DAYS"
        const val THIRTY_DAYS_RANGE = "THIRTY_DAYS"
        const val ALL_TIME_RANGE = "ALL_TIME"
        const val SYNC_BATCH_SIZE = 200
        const val TOP_PLATE_LIMIT = 5
    }
}

private fun String?.normalizedHistoryQuery(): String? = this
    ?.filterNot { it.isWhitespace() || it == '·' }
    ?.takeIf { it.isNotBlank() }
