package com.jaydocoder.plateview.data.workorder

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaydocoder.plateview.data.cache.VehicleCachePassphrase
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkOrderCacheDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkOrderCacheDatabase::class.java,
    )

    private lateinit var database: WorkOrderCacheDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SQLiteDatabase.loadLibs(context)
        database = Room.inMemoryDatabaseBuilder(context, WorkOrderCacheDatabase::class.java)
            .openHelperFactory(SupportFactory(VehicleCachePassphrase(context).getOrCreate()))
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 搜索候选按业务单号日期和序号倒序排列() = runBlocking {
        database.dao().upsert(
            listOf(
                entity(1, "0915029", "2026-09-20T03:00:00Z"),
                entity(2, "0916002", "2026-09-18T03:00:00Z"),
                entity(3, "0916024", "2026-09-17T03:00:00Z"),
                entity(4, "格式异常", "2026-09-21T03:00:00Z"),
            ),
        )

        val result = database.dao().search(7, "测试", 20)

        assertEquals(listOf("0916024", "0916002", "0915029", "格式异常"), result.map { it.orderNumber })
    }

    @Test
    fun 附件任务优先领取前台请求并隔离账号() = runBlocking {
        database.dao().upsertAttachmentTasks(
            listOf(
                attachmentTask(userId = 7, attachmentId = 1, priority = 100, foreground = false),
                attachmentTask(userId = 7, attachmentId = 2, priority = 1_000, foreground = true),
                attachmentTask(userId = 8, attachmentId = 3, priority = 2_000, foreground = true),
            ),
        )

        val ready = database.dao().readyAttachmentTasks(7, now = 2_000, limit = 2)

        assertEquals(listOf(2L, 1L), ready.map { it.attachmentId })
        assertNull(database.dao().attachmentTask(7, 3, "original", "sha-3", "ORIGINAL"))
    }

    @Test
    fun 附件任务从版本四迁移后保留进度并补齐调度字段() {
        migrationHelper.createDatabase("work-order-migration-test", 4).apply {
            execSQL(
                "INSERT INTO wechat_attachment_download_tasks(userId, attachmentId, variant, sha256, expectedSize, downloadedBytes, localPath, status, attemptCount, nextRetryAt, lastErrorCode, manifestRevision, updatedAt) VALUES (7, 91, 'original', 'abc', 1024, 512, NULL, 'RETRY_WAIT', 2, 3000, '网络中断', 5, 2000)",
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            "work-order-migration-test",
            5,
            true,
            WORK_ORDER_CACHE_MIGRATION_4_5,
        ).use { migrated ->
            migrated.query("SELECT kind, sourceQuality, priority, foregroundRequested, createdAt, downloadedBytes FROM wechat_attachment_download_tasks WHERE attachmentId = 91").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("IMAGE", cursor.getString(0))
                assertEquals("UNKNOWN", cursor.getString(1))
                assertEquals(100, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(2000L, cursor.getLong(4))
                assertEquals(512L, cursor.getLong(5))
            }
        }
    }

    @Test
    fun 目录事务同时应用更新墓碑并推进版本() = runBlocking {
        val dao = database.dao()
        dao.upsert(listOf(entity(1, "0924001", "2026-09-24T03:00:00Z")))
        dao.upsertMessages(listOf(messageEntity(7, 11)))

        dao.applyCatalogSync(
            records = listOf(entity(2, "0924002", "2026-09-24T04:00:00Z")),
            messages = listOf(messageEntity(7, 12)),
            removedRecordIds = listOf(1),
            removedMessageIds = listOf(11),
            replaceWorkOrders = false,
            replaceMessages = false,
            state = WorkOrderCatalogStateEntity(7, 20, 21, 1_000),
        )

        assertNull(dao.get(7, 1))
        assertEquals(2L, dao.get(7, 2)?.recordId)
        assertNull(dao.getMessage(7, 11))
        assertEquals(12L, dao.getMessage(7, 12)?.messageId)
        assertEquals(20L, dao.state(7)?.catalogVersion)
        assertEquals(21L, dao.state(7)?.messageCatalogVersion)
    }

    @Test
    fun 全量重建只替换指定账号对应目录() = runBlocking {
        val dao = database.dao()
        dao.upsert(listOf(entity(1, "旧车单", "2026-09-24T03:00:00Z"), entity(9, "其他账号", "2026-09-24T03:00:00Z", userId = 8)))
        dao.upsertMessages(listOf(messageEntity(7, 11), messageEntity(8, 19)))

        dao.applyCatalogSync(
            records = listOf(entity(2, "新车单", "2026-09-24T04:00:00Z")),
            messages = listOf(messageEntity(7, 12)),
            removedRecordIds = emptyList(),
            removedMessageIds = emptyList(),
            replaceWorkOrders = true,
            replaceMessages = true,
            state = WorkOrderCatalogStateEntity(7, 30, 31, 2_000),
        )

        assertNull(dao.get(7, 1))
        assertEquals(2L, dao.get(7, 2)?.recordId)
        assertEquals(9L, dao.get(8, 9)?.recordId)
        assertNull(dao.getMessage(7, 11))
        assertEquals(19L, dao.getMessage(8, 19)?.messageId)
    }

    @Test
    fun 权限撤销独立清理对应目录并将其版本归零() = runBlocking {
        val dao = database.dao()
        dao.upsert(listOf(entity(1, "0924001", "2026-09-24T03:00:00Z")))
        dao.upsertMessages(listOf(messageEntity(7, 11)))
        dao.updateState(WorkOrderCatalogStateEntity(7, 20, 21, 1_000))

        dao.revokeWorkOrders(7)

        assertNull(dao.get(7, 1))
        assertEquals(11L, dao.getMessage(7, 11)?.messageId)
        assertEquals(0L, dao.state(7)?.catalogVersion)
        assertEquals(21L, dao.state(7)?.messageCatalogVersion)

        dao.revokeMessages(7)

        assertNull(dao.getMessage(7, 11))
        assertEquals(0L, dao.state(7)?.messageCatalogVersion)
        assertEquals(0L, dao.state(7)?.checkedAtEpochMillis)
    }

    private fun attachmentTask(userId: Long, attachmentId: Long, priority: Int, foreground: Boolean) =
        WechatAttachmentDownloadTaskEntity(
            userId = userId,
            attachmentId = attachmentId,
            kind = "IMAGE",
            fileName = "$attachmentId.jpg",
            variant = "original",
            sha256 = "sha-$attachmentId",
            sourceQuality = "ORIGINAL",
            expectedSize = 128,
            downloadedBytes = 0,
            localPath = null,
            status = "DISCOVERED",
            priority = priority,
            foregroundRequested = foreground,
            attemptCount = 0,
            nextRetryAt = null,
            lastErrorCode = null,
            manifestRevision = 1,
            createdAt = 1_000,
            updatedAt = 1_000,
        )

    private fun entity(id: Long, orderNumber: String, sentAt: String, userId: Long = 7) = WorkOrderCacheEntity(
        userId = userId,
        recordId = id,
        orderNumber = orderNumber,
        rawPlate = "新H27274",
        status = "ACTIVE",
        sourceName = "2026车单子接收群",
        location = "禾木",
        rawValidTime = "9.20-9.23",
        sentAt = sentAt,
        searchableText = "测试$orderNumber",
        catalogRevision = id,
        cachedAt = id,
        lastValidatedAt = id,
        detailJson = "{}",
    )

    private fun messageEntity(userId: Long, messageId: Long) = WechatMessageCacheEntity(
        userId = userId,
        messageId = messageId,
        businessType = "GENERAL_MESSAGE",
        displayName = "测试发送者",
        sourceName = "测试群",
        sentAt = "2026-09-24T03:00:00Z",
        searchableText = "测试消息$messageId",
        catalogRevision = messageId,
        cachedAt = messageId,
        lastValidatedAt = messageId,
        detailJson = "{}",
    )
}
