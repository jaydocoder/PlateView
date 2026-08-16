package com.jaydocoder.plateview.data.admin

import com.google.gson.JsonObject

data class AdminVehicleListResponseDto(val items: List<AdminVehicleListItemDto>, val total: Int)
data class AdminVehicleListItemDto(val id: Long, val plateNumber: String, val category: String, val categoryLabel: String, val status: String, val version: Int, val vehicleType: String?)
data class AdminVehicleDto(val id: Long, val plateNumber: String, val normalizedPlate: String, val category: String, val categoryLabel: String, val status: String, val version: Int, val vehicleType: String?, val attributes: JsonObject, val residentProfile: AdminResidentProfileDto?, val longTermProfile: AdminLongTermProfileDto?)
data class AdminResidentProfileDto(val ownerName: String, val identityCardNumber: String, val contactPhone: String?, val remarks: String?)
data class AdminLongTermProfileDto(val organizationName: String?, val passHolder: String?, val passageDetails: String?, val remarks: String?)
data class AdminVehicleWriteRequestDto(val plateNumber: String, val category: String, val vehicleType: String?, val status: String, val attributes: Map<String, String>, val residentProfile: AdminResidentProfileDto?, val longTermProfile: AdminLongTermProfileDto?)

data class AdminUserListResponseDto(val items: List<AdminUserDto>)
data class AdminUserDto(val id: Long, val username: String, val role: String, val status: String, val version: Int, val createdAt: String?, val updatedAt: String?)
data class AdminUserCreateRequestDto(val username: String, val password: String, val role: String)
data class AdminUserUpdateRequestDto(val role: String, val status: String)

data class AdminImportBatchListResponseDto(val items: List<AdminImportBatchSummaryDto>)
data class AdminImportBatchSummaryDto(val id: Long, val sourceFileName: String, val status: String, val totalRows: Int, val validRows: Int, val duplicateRows: Int, val errorRows: Int, val version: Int, val createdAt: String?, val publishedAt: String?, val rollbackAt: String?)
data class AdminImportBatchDto(val id: Long, val sourceFileName: String, val status: String, val stats: AdminImportBatchStatsDto, val createdAt: String?, val publishedAt: String?, val rollbackAt: String?, val rowTotal: Int, val rows: List<AdminImportRowDto>)
data class AdminImportBatchStatsDto(val totalRows: Int, val newRows: Int, val updateRows: Int, val reactivateRows: Int = 0, val deactivateRows: Int = 0, val duplicateRows: Int, val errorRows: Int, val warningRows: Int, val publishableRows: Int, val pendingReviewRows: Int)
data class AdminImportRowDto(val id: Long, val sourceSheetName: String, val sourceRowNumber: Int, val sourceItemIndex: Int, val plateNumber: String?, val normalizedPlate: String?, val category: String?, val primarySubject: String?, val resultStatus: String, val plannedAction: String, val resolution: String, val errorMessage: String?, val warningMessage: String?)
data class AdminImportRowDetailDto(val row: AdminImportRowDto, val sections: List<AdminImportDiffSectionDto>, val sourceValues: List<AdminImportSourceValueDto>)
data class AdminImportDiffSectionDto(val title: String, val fields: List<AdminImportFieldDifferenceDto>)
data class AdminImportFieldDifferenceDto(val label: String, val before: String?, val after: String?)
data class AdminImportSourceValueDto(val label: String, val value: String)
data class AdminImportResolutionRequestDto(val rows: List<AdminImportResolutionDto>)
data class AdminImportResolutionDto(val rowId: Long, val resolution: String)

data class AdminAuditListResponseDto(
    val items: List<AdminAuditEntryDto>,
    val total: Int,
    val summary: AdminAuditSummaryDto,
    val actors: List<AdminAuditActorDto>,
    val actionTypes: List<String>,
)
data class AdminAuditEntryDto(val id: Long, val actorUsername: String?, val actionType: String, val targetType: String, val targetId: Long?, val resultStatus: String, val createdAt: String)
data class AdminAuditSummaryDto(val total: Int, val successCount: Int, val abnormalCount: Int, val activeActorCount: Int)
data class AdminAuditActorDto(val id: Long, val username: String?)
