package com.jaydocoder.plateview.data.workorder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaydocoder.plateview.data.cache.VehicleCachePassphrase
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkOrderCacheDatabaseTest {
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

        val result = database.dao().search("测试", 20)

        assertEquals(listOf("0916024", "0916002", "0915029", "格式异常"), result.map { it.orderNumber })
    }

    private fun entity(id: Long, orderNumber: String, sentAt: String) = WorkOrderCacheEntity(
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
        detailJson = "{}",
    )
}
