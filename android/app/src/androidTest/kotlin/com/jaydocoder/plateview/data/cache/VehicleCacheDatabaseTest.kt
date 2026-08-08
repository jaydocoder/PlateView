package com.jaydocoder.plateview.data.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleCacheDatabaseTest {
    private lateinit var database: VehicleCacheDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SQLiteDatabase.loadLibs(context)
        database = Room.inMemoryDatabaseBuilder(context, VehicleCacheDatabase::class.java)
            .openHelperFactory(SupportFactory(VehicleCachePassphrase(context).getOrCreate()))
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 完整快照支持模糊排序() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(
            listOf(
                snapshot(11, 1, "新A12345"),
                snapshot(11, 2, "新A12399"),
                snapshot(11, 3, "新B12345"),
            ),
        )
        dao.promoteGeneration(11, 7, 100, 100)

        val result = dao.searchCandidates("A123", 20)

        assertEquals(listOf(1L, 2L), result.map { it.vehicleId })
    }

    @Test
    fun 切换快照代次后旧数据不再可见() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(listOf(snapshot(11, 1, "新A12345")))
        dao.promoteGeneration(11, 1, 100, 100)
        dao.insertSnapshots(listOf(snapshot(12, 2, "新A54321")))

        dao.promoteGeneration(12, 2, 200, 200)

        assertEquals(null, dao.getDetail(1))
        assertEquals(2L, dao.getDetail(2)?.vehicleId)
        assertEquals(2L, dao.getCatalogState()?.catalogVersion)
    }

    @Test
    fun 清除快照会删除目录状态和车辆资料() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(listOf(snapshot(11, 1, "新A12345")))
        dao.promoteGeneration(11, 1, 100, 100)

        dao.clearSnapshot()

        assertEquals(null, dao.getCatalogState())
        assertEquals(null, dao.getDetail(1))
    }

    private fun snapshot(generation: Long, vehicleId: Long, plateNumber: String): VehicleSnapshotCacheEntity =
        VehicleSnapshotCacheEntity(
            generation = generation,
            vehicleId = vehicleId,
            plateNumber = plateNumber,
            normalizedPlate = plateNumber,
            category = "RESIDENT",
            categoryLabel = "村民车辆",
            detailJson = "{}",
        )
}
