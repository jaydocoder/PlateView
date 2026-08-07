package com.jaydocoder.plateview.feature.admin

import com.jaydocoder.plateview.domain.admin.ManagedAuditEntry
import com.jaydocoder.plateview.domain.admin.ManagedImportBatch
import com.jaydocoder.plateview.domain.admin.ManagedImportBatchSummary
import com.jaydocoder.plateview.domain.admin.ManagedLongTermProfile
import com.jaydocoder.plateview.domain.admin.ManagedResidentProfile
import com.jaydocoder.plateview.domain.admin.ManagedUser
import com.jaydocoder.plateview.domain.admin.ManagedVehicle
import com.jaydocoder.plateview.domain.admin.ManagedVehicleSummary

data class AdminUiState(
    val tab: AdminTab = AdminTab.Dashboard,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isVehiclePageLoading: Boolean = false,
    val isImportPageLoading: Boolean = false,
    val failure: AdminFailure? = null,
    val vehicles: List<ManagedVehicleSummary> = emptyList(),
    val vehicleSearchQuery: String = "",
    val vehicleTotalCount: Int = 0,
    val users: List<ManagedUser> = emptyList(),
    val importBatches: List<ManagedImportBatchSummary> = emptyList(),
    val auditEntries: List<ManagedAuditEntry> = emptyList(),
    val vehicleEditor: VehicleEditorState? = null,
    val userEditor: UserEditorState? = null,
    val selectedImportBatch: ManagedImportBatch? = null,
    val pendingVehicleDeactivation: ManagedVehicleSummary? = null,
)

enum class AdminTab {
    Dashboard,
    Vehicles,
    Users,
    Imports,
    Audit,
}

sealed interface AdminFailure {
    data object SessionExpired : AdminFailure
    data object PermissionDenied : AdminFailure
    data object Conflict : AdminFailure
    data class Validation(val message: String?) : AdminFailure
    data class ServiceUnavailable(val message: String?) : AdminFailure
}

data class VehicleEditorState(
    val id: Long? = null,
    val version: Int = 0,
    val plateNumber: String = "",
    val category: String = "RESIDENT",
    val status: String = "ACTIVE",
    val vehicleType: String = "",
    val ownerName: String = "",
    val identityCardNumber: String = "",
    val contactPhone: String = "",
    val organizationName: String = "",
    val passHolder: String = "",
    val passageDetails: String = "",
    val remarks: String = "",
    val vehicleUse: String = "",
    val passageArea: String = "",
    val position: String = "",
    val brandModel: String = "",
    val approvedCapacity: String = "",
    val plateColor: String = "",
    val error: String? = null,
) {
    val isResident: Boolean get() = category == "RESIDENT"

    fun validate(): String? = when {
        plateNumber.isBlank() -> "请输入车牌号"
        isResident && (ownerName.isBlank() || identityCardNumber.isBlank()) -> "村民车辆必须填写姓名和身份证号"
        else -> null
    }

    fun toCommand() = com.jaydocoder.plateview.domain.admin.VehicleWriteCommand(
        plateNumber = plateNumber,
        category = category,
        vehicleType = vehicleType.trim().ifEmpty { null },
        status = status,
        attributes = listOf(
            "vehicleUse" to vehicleUse,
            "passageArea" to passageArea,
            "position" to position,
            "brandModel" to brandModel,
            "approvedCapacity" to approvedCapacity,
            "plateColor" to plateColor,
        ).mapNotNull { (key, value) -> value.trim().takeIf(String::isNotEmpty)?.let { key to it } }.toMap(),
        residentProfile = if (isResident) {
            ManagedResidentProfile(ownerName, identityCardNumber, contactPhone.trim().ifEmpty { null }, remarks.trim().ifEmpty { null })
        } else {
            null
        },
        longTermProfile = if (isResident) {
            null
        } else {
            ManagedLongTermProfile(
                organizationName.trim().ifEmpty { null },
                passHolder.trim().ifEmpty { null },
                passageDetails.trim().ifEmpty { null },
                remarks.trim().ifEmpty { null },
            )
        },
    )
}

data class UserEditorState(
    val id: Long? = null,
    val version: Int = 0,
    val username: String = "",
    val password: String = "",
    val role: String = "USER",
    val status: String = "ACTIVE",
    val error: String? = null,
) {
    val isCreate: Boolean get() = id == null

    fun validate(): String? = when {
        isCreate && username.trim().length !in 3..64 -> "账号名称长度应为3至64个字符"
        isCreate && password.length !in 6..128 -> "密码长度应为6至128个字符"
        else -> null
    }
}

fun ManagedVehicle.toEditor(): VehicleEditorState = VehicleEditorState(
    id = id,
    version = version,
    plateNumber = plateNumber,
    category = category,
    status = status,
    vehicleType = vehicleType.orEmpty(),
    ownerName = residentProfile?.ownerName.orEmpty(),
    identityCardNumber = residentProfile?.identityCardNumber.orEmpty(),
    contactPhone = residentProfile?.contactPhone.orEmpty(),
    organizationName = longTermProfile?.organizationName.orEmpty(),
    passHolder = longTermProfile?.passHolder.orEmpty(),
    passageDetails = longTermProfile?.passageDetails.orEmpty(),
    remarks = residentProfile?.remarks ?: longTermProfile?.remarks.orEmpty(),
    vehicleUse = attributes["vehicleUse"].orEmpty(),
    passageArea = attributes["passageArea"].orEmpty(),
    position = attributes["position"].orEmpty(),
    brandModel = attributes["brandModel"].orEmpty(),
    approvedCapacity = attributes["approvedCapacity"].orEmpty(),
    plateColor = attributes["plateColor"].orEmpty(),
)

fun ManagedUser.toEditor(): UserEditorState = UserEditorState(
    id = id,
    version = version,
    username = username,
    role = role,
    status = status,
)
