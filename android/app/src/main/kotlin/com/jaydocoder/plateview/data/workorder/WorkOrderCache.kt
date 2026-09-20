package com.jaydocoder.plateview.data.workorder

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "work_order_cache")
data class WorkOrderCacheEntity(
    @PrimaryKey val recordId: Long,
    val orderNumber: String?,
    val rawPlate: String?,
    val status: String,
    val sourceName: String,
    val location: String?,
    val rawValidTime: String?,
    val sentAt: String,
    val searchableText: String,
    val catalogRevision: Long,
    val detailJson: String,
)

@Entity(tableName = "work_order_catalog_state")
data class WorkOrderCatalogStateEntity(@PrimaryKey val id: Int = 1, val catalogVersion: Long, val checkedAtEpochMillis: Long)

@Entity(tableName = "wechat_message_cache")
data class WechatMessageCacheEntity(
    @PrimaryKey val messageId: Long,
    val businessType: String,
    val displayName: String,
    val sourceName: String,
    val sentAt: String,
    val searchableText: String,
    val detailJson: String,
)

@Dao
interface WorkOrderCacheDao {
    @Query(
        """
        SELECT c.* FROM work_order_cache AS c
        WHERE c.searchableText LIKE '%' || :keyword || '%'
          AND (
            c.orderNumber IS NULL OR NOT EXISTS (
                SELECT 1 FROM work_order_cache AS n
                WHERE n.orderNumber = c.orderNumber
                  AND (n.sentAt > c.sentAt OR (n.sentAt = c.sentAt AND n.recordId > c.recordId))
            )
          )
        ORDER BY
          CASE WHEN LENGTH(c.orderNumber) = 7
                     AND c.orderNumber NOT GLOB '*[^0-9]*'
                     AND CAST(SUBSTR(c.orderNumber, 1, 2) AS INTEGER) BETWEEN 1 AND 12
                     AND CAST(SUBSTR(c.orderNumber, 3, 2) AS INTEGER) BETWEEN 1 AND 31
               THEN 0 ELSE 1 END,
          CASE WHEN LENGTH(c.orderNumber) = 7
                     AND c.orderNumber NOT GLOB '*[^0-9]*'
                     AND CAST(SUBSTR(c.orderNumber, 1, 2) AS INTEGER) BETWEEN 1 AND 12
                     AND CAST(SUBSTR(c.orderNumber, 3, 2) AS INTEGER) BETWEEN 1 AND 31
               THEN CAST(c.orderNumber AS INTEGER) END DESC,
          c.sentAt DESC,
          c.recordId DESC
        LIMIT :limit
        """,
    )
    suspend fun search(keyword: String, limit: Int): List<WorkOrderCacheEntity>

    @Query("SELECT * FROM work_order_cache WHERE recordId = :recordId")
    suspend fun get(recordId: Long): WorkOrderCacheEntity?

    @Query("SELECT * FROM wechat_message_cache WHERE searchableText LIKE '%' || :keyword || '%' ORDER BY sentAt DESC, messageId DESC LIMIT :limit")
    suspend fun searchMessages(keyword: String, limit: Int): List<WechatMessageCacheEntity>

    @Query("SELECT * FROM wechat_message_cache WHERE messageId = :messageId")
    suspend fun getMessage(messageId: Long): WechatMessageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(records: List<WechatMessageCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(records: List<WorkOrderCacheEntity>)

    @Query("SELECT * FROM work_order_catalog_state WHERE id = 1")
    suspend fun state(): WorkOrderCatalogStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateState(state: WorkOrderCatalogStateEntity)

    @Query("DELETE FROM work_order_cache")
    suspend fun clearRecords()

    @Query("DELETE FROM work_order_catalog_state")
    suspend fun clearState()

    @Query("DELETE FROM wechat_message_cache")
    suspend fun clearMessages()

    @Transaction
    suspend fun clear() { clearRecords(); clearState(); clearMessages() }
}

@Database(entities = [WorkOrderCacheEntity::class, WorkOrderCatalogStateEntity::class, WechatMessageCacheEntity::class], version = 2, exportSchema = true)
abstract class WorkOrderCacheDatabase : RoomDatabase() {
    abstract fun dao(): WorkOrderCacheDao
}
