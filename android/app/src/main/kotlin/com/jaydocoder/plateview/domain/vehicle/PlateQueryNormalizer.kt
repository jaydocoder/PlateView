package com.jaydocoder.plateview.domain.vehicle

import java.util.Locale

object PlateQueryNormalizer {
    const val minimumSearchLength: Int = 3

    private val separators = Regex("[\\s　·•．.—_\\-]+")
    private val invalidCharacters = Regex("[^\\p{IsHan}A-Z0-9]")

    fun normalize(value: String): String = value
        .uppercase(Locale.ROOT)
        .replace(separators, "")
        .replace(invalidCharacters, "")
}
