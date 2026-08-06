package com.jaydocoder.plateview

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaydocoder.plateview.data.history.SearchHistoryDao
import com.jaydocoder.plateview.data.history.SearchHistoryDatabase
import com.jaydocoder.plateview.data.history.SearchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchHistoryDaoTest {
    private lateinit var database: SearchHistoryDatabase
    private lateinit var dao: SearchHistoryDao

    @Before
    fun 创建内存数据库() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SearchHistoryDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.searchHistoryDao()
    }

    @After
    fun 关闭数据库() {
        database.close()
    }

    @Test
    fun 历史记录按账号隔离并按最近时间排序() = runBlocking {
        dao.insert(history(username = "guard-a", vehicleId = 101, searchedAt = 100L))
        dao.insert(history(username = "guard-a", vehicleId = 102, searchedAt = 200L))
        dao.insert(history(username = "guard-b", vehicleId = 201, searchedAt = 300L))

        val accountAHistory = dao.observe("guard-a", limit = 50).first()

        assertEquals(listOf(102L, 101L), accountAHistory.map(SearchHistoryEntity::vehicleId))
    }

    @Test
    fun 删除和清空仅影响当前账号的历史记录() = runBlocking {
        dao.insert(history(username = "guard-a", vehicleId = 101, searchedAt = 100L))
        dao.insert(history(username = "guard-a", vehicleId = 102, searchedAt = 200L))
        dao.insert(history(username = "guard-b", vehicleId = 201, searchedAt = 300L))
        val newestAccountAItem = dao.observe("guard-a", limit = 50).first().first()

        dao.delete(username = "guard-a", historyId = newestAccountAItem.id)
        dao.clear(username = "guard-a")

        assertEquals(emptyList<SearchHistoryEntity>(), dao.observe("guard-a", limit = 50).first())
        assertEquals(listOf(201L), dao.observe("guard-b", limit = 50).first().map(SearchHistoryEntity::vehicleId))
    }

    private fun history(
        username: String,
        vehicleId: Long,
        searchedAt: Long,
    ) = SearchHistoryEntity(
        username = username,
        vehicleId = vehicleId,
        plateNumber = "新A$vehicleId",
        category = "RESIDENT",
        categoryLabel = "村民车辆",
        searchedAtEpochMillis = searchedAt,
    )
}
