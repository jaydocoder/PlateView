package com.jaydocoder.plateview.data.admin

import com.jaydocoder.plateview.domain.admin.CachedAdminAttachment
import com.jaydocoder.plateview.domain.admin.AdminRepository
import com.jaydocoder.plateview.domain.admin.AuditFilter
import com.jaydocoder.plateview.domain.admin.ImportBatchStats
import com.jaydocoder.plateview.domain.admin.ImportRowFilter
import com.jaydocoder.plateview.domain.admin.ManagedAuditEntry
import com.jaydocoder.plateview.domain.admin.ManagedAuditActor
import com.jaydocoder.plateview.domain.admin.ManagedAuditPage
import com.jaydocoder.plateview.domain.admin.ManagedAuditSummary
import com.jaydocoder.plateview.domain.admin.ManagedImportBatch
import com.jaydocoder.plateview.domain.admin.ManagedImportBatchSummary
import com.jaydocoder.plateview.domain.admin.ManagedImportRow
import com.jaydocoder.plateview.domain.admin.ManagedImportRowDetail
import com.jaydocoder.plateview.domain.admin.ManagedImportDiffSection
import com.jaydocoder.plateview.domain.admin.ManagedImportFieldDifference
import com.jaydocoder.plateview.domain.admin.ManagedImportSourceValue
import com.jaydocoder.plateview.domain.admin.ManagedLongTermProfile
import com.jaydocoder.plateview.domain.admin.ManagedResidentProfile
import com.jaydocoder.plateview.domain.admin.ManagedUser
import com.jaydocoder.plateview.domain.admin.ManagedVehicle
import com.jaydocoder.plateview.domain.admin.ManagedVehiclePage
import com.jaydocoder.plateview.domain.admin.ManagedVehicleSummary
import com.jaydocoder.plateview.domain.admin.VehicleCreationCapabilities
import com.jaydocoder.plateview.domain.admin.UserCreateCommand
import com.jaydocoder.plateview.domain.admin.UserUpdatePolicy
import com.jaydocoder.plateview.domain.admin.WechatSyncSource
import com.jaydocoder.plateview.domain.admin.WechatSyncIssue
import com.jaydocoder.plateview.domain.admin.WechatSyncOverview
import com.jaydocoder.plateview.domain.admin.WorkOrderCorrectionCommand
import com.jaydocoder.plateview.domain.admin.UserUpdateCommand
import com.jaydocoder.plateview.domain.admin.ClientPolicy
import com.jaydocoder.plateview.domain.admin.ClientPolicyUpdateCommand
import com.jaydocoder.plateview.domain.admin.ClientPolicyLimitsCommand
import com.jaydocoder.plateview.domain.admin.CacheResetStatus
import com.jaydocoder.plateview.domain.admin.VehicleWriteCommand
import com.jaydocoder.plateview.data.workorder.WechatAttachmentCacheRepository
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class NetworkAdminRepository @Inject constructor(
    private val api: AdminApi,
    private val attachmentCacheRepository: WechatAttachmentCacheRepository,
) : AdminRepository {
    override suspend fun getDashboardSummary(accessToken: String) = api.getDashboardSummary(bearer(accessToken)).let {
        com.jaydocoder.plateview.domain.admin.AdminDashboardSummary(
            it.vehicleCount,
            it.userCount,
            it.importBatchCount,
            it.isPrimaryAdministrator,
            it.showSchedulePlanner,
            it.showWechatSync,
            it.updatedAt,
            it.revision,
        )
    }
    override suspend fun getClientPolicy(accessToken: String): ClientPolicy = api.getClientPolicy(bearer(accessToken)).toDomain()

    override suspend fun updateClientPolicy(accessToken: String, command: ClientPolicyUpdateCommand): ClientPolicy = api
        .updateClientPolicy(
            bearer(accessToken),
            ClientPolicyUpdateRequestDto(
                command.vehicleResultLimit,
                command.workOrderResultLimit,
                command.wechatMessageResultLimit,
                command.apiBaseUrl,
                command.updateBaseUrl,
            ),
        ).toDomain()

    override suspend fun updateClientPolicyLimits(accessToken: String, command: ClientPolicyLimitsCommand): ClientPolicy = api
        .updateClientPolicyLimits(
            bearer(accessToken),
            ClientPolicyLimitsUpdateRequestDto(command.vehicleResultLimit, command.workOrderResultLimit, command.wechatMessageResultLimit),
        ).toDomain()

    override suspend fun updateApiEndpoint(accessToken: String, baseUrl: String): ClientPolicy = api
        .updateApiEndpoint(bearer(accessToken), EndpointTestRequestDto(baseUrl)).toDomain()

    override suspend fun updateUpdateEndpoint(accessToken: String, baseUrl: String): ClientPolicy = api
        .updateUpdateEndpoint(bearer(accessToken), EndpointTestRequestDto(baseUrl)).toDomain()

    override suspend fun testApiEndpoint(accessToken: String, baseUrl: String): String = api
        .testApiEndpoint(bearer(accessToken), EndpointTestRequestDto(baseUrl)).message

    override suspend fun testUpdateEndpoint(accessToken: String, baseUrl: String): String = api
        .testUpdateEndpoint(bearer(accessToken), EndpointTestRequestDto(baseUrl)).message

    override suspend fun requestUserCacheReset(accessToken: String, userId: Long): CacheResetStatus = api
        .requestUserCacheReset(bearer(accessToken), userId).toDomain()

    override suspend fun getVehicleCreationCapabilities(accessToken: String): VehicleCreationCapabilities = api
        .getVehicleCreationCapabilities(bearer(accessToken))
        .let { VehicleCreationCapabilities(it.creatableCategories, it.canChangeVehicleCategory) }

    override suspend fun listVehicles(
        accessToken: String,
        keyword: String?,
        status: String?,
        limit: Int,
        offset: Int,
    ): ManagedVehiclePage = api
        .listVehicles(bearer(accessToken), keyword, status, limit, offset)
        .let { response -> ManagedVehiclePage(response.items.map(AdminVehicleListItemDto::toDomain), response.total) }

    override suspend fun getVehicle(accessToken: String, vehicleId: Long): ManagedVehicle = api
        .getVehicle(bearer(accessToken), vehicleId)
        .toDomain()

    override suspend fun createVehicle(accessToken: String, command: VehicleWriteCommand): ManagedVehicle = api
        .createVehicle(bearer(accessToken), command.toRequest())
        .toDomain()

    override suspend fun updateVehicle(
        accessToken: String,
        vehicleId: Long,
        version: Int,
        command: VehicleWriteCommand,
    ): ManagedVehicle = api
        .updateVehicle(bearer(accessToken), version, vehicleId, command.toRequest())
        .toDomain()

    override suspend fun updateVehicleStatus(accessToken: String, vehicleId: Long, version: Int, status: String): ManagedVehicle = api
        .updateVehicleStatus(bearer(accessToken), version, vehicleId, AdminVehicleStatusUpdateRequestDto(status))
        .toDomain()

    override suspend fun listUsers(accessToken: String): List<ManagedUser> = api
        .listUsers(bearer(accessToken))
        .items
        .map(AdminUserDto::toDomain)

    override suspend fun createUser(accessToken: String, command: UserCreateCommand): ManagedUser = api
        .createUser(bearer(accessToken), AdminUserCreateRequestDto(command.username, command.password, command.role, command.realName, command.scheduleAccessEnabled))
        .toDomain()

    override suspend fun updateUser(
        accessToken: String,
        userId: Long,
        version: Int,
        command: UserUpdateCommand,
    ): ManagedUser = api
        .updateUser(
            bearer(accessToken),
            version,
            userId,
            AdminUserUpdateRequestDto(command.role, command.status, command.username, command.password, command.realName, command.scheduleAccessEnabled, command.updatePolicy?.name, command.otherLongTermAccessEnabled, command.residentRemarksAccessEnabled, command.wechatWorkOrderAccessEnabled),
        )
        .toDomain()

    override suspend fun listImportBatches(accessToken: String): List<ManagedImportBatchSummary> = api
        .listImports(bearer(accessToken))
        .items
        .map(AdminImportBatchSummaryDto::toDomain)

    override suspend fun getImportBatch(
        accessToken: String,
        batchId: Long,
        limit: Int,
        offset: Int,
        filter: ImportRowFilter,
    ): ManagedImportBatch = api
        .getImportBatch(bearer(accessToken), batchId, limit, offset, filter.requestValue)
        .toDomain()

    override suspend fun getImportRowDetail(accessToken: String, batchId: Long, rowId: Long): ManagedImportRowDetail = api
        .getImportRowDetail(bearer(accessToken), batchId, rowId)
        .toDomain()

    override suspend fun previewImport(accessToken: String, fileName: String, content: ByteArray): ManagedImportBatch {
        val requestBody = content.toRequestBody(EXCEL_MEDIA_TYPE)
        val file = MultipartBody.Part.createFormData("file", fileName, requestBody)
        return api.previewImport(bearer(accessToken), file).toDomain()
    }

    override suspend fun updateImportResolution(
        accessToken: String,
        batchId: Long,
        rowId: Long,
        resolution: String,
    ): ManagedImportBatch = api
        .updateImportResolution(
            bearer(accessToken),
            batchId,
            AdminImportResolutionRequestDto(listOf(AdminImportResolutionDto(rowId, resolution))),
        )
        .toDomain()

    override suspend fun publishImport(accessToken: String, batchId: Long): ManagedImportBatch = api
        .publishImport(bearer(accessToken), batchId)
        .toDomain()

    override suspend fun rollbackImport(accessToken: String, batchId: Long): ManagedImportBatch = api
        .rollbackImport(bearer(accessToken), batchId)
        .toDomain()

    override suspend fun listAuditEntries(
        accessToken: String,
        filter: AuditFilter,
        limit: Int,
        offset: Int,
    ): ManagedAuditPage = api
        .listAudit(
            authorization = bearer(accessToken),
            range = filter.range.requestValue,
            actorId = filter.actorId,
            actionType = filter.actionType,
            result = filter.result.requestValue,
            keyword = filter.keyword.trim().ifEmpty { null },
            limit = limit,
            offset = offset,
        )
        .let { response ->
            ManagedAuditPage(
                items = response.items.map(AdminAuditEntryDto::toDomain),
                total = response.total,
                summary = ManagedAuditSummary(
                    total = response.summary.total,
                    successCount = response.summary.successCount,
                    abnormalCount = response.summary.abnormalCount,
                    activeActorCount = response.summary.activeActorCount,
                ),
                actors = response.actors.map { ManagedAuditActor(it.id, it.username) },
                actionTypes = response.actionTypes,
            )
        }

    private fun bearer(accessToken: String): String = "Bearer $accessToken"

    private companion object {
        val EXCEL_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".toMediaType()
    }

    override suspend fun getWechatSyncStatus(accessToken: String): List<WechatSyncSource> = api
        .getWechatSyncStatus(bearer(accessToken))
        .sources
        .map { WechatSyncSource(it.sourceKey, it.displayName, it.status, it.latestMessageAt, it.lastHeartbeatAt, it.lastUploadedAt, it.backlogCount, it.errorCode) }

    override suspend fun getWechatSyncOverview(accessToken: String): WechatSyncOverview {
        val response = api.getWechatSyncIssues(bearer(accessToken))
        val cacheStatus = api.getWechatCacheStatus(bearer(accessToken))
        val issues = response.items.map {
            WechatSyncIssue(
                it.type, it.recordId, it.imageId, it.sourceName, it.sentAt, it.summary, it.attachmentKind, it.fileName,
                it.pageCount,
                it.sha256,
                it.sourceQuality,
                it.candidates.map { candidate -> com.jaydocoder.plateview.domain.admin.WechatAttachmentCandidate(candidate.recordId, candidate.orderNumber, candidate.sentAt, candidate.summary) },
            )
        }
        return WechatSyncOverview(
            issues = issues,
            totalAttachmentCount = response.totalAttachmentCount,
            completedAttachmentCount = response.completedAttachmentCount,
            pendingAttachmentCount = response.pendingAttachmentCount,
            integrity = com.jaydocoder.plateview.domain.admin.WechatSyncIntegrity(
                response.integrity.unconfirmedBatchCount,
                response.integrity.retryTaskCount,
                response.integrity.metadataOnlyAttachmentCount,
                response.integrity.failedTaskCount,
                response.integrity.status,
            ),
            cacheStatus = com.jaydocoder.plateview.domain.admin.WechatCacheStatusSummary(
                cacheStatus.clientCount, cacheStatus.completedCount, cacheStatus.pendingCount, cacheStatus.failedCount, cacheStatus.totalBytes,
            ),
        )
    }

    override suspend fun getWechatPassageSenders(accessToken: String) = api.getWechatPassageSenders(bearer(accessToken)).items.map {
        com.jaydocoder.plateview.domain.admin.WechatPassageSender(it.senderUsername, it.originalDisplayName, it.displayAlias, it.enabled)
    }

    override suspend fun saveWechatPassageSender(accessToken: String, sender: com.jaydocoder.plateview.domain.admin.WechatPassageSender) {
        api.saveWechatPassageSender(
            bearer(accessToken), sender.senderUsername,
            WechatPassageSenderRequestDto(sender.originalDisplayName, sender.displayAlias, sender.enabled),
        )
    }

    override suspend fun correctWechatWorkOrder(accessToken: String, recordId: Long, command: WorkOrderCorrectionCommand) {
        api.correctWechatWorkOrder(bearer(accessToken), recordId, WorkOrderCorrectionRequestDto(command.orderNumber, command.rawPlate, command.status))
    }

    override suspend fun associateWechatImage(accessToken: String, imageId: Long, recordId: Long) {
        api.associateWechatImage(bearer(accessToken), imageId, WorkOrderImageAssociationRequestDto(recordId))
    }

    override suspend fun removeWechatImageAssociation(accessToken: String, imageId: Long) {
        api.removeWechatImageAssociation(bearer(accessToken), imageId)
    }

    override suspend fun ignoreWechatImage(accessToken: String, imageId: Long) {
        api.ignoreWechatImage(bearer(accessToken), imageId)
    }

    override suspend fun downloadWechatAttachment(
        accessToken: String,
        userId: Long,
        imageId: Long,
        variant: String,
        sha256: String?,
        sourceQuality: String,
    ): CachedAdminAttachment {
        val cached = attachmentCacheRepository.getOrDownload(
            userId = userId,
            attachmentId = imageId,
            variant = variant,
            sha256 = sha256,
            sourceQuality = sourceQuality,
            request = { range -> api.downloadWechatAttachment(bearer(accessToken), range, imageId, variant) },
        )
        return CachedAdminAttachment(cached.file, cached.variant)
    }

    override suspend fun searchWechatWorkOrders(accessToken: String, keyword: String) =
        api.searchWechatWorkOrders(bearer(accessToken), keyword).candidates.map {
            com.jaydocoder.plateview.domain.admin.WechatWorkOrderSearchItem(it.id, it.orderNumber, it.sentAt, it.rawContent.take(160))
        }
}

private fun ClientPolicyDto.toDomain() = ClientPolicy(
    revision, vehicleResultLimit, workOrderResultLimit, wechatMessageResultLimit,
    apiBaseUrl, previousApiBaseUrl, updateBaseUrl, previousUpdateBaseUrl, updatedAt,
    clientCount, appliedClientCount, lastConfirmedAt, cacheResetStatuses.map(CacheResetStatusDto::toDomain),
)

private fun CacheResetStatusDto.toDomain() = CacheResetStatus(
    userId, revision, expectedClientCount, completedClientCount, status, lastConfirmedAt,
)

private fun VehicleWriteCommand.toRequest(): AdminVehicleWriteRequestDto = AdminVehicleWriteRequestDto(
    plateNumber = plateNumber,
    category = category,
    vehicleType = vehicleType,
    status = status,
    attributes = attributes,
    residentProfile = residentProfile?.let { AdminResidentProfileDto(it.ownerName, it.identityCardNumber, it.contactPhone, it.remarks) },
    longTermProfile = longTermProfile?.let { AdminLongTermProfileDto(it.organizationName, it.passHolder, it.passageDetails, it.remarks) },
)

private fun AdminVehicleListItemDto.toDomain(): ManagedVehicleSummary = ManagedVehicleSummary(
    id = id,
    plateNumber = plateNumber,
    category = category,
    categoryLabel = categoryLabel,
    status = status,
    version = version,
    vehicleType = vehicleType,
)

private fun AdminVehicleDto.toDomain(): ManagedVehicle = ManagedVehicle(
    id = id,
    plateNumber = plateNumber,
    normalizedPlate = normalizedPlate,
    category = category,
    categoryLabel = categoryLabel,
    status = status,
    version = version,
    vehicleType = vehicleType,
    attributes = attributes.entrySet().mapNotNull { (key, value) ->
        value.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString?.let { key to it }
    }.toMap(),
    residentProfile = residentProfile?.let { ManagedResidentProfile(it.ownerName, it.identityCardNumber, it.contactPhone, it.remarks) },
    longTermProfile = longTermProfile?.let { ManagedLongTermProfile(it.organizationName, it.passHolder, it.passageDetails, it.remarks) },
)

private fun AdminUserDto.toDomain(): ManagedUser = ManagedUser(
    id = id,
    username = username,
    role = role,
    status = status,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt,
    avatarVersion = avatarVersion,
    hasAvatar = hasAvatar,
    realName = realName,
    scheduleAccessEnabled = scheduleAccessEnabled,
    updatePolicy = runCatching { UserUpdatePolicy.valueOf(updatePolicy) }.getOrDefault(UserUpdatePolicy.OPTIONAL),
    otherLongTermAccessEnabled = otherLongTermAccessEnabled,
    residentRemarksAccessEnabled = residentRemarksAccessEnabled,
    wechatWorkOrderAccessEnabled = wechatWorkOrderAccessEnabled,
)

private fun AdminImportBatchSummaryDto.toDomain(): ManagedImportBatchSummary = ManagedImportBatchSummary(
    id, sourceFileName, status, totalRows, validRows, duplicateRows, errorRows, version, createdAt, publishedAt, rollbackAt,
)

private fun AdminImportBatchDto.toDomain(): ManagedImportBatch = ManagedImportBatch(
    id = id,
    sourceFileName = sourceFileName,
    status = status,
    stats = ImportBatchStats(
        stats.totalRows,
        stats.newRows,
        stats.updateRows,
        stats.reactivateRows,
        stats.deactivateRows,
        stats.duplicateRows,
        stats.errorRows,
        stats.warningRows,
        stats.publishableRows,
        stats.pendingReviewRows,
    ),
    createdAt = createdAt,
    publishedAt = publishedAt,
    rollbackAt = rollbackAt,
    rowTotal = rowTotal,
    rows = rows.map { row ->
        ManagedImportRow(
            row.id,
            row.sourceSheetName,
            row.sourceRowNumber,
            row.sourceItemIndex,
            row.plateNumber,
            row.category,
            row.primarySubject,
            row.resultStatus,
            row.plannedAction,
            row.resolution,
            row.errorMessage,
            row.warningMessage,
        )
    },
)

private fun AdminImportRowDetailDto.toDomain(): ManagedImportRowDetail = ManagedImportRowDetail(
    row = row.toDomain(),
    sections = sections.map { section ->
        ManagedImportDiffSection(
            title = section.title,
            fields = section.fields.map { field -> ManagedImportFieldDifference(field.label, field.before, field.after) },
        )
    },
    sourceValues = sourceValues.map { value -> ManagedImportSourceValue(value.label, value.value) },
)

private fun AdminImportRowDto.toDomain(): ManagedImportRow = ManagedImportRow(
    id,
    sourceSheetName,
    sourceRowNumber,
    sourceItemIndex,
    plateNumber,
    category,
    primarySubject,
    resultStatus,
    plannedAction,
    resolution,
    errorMessage,
    warningMessage,
)

private fun AdminAuditEntryDto.toDomain(): ManagedAuditEntry = ManagedAuditEntry(
    id, actorUsername, actionType, targetType, targetId, resultStatus, createdAt,
)
