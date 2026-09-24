package com.jaydocoder.plateview.data.workorder

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "work_order_cache", primaryKeys = ["userId", "recordId"])
data class WorkOrderCacheEntity(
    val userId: Long,
    val recordId: Long,
    val orderNumber: String?,
    val rawPlate: String?,
    val status: String,
    val sourceName: String,
    val location: String?,
    val rawValidTime: String?,
    val sentAt: String,
    val searchableText: String,
    val catalogRevision: Long,
    val cachedAt: Long,
    val lastValidatedAt: Long,
    val detailJson: String,
)

@Entity(tableName = "work_order_catalog_state")
data class WorkOrderCatalogStateEntity(
    @PrimaryKey val userId: Long,
    val catalogVersion: Long,
    val messageCatalogVersion: Long,
    val checkedAtEpochMillis: Long,
)

@Entity(tableName = "wechat_message_cache", primaryKeys = ["userId", "messageId"])
data class WechatMessageCacheEntity(
    val userId: Long,
    val messageId: Long,
    val businessType: String,
    val displayName: String,
    val sourceName: String,
    val sentAt: String,
    val searchableText: String,
    val catalogRevision: Long,
    val cachedAt: Long,
    val lastValidatedAt: Long,
    val detailJson: String,
)

@Entity(
    tableName = "wechat_attachment_download_tasks",
    primaryKeys = ["userId", "attachmentId", "variant", "sha256", "sourceQuality"],
    indices = [Index(value = ["userId", "status", "nextRetryAt", "foregroundRequested", "priority", "createdAt"])],
)
data class WechatAttachmentDownloadTaskEntity(
    val userId: Long,
    val attachmentId: Long,
    val kind: String,
    val fileName: String?,
    val variant: String,
    val sha256: String,
    val sourceQuality: String,
    val expectedSize: Long?,
    val downloadedBytes: Long,
    val localPath: String?,
    val status: String,
    val priority: Int,
    val foregroundRequested: Boolean,
    val attemptCount: Int,
    val nextRetryAt: Long?,
    val lastErrorCode: String?,
    val manifestRevision: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface WorkOrderCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachmentTasks(tasks: List<WechatAttachmentDownloadTaskEntity>)

    @Query("DELETE FROM wechat_attachment_download_tasks WHERE userId = :userId")
    suspend fun clearAttachmentTasks(userId: Long)

    @Query("SELECT * FROM wechat_attachment_download_tasks WHERE userId = :userId")
    suspend fun attachmentTasks(userId: Long): List<WechatAttachmentDownloadTaskEntity>

    @Query("SELECT * FROM wechat_attachment_download_tasks WHERE userId = :userId AND status = :status")
    suspend fun attachmentTasksByStatus(userId: Long, status: String): List<WechatAttachmentDownloadTaskEntity>

    @Query("SELECT * FROM wechat_attachment_download_tasks WHERE userId = :userId AND attachmentId = :attachmentId AND status <> 'REVOKED' ORDER BY updatedAt DESC, priority DESC LIMIT 1")
    fun observeAttachmentTask(userId: Long, attachmentId: Long): Flow<WechatAttachmentDownloadTaskEntity?>

    @Query("SELECT * FROM wechat_attachment_download_tasks WHERE userId = :userId AND attachmentId = :attachmentId AND variant = :variant AND sha256 = :sha256 AND sourceQuality = :sourceQuality LIMIT 1")
    suspend fun attachmentTask(userId: Long, attachmentId: Long, variant: String, sha256: String, sourceQuality: String): WechatAttachmentDownloadTaskEntity?

    @Query(
        "SELECT * FROM wechat_attachment_download_tasks WHERE userId = :userId AND status IN ('DISCOVERED', 'WAITING_NETWORK', 'RETRY_WAIT') AND (nextRetryAt IS NULL OR nextRetryAt <= :now) ORDER BY foregroundRequested DESC, priority DESC, createdAt DESC LIMIT :limit",
    )
    suspend fun readyAttachmentTasks(userId: Long, now: Long, limit: Int): List<WechatAttachmentDownloadTaskEntity>

    @Query("UPDATE wechat_attachment_download_tasks SET status = :status, downloadedBytes = :downloadedBytes, localPath = :localPath, attemptCount = :attemptCount, nextRetryAt = :nextRetryAt, lastErrorCode = :lastErrorCode, foregroundRequested = :foregroundRequested, updatedAt = :updatedAt WHERE userId = :userId AND attachmentId = :attachmentId AND variant = :variant AND sha256 = :sha256 AND sourceQuality = :sourceQuality")
    suspend fun updateAttachmentTask(
        userId: Long,
        attachmentId: Long,
        variant: String,
        sha256: String,
        sourceQuality: String,
        status: String,
        downloadedBytes: Long,
        localPath: String?,
        attemptCount: Int,
        nextRetryAt: Long?,
        lastErrorCode: String?,
        foregroundRequested: Boolean,
        updatedAt: Long,
    )

    @Query("UPDATE wechat_attachment_download_tasks SET priority = :priority, foregroundRequested = 1, status = CASE WHEN status = 'FAILED' THEN 'DISCOVERED' ELSE status END, attemptCount = CASE WHEN status = 'FAILED' THEN 0 ELSE attemptCount END, nextRetryAt = NULL, updatedAt = :updatedAt WHERE userId = :userId AND attachmentId = :attachmentId AND status <> 'REVOKED'")
    suspend fun prioritizeAttachment(userId: Long, attachmentId: Long, priority: Int, updatedAt: Long)

    @Query("UPDATE wechat_attachment_download_tasks SET status = 'REVOKED', foregroundRequested = 0, updatedAt = :updatedAt WHERE userId = :userId AND manifestRevision <> :manifestRevision AND status <> 'REVOKED'")
    suspend fun revokeMissingAttachmentTasks(userId: Long, manifestRevision: Long, updatedAt: Long)
    @Query(
        """
        SELECT c.* FROM work_order_cache AS c
        WHERE c.userId = :userId AND c.searchableText LIKE '%' || :keyword || '%'
          AND (
            c.orderNumber IS NULL OR NOT EXISTS (
                SELECT 1 FROM work_order_cache AS n
                WHERE n.userId = c.userId AND n.orderNumber = c.orderNumber
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
    suspend fun search(userId: Long, keyword: String, limit: Int): List<WorkOrderCacheEntity>

    @Query("SELECT * FROM work_order_cache WHERE userId = :userId AND recordId = :recordId")
    suspend fun get(userId: Long, recordId: Long): WorkOrderCacheEntity?

    @Query("SELECT * FROM wechat_message_cache WHERE userId = :userId AND searchableText LIKE '%' || :keyword || '%' ORDER BY sentAt DESC, messageId DESC LIMIT :limit")
    suspend fun searchMessages(userId: Long, keyword: String, limit: Int): List<WechatMessageCacheEntity>

    @Query("SELECT * FROM wechat_message_cache WHERE userId = :userId AND messageId = :messageId")
    suspend fun getMessage(userId: Long, messageId: Long): WechatMessageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(records: List<WechatMessageCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(records: List<WorkOrderCacheEntity>)

    @Query("SELECT * FROM work_order_catalog_state WHERE userId = :userId")
    suspend fun state(userId: Long): WorkOrderCatalogStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateState(state: WorkOrderCatalogStateEntity)

    @Query("DELETE FROM work_order_cache WHERE userId = :userId")
    suspend fun clearRecords(userId: Long)

    @Query("DELETE FROM work_order_catalog_state WHERE userId = :userId")
    suspend fun clearState(userId: Long)

    @Query("DELETE FROM wechat_message_cache WHERE userId = :userId")
    suspend fun clearMessages(userId: Long)

    @Query("DELETE FROM work_order_cache")
    suspend fun clearAllRecords()

    @Query("DELETE FROM work_order_catalog_state")
    suspend fun clearAllStates()

    @Query("DELETE FROM wechat_message_cache")
    suspend fun clearAllMessages()

    @Query("DELETE FROM wechat_attachment_download_tasks")
    suspend fun clearAllAttachmentTasks()

    @Transaction
    suspend fun clear(userId: Long) { clearRecords(userId); clearState(userId); clearMessages(userId); clearAttachmentTasks(userId) }

    @Transaction
    suspend fun clearAll() { clearAllRecords(); clearAllStates(); clearAllMessages(); clearAllAttachmentTasks() }
}

@Database(entities = [WorkOrderCacheEntity::class, WorkOrderCatalogStateEntity::class, WechatMessageCacheEntity::class, WechatAttachmentDownloadTaskEntity::class], version = 5, exportSchema = true)
abstract class WorkOrderCacheDatabase : RoomDatabase() {
    abstract fun dao(): WorkOrderCacheDao
}
