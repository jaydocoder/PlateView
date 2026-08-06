package com.jaydocoder.plateview.data.history

import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.history.SearchHistoryRepository
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSearchHistoryRepository @Inject constructor(
    private val dao: SearchHistoryDao,
) : SearchHistoryRepository {
    override fun observe(username: String): Flow<List<SearchHistoryItem>> = dao
        .observe(username, HISTORY_LIMIT)
        .map { entities -> entities.map(SearchHistoryEntity::toDomain) }

    override suspend fun save(username: String, candidate: VehicleCandidate) {
        dao.insert(
            SearchHistoryEntity(
                username = username,
                vehicleId = candidate.id,
                plateNumber = candidate.plateNumber,
                category = candidate.category,
                categoryLabel = candidate.categoryLabel,
                searchedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun delete(username: String, historyId: Long) {
        dao.delete(username, historyId)
    }

    override suspend fun clear(username: String) {
        dao.clear(username)
    }

    private companion object {
        const val HISTORY_LIMIT = 50
    }
}

private fun SearchHistoryEntity.toDomain(): SearchHistoryItem = SearchHistoryItem(
    id = id,
    vehicleId = vehicleId,
    plateNumber = plateNumber,
    category = category,
    categoryLabel = categoryLabel,
    searchedAtEpochMillis = searchedAtEpochMillis,
)
