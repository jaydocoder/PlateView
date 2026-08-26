package com.jaydocoder.plateview.data.statistics

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "local_query_events",
    primaryKeys = ["eventId"],
    indices = [
        Index(value = ["accountId", "occurredAtEpochMillis"]),
        Index(value = ["syncedAtEpochMillis"]),
    ],
)
data class LocalQueryEventEntity(
    val eventId: String,
    val accountId: Long,
    val vehicleId: Long,
    val plateNumber: String?,
    val category: String,
    val occurredAtEpochMillis: Long,
    val syncedAtEpochMillis: Long? = null,
)

data class LocalStatisticsSummaryRow(
    val totalQueries: Long,
    val distinctPlates: Long,
    val activeUsers: Long,
)

data class LocalStatisticsTrendRow(
    val bucket: String,
    val queryCount: Long,
)

data class LocalStatisticsCategoryRow(
    val category: String,
    val queryCount: Long,
)

data class LocalStatisticsTopPlateRow(
    val plateNumber: String,
    val queryCount: Long,
)

data class LocalQueryHistoryRow(
    val vehicleId: Long,
    val plateNumber: String?,
    val category: String,
    val occurredAtEpochMillis: Long,
)
