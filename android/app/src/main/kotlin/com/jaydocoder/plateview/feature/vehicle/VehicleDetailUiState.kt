package com.jaydocoder.plateview.feature.vehicle

import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
import com.jaydocoder.plateview.data.network.AppError

data class VehicleDetailUiState(
    val content: VehicleDetailContent = VehicleDetailContent.Loading,
    val freshnessLabel: String = "正在确认最新数据",
    val dataConfirmed: Boolean = false,
)

sealed interface VehicleDetailContent {
    data object Loading : VehicleDetailContent

    data class Data(
        val vehicle: VehicleDetail,
        val isCached: Boolean = false,
    ) : VehicleDetailContent

    data class Error(val error: AppError) : VehicleDetailContent
}
