package com.jaydocoder.plateview.domain.admin

import com.jaydocoder.plateview.domain.workorder.AttachmentDownloadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AdminRepository {
    suspend fun getDashboardSummary(accessToken: String): AdminDashboardSummary
    suspend fun getClientPolicy(accessToken: String): ClientPolicy = error("当前仓库未实现客户端策略")
    suspend fun updateClientPolicy(accessToken: String, command: ClientPolicyUpdateCommand): ClientPolicy = error("当前仓库未实现客户端策略")
    suspend fun updateClientPolicyLimits(accessToken: String, command: ClientPolicyLimitsCommand): ClientPolicy = error("当前仓库未实现客户端数量策略")
    suspend fun updateApiEndpoint(accessToken: String, baseUrl: String): ClientPolicy = error("当前仓库未实现后台地址策略")
    suspend fun updateUpdateEndpoint(accessToken: String, baseUrl: String): ClientPolicy = error("当前仓库未实现更新地址策略")
    suspend fun testApiEndpoint(accessToken: String, baseUrl: String): String = error("当前仓库未实现后台地址测试")
    suspend fun testUpdateEndpoint(accessToken: String, baseUrl: String): String = error("当前仓库未实现更新地址测试")
    suspend fun requestUserCacheReset(accessToken: String, userId: Long): CacheResetStatus = error("当前仓库未实现远程清缓存")

    suspend fun getVehicleCreationCapabilities(accessToken: String): VehicleCreationCapabilities

    suspend fun listVehicles(
        accessToken: String,
        keyword: String? = null,
        status: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): ManagedVehiclePage
    suspend fun getVehicle(accessToken: String, vehicleId: Long): ManagedVehicle
    suspend fun createVehicle(accessToken: String, command: VehicleWriteCommand): ManagedVehicle
    suspend fun updateVehicle(accessToken: String, vehicleId: Long, version: Int, command: VehicleWriteCommand): ManagedVehicle
    suspend fun updateVehicleStatus(accessToken: String, vehicleId: Long, version: Int, status: String): ManagedVehicle

    suspend fun listUsers(accessToken: String): List<ManagedUser>
    suspend fun createUser(accessToken: String, command: UserCreateCommand): ManagedUser
    suspend fun updateUser(accessToken: String, userId: Long, version: Int, command: UserUpdateCommand): ManagedUser

    suspend fun listImportBatches(accessToken: String): List<ManagedImportBatchSummary>
    suspend fun getImportBatch(
        accessToken: String,
        batchId: Long,
        limit: Int = 100,
        offset: Int = 0,
        filter: ImportRowFilter = ImportRowFilter.REVIEW,
    ): ManagedImportBatch
    suspend fun getImportRowDetail(accessToken: String, batchId: Long, rowId: Long): ManagedImportRowDetail
    suspend fun previewImport(accessToken: String, fileName: String, content: ByteArray): ManagedImportBatch
    suspend fun updateImportResolution(accessToken: String, batchId: Long, rowId: Long, resolution: String): ManagedImportBatch
    suspend fun publishImport(accessToken: String, batchId: Long): ManagedImportBatch
    suspend fun rollbackImport(accessToken: String, batchId: Long): ManagedImportBatch

    suspend fun listAuditEntries(
        accessToken: String,
        filter: AuditFilter = AuditFilter(),
        limit: Int = 50,
        offset: Int = 0,
    ): ManagedAuditPage

    suspend fun getWechatSyncStatus(accessToken: String): List<WechatSyncSource>
    suspend fun getWechatSyncOverview(accessToken: String, cachePage: Int = 1, cachePageSize: Int = 10): WechatSyncOverview
    suspend fun getWechatCacheStatus(accessToken: String, page: Int = 1, pageSize: Int = 10): WechatCacheStatusSummary
    suspend fun getWechatPassageSenders(accessToken: String): List<WechatPassageSender>
    suspend fun saveWechatPassageSender(accessToken: String, sender: WechatPassageSender)
    suspend fun correctWechatWorkOrder(accessToken: String, recordId: Long, command: WorkOrderCorrectionCommand)
    suspend fun associateWechatImage(accessToken: String, imageId: Long, recordId: Long)
    suspend fun removeWechatImageAssociation(accessToken: String, imageId: Long)
    suspend fun ignoreWechatImage(accessToken: String, imageId: Long)
    suspend fun downloadWechatAttachment(
        accessToken: String,
        userId: Long,
        imageId: Long,
        variant: String = "preview",
        sha256: String? = null,
        sourceQuality: String = "UNKNOWN",
        kind: String = "IMAGE",
        fileName: String? = null,
    ): CachedAdminAttachment
    fun observeWechatAttachmentDownload(userId: Long, imageId: Long): Flow<AttachmentDownloadState?> = flowOf(null)
    suspend fun searchWechatWorkOrders(accessToken: String, keyword: String): List<WechatWorkOrderSearchItem>
}
