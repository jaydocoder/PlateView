package com.jaydocoder.plateview.data.workorder

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LocalSearchMetricsTest {
    @Before
    fun setUp() {
        LocalSearchMetricsRecorder.clear()
    }

    @Test
    fun 百分位耗时按升序返回并支持空样本() {
        assertEquals(0L, LocalSearchMetricsRecorder.p95 { it.workOrderQueryMs })
        listOf(10L, 20L, 30L, 40L).forEach { elapsed ->
            LocalSearchMetricsRecorder.record(
                LocalSearchMetric(
                    queryNormalizeMs = 1,
                    workOrderQueryMs = elapsed,
                    cacheHit = false,
                    ftsEnabled = true,
                    resultCount = 1,
                ),
            )
        }

        assertEquals(20L, LocalSearchMetricsRecorder.p50 { it.workOrderQueryMs })
        assertEquals(40L, LocalSearchMetricsRecorder.p95 { it.workOrderQueryMs })
        assertEquals(40L, LocalSearchMetricsRecorder.p99 { it.workOrderQueryMs })
    }

    @Test(expected = IllegalArgumentException::class)
    fun 百分位参数超出范围时拒绝() {
        LocalSearchMetricsRecorder.percentile({ it.workOrderQueryMs }, 1.1)
    }
}
