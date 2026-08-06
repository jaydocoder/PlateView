package com.jaydocoder.plateview.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query(
        "SELECT * FROM search_history WHERE username = :username " +
            "ORDER BY searchedAtEpochMillis DESC, id DESC LIMIT :limit",
    )
    fun observe(username: String, limit: Int): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE username = :username AND id = :historyId")
    suspend fun delete(username: String, historyId: Long)

    @Query("DELETE FROM search_history WHERE username = :username")
    suspend fun clear(username: String)
}
