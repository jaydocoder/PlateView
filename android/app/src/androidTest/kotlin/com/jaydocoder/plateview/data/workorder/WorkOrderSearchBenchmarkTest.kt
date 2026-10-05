package com.jaydocoder.plateview.data.workorder

import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.jaydocoder.plateview.data.cache.VehicleCachePassphrase
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.system.measureTimeMillis

/** 可配置的真机查询基准入口。默认使用小数据集冒烟，正式档位通过 searchBenchmarkRows 参数执行。 */
class WorkOrderSearchBenchmarkTest {
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
    fun tearDown() = database.close()

    @Test
    fun 可配置数据集搜索基准() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val rows = args.getString("searchBenchmarkRows")?.toIntOrNull()?.coerceIn(100, 500_000) ?: 1_000
        val index = WorkOrderSearchIndex(database)
        val dao = database.dao()
        val batchSize = 500
        var nextId = 1L
        while (nextId <= rows) {
            val batch = (nextId until (nextId + batchSize).coerceAtMost(rows.toLong() + 1L)).map { id ->
                WorkOrderCacheEntity(
                    userId = 7,
                    recordId = id,
                    orderNumber = "10${(id % 1000000).toString().padStart(5, '0')}",
                    rawPlate = "新H${(id % 10000).toString().padStart(4, '0')}",
                    status = "ACTIVE",
                    sourceName = "基准车单群",
                    location = "禾木",
                    rawValidTime = "10.1-10.7",
                    sentAt = "2026-10-05T01:00:00Z",
                    searchableText = "基准消息车单${id}",
                    catalogRevision = id,
                    cachedAt = id,
                    lastValidatedAt = id,
                    detailJson = "{}",
                    orderYear = 2026,
                    sourceKey = "benchmark",
                    normalizedPlate = "新H${(id % 10000).toString().padStart(4, '0')}",
                    latestGroupKey = "7|benchmark|2026|10${(id % 1000000).toString().padStart(5, '0')}"
                )
            }
            dao.upsert(batch)
            nextId += batch.size
        }
        nextId = 1L
        while (nextId <= rows * 2L) {
            val batch = (nextId until (nextId + batchSize).coerceAtMost(rows.toLong() * 2L + 1L)).map { id ->
                WechatMessageCacheEntity(
                    userId = 7,
                    messageId = id,
                    businessType = "GENERAL_MESSAGE",
                    displayName = "基准发送者",
                    sourceName = "基准聊天群",
                    sentAt = "2026-10-05T01:00:00Z",
                    searchableText = "基准聊天文本${id}",
                    catalogRevision = id,
                    cachedAt = id,
                    lastValidatedAt = id,
                    detailJson = "{}",
                    rawContent = "基准聊天文本${id}",
                    matchedSnippet = "基准聊天文本${id}",
                    sourceKey = "benchmark-chat",
                )
            }
            dao.upsertMessages(batch)
            nextId += batch.size
        }

        index.ensureReady()
        val workOrderSamples = buildList {
            repeat(20) {
                var resultCount = 0
                val elapsed = measureTimeMillis {
                    resultCount = index.searchWorkOrders(7, "新H123", 8).size
                }
                add(elapsed)
                assertTrue(resultCount >= 0)
            }
        }.sorted()
        val messageSamples = buildList {
            repeat(20) {
                var resultCount = 0
                val elapsed = measureTimeMillis {
                    resultCount = index.searchMessages(7, "聊天文本", 8).size
                }
                add(elapsed)
                assertTrue(resultCount >= 0)
            }
        }.sorted()
        fun percentile(samples: List<Long>, p: Double): Long = samples[((samples.size * p).toInt() - 1).coerceAtLeast(0)]
        Log.i("WorkOrderSearchBenchmark", String.format(Locale.US,
            "workOrders=%d messages=%d workOrderP50=%dms workOrderP95=%dms workOrderP99=%dms messageP50=%dms messageP95=%dms messageP99=%dms",
            rows, rows * 2, percentile(workOrderSamples, 0.50), percentile(workOrderSamples, 0.95), percentile(workOrderSamples, 0.99),
            percentile(messageSamples, 0.50), percentile(messageSamples, 0.95), percentile(messageSamples, 0.99)))
        Unit
    }
}
