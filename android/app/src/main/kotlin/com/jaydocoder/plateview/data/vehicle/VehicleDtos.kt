package com.jaydocoder.plateview.data.vehicle

import com.google.gson.JsonObject

data class VehicleSearchResponseDto(
    val candidates: List<VehicleCandidateDto>,
)

data class VehicleCatalogVersionDto(
    val catalogVersion: Long,
)

data class VehicleCatalogResponseDto(
    val catalogVersion: Long,
    val total: Int,
    val items: List<VehicleCandidateDto>,
)

data class VehicleFullCatalogResponseDto(
    val catalogVersion: Long,
    val total: Int,
    val items: List<VehicleDetailDto>,
)

data class VehicleCandidateDto(
    val id: Long,
    val plateNumber: String,
    val category: String,
    val categoryLabel: String,
)

data class VehicleDetailDto(
    val id: Long,
    val plateNumber: String,
    val normalizedPlate: String,
    val category: String,
    val categoryLabel: String,
    val vehicleType: String?,
    val attributes: JsonObject,
    val residentProfile: ResidentProfileDto?,
    val longTermProfile: LongTermProfileDto?,
)

data class ResidentProfileDto(
    val ownerName: String,
    val identityCardNumber: String,
    val contactPhone: String?,
    val remarks: String?,
)

data class LongTermProfileDto(
    val organizationName: String?,
    val passHolder: String?,
    val passageDetails: String?,
    val remarks: String?,
)
