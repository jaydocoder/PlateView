package com.jaydocoder.plateview.domain.vehicle

fun formatPlateForDisplay(plateNumber: String): String {
    val normalized = PlateQueryNormalizer.normalize(plateNumber)
    return if (normalized.length >= 3 && normalized.firstOrNull()?.isLetter() == true && normalized[1].isLetter()) {
        normalized.take(2) + "·" + normalized.drop(2)
    } else {
        plateNumber
    }
}
