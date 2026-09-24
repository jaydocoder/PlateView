package com.jaydocoder.plateview.server.client

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogStateServiceTest {
    @Test
    fun `无微信权限账号不会获得微信目录修订号`() {
        val response = CatalogRevisionSnapshot(
            vehicleRevision = 11,
            workOrderRevision = 12,
            wechatMessageRevision = 13,
            attachmentManifestRevision = 14,
            loadedAtMillis = 0,
        ).visibleTo(
            visibility = CatalogVisibility(
                vehicleBits = 0,
                policyRevision = 5,
                vehicleAllowed = true,
                wechatAccessEnabled = false,
                workOrderAllowedByPolicy = true,
                messageAllowedByPolicy = true,
            ),
            serverTime = Instant.parse("2026-09-24T08:00:00Z"),
        )

        assertEquals(44, response.vehicleRevision)
        assertEquals(-1, response.workOrderRevision)
        assertEquals(-1, response.wechatMessageRevision)
        assertEquals(-1, response.attachmentManifestRevision)
    }

    @Test
    fun `有微信权限时仍按两类结果上限分别发布目录修订号`() {
        val response = CatalogRevisionSnapshot(
            vehicleRevision = 11,
            workOrderRevision = 12,
            wechatMessageRevision = 13,
            attachmentManifestRevision = 14,
            loadedAtMillis = 0,
        ).visibleTo(
            visibility = CatalogVisibility(
                vehicleBits = 0,
                policyRevision = 5,
                vehicleAllowed = true,
                wechatAccessEnabled = true,
                workOrderAllowedByPolicy = true,
                messageAllowedByPolicy = false,
            ),
            serverTime = Instant.parse("2026-09-24T08:00:00Z"),
        )

        assertEquals(12, response.workOrderRevision)
        assertEquals(-1, response.wechatMessageRevision)
        assertEquals(58, response.attachmentManifestRevision)
    }
}
