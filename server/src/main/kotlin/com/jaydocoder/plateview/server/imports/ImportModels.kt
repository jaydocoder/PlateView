package com.jaydocoder.plateview.server.imports

import com.jaydocoder.plateview.server.vehicle.VehicleCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal typealias ImportCategory = VehicleCategory

internal enum class ImportResultStatus {
    VALID,
    DUPLICATE,
    ERROR,
    PUBLISHED,
}

internal enum class ImportResolution {
    PENDING,
    PUBLISH,
    SKIP,
    ERROR,
}

internal enum class ImportPlannedAction {
    CREATE,
    UPDATE,
    DEACTIVATE,
    REACTIVATE,
    SKIP,
    NONE,
}

internal enum class ImportPublishMode {
    INITIAL,
    REPUBLISH,
}

internal fun prepareImportPublish(status: String): ImportPublishMode = when (status) {
    "VALIDATED" -> ImportPublishMode.INITIAL
    "ROLLED_BACK" -> ImportPublishMode.REPUBLISH
    else -> throw ImportWorkflowConflictException("IMPORT_BATCH_NOT_PUBLISHABLE", "当前批次状态不允许发布")
}

internal fun ensureImportReadyToPublish(pendingReviewRows: Int) {
    require(pendingReviewRows >= 0) { "待确认记录数不能为负数" }
    if (pendingReviewRows > 0) {
        throw ImportWorkflowConflictException("IMPORT_PENDING_REVIEW", "仍有待确认的导入差异，完成处置后才能发布")
    }
}

internal data class ParsedVehicle(
    val originalPlate: String?,
    val normalizedPlate: String?,
    val category: ImportCategory?,
    val vehicleType: String?,
    val ownerName: String?,
    val identityCardNumber: String?,
    val contactPhone: String?,
    val organizationName: String?,
    val passHolder: String?,
    val passageDetails: String?,
    val remarks: String?,
    val attributes: JsonObject,
    val sourceVehicleId: Long? = null,
    val sourceVehicleVersion: Int? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        putNullable("originalPlate", originalPlate)
        putNullable("normalizedPlate", normalizedPlate)
        putNullable("category", category?.name)
        putNullable("vehicleType", vehicleType)
        putNullable("ownerName", ownerName)
        putNullable("identityCardNumber", identityCardNumber)
        putNullable("contactPhone", contactPhone)
        putNullable("organizationName", organizationName)
        putNullable("passHolder", passHolder)
        putNullable("passageDetails", passageDetails)
        putNullable("remarks", remarks)
        put("attributes", attributes)
        putNullable("sourceVehicleId", sourceVehicleId)
        putNullable("sourceVehicleVersion", sourceVehicleVersion)
    }

    companion object {
        fun fromJson(value: String): ParsedVehicle {
            val objectValue = Json.parseToJsonElement(value).jsonObject
            return ParsedVehicle(
                originalPlate = objectValue.stringOrNull("originalPlate"),
                normalizedPlate = objectValue.stringOrNull("normalizedPlate"),
                category = objectValue.stringOrNull("category")?.let(ImportCategory::valueOf),
                vehicleType = objectValue.stringOrNull("vehicleType"),
                ownerName = objectValue.stringOrNull("ownerName"),
                identityCardNumber = objectValue.stringOrNull("identityCardNumber"),
                contactPhone = objectValue.stringOrNull("contactPhone"),
                organizationName = objectValue.stringOrNull("organizationName"),
                passHolder = objectValue.stringOrNull("passHolder"),
                passageDetails = objectValue.stringOrNull("passageDetails"),
                remarks = objectValue.stringOrNull("remarks"),
                attributes = objectValue["attributes"]?.jsonObject ?: JsonObject(emptyMap()),
                sourceVehicleId = objectValue.longOrNull("sourceVehicleId"),
                sourceVehicleVersion = objectValue.intOrNull("sourceVehicleVersion"),
            )
        }
    }
}

internal data class ParsedImportRow(
    val sourceSheetName: String,
    val sourceRowNumber: Int,
    val sourceItemIndex: Int,
    val rawValues: JsonObject,
    val vehicle: ParsedVehicle,
    val resultStatus: ImportResultStatus,
    val plannedAction: ImportPlannedAction,
    val resolution: ImportResolution,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val beforeValues: JsonObject? = null,
)

internal data class ExistingVehicle(
    val id: Long,
    val plateNumber: String,
    val normalizedPlate: String,
    val category: ImportCategory,
    val vehicleType: String?,
    val status: String,
    val importBatchId: Long?,
    val attributes: JsonObject,
    val version: Int,
    val residentProfile: ResidentProfileSnapshot?,
    val longTermProfile: LongTermProfileSnapshot?,
)

internal data class ResidentProfileSnapshot(
    val ownerName: String,
    val identityCardNumber: String,
    val contactPhone: String?,
    val remarks: String?,
    val version: Int,
)

internal data class LongTermProfileSnapshot(
    val organizationName: String?,
    val passHolder: String?,
    val passageDetails: String?,
    val remarks: String?,
    val version: Int,
)

internal fun JsonObject.stringOrNull(key: String): String? = this[key]
    ?.takeUnless { it is JsonNull }
    ?.jsonPrimitive
    ?.content
    ?.trim()
    ?.takeIf(String::isNotEmpty)

internal fun JsonObject.longOrNull(key: String): Long? = stringOrNull(key)?.toLongOrNull()

internal fun JsonObject.intOrNull(key: String): Int? = stringOrNull(key)?.toIntOrNull()

internal fun JsonObjectBuilder.putNullable(key: String, value: String?) {
    put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}

internal fun JsonObjectBuilder.putNullable(key: String, value: Long?) {
    put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}

internal fun JsonObjectBuilder.putNullable(key: String, value: Int?) {
    put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}
