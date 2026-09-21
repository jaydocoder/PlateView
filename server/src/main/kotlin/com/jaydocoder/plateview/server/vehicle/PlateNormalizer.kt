package com.jaydocoder.plateview.server.vehicle

import java.util.Locale

internal fun normalizePlate(value: String): String = value
    .uppercase(Locale.ROOT)
    .replace(Regex("[\\s　·•．.—_\\-]+"), "")
    .replace(Regex("[^\\p{IsHan}A-Z0-9]"), "")

internal fun isCompletePlateNumber(value: String): Boolean = COMPLETE_PLATE_PATTERN.matches(value)

internal const val MINIMUM_SEARCH_KEYWORD_LENGTH = 1
internal const val MAXIMUM_SEARCH_RESULT_COUNT = 8
private val COMPLETE_PLATE_PATTERN = Regex("^[\\p{IsHan}][A-Z][A-Z0-9]{5,6}$")
