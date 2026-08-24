package com.jaydocoder.plateview.data.statistics

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueryEventDatabaseTest {
    private lateinit var database: QueryEventDatabase
    private lateinit var dao: QueryEventDao

    @Before
    fun 创建内存数据库() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            QueryEventDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.queryEventDao()
    }

    @After
    fun 关闭数据库() {
        database.close()
    }

    @Test
    fun 本地统计按账号时间和类别隔离() = runBlocking {
        dao.insert(event("event-1", accountId = 1, vehicleId = 101, category = "RESIDENT", occurredAt = 1_000L))
        dao.insert(event("event-2", accountId = 1, vehicleId = 102, category = "SCENIC_UNIT", occurredAt = 2_000L))
        dao.insert(event("event-3", accountId = 2, vehicleId = 103, category = "RESIDENT", occurredAt = 3_000L))

        val allCategories = dao.summary(accountId = 1, startAtEpochMillis = 0L, category = null)
        val residents = dao.summary(accountId = 1, startAtEpochMillis = 0L, category = "RESIDENT")
        val categories = dao.categories(accountId = 1, startAtEpochMillis = 0L, category = null)

        assertEquals(2L, allCategories.totalQueries)
        assertEquals(2L, allCategories.distinctPlates)
        assertEquals(1L, allCategories.activeUsers)
        assertEquals(1L, residents.totalQueries)
        assertEquals(listOf("RESIDENT", "SCENIC_UNIT"), categories.map(LocalStatisticsCategoryRow::category).sorted())
    }

    @Test
    fun 已确认的批次不再作为待同步事件返回() = runBlocking {
        dao.insert(event("event-1", accountId = 1, vehicleId = 101, category = "RESIDENT", occurredAt = 1_000L))
        dao.insert(event("event-2", accountId = 1, vehicleId = 102, category = "SCENIC_UNIT", occurredAt = 2_000L))
        dao.insert(event("event-3", accountId = 2, vehicleId = 103, category = "RESIDENT", occurredAt = 3_000L))

        dao.markSynced(listOf("event-1"), syncedAtEpochMillis = 9_000L)

        assertEquals(listOf("event-2"), dao.pendingSync(accountId = 1, limit = 10).map(LocalQueryEventEntity::eventId))
        assertEquals(1L, dao.pendingSyncCount(accountId = 1))
        assertEquals(listOf("event-3"), dao.pendingSync(accountId = 2, limit = 10).map(LocalQueryEventEntity::eventId))
    }

    @Test
    fun 查询明细按类别过滤并按最新时间排序() = runBlocking {
        dao.insert(event("event-1", accountId = 1, vehicleId = 101, category = "RESIDENT", occurredAt = 1_000L))
        dao.insert(event("event-2", accountId = 1, vehicleId = 102, category = "RESIDENT", occurredAt = 3_000L))
        dao.insert(event("event-3", accountId = 1, vehicleId = 103, category = "CADRE", occurredAt = 2_000L))

        val history = dao.history(accountId = 1, startAtEpochMillis = 0L, category = "RESIDENT", limit = 10)

        assertEquals(listOf(102L, 101L), history.map(LocalQueryHistoryRow::vehicleId))
        assertEquals(listOf("新A102", "新A101"), history.map(LocalQueryHistoryRow::plateNumber))
    }

    private fun event(
        eventId: String,
        accountId: Long,
        vehicleId: Long,
        category: String,
        occurredAt: Long,
    ) = LocalQueryEventEntity(
        eventId = eventId,
        accountId = accountId,
        vehicleId = vehicleId,
        plateNumber = "新A$vehicleId",
        category = category,
        occurredAtEpochMillis = occurredAt,
    )
}
