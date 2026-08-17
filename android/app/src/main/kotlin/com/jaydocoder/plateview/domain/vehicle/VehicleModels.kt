package com.jaydocoder.plateview.domain.vehicle

import androidx.compose.runtime.Immutable

@Immutable
data class VehicleCandidate(
    val id: Long,
    val plateNumber: String,
    val category: String,
    val categoryLabel: String,
    val organizationName: String? = null,
)

data class VehicleDetail(
    val id: Long,
    val plateNumber: String,
    val normalizedPlate: String,
    val category: String,
    val categoryLabel: String,
    val vehicleType: String?,
    val attributes: List<VehicleAttribute>,
    val residentProfile: ResidentProfile?,
    val longTermProfile: LongTermProfile?,
)

@Immutable
data class VehicleAttribute(
    val label: String,
    val value: String,
)

@Immutable
data class ResidentProfile(
    val ownerName: String?,
    val identityCardNumber: String?,
    val contactPhone: String?,
    val remarks: String?,
)

@Immutable
data class LongTermProfile(
    val organizationName: String?,
    val passHolder: String?,
    val passageDetails: String?,
    val remarks: String?,
)
