package com.jaydocoder.plateview.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["username", "searchedAtEpochMillis"])],
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val vehicleId: Long,
    val plateNumber: String,
    val category: String,
    val categoryLabel: String,
    val searchedAtEpochMillis: Long,
)
