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

    private fun entity(id: Long, orderNumber: String, sentAt: String) = WorkOrderCacheEntity(
        userId = 7,
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
}
