package com.jaydocoder.plateview.domain.workorder

import com.jaydocoder.plateview.domain.vehicle.PlateQueryNormalizer
import java.text.Normalizer
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val workOrderPlateSeparators = Regex("[\\s　·•.．。—_\\-，,、]+")
private val workOrderPlatePattern = Regex(
    "[京津沪渝冀豫云辽黑湘皖鲁苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼新使领][A-HJ-NP-Z](?:[DF][A-HJ-NP-Z0-9]{5}|[A-HJ-NP-Z0-9]{5}[DF]|[A-HJ-NP-Z0-9]{5})",
    RegexOption.IGNORE_CASE,
)
private val listPrefixPattern = Regex("^\\s*\\d+[.、．]\\s*")
private val identityTextPattern = Regex("(?i)\\d{15}|\\d{17}[0-9x]")
private val remarkSegmentSeparators = Regex("[,，;；。\\n\\r]+")
private val passageTimeHintPattern = Regex(
    "(?:早|晚|上午|中午|下午|凌晨|夜间|全天|\\d{1,2}\\s*[:：]\\s*\\d{1,2}|[零〇一二三四五六七八九十两\\d]{1,3}\\s*[点时])",
)
private val workOrderDatePattern = Regex(
    "(?:(\\d{4})\\s*[年./-]\\s*)?(\\d{1,2})\\s*[月./-]\\s*(\\d{1,2})\\s*日?",
)
private val workOrderRangeEndDayPattern = Regex("(?:-|—|~|～|至|到)\\s*(\\d{1,2})\\s*日?")
private const val chineseTimeNumber = "[零〇一二三四五六七八九十两\\d]{1,3}"
private const val chineseTimePeriod = "凌晨|早上|早|上午|中午|下午|傍晚|晚上|晚|夜间"
private const val clockToken =
    "($chineseTimePeriod)?\\s*($chineseTimeNumber)" +
        "(?:\\s*[:：]\\s*($chineseTimeNumber)|\\s*[点时]\\s*($chineseTimeNumber)?\\s*分?)?"
private val earlyLatePassagePattern = Regex(
    "(?:凌晨|早上|早|上午)\\s*($chineseTimeNumber)" +
        "(?:\\s*[:：]\\s*($chineseTimeNumber)|\\s*[点时]\\s*($chineseTimeNumber)?\\s*分?)?" +
        "\\s*(?:前)?\\s*(?:晚上|晚)\\s*($chineseTimeNumber)" +
        "(?:\\s*[:：]\\s*($chineseTimeNumber)|\\s*[点时]\\s*($chineseTimeNumber)?\\s*分?)?\\s*(?:后)?",
)
private val passageRangePattern = Regex("$clockToken\\s*(?:-|—|~|～|至|到)\\s*$clockToken")
private val beijingZoneId: ZoneId = ZoneId.of("Asia/Shanghai")
private val wechatImagePlaceholderPattern = Regex("""^\[图片]\s+local_id=\S+\s*$""", RegexOption.IGNORE_CASE)
private val wechatPdfPlaceholderPattern = Regex("""^\[文件]\s+.+\.pdf(?:\s*\([^)]*\))?\s*$""", RegexOption.IGNORE_CASE)

enum class WorkOrderPassageValidity {
    VALID,
    EXPIRED,
    NOT_STARTED,
    OUTSIDE_ALLOWED_HOURS,
    UNKNOWN,
}

enum class WorkOrderPassageState {
    VALID,
    EXPIRED,
    NOT_STARTED,
    OUTSIDE_ALLOWED_HOURS,
    UNKNOWN,
    VOID,
    AREA_MISMATCH,
    AREA_UNKNOWN,
}

fun WechatMessage.resolvedSenderName(): String = sequenceOf(
    displayName.takeUnless { it.isBlank() || it == "未知发送者" },
    senderGroupNickname,
    senderDisplay,
    senderUsername,
).firstOrNull { !it.isNullOrBlank() }?.trim() ?: "未知发送者"

fun WorkOrder.resolvedSenderName(): String = sequenceOf(displayName, senderGroupNickname, senderDisplay, senderUsername)
    .firstOrNull { !it.isNullOrBlank() }?.trim() ?: "未知发送者"

fun WechatMessage.hasAttachmentPlaceholderContent(): Boolean {
    if (attachments.isEmpty()) return false
    val content = rawContent.trim()
    return (wechatImagePlaceholderPattern.matches(content) && attachments.any { it.kind == "IMAGE" }) ||
        (wechatPdfPlaceholderPattern.matches(content) && attachments.any { it.kind == "PDF" })
}

fun extractWorkOrderPlateNumbers(rawPlate: String?, rawContent: String? = null): List<String> {
    val raw = rawPlate?.trim().orEmpty()
    val recognized = sequenceOf(rawPlate, rawContent)
        .filterNotNull()
        .map { value ->
            Normalizer.normalize(value, Normalizer.Form.NFKC)
                .uppercase()
                .replace(workOrderPlateSeparators, "")
        }
        .flatMap(workOrderPlatePattern::findAll)
        .map(MatchResult::value)
        .distinct()
        .toList()
    if (recognized.isNotEmpty()) return recognized
    if (raw.isEmpty()) return emptyList()
    val fallback = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        .substringBefore('(')
        .substringBefore('（')
        .trim()
    val plateOnly = buildString {
        fallback.forEachIndexed { index, character ->
            if (index > 0 && character in '\u4E00'..'\u9FFF') return@buildString
            append(character)
        }
    }.trim()
    return listOfNotNull(plateOnly.takeIf { it.length in 2..9 })
}

fun selectWorkOrderCandidatePlate(
    rawPlate: String?,
    orderNumber: String?,
    query: String,
    rawContent: String? = null,
): String? {
    val plateNumbers = extractWorkOrderPlateNumbers(rawPlate, rawContent)
    if (plateNumbers.isEmpty()) return null
    val normalizedQuery = PlateQueryNormalizer.normalize(query)
    if (normalizedQuery.isBlank()) return plateNumbers.first()
    val normalizedOrderNumber = PlateQueryNormalizer.normalize(orderNumber.orEmpty())
    if (normalizedOrderNumber.contains(normalizedQuery)) return plateNumbers.first()
    return plateNumbers.firstOrNull { plateNumber ->
        PlateQueryNormalizer.normalize(plateNumber).contains(normalizedQuery)
    } ?: plateNumbers.first()
}

fun selectWorkOrderDetailHeaderPlates(
    rawPlate: String?,
    rawContent: String?,
    orderNumber: String?,
    query: String,
): List<String> {
    val plateNumbers = extractWorkOrderPlateNumbers(rawPlate, rawContent)
    if (plateNumbers.isEmpty()) return emptyList()
    val normalizedQuery = PlateQueryNormalizer.normalize(query)
    if (normalizedQuery.isBlank()) return plateNumbers
    val normalizedOrderNumber = PlateQueryNormalizer.normalize(orderNumber.orEmpty())
    if (normalizedOrderNumber.contains(normalizedQuery)) return plateNumbers
    return plateNumbers.filter { plateNumber ->
        PlateQueryNormalizer.normalize(plateNumber).contains(normalizedQuery)
    }.ifEmpty { plateNumbers }
}

fun extractWorkOrderPassageTimeRemark(remarks: String?): String? = remarks
    ?.split(remarkSegmentSeparators)
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.filter(passageTimeHintPattern::containsMatchIn)
    ?.joinToString("，")
    ?.takeIf(String::isNotBlank)

fun evaluateWorkOrderPassageValidity(
    rawValidTime: String?,
    remarks: String?,
    sentAt: String,
    now: ZonedDateTime = ZonedDateTime.now(beijingZoneId),
    vehicleType: String? = null,
): WorkOrderPassageValidity {
    val beijingNow = now.withZoneSameInstant(beijingZoneId)
    val dateRange = parseWorkOrderDateRange(rawValidTime, sentAt, beijingNow.year)
        ?: return WorkOrderPassageValidity.UNKNOWN
    val currentDate = beijingNow.toLocalDate()
    if (currentDate.isBefore(dateRange.start)) return WorkOrderPassageValidity.NOT_STARTED
    if (currentDate.isAfter(dateRange.end)) return WorkOrderPassageValidity.EXPIRED
    val allowedWindows = parseAllowedPassageWindows(remarks, vehicleType) ?: return WorkOrderPassageValidity.VALID
    if (allowedWindows.isEmpty()) return WorkOrderPassageValidity.UNKNOWN
    val currentTime = beijingNow.toLocalTime()
    if (allowedWindows.any { it.contains(currentTime) }) return WorkOrderPassageValidity.VALID
    return if (currentDate == dateRange.end && allowedWindows.all { currentTime.isAfter(it.endInclusive) }) {
        WorkOrderPassageValidity.EXPIRED
    } else {
        WorkOrderPassageValidity.OUTSIDE_ALLOWED_HOURS
    }
}

fun resolveWorkOrderPassageState(
    workOrder: WorkOrder,
    now: ZonedDateTime = ZonedDateTime.now(beijingZoneId),
    selectedPlate: String? = null,
): WorkOrderPassageState {
    if (workOrder.status == "VOID") return WorkOrderPassageState.VOID
    if (!workOrder.location.isNullOrBlank() && !workOrder.location.contains("喀纳斯")) {
        return WorkOrderPassageState.AREA_MISMATCH
    }
    return when (
        evaluateWorkOrderPassageValidity(
            rawValidTime = workOrder.rawValidTime,
            remarks = workOrder.remarks,
            sentAt = workOrder.sentAt,
            now = now,
            vehicleType = workOrder.vehicleTypeForPlate(selectedPlate),
        )
    ) {
        WorkOrderPassageValidity.VALID -> WorkOrderPassageState.VALID
        WorkOrderPassageValidity.EXPIRED -> WorkOrderPassageState.EXPIRED
        WorkOrderPassageValidity.NOT_STARTED -> WorkOrderPassageState.NOT_STARTED
        WorkOrderPassageValidity.OUTSIDE_ALLOWED_HOURS -> WorkOrderPassageState.OUTSIDE_ALLOWED_HOURS
        WorkOrderPassageValidity.UNKNOWN -> WorkOrderPassageState.UNKNOWN
    }
}

fun resolveWechatMessagePassageState(
    message: WechatMessage,
    now: ZonedDateTime = ZonedDateTime.now(beijingZoneId),
): WorkOrderPassageState? {
    if (message.businessType != "PASSAGE_MESSAGE") return null
    val regionMatches = message.rawContent.contains("贾登峪") || message.rawContent.contains("喀纳斯")
    if (!regionMatches) {
        val hasExplicitOtherRegion = listOf("禾木", "白哈巴").any(message.rawContent::contains)
        return if (hasExplicitOtherRegion) WorkOrderPassageState.AREA_MISMATCH else WorkOrderPassageState.AREA_UNKNOWN
    }
    val sentDate = runCatching { Instant.parse(message.sentAt).atZone(beijingZoneId).toLocalDate() }.getOrNull()
        ?: return WorkOrderPassageState.UNKNOWN
    val validDate = when {
        message.rawContent.contains("后天") -> sentDate.plusDays(2)
        message.rawContent.contains("明天") || message.rawContent.contains("明早") -> sentDate.plusDays(1)
        else -> sentDate
    }
    val currentDate = now.withZoneSameInstant(beijingZoneId).toLocalDate()
    return when {
        currentDate.isBefore(validDate) -> WorkOrderPassageState.NOT_STARTED
        currentDate.isAfter(validDate) -> WorkOrderPassageState.EXPIRED
        else -> WorkOrderPassageState.VALID
    }
}

fun WorkOrderPassageState.displayLabel(): String = when (this) {
    WorkOrderPassageState.VALID -> "通行时间有效"
    WorkOrderPassageState.EXPIRED -> "通行时间已过期"
    WorkOrderPassageState.NOT_STARTED -> "通行时间未开始"
    WorkOrderPassageState.OUTSIDE_ALLOWED_HOURS -> "不在通行时间"
    WorkOrderPassageState.UNKNOWN -> "通行时间待核实"
    WorkOrderPassageState.VOID -> "已失效"
    WorkOrderPassageState.AREA_MISMATCH -> "通行区域不符"
    WorkOrderPassageState.AREA_UNKNOWN -> "通行区域未知"
}

fun formatWorkOrderPeople(people: List<WorkOrderPerson>): List<String> {
    val result = mutableListOf<String>()
    var index = 0
    while (index < people.size) {
        val person = people[index]
        val name = person.displayName()
        var identity = person.identityNumber?.uppercase()
        if (identity == null && name != null) {
            val next = people.getOrNull(index + 1)
            if (next != null && next.displayName() == null && next.identityNumber != null) {
                identity = next.identityNumber.uppercase()
                index += 1
            }
        }
        val content = listOfNotNull(name, identity).joinToString("  ").ifBlank { person.rawLine.normalizedPersonText() }
        result += "${result.size + 1}. $content"
        index += 1
    }
    return result
}

private fun WorkOrderPerson.displayName(): String? {
    name?.trim()?.removeListPrefix()?.takeIf(String::isNotBlank)?.let { return it }
    val withoutIdentity = rawLine.replace(identityTextPattern, " ").normalizedPersonText().removeListPrefix()
    return withoutIdentity.takeIf { value -> value.any { it.code > 0x7F } }
}

private fun String.normalizedPersonText(): String = this
    .lineSequence()
    .flatMap { it.trim().split(Regex("\\s+")).asSequence() }
    .filter(String::isNotBlank)
    .distinct()
    .joinToString(" ")
    .removeListPrefix()

private fun String.removeListPrefix(): String = replace(listPrefixPattern, "").trim()

private fun parseWorkOrderDateRange(rawValidTime: String?, sentAt: String, fallbackYear: Int): PassageDateRange? {
    val value = rawValidTime?.trim().orEmpty()
    if (value.isEmpty()) return null
    val matches = workOrderDatePattern.findAll(value).toList()
    if (matches.isEmpty()) return null
    val sentYear = runCatching {
        Instant.parse(sentAt).atZone(beijingZoneId).year
    }.getOrDefault(fallbackYear)
    val start = matches.first().toDateParts(sentYear) ?: return null
    val explicitEnd = matches.getOrNull(1)?.toDateParts(start.year)
    val end = if (explicitEnd != null) {
        explicitEnd.copy(year = explicitEnd.year + if (explicitEnd.year == start.year && explicitEnd.month < start.month) 1 else 0)
    } else {
        val endDay = workOrderRangeEndDayPattern.find(value.substring(matches.first().range.last + 1))
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        DateParts(start.year, start.month, endDay ?: start.day)
    }
    return try {
        PassageDateRange(
            start = LocalDate.of(start.year, start.month, start.day),
            end = LocalDate.of(end.year, end.month, end.day),
        ).takeIf { !it.end.isBefore(it.start) }
    } catch (_: DateTimeException) {
        null
    }
}

private fun MatchResult.toDateParts(defaultYear: Int): DateParts? {
    val year = groupValues[1].toIntOrNull() ?: defaultYear
    val month = groupValues[2].toIntOrNull() ?: return null
    val day = groupValues[3].toIntOrNull() ?: return null
    return DateParts(year, month, day)
}

private fun parseAllowedPassageWindows(remarks: String?, vehicleType: String?): List<PassageTimeWindow>? {
    val value = selectPassageTimeRemarkForVehicle(remarks, vehicleType) ?: return null
    if (value.contains("全天")) return listOf(PassageTimeWindow(LocalTime.MIN, LocalTime.MAX))
    val windows = mutableListOf<PassageTimeWindow>()
    earlyLatePassagePattern.findAll(value).forEach { match ->
        val earlyEnd = parseClock("早", match.groupValues[1], match.groupValues[2], match.groupValues[3])
        if (earlyEnd != null) {
            windows += PassageTimeWindow(LocalTime.MIN, earlyEnd.minusNanos(1))
        }
        val lateHour = parseChineseNumber(match.groupValues[4])
        if (lateHour != 12) {
            val lateStart = parseClock("晚", match.groupValues[4], match.groupValues[5], match.groupValues[6])
            if (lateStart != null) {
                windows += PassageTimeWindow(lateStart, LocalTime.MAX)
            }
        }
    }
    passageRangePattern.findAll(value).forEach { match ->
        val startPeriod = match.groupValues[1].takeIf(String::isNotBlank)
        val endPeriod = match.groupValues[5].takeIf(String::isNotBlank) ?: startPeriod
        val start = parseClock(startPeriod, match.groupValues[2], match.groupValues[3], match.groupValues[4])
        val end = parseClock(endPeriod, match.groupValues[6], match.groupValues[7], match.groupValues[8])
        if (start != null && end != null && !end.isBefore(start)) {
            windows += PassageTimeWindow(start, end.withSecond(59).withNano(999_999_999))
        }
    }
    return windows.distinct()
}

private fun selectPassageTimeRemarkForVehicle(remarks: String?, vehicleType: String?): String? {
    val segments = remarks
        ?.split(remarkSegmentSeparators)
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.filter(passageTimeHintPattern::containsMatchIn)
        .orEmpty()
    if (segments.isEmpty()) return null
    val targetClass = when {
        vehicleType?.contains("重型") == true -> PassageVehicleClass.HEAVY
        vehicleType?.contains("轻型") == true -> PassageVehicleClass.LIGHT
        !vehicleType.isNullOrBlank() && segments.any { it.passageVehicleClass() == PassageVehicleClass.LIGHT } &&
            segments.any { it.passageVehicleClass() == PassageVehicleClass.HEAVY } -> PassageVehicleClass.LIGHT
        else -> null
    }
    if (targetClass == null) return segments.joinToString("，")
    return segments
        .filter { segment -> segment.passageVehicleClass()?.let { it == targetClass } ?: true }
        .joinToString("，")
        .takeIf(String::isNotBlank)
}

private fun String.passageVehicleClass(): PassageVehicleClass? = when {
    contains("重型") -> PassageVehicleClass.HEAVY
    contains("轻型") -> PassageVehicleClass.LIGHT
    else -> null
}

private fun WorkOrder.vehicleTypeForPlate(selectedPlate: String?): String? {
    if (selectedPlate.isNullOrBlank()) return vehicleType
    val normalizedSelectedPlate = PlateQueryNormalizer.normalize(selectedPlate)
    return vehicles.firstOrNull { vehicle ->
        PlateQueryNormalizer.normalize(vehicle.normalizedPlate) == normalizedSelectedPlate ||
            PlateQueryNormalizer.normalize(vehicle.rawPlate) == normalizedSelectedPlate
    }?.vehicleType ?: vehicleType
}

private fun parseClock(period: String?, hourText: String, colonMinute: String, pointMinute: String): LocalTime? {
    val rawHour = parseChineseNumber(hourText) ?: return null
    val minute = parseChineseNumber(colonMinute.ifBlank { pointMinute }) ?: 0
    if (rawHour !in 0..23 || minute !in 0..59) return null
    val hour = when (period) {
        "凌晨" -> if (rawHour == 12) 0 else rawHour
        "早上", "早", "上午" -> if (rawHour == 12) 0 else rawHour
        "中午", "下午", "傍晚", "晚上", "晚", "夜间" -> if (rawHour in 1..11) rawHour + 12 else rawHour
        else -> rawHour
    }
    return LocalTime.of(hour, minute)
}

private fun parseChineseNumber(value: String): Int? {
    value.toIntOrNull()?.let { return it }
    if (value.isBlank()) return null
    val digits = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
    if ('十' !in value) return value.singleOrNull()?.let(digits::get)
    val parts = value.split('十', limit = 2)
    val tens = parts[0].singleOrNull()?.let(digits::get) ?: 1
    val ones = parts.getOrNull(1)?.singleOrNull()?.let(digits::get) ?: 0
    return tens * 10 + ones
}

private data class DateParts(val year: Int, val month: Int, val day: Int)
private data class PassageDateRange(val start: LocalDate, val end: LocalDate)
private data class PassageTimeWindow(val startInclusive: LocalTime, val endInclusive: LocalTime) {
    fun contains(time: LocalTime): Boolean = !time.isBefore(startInclusive) && !time.isAfter(endInclusive)
}
private enum class PassageVehicleClass { LIGHT, HEAVY }
