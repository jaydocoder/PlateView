package com.jaydocoder.plateview.server.vehicle

import java.util.Locale

internal fun normalizePlate(value: String): String = value
    .uppercase(Locale.ROOT)
    .replace(Regex("[\\s　·•．.—_\\-]+"), "")
    .replace(Regex("[^\\p{IsHan}A-Z0-9]"), "")

internal const val MINIMUM_SEARCH_KEYWORD_LENGTH = 3
internal const val MAXIMUM_SEARCH_RESULT_COUNT = 20
