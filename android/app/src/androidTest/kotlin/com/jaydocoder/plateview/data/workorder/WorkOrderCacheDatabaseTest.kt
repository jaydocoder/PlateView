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
    fun 相同单号按来源和年份隔离且当前年份优先() = runBlocking {
        database.dao().upsert(
            listOf(
                entity(1, "0924035", "2025-09-24T03:00:00Z", orderYear = 2025, sourceKey = "source-a"),
                entity(2, "0924035", "2026-09-24T03:00:00Z", orderYear = 2026, sourceKey = "source-a"),
                entity(3, "0924035", "2026-09-24T04:00:00Z", orderYear = 2026, sourceKey = "source-b"),
            ),
        )

        val result = database.dao().search(7, "0924035", 20)

        assertEquals(listOf(3L, 2L, 1L), result.map { it.recordId })
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
    fun 车单缓存从版本六迁移后按北京时间回填年份并保留来源() {
        migrationHelper.createDatabase("work-order-migration-6-7", 6).apply {
            execSQL(
                "INSERT INTO work_order_cache(userId, recordId, orderNumber, rawPlate, status, sourceName, location, rawValidTime, sentAt, searchableText, catalogRevision, cachedAt, lastValidatedAt, detailJson) VALUES (7, 35, '1231035', '新H26927', 'ACTIVE', '测试群', '喀纳斯', '12月31日', '2025-12-31T16:30:00Z', '1231035', 8, 1, 1, '{}')",
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            "work-order-migration-6-7",
            7,
            true,
            WORK_ORDER_CACHE_MIGRATION_6_7,
        ).use { migrated ->
            migrated.query("SELECT orderYear, sourceKey, orderNumber FROM work_order_cache WHERE recordId = 35").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(2026, cursor.getInt(0))
                assertEquals("测试群", cursor.getString(1))
                assertEquals("1231035", cursor.getString(2))
            }
        }
    }

    @Test
    fun 版本八迁移后回填摘要分组键并保留最新记录标记() {
        migrationHelper.createDatabase("work-order-migration-8-9", 8).apply {
            execSQL(
                "INSERT INTO work_order_cache(userId, recordId, orderNumber, rawPlate, status, sourceName, location, rawValidTime, sentAt, searchableText, catalogRevision, cachedAt, lastValidatedAt, detailJson, orderYear, sourceKey) VALUES (7, 51, '0924001', '新H27274', 'ACTIVE', '测试群', '禾木', '9.24', '2026-09-24T03:00:00Z', '0924001', 8, 1, 1, '{}', 2026, 'source-a')",
            )
            execSQL(
                "INSERT INTO work_order_cache(userId, recordId, orderNumber, rawPlate, status, sourceName, location, rawValidTime, sentAt, searchableText, catalogRevision, cachedAt, lastValidatedAt, detailJson, orderYear, sourceKey) VALUES (7, 52, '0924001', '新H27274', 'ACTIVE', '测试群', '禾木', '9.24', '2026-09-24T04:00:00Z', '0924001', 9, 1, 1, '{}', 2026, 'source-a')",
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            "work-order-migration-8-9",
            9,
            true,
            WORK_ORDER_CACHE_MIGRATION_8_9,
        ).use { migrated ->
            migrated.query("SELECT latestGroupKey, displaySummary, isLatest FROM work_order_cache ORDER BY recordId").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("7|source-a|2026|0924001", cursor.getString(0))
                assertEquals("0924001 · 新H27274 · 测试群", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(true, cursor.moveToNext())
                assertEquals(1, cursor.getInt(2))
            }
        }
    }

    @Test
    fun FTS索引搜索车单和聊天摘要且只返回最新车单() = runBlocking {
        val index = WorkOrderSearchIndex(database)
        index.ensureReady()
        database.dao().upsert(
            listOf(
                entity(61, "0924001", "2026-09-24T03:00:00Z"),
                entity(62, "0924001", "2026-09-24T04:00:00Z"),
            ),
        )
        database.dao().upsertMessages(listOf(messageEntity(7, 71)))

        val orders = index.searchWorkOrders(7, "测试0", 20)
        val messages = index.searchMessages(7, "测试消", 20)

        assertEquals(listOf(62L), orders.map { it.id })
        assertEquals(listOf(71L), messages.map { it.id })
    }

    @Test
    fun FTS不可用时回退到兼容查询仍可搜索() = runBlocking {
        val index = WorkOrderSearchIndex(database)
        index.forceFtsUnavailableForTest()
        database.dao().upsertMessages(listOf(messageEntity(7, 81)))

        val messages = index.searchMessages(7, "测试消息", 20)

        assertEquals(listOf(81L), messages.map { it.id })
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

    private fun entity(
        id: Long,
        orderNumber: String,
        sentAt: String,
        userId: Long = 7,
        orderYear: Int = 2026,
        sourceKey: String = "source-a",
    ) = WorkOrderCacheEntity(
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
        orderYear = orderYear,
        sourceKey = sourceKey,
        latestGroupKey = listOf(userId, sourceKey, orderYear, orderNumber).joinToString("|"),
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
