package com.jaydocoder.plateview.data.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchHistoryDatabaseTest {
    private lateinit var database: SearchHistoryDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SearchHistoryDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 搜索历史按账号隔离() = runBlocking {
        val dao = database.searchHistoryDao()
        dao.insert(
            SearchHistoryEntity(
                username = "guard-a",
                vehicleId = 101,
                plateNumber = "新A12345",
                category = "RESIDENT",
                categoryLabel = "村民车辆",
                searchedAtEpochMillis = 2L,
            ),
        )
        dao.insert(
            SearchHistoryEntity(
                username = "guard-b",
                vehicleId = 102,
                plateNumber = "新A54321",
                category = "CADRE",
                categoryLabel = "干部车辆",
                searchedAtEpochMillis = 1L,
            ),
        )

        val guardAHistory = dao.observe("guard-a", 50).first()

        assertEquals(1, guardAHistory.size)
        assertEquals("新A12345", guardAHistory.single().plateNumber)
    }
}
