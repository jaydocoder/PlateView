package com.jaydocoder.plateview.data.admin

import com.jaydocoder.plateview.domain.admin.AdminRepository
import com.jaydocoder.plateview.domain.admin.ImportBatchStats
import com.jaydocoder.plateview.domain.admin.ManagedAuditEntry
import com.jaydocoder.plateview.domain.admin.ManagedImportBatch
import com.jaydocoder.plateview.domain.admin.ManagedImportBatchSummary
import com.jaydocoder.plateview.domain.admin.ManagedImportRow
import com.jaydocoder.plateview.domain.admin.ManagedLongTermProfile
import com.jaydocoder.plateview.domain.admin.ManagedResidentProfile
import com.jaydocoder.plateview.domain.admin.ManagedUser
import com.jaydocoder.plateview.domain.admin.ManagedVehicle
import com.jaydocoder.plateview.domain.admin.ManagedVehiclePage
import com.jaydocoder.plateview.domain.admin.ManagedVehicleSummary
import com.jaydocoder.plateview.domain.admin.UserCreateCommand
import com.jaydocoder.plateview.domain.admin.UserUpdateCommand
import com.jaydocoder.plateview.domain.admin.VehicleWriteCommand
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class NetworkAdminRepository @Inject constructor(
    private val api: AdminApi,
) : AdminRepository {
    override suspend fun listVehicles(
        accessToken: String,
        keyword: String?,
        limit: Int,
        offset: Int,
    ): ManagedVehiclePage = api
        .listVehicles(bearer(accessToken), keyword, limit, offset)
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

    override suspend fun deactivateVehicle(accessToken: String, vehicleId: Long, version: Int): ManagedVehicle = api
        .deactivateVehicle(bearer(accessToken), version, vehicleId)
        .toDomain()

    override suspend fun listUsers(accessToken: String): List<ManagedUser> = api
        .listUsers(bearer(accessToken))
        .items
        .map(AdminUserDto::toDomain)

    override suspend fun createUser(accessToken: String, command: UserCreateCommand): ManagedUser = api
        .createUser(bearer(accessToken), AdminUserCreateRequestDto(command.username, command.password, command.role))
        .toDomain()

    override suspend fun updateUser(
        accessToken: String,
        userId: Long,
        version: Int,
        command: UserUpdateCommand,
    ): ManagedUser = api
        .updateUser(bearer(accessToken), version, userId, AdminUserUpdateRequestDto(command.role, command.status))
        .toDomain()

    override suspend fun listImportBatches(accessToken: String): List<ManagedImportBatchSummary> = api
        .listImports(bearer(accessToken))
        .items
        .map(AdminImportBatchSummaryDto::toDomain)

    override suspend fun getImportBatch(accessToken: String, batchId: Long): ManagedImportBatch = api
        .getImportBatch(bearer(accessToken), batchId)
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

    override suspend fun listAuditEntries(accessToken: String): List<ManagedAuditEntry> = api
        .listAudit(bearer(accessToken))
        .items
        .map(AdminAuditEntryDto::toDomain)

    private fun bearer(accessToken: String): String = "Bearer $accessToken"

    private companion object {
        val EXCEL_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".toMediaType()
    }
}

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

private fun AdminUserDto.toDomain(): ManagedUser = ManagedUser(id, username, role, status, version, createdAt, updatedAt)

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
        stats.duplicateRows,
        stats.errorRows,
        stats.warningRows,
        stats.publishableRows,
        stats.pendingReviewRows,
    ),
    createdAt = createdAt,
    publishedAt = publishedAt,
    rollbackAt = rollbackAt,
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

private fun AdminAuditEntryDto.toDomain(): ManagedAuditEntry = ManagedAuditEntry(
    id, actorUsername, actionType, targetType, targetId, resultStatus, createdAt,
)
