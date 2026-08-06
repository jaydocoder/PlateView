package com.jaydocoder.plateview.domain.history

import androidx.compose.runtime.Immutable
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate

@Immutable
data class SearchHistoryItem(
    val id: Long,
    val vehicleId: Long,
    val plateNumber: String,
    val category: String,
    val categoryLabel: String,
    val searchedAtEpochMillis: Long,
)

interface SearchHistoryRepository {
    fun observe(username: String): kotlinx.coroutines.flow.Flow<List<SearchHistoryItem>>

    suspend fun save(username: String, candidate: VehicleCandidate)

    suspend fun delete(username: String, historyId: Long)

    suspend fun clear(username: String)
}
