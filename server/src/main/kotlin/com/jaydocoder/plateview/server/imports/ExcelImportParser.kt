package com.jaydocoder.plateview.server.imports

import java.io.ByteArrayInputStream
import java.util.Locale
import com.jaydocoder.plateview.server.vehicle.normalizePlate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory

internal class ExcelImportParser {
    fun parse(bytes: ByteArray): List<ParsedImportRow> {
        val workbook = try {
            WorkbookFactory.create(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            throw ImportFileInvalidException("Excel文件格式无效或无法读取")
        }
        return workbook.use { openedWorkbook ->
            val formatter = DataFormatter()
            openedWorkbook.sheetIterator().asSequence()
                .flatMap { sheet -> parseSheet(sheet, formatter).asSequence() }
                .toList()
        }
    }

    private fun parseSheet(sheet: Sheet, formatter: DataFormatter): List<ParsedImportRow> {
        val header = findHeader(sheet, formatter) ?: return emptyList()
        val category = resolveCategory(sheet.sheetName, header.values.keys) ?: return emptyList()
        val rows = mutableListOf<ParsedImportRow>()
        var inheritedOrganization: String? = null

        for (rowIndex in (header.rowIndex + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            val rawValues = readRawValues(row, header, formatter)
            if (rawValues.isEmpty()) continue

            val directOrganization = rawValues["单位名称"].orEmpty().trim().takeIf(String::isNotEmpty)
            if (directOrganization != null) {
                inheritedOrganization = directOrganization
            }
            val organization = directOrganization ?: inheritedOrganization
            val plateCell = rawValues["车牌号"].orEmpty()
            val baseVehicle = buildVehicle(category, rawValues, organization)
            val rowNumber = rowIndex + 1
            val rowWarnings = mutableListOf<String>()
            val rowErrors = mutableListOf<String>()

            if (category in LONG_TERM_CATEGORIES && organization == null) {
                rowErrors += "单位名称为空且无可继承的上一有效单位"
            }
            validateRequiredFields(category, baseVehicle, rowErrors)
            validateStorageLengths(baseVehicle, rowErrors)
            validateWarnings(category, baseVehicle, rowWarnings)

            val plateCandidates = extractPlateCandidates(plateCell)
            if (plateCell.isBlank()) {
                rowErrors += "车牌号为空"
            } else if (plateCandidates.isEmpty()) {
                rowErrors += "车牌号格式不规范或无法拆分"
            }

            if (plateCandidates.size > 1) {
                rowWarnings += "一个源行拆分为${plateCandidates.size}个车牌，请核对关联字段"
            }

            if (plateCandidates.isEmpty()) {
                rows += ParsedImportRow(
                    sourceSheetName = sheet.sheetName,
                    sourceRowNumber = rowNumber,
                    sourceItemIndex = 0,
                    rawValues = rawValues.toJsonObject(),
                    vehicle = baseVehicle,
                    resultStatus = ImportResultStatus.ERROR,
                    plannedAction = ImportPlannedAction.NONE,
                    resolution = ImportResolution.ERROR,
                    errorMessage = rowErrors.distinct().joinToString("；"),
                    warningMessage = rowWarnings.distinct().joinToString("；").takeIf(String::isNotEmpty),
                )
                continue
            }

            plateCandidates.forEachIndexed { itemIndex, candidate ->
                val vehicle = baseVehicle.copy(
                    originalPlate = candidate.original,
                    normalizedPlate = candidate.normalized,
                )
                rows += ParsedImportRow(
                    sourceSheetName = sheet.sheetName,
                    sourceRowNumber = rowNumber,
                    sourceItemIndex = itemIndex,
                    rawValues = rawValues.toJsonObject(),
                    vehicle = vehicle,
                    resultStatus = if (rowErrors.isEmpty()) ImportResultStatus.VALID else ImportResultStatus.ERROR,
                    plannedAction = if (rowErrors.isEmpty()) ImportPlannedAction.CREATE else ImportPlannedAction.NONE,
                    resolution = if (rowErrors.isEmpty() && rowWarnings.isEmpty()) ImportResolution.PUBLISH else if (rowErrors.isEmpty()) ImportResolution.PENDING else ImportResolution.ERROR,
                    errorMessage = rowErrors.distinct().joinToString("；").takeIf(String::isNotEmpty),
                    warningMessage = rowWarnings.distinct().joinToString("；").takeIf(String::isNotEmpty),
                )
            }
        }
        return rows
    }

    private fun findHeader(sheet: Sheet, formatter: DataFormatter): Header? {
        val scanEnd = minOf(sheet.lastRowNum, HEADER_SCAN_ROW_COUNT - 1)
        for (index in 0..scanEnd) {
            val row = sheet.getRow(index) ?: continue
            val values = (0 until row.lastCellNum.coerceAtLeast(0))
                .mapNotNull { column ->
                    formatter.formatCellValue(row.getCell(column)).canonicalHeader().takeIf(String::isNotEmpty)?.let { it to column }
                }
                .toMap()
            if ("车牌号" in values && isRecognizedHeader(values.keys)) {
                return Header(index, values)
            }
        }
        return null
    }

    private fun isRecognizedHeader(headers: Set<String>): Boolean =
        ("姓名" in headers && "身份证号" in headers) ||
            "单位名称" in headers ||
            "品牌型号" in headers ||
            "号牌颜色" in headers

    private fun resolveCategory(sheetName: String, headers: Set<String>): ImportCategory? = when {
        "姓名" in headers && "身份证号" in headers -> ImportCategory.RESIDENT
        "品牌型号" in headers || "号牌颜色" in headers -> ImportCategory.KANAS_TOURISM_DEVELOPMENT
        sheetName.contains("单位") -> ImportCategory.SCENIC_UNIT
        sheetName.contains("企业") -> ImportCategory.SCENIC_ENTERPRISE
        sheetName.contains("干部") -> ImportCategory.CADRE
        else -> null
    }

    private fun readRawValues(row: Row, header: Header, formatter: DataFormatter): Map<String, String> =
        header.values.mapNotNull { (name, index) ->
            formatter.formatCellValue(row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .trim()
                .takeIf(String::isNotEmpty)
                ?.let { name to it }
        }.toMap()

    private fun buildVehicle(
        category: ImportCategory,
        raw: Map<String, String>,
        organization: String?,
    ): ParsedVehicle {
        val passageDetails = listOfNotNull(
            raw["车辆用途"]?.let { "车辆用途：$it" },
            raw["通行区域"]?.let { "通行区域：$it" },
        ).joinToString("；").takeIf(String::isNotEmpty)
        val attributes = buildJsonObject {
            raw["车辆用途"]?.let { put("vehicleUse", JsonPrimitive(it)) }
            raw["通行区域"]?.let { put("passageArea", JsonPrimitive(it)) }
            raw["职务"]?.let { put("position", JsonPrimitive(it)) }
            raw["品牌型号"]?.let { put("brandModel", JsonPrimitive(it)) }
            raw["核载人数"]?.let { put("approvedCapacity", JsonPrimitive(it)) }
            raw["号牌颜色"]?.let { put("plateColor", JsonPrimitive(it)) }
        }
        return ParsedVehicle(
            originalPlate = null,
            normalizedPlate = null,
            category = category,
            vehicleType = raw["车辆类型"],
            ownerName = raw["姓名"],
            identityCardNumber = raw["身份证号"],
            contactPhone = raw["联系方式"],
            organizationName = if (category == ImportCategory.KANAS_TOURISM_DEVELOPMENT) TOURISM_ORGANIZATION else organization,
            passHolder = raw["通行人员"],
            passageDetails = passageDetails,
            remarks = raw["备注"],
            attributes = attributes,
        )
    }

    private fun validateRequiredFields(
        category: ImportCategory,
        vehicle: ParsedVehicle,
        errors: MutableList<String>,
    ) {
        if (category == ImportCategory.RESIDENT) {
            if (vehicle.ownerName.isNullOrBlank()) errors += "村民车辆姓名为空"
            if (vehicle.identityCardNumber.isNullOrBlank()) errors += "村民车辆身份证号为空"
        }
    }

    private fun validateWarnings(
        category: ImportCategory,
        vehicle: ParsedVehicle,
        warnings: MutableList<String>,
    ) {
        if (category == ImportCategory.RESIDENT) {
            val identity = vehicle.identityCardNumber
            if (!identity.isNullOrBlank() && !IDENTITY_CARD_PATTERN.matches(identity.uppercase(Locale.ROOT))) {
                warnings += "身份证号格式不规范，确认后可发布"
            }
            val phone = vehicle.contactPhone
            if (!phone.isNullOrBlank() && !PHONE_PATTERN.matches(phone.replace(" ", ""))) {
                warnings += "联系方式格式不规范，确认后可发布"
            }
        }
    }

    private fun validateStorageLengths(vehicle: ParsedVehicle, errors: MutableList<String>) {
        if (vehicle.vehicleType?.length ?: 0 > 128) errors += "车辆类型长度超过128个字符"
        if (vehicle.ownerName?.length ?: 0 > 128) errors += "姓名长度超过128个字符"
        if (vehicle.identityCardNumber?.length ?: 0 > 32) errors += "身份证号长度超过32个字符"
        if (vehicle.contactPhone?.length ?: 0 > 32) errors += "联系方式长度超过32个字符"
        if (vehicle.organizationName?.length ?: 0 > 255) errors += "单位名称长度超过255个字符"
        if (vehicle.passHolder?.length ?: 0 > 255) errors += "通行人员长度超过255个字符"
    }

    private fun extractPlateCandidates(value: String): List<PlateCandidate> {
        if (value.isBlank()) return emptyList()
        val candidates = value.split(PLATE_SEPARATOR)
            .flatMap { segment ->
                val normalizedSegment = normalizePlate(segment)
                PLATE_PATTERN.findAll(normalizedSegment)
                    .map { match -> PlateCandidate(match.value, match.value) }
                    .toList()
            }
            .distinctBy(PlateCandidate::normalized)
        return candidates
    }

    private fun Map<String, String>.toJsonObject(): JsonObject = buildJsonObject {
        forEach { (key, value) -> put(key, JsonPrimitive(value)) }
    }

    private data class Header(
        val rowIndex: Int,
        val values: Map<String, Int>,
    )

    private data class PlateCandidate(
        val original: String,
        val normalized: String,
    )

    private companion object {
        const val HEADER_SCAN_ROW_COUNT = 10
        const val TOURISM_ORGANIZATION = "喀纳斯旅游发展股份有限公司"
        val LONG_TERM_CATEGORIES = setOf(
            ImportCategory.SCENIC_UNIT,
            ImportCategory.SCENIC_ENTERPRISE,
            ImportCategory.CADRE,
        )
        val IDENTITY_CARD_PATTERN = Regex("^[1-9]\\d{16}[0-9X]$")
        val PHONE_PATTERN = Regex("^(1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8})$")
        val PLATE_SEPARATOR = Regex("[\\r\\n,，、;；/|]+")
        val PLATE_PATTERN = Regex("[\\p{IsHan}][A-Z][A-Z0-9]{5,6}")
    }
}

private fun String.canonicalHeader(): String = trim().replace(Regex("[\\s　]+"), "")
