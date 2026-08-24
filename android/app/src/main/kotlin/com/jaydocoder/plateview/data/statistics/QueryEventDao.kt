package com.jaydocoder.plateview.data.statistics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QueryEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: LocalQueryEventEntity)

    @Query(
        "SELECT COUNT(*) AS totalQueries, COUNT(DISTINCT vehicleId) AS distinctPlates, " +
            "COUNT(DISTINCT accountId) AS activeUsers FROM local_query_events " +
            "WHERE accountId = :accountId AND occurredAtEpochMillis >= :startAtEpochMillis " +
            "AND (:category IS NULL OR category = :category)",
    )
    suspend fun summary(
        accountId: Long,
        startAtEpochMillis: Long,
        category: String?,
    ): LocalStatisticsSummaryRow

    @Query(
        "SELECT strftime('%H:00', occurredAtEpochMillis / 1000, 'unixepoch', 'localtime') AS bucket, " +
            "COUNT(*) AS queryCount FROM local_query_events " +
            "WHERE accountId = :accountId AND occurredAtEpochMillis >= :startAtEpochMillis " +
            "AND (:category IS NULL OR category = :category) GROUP BY bucket ORDER BY bucket",
    )
    suspend fun hourlyTrend(
        accountId: Long,
        startAtEpochMillis: Long,
        category: String?,
    ): List<LocalStatisticsTrendRow>

    @Query(
        "SELECT strftime('%m-%d', occurredAtEpochMillis / 1000, 'unixepoch', 'localtime') AS bucket, " +
            "COUNT(*) AS queryCount FROM local_query_events " +
            "WHERE accountId = :accountId AND occurredAtEpochMillis >= :startAtEpochMillis " +
            "AND (:category IS NULL OR category = :category) GROUP BY bucket ORDER BY bucket",
    )
    suspend fun dailyTrend(
        accountId: Long,
        startAtEpochMillis: Long,
        category: String?,
    ): List<LocalStatisticsTrendRow>

    @Query(
        "SELECT category, COUNT(*) AS queryCount FROM local_query_events " +
            "WHERE accountId = :accountId AND occurredAtEpochMillis >= :startAtEpochMillis " +
            "AND (:category IS NULL OR category = :category) GROUP BY category ORDER BY queryCount DESC, category",
    )
    suspend fun categories(
        accountId: Long,
        startAtEpochMillis: Long,
        category: String?,
    ): List<LocalStatisticsCategoryRow>

    @Query(
        "SELECT vehicleId, plateNumber, category, occurredAtEpochMillis FROM local_query_events " +
            "WHERE accountId = :accountId AND occurredAtEpochMillis >= :startAtEpochMillis " +
            "AND (:category IS NULL OR category = :category) " +
            "ORDER BY occurredAtEpochMillis DESC, eventId DESC LIMIT :limit",
    )
    suspend fun history(
        accountId: Long,
        startAtEpochMillis: Long,
        category: String?,
        limit: Int,
    ): List<LocalQueryHistoryRow>

    @Query(
        "SELECT * FROM local_query_events WHERE accountId = :accountId AND syncedAtEpochMillis IS NULL " +
            "ORDER BY occurredAtEpochMillis ASC LIMIT :limit",
    )
    suspend fun pendingSync(accountId: Long, limit: Int): List<LocalQueryEventEntity>

    @Query("UPDATE local_query_events SET syncedAtEpochMillis = :syncedAtEpochMillis WHERE eventId IN (:eventIds)")
    suspend fun markSynced(eventIds: List<String>, syncedAtEpochMillis: Long)

    @Query("SELECT COUNT(*) FROM local_query_events WHERE accountId = :accountId AND syncedAtEpochMillis IS NULL")
    suspend fun pendingSyncCount(accountId: Long): Long
}
