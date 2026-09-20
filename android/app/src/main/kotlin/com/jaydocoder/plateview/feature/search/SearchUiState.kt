package com.jaydocoder.plateview.feature.search

import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.data.network.AppError
import com.jaydocoder.plateview.domain.workorder.WorkOrder
import com.jaydocoder.plateview.domain.workorder.WechatMessage

data class SearchUiState(
    val query: String = "",
    val resultState: SearchResultState = SearchResultState.Idle,
    val candidates: List<VehicleCandidate> = emptyList(),
    val workOrderCandidates: List<WorkOrder> = emptyList(),
    val wechatMessages: List<WechatMessage> = emptyList(),
    val history: List<SearchHistoryItem> = emptyList(),
)

sealed interface SearchResultState {
    data object Idle : SearchResultState

    data object AwaitingInput : SearchResultState

    data object Loading : SearchResultState

    data object Empty : SearchResultState

    data class Error(val error: AppError) : SearchResultState
}

sealed interface SearchEvent {
    data class OpenVehicle(val vehicleId: Long) : SearchEvent
    data class OpenWorkOrder(val recordId: Long, val query: String) : SearchEvent
    data class OpenWechatMessage(val messageId: Long) : SearchEvent
}
