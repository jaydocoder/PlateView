package com.jaydocoder.plateview.feature.auth

import com.jaydocoder.plateview.feature.consistency.ClientCatalogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountCatalogStateCacheTest {
    @Test
    fun 不同账号的目录状态不会互相覆盖() {
        var now = 100L
        val cache = AccountCatalogStateCache { now }
        val first = state(vehicleRevision = 11)
        val second = state(vehicleRevision = 22)

        cache.remember(1, first)
        now = 200L
        cache.remember(2, second)

        assertEquals(first, cache.get(1)?.state)
        assertEquals(100L, cache.get(1)?.receivedAtEpochMillis)
        assertEquals(second, cache.get(2)?.state)
        assertEquals(200L, cache.get(2)?.receivedAtEpochMillis)
    }

    @Test
    fun 退出账号只清除对应账号的目录状态() {
        val cache = AccountCatalogStateCache { 100L }
        cache.remember(1, state(vehicleRevision = 11))
        cache.remember(2, state(vehicleRevision = 22))

        cache.remove(1)

        assertNull(cache.get(1))
        assertEquals(22L, cache.get(2)?.state?.vehicleRevision)
    }

    private fun state(vehicleRevision: Long) = ClientCatalogState(
        vehicleRevision = vehicleRevision,
        workOrderRevision = 2,
        wechatMessageRevision = 3,
        attachmentManifestRevision = 4,
        policyRevision = 5,
        serverTime = "2026-09-24T00:00:00Z",
    )
}
