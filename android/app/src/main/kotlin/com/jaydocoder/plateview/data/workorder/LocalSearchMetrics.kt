package com.jaydocoder.plateview.data.workorder

import java.util.concurrent.ConcurrentLinkedDeque

data class LocalSearchMetric(
    val queryNormalizeMs: Long,
    val vehicleQueryMs: Long = 0,
    val workOrderQueryMs: Long = 0,
    val wechatMessageQueryMs: Long = 0,
    val ftsQueryMs: Long = 0,
    val summaryMappingMs: Long = 0,
    val detailDecodeMs: Long = 0,
    val cacheHit: Boolean,
    val ftsEnabled: Boolean,
    val resultCount: Int,
    val databaseVersion: Int = 9,
)

object LocalSearchMetricsRecorder {
    private const val MAX_SAMPLES = 256
    private val samples = ConcurrentLinkedDeque<LocalSearchMetric>()

    fun record(metric: LocalSearchMetric) {
        samples.addLast(metric)
        while (samples.size > MAX_SAMPLES) samples.pollFirst()
    }

    fun snapshot(): List<LocalSearchMetric> = samples.toList()

    fun clear() = samples.clear()

    /** 返回指定指标的百分位耗时，样本不足时返回当前可用的最高百分位。 */
    fun percentile(selector: (LocalSearchMetric) -> Long, percentile: Double): Long {
        require(percentile in 0.0..1.0) { "百分位必须位于 0 到 1 之间" }
        val values = snapshot().map(selector).sorted()
        if (values.isEmpty()) return 0
        val index = (kotlin.math.ceil(values.size * percentile).toInt() - 1).coerceAtLeast(0)
        return values[index.coerceIn(0, values.lastIndex)]
    }

    fun p50(selector: (LocalSearchMetric) -> Long): Long = percentile(selector, 0.50)

    fun p95(selector: (LocalSearchMetric) -> Long): Long = percentile(selector, 0.95)

    fun p99(selector: (LocalSearchMetric) -> Long): Long = percentile(selector, 0.99)
}
