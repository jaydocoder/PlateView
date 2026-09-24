package com.jaydocoder.plateview.feature.consistency

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogReconcileActionTest {
    @Test
    fun `版本一致时只确认而不触发目录同步`() {
        assertEquals(CatalogReconcileAction.CONFIRM, catalogReconcileAction(42, 42))
    }

    @Test
    fun `服务器版本较新时仅同步对应目录`() {
        assertEquals(CatalogReconcileAction.SYNCHRONIZE, catalogReconcileAction(41, 42))
    }

    @Test
    fun `权限撤销和服务器版本回退使用独立保护动作`() {
        assertEquals(CatalogReconcileAction.REVOKE, catalogReconcileAction(42, -1))
        assertEquals(CatalogReconcileAction.REJECT_ROLLBACK, catalogReconcileAction(42, 41))
    }

    @Test
    fun `权限撤销必须清空已应用版本以保证恢复后重建`() {
        val revoked = CatalogFreshness(
            kind = CatalogKind.WORK_ORDER,
            appliedRevision = 42,
            observedServerRevision = 42,
            lastConfirmedAtEpochMillis = 100,
            lastSuccessfulSyncAtEpochMillis = 90,
            status = CatalogSyncStatus.CONFIRMED,
            lastErrorCode = "旧错误",
        ).revoked(-1)

        assertEquals(0, revoked.appliedRevision)
        assertEquals(-1, revoked.observedServerRevision)
        assertEquals(0, revoked.lastConfirmedAtEpochMillis)
        assertEquals(0, revoked.lastSuccessfulSyncAtEpochMillis)
        assertEquals(CatalogSyncStatus.PERMISSION_REVOKED, revoked.status)
        assertEquals(null, revoked.lastErrorCode)
    }

    @Test
    fun `策略版本变化会将目录状态重置为待重建`() {
        val reset = CatalogFreshness(
            kind = CatalogKind.ATTACHMENT,
            appliedRevision = 9,
            observedServerRevision = 9,
            lastConfirmedAtEpochMillis = 100,
            lastSuccessfulSyncAtEpochMillis = 90,
            status = CatalogSyncStatus.CONFIRMED,
        ).resetForPolicyChange()

        assertEquals(CatalogFreshness(CatalogKind.ATTACHMENT), reset)
    }

    @Test
    fun `无微信权限时服务器误返回正修订也必须在客户端撤权`() {
        val targets = catalogTargetsForAccount(
            hasWechatAccess = false,
            vehicleRevision = 44,
            workOrderRevision = 12,
            messageRevision = 13,
            attachmentRevision = 14,
        )

        assertEquals(44L, targets[CatalogKind.VEHICLE])
        assertEquals(-1L, targets[CatalogKind.WORK_ORDER])
        assertEquals(-1L, targets[CatalogKind.WECHAT_MESSAGE])
        assertEquals(-1L, targets[CatalogKind.ATTACHMENT])
    }

    @Test
    fun `无权限目录状态不会被网络失败覆盖为离线陈旧`() {
        val revoked = CatalogFreshness(CatalogKind.WORK_ORDER).revoked(-1)
        assertEquals(CatalogSyncStatus.PERMISSION_REVOKED, revoked.status)
        assertEquals(CatalogSyncStatus.PERMISSION_REVOKED, revoked.unavailableAfterNetworkFailure(100_000, "IOException").status)
    }
}
