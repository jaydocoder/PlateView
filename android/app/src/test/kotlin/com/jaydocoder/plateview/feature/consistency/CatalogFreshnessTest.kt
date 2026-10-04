package com.jaydocoder.plateview.feature.consistency

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFreshnessTest {
    @Test
    fun `确认状态在五分钟边界内视为新鲜`() {
        val now = 100_000L
        val state = CatalogFreshness(
            kind = CatalogKind.VEHICLE,
            lastConfirmedAtEpochMillis = now - 5 * 60 * 1_000L,
            status = CatalogSyncStatus.CONFIRMED,
        )

        assertTrue(state.isConfirmed(now))
    }

    @Test
    fun `超过五分钟或状态异常时不能显示核验就绪`() {
        val now = 100_000L
        assertFalse(
            CatalogFreshness(
                kind = CatalogKind.WORK_ORDER,
                lastConfirmedAtEpochMillis = now - (5 * 60 * 1_000L + 1L),
                status = CatalogSyncStatus.CONFIRMED,
            ).isConfirmed(now),
        )
        assertFalse(
            CatalogFreshness(
                kind = CatalogKind.WORK_ORDER,
                lastConfirmedAtEpochMillis = now,
                status = CatalogSyncStatus.OFFLINE_STALE,
            ).isConfirmed(now),
        )
    }
}
