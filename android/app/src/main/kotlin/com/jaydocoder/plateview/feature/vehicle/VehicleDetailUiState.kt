package com.jaydocoder.plateview.feature.vehicle

import com.jaydocoder.plateview.domain.vehicle.VehicleDetail

data class VehicleDetailUiState(
    val content: VehicleDetailContent = VehicleDetailContent.Loading,
)

sealed interface VehicleDetailContent {
    data object Loading : VehicleDetailContent

    data class Data(val vehicle: VehicleDetail) : VehicleDetailContent

    data class Error(val reason: VehicleDetailFailure) : VehicleDetailContent
}

enum class VehicleDetailFailure {
    SessionExpired,
    VehicleNotFound,
    ServiceUnavailable,
}
