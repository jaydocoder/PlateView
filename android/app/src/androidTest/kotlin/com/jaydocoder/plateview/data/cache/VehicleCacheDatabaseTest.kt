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
    private val userId = 101L

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
    fun 完整快照优先返回村民车辆再按车牌匹配排序() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(
            listOf(
                snapshot(11, 1, "新A12345", "SCENIC_UNIT"),
                snapshot(11, 2, "新A12399", "RESIDENT"),
                snapshot(11, 3, "新B12345", "RESIDENT"),
            ),
        )
        dao.promoteGeneration(userId, 11, 7, 100, 100)

        val result = dao.searchCandidates(userId, "A123", 20)

        assertEquals(listOf(2L, 1L), result.map { it.vehicleId })
    }

    @Test
    fun 切换快照代次后旧数据不再可见() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(listOf(snapshot(11, 1, "新A12345")))
        dao.promoteGeneration(userId, 11, 1, 100, 100)
        dao.insertSnapshots(listOf(snapshot(12, 2, "新A54321")))

        dao.promoteGeneration(userId, 12, 2, 200, 200)

        assertEquals(null, dao.getDetail(userId, 1))
        assertEquals(2L, dao.getDetail(userId, 2)?.vehicleId)
        assertEquals(2L, dao.getCatalogState(userId)?.catalogVersion)
    }

    @Test
    fun 清除快照会删除目录状态和车辆资料() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(listOf(snapshot(11, 1, "新A12345")))
        dao.promoteGeneration(userId, 11, 1, 100, 100)

        dao.clearSnapshot(userId)

        assertEquals(null, dao.getCatalogState(userId))
        assertEquals(null, dao.getDetail(userId, 1))
    }

    @Test
    fun 备注内容可在离线快照中模糊匹配() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(
            listOf(
                snapshot(
                    generation = 11,
                    vehicleId = 1,
                    plateNumber = "新A12345",
                    searchableText = "新A12345 专项通行保障车辆",
                ),
            ),
        )
        dao.promoteGeneration(userId, 11, 7, 100, 100)

        val result = dao.searchCandidates(userId, "保障", 20)

        assertEquals(listOf(1L), result.map { it.vehicleId })
    }

    @Test
    fun 已删除车辆不会从遗留本地快照中被搜索或读取详情() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(listOf(snapshot(11, 1, "新A12345", status = "DELETED")))
        dao.promoteGeneration(userId, 11, 7, 100, 100)

        assertEquals(emptyList<Long>(), dao.searchCandidates(userId, "A123", 20).map { it.vehicleId })
        assertEquals(null, dao.getDetail(userId, 1))
    }

    @Test
    fun 本地快照会持久化档案中的号牌颜色() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(listOf(snapshot(11, 1, "新A12345", plateColor = "黄色")))
        dao.promoteGeneration(userId, 11, 7, 100, 100)

        assertEquals("黄色", dao.searchCandidates(userId, "A123", 20).single().plateColor)
    }

    @Test
    fun V7迁移清空旧快照以重新同步号牌颜色() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(listOf(snapshot(11, 1, "新H13032")))
        dao.promoteGeneration(userId, 11, 7, 100, 100)

        VehicleCacheDatabase.MIGRATION_6_7.migrate(database.openHelper.writableDatabase)

        assertEquals(null, dao.getCatalogState(userId))
        assertEquals(null, dao.getDetail(userId, 1))
    }

    @Test
    fun 增量事务原子更新删除并推进目录版本() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(listOf(snapshot(11, 1, "新A12345"), snapshot(11, 2, "新A54321")))
        dao.promoteGeneration(userId, 11, 7, 100, 100)

        dao.applyChanges(
            userId = userId,
            generation = 11,
            upserts = listOf(snapshot(11, 3, "新A99999")),
            removals = listOf(1),
            catalogVersion = 11,
            checkedAtEpochMillis = 200,
            updatedAtEpochMillis = 200,
        )

        assertEquals(null, dao.getDetail(userId, 1))
        assertEquals(2L, dao.getDetail(userId, 2)?.vehicleId)
        assertEquals(3L, dao.getDetail(userId, 3)?.vehicleId)
        assertEquals(11L, dao.getCatalogState(userId)?.catalogVersion)
    }

    @Test
    fun 不同账号的车辆快照完全隔离() = runBlocking {
        val dao = database.vehicleCacheDao()
        dao.insertSnapshots(listOf(snapshot(11, 1, "新A12345", ownerUserId = 101), snapshot(21, 2, "新B54321", ownerUserId = 202)))
        dao.promoteGeneration(101, 11, 7, 100, 100)
        dao.promoteGeneration(202, 21, 9, 100, 100)

        assertEquals(listOf(1L), dao.searchCandidates(101, "新", 20).map { it.vehicleId })
        assertEquals(listOf(2L), dao.searchCandidates(202, "新", 20).map { it.vehicleId })

        dao.clearSnapshot(101)

        assertEquals(emptyList<Long>(), dao.searchCandidates(101, "新", 20).map { it.vehicleId })
        assertEquals(2L, dao.getDetail(202, 2)?.vehicleId)
    }

    private fun snapshot(
        generation: Long,
        vehicleId: Long,
        plateNumber: String,
        category: String = "RESIDENT",
        searchableText: String = plateNumber,
        status: String = "ACTIVE",
        plateColor: String? = null,
        ownerUserId: Long = userId,
    ): VehicleSnapshotCacheEntity =
        VehicleSnapshotCacheEntity(
            userId = ownerUserId,
            generation = generation,
            vehicleId = vehicleId,
            plateNumber = plateNumber,
            normalizedPlate = plateNumber,
            category = category,
            categoryLabel = if (category == "RESIDENT") "村民车辆" else "驻景区单位车辆",
            organizationName = null,
            plateColor = plateColor,
            status = status,
            searchableText = searchableText,
            detailJson = "{}",
        )
}
