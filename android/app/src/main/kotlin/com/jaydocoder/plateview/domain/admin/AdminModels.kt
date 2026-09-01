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

data class VehicleCreationCapabilities(
    val creatableCategories: List<String>,
    val canChangeVehicleCategory: Boolean,
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
    val avatarVersion: Long = 0L,
    val hasAvatar: Boolean = false,
    val realName: String? = null,
    val scheduleAccessEnabled: Boolean = false,
)

data class UserCreateCommand(
    val username: String,
    val password: String,
    val role: String,
    val realName: String? = null,
    val scheduleAccessEnabled: Boolean = false,
)

data class UserUpdateCommand(
    val role: String,
    val status: String,
    val username: String? = null,
    val password: String? = null,
    val realName: String? = null,
    val scheduleAccessEnabled: Boolean? = null,
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
    val rowTotal: Int = 0,
    val rows: List<ManagedImportRow> = emptyList(),
)

data class ImportBatchStats(
    val totalRows: Int,
    val newRows: Int,
    val updateRows: Int,
    val reactivateRows: Int = 0,
    val deactivateRows: Int = 0,
    val duplicateRows: Int = 0,
    val errorRows: Int = 0,
    val warningRows: Int = 0,
    val publishableRows: Int = 0,
    val pendingReviewRows: Int = 0,
)

enum class ImportRowFilter(val requestValue: String, val label: String) {
    REVIEW("REVIEW", "全部待核对"),
    CREATE("CREATE", "新增"),
    UPDATE("UPDATE", "更新"),
    REACTIVATE("REACTIVATE", "恢复"),
    DEACTIVATE("DEACTIVATE", "待失效"),
    ERROR("ERROR", "异常"),
}

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

data class ManagedImportRowDetail(
    val row: ManagedImportRow,
    val sections: List<ManagedImportDiffSection>,
    val sourceValues: List<ManagedImportSourceValue>,
)

data class ManagedImportDiffSection(val title: String, val fields: List<ManagedImportFieldDifference>)

data class ManagedImportFieldDifference(val label: String, val before: String?, val after: String?)

data class ManagedImportSourceValue(val label: String, val value: String)

data class ManagedAuditEntry(
    val id: Long,
    val actorUsername: String?,
    val actionType: String,
    val targetType: String,
    val targetId: Long?,
    val resultStatus: String,
    val createdAt: String,
)

data class ManagedAuditPage(
    val items: List<ManagedAuditEntry>,
    val total: Int,
    val summary: ManagedAuditSummary,
    val actors: List<ManagedAuditActor>,
    val actionTypes: List<String>,
)

data class ManagedAuditSummary(
    val total: Int = 0,
    val successCount: Int = 0,
    val abnormalCount: Int = 0,
    val activeActorCount: Int = 0,
)

data class ManagedAuditActor(
    val id: Long,
    val username: String?,
)

data class AuditFilter(
    val range: AuditRange = AuditRange.THIRTY_DAYS,
    val actorId: Long? = null,
    val actionType: String? = null,
    val result: AuditResult = AuditResult.ALL,
    val keyword: String = "",
)

enum class AuditRange(val requestValue: String, val label: String) {
    DAY("24h", "近24小时"),
    WEEK("7d", "近7天"),
    THIRTY_DAYS("30d", "近30天"),
    ALL("all", "全部时间"),
}

enum class AuditResult(val requestValue: String?, val label: String) {
    ALL(null, "全部结果"),
    SUCCESS("SUCCESS", "正常"),
    ABNORMAL("ABNORMAL", "异常"),
}
