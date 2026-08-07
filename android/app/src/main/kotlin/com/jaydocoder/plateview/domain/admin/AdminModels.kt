package com.jaydocoder.plateview.domain.admin

data class ManagedVehicleSummary(
    val id: Long,
    val plateNumber: String,
    val category: String,
    val categoryLabel: String,
    val status: String,
    val version: Int,
    val vehicleType: String?,
)

data class ManagedVehiclePage(
    val items: List<ManagedVehicleSummary>,
    val total: Int,
)

data class ManagedVehicle(
    val id: Long,
    val plateNumber: String,
    val normalizedPlate: String,
    val category: String,
    val categoryLabel: String,
    val status: String,
    val version: Int,
    val vehicleType: String?,
    val attributes: Map<String, String>,
    val residentProfile: ManagedResidentProfile?,
    val longTermProfile: ManagedLongTermProfile?,
)

data class ManagedResidentProfile(
    val ownerName: String,
    val identityCardNumber: String,
    val contactPhone: String?,
    val remarks: String?,
)

data class ManagedLongTermProfile(
    val organizationName: String?,
    val passHolder: String?,
    val passageDetails: String?,
    val remarks: String?,
)

data class VehicleWriteCommand(
    val plateNumber: String,
    val category: String,
    val vehicleType: String?,
    val status: String,
    val attributes: Map<String, String>,
    val residentProfile: ManagedResidentProfile?,
    val longTermProfile: ManagedLongTermProfile?,
)

data class ManagedUser(
    val id: Long,
    val username: String,
    val role: String,
    val status: String,
    val version: Int,
    val createdAt: String?,
    val updatedAt: String?,
)

data class UserCreateCommand(
    val username: String,
    val password: String,
    val role: String,
)

data class UserUpdateCommand(
    val role: String,
    val status: String,
)

data class ManagedImportBatchSummary(
    val id: Long,
    val sourceFileName: String,
    val status: String,
    val totalRows: Int,
    val validRows: Int,
    val duplicateRows: Int,
    val errorRows: Int,
    val version: Int,
    val createdAt: String?,
    val publishedAt: String?,
    val rollbackAt: String?,
)

data class ManagedImportBatch(
    val id: Long,
    val sourceFileName: String,
    val status: String,
    val stats: ImportBatchStats,
    val createdAt: String?,
    val publishedAt: String?,
    val rollbackAt: String?,
    val rows: List<ManagedImportRow>,
)

data class ImportBatchStats(
    val totalRows: Int,
    val newRows: Int,
    val updateRows: Int,
    val duplicateRows: Int,
    val errorRows: Int,
    val warningRows: Int,
    val publishableRows: Int,
    val pendingReviewRows: Int,
)

data class ManagedImportRow(
    val id: Long,
    val sourceSheetName: String,
    val sourceRowNumber: Int,
    val sourceItemIndex: Int,
    val plateNumber: String?,
    val category: String?,
    val primarySubject: String?,
    val resultStatus: String,
    val plannedAction: String,
    val resolution: String,
    val errorMessage: String?,
    val warningMessage: String?,
)

data class ManagedAuditEntry(
    val id: Long,
    val actorUsername: String?,
    val actionType: String,
    val targetType: String,
    val targetId: Long?,
    val resultStatus: String,
    val createdAt: String,
)
