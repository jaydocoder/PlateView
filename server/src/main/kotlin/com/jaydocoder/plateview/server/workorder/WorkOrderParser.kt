package com.jaydocoder.plateview.server.workorder

import java.text.Normalizer

internal data class ParsedWorkOrder(
    val orderNumber: String?,
    val rawPlate: String?,
    val normalizedPlate: String?,
    val vehicleType: String?,
    val declaredPeople: Int?,
    val rawValidTime: String?,
    val location: String?,
    val verificationMethod: String?,
    val reason: String?,
    val remarks: String?,
    val status: String,
    val parseQuality: String,
    val searchableText: String,
    val people: List<ParsedWorkOrderPerson>,
    val vehicles: List<ParsedWorkOrderVehicle>,
)

internal data class ParsedWorkOrderPerson(
    val rawLine: String,
    val name: String?,
    val identityNumber: String?,
)

internal data class ParsedWorkOrderVehicle(
    val rawDescription: String,
    val rawPlate: String,
    val normalizedPlate: String,
    val vehicleType: String?,
)

internal object WorkOrderParser {
    private val fieldPattern = Regex("【([^】]+)】")
    private val plateTypePattern = Regex("^\\s*(.+?)\\s*[（(]([^）)]+)[）)]\\s*$")
    private val peopleCountPattern = Regex("(\\d+)\\s*人")
    private val identityPattern = Regex("(?i)(\\d{17}[0-9x]|\\d{15})(?![0-9x])")
    private val shortOrderNumberPattern = Regex("(?<!\\d)((?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3})(?!\\d)")
    private val punctuationPattern = Regex("[\\s，。；：、,.;:()（）【】\\[\\]_-]+")
    private val plateSeparators = Regex("[\\s　·•．。—_\\-，,、]+")
    private val platePattern = Regex(
        "[京津沪渝冀豫云辽黑湘皖鲁苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼新使领][A-HJ-NP-Z](?:[DF][A-HJ-NP-Z0-9]{5}|[A-HJ-NP-Z0-9]{5}[DF]|[A-HJ-NP-Z0-9]{5})",
        RegexOption.IGNORE_CASE,
    )
    private val passageIntentPattern = Regex(
        "通行|放行|予以|允许|前往|去白哈巴|进(?:入)?(?:喀纳斯|贾登峪|禾木|白哈巴)",
    )

    fun parse(rawContent: String): ParsedWorkOrder {
        val fields = extractFields(rawContent)
        val rawVehicle = fields["车号"]?.trimToNull()
        val plateMatch = rawVehicle?.let(plateTypePattern::matchEntire)
        val plateSource = plateMatch?.groupValues?.get(1)?.trimToNull() ?: rawVehicle
        val vehicles = (rawVehicle?.let(::parseVehicles).orEmpty())
            .distinctBy(ParsedWorkOrderVehicle::normalizedPlate)
        val parsedPlates = vehicles.map(ParsedWorkOrderVehicle::rawPlate)
            .ifEmpty { plateSource?.let(::extractPlateNumbers).orEmpty() }
        val rawPlate = parsedPlates.takeIf(List<String>::isNotEmpty)?.joinToString("、") ?: plateSource
        val vehicleType = plateMatch?.groupValues?.get(2)?.trimToNull()
        val people = parsePeople(fields["人数"])
        val orderNumber = fields["单号"]?.lineSequence()?.firstOrNull()?.trimToNull()
            ?: shortOrderNumberPattern.find(rawContent)?.groupValues?.get(1)
        val recognizedFields = listOf(
            orderNumber,
            rawPlate,
            fields["时间"],
            fields["地点"],
            fields["方式"],
            fields["事由"],
            fields["备注"],
        ).count { !it.isNullOrBlank() }
        val parseQuality = when {
            orderNumber != null && rawPlate != null && recognizedFields >= 5 -> "COMPLETE"
            orderNumber != null || rawPlate != null -> "PARTIAL"
            else -> "RAW_FALLBACK"
        }
        return ParsedWorkOrder(
            orderNumber = orderNumber,
            rawPlate = rawPlate,
            normalizedPlate = rawPlate?.let(::normalizeSearchText),
            vehicleType = vehicleType,
            declaredPeople = fields["人数"]?.let { peopleCountPattern.find(it)?.groupValues?.get(1)?.toIntOrNull() },
            rawValidTime = fields["时间"]?.trimToNull(),
            location = fields["地点"]?.trimToNull(),
            verificationMethod = fields["方式"]?.trimToNull(),
            reason = fields["事由"]?.trimToNull(),
            remarks = fields["备注"]?.trimToNull(),
            status = if (orderNumber != null && rawContent.contains("作废")) "VOID" else "ACTIVE",
            parseQuality = parseQuality,
            searchableText = normalizeSearchText(rawContent),
            people = people,
            vehicles = vehicles.ifEmpty {
                parsedPlates.map { plate -> ParsedWorkOrderVehicle(plate, plate, normalizeSearchText(plate), vehicleType) }
            },
        )
    }

    internal fun classify(parsed: ParsedWorkOrder, rawContent: String, passageSenderEnabled: Boolean): String = when {
        parsed.orderNumber != null && (rawContent.contains("【车号】") || rawContent.contains("【时间】") || parsed.rawPlate != null) ->
            "STRUCTURED_WORK_ORDER"
        parsed.orderNumber != null -> "ATTACHMENT_WORK_ORDER"
        passageSenderEnabled && extractPlateNumbers(rawContent).isNotEmpty() && passageIntentPattern.containsMatchIn(rawContent) ->
            "PASSAGE_MESSAGE"
        else -> "GENERAL_MESSAGE"
    }

    internal fun normalizeSearchText(value: String): String = value
        .uppercase()
        .replace(punctuationPattern, "")

    internal fun extractPlateNumbers(value: String): List<String> = platePattern
        .findAll(
            Normalizer.normalize(value, Normalizer.Form.NFKC)
                .uppercase()
                .replace(plateSeparators, ""),
        )
        .map { it.value }
        .distinct()
        .toList()

    private fun parseVehicles(rawVehicle: String): List<ParsedWorkOrderVehicle> {
        val normalized = Normalizer.normalize(rawVehicle, Normalizer.Form.NFKC).uppercase()
        val matches = platePattern.findAll(normalized.replace(plateSeparators, "")).toList()
        if (matches.isEmpty()) return emptyList()
        val compact = normalized.replace(plateSeparators, "")
        return matches.mapIndexed { index, match ->
            val previousEnd = matches.getOrNull(index - 1)?.range?.last?.plus(1) ?: 0
            val prefix = compact.substring(previousEnd, match.range.first)
                .replace(Regex("^\\d+[、.．]?"), "")
                .removeSuffix("车牌号")
                .removeSuffix("车牌号:")
                .removeSuffix("车牌号：")
                .trim(':', '：', ';', '；')
            val suffixEnd = matches.getOrNull(index + 1)?.range?.first ?: compact.length
            val suffix = compact.substring(match.range.last + 1, suffixEnd)
            val parenthesizedType = Regex("^[（(]([^）)]+)[）)]").find(suffix)?.groupValues?.get(1)
            val type = parenthesizedType ?: prefix.takeIf { it.isNotBlank() }
            ParsedWorkOrderVehicle(
                rawDescription = listOfNotNull(type, match.value).joinToString(" "),
                rawPlate = match.value,
                normalizedPlate = normalizeSearchText(match.value),
                vehicleType = type,
            )
        }
    }

    private fun extractFields(rawContent: String): Map<String, String> {
        val matches = fieldPattern.findAll(rawContent).toList()
        if (matches.isEmpty()) return emptyMap()
        return buildMap {
            matches.forEachIndexed { index, match ->
                val start = match.range.last + 1
                val end = matches.getOrNull(index + 1)?.range?.first ?: rawContent.length
                put(match.groupValues[1].trim(), rawContent.substring(start, end).trim())
            }
        }
    }

    private fun parsePeople(rawPeople: String?): List<ParsedWorkOrderPerson> {
        val content = rawPeople?.trimToNull() ?: return emptyList()
        val withoutCount = content.replace(peopleCountPattern, "").trim(' ', '，', ',', '\n', '\r')
        val lines = withoutCount.lineSequence()
            .flatMap { it.split(Regex("[,，](?=\\s*[\\p{IsHan}])")).asSequence() }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        return lines.map { line ->
            val identityMatch = identityPattern.find(line)
            val identity = identityMatch?.value?.uppercase()
            val name = identityMatch?.let { line.substring(0, it.range.first).trim().trim('，', ',') }?.trimToNull()
            ParsedWorkOrderPerson(line, name, identity)
        }
    }
}

private fun String?.trimToNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)
