package com.jaydocoder.plateview.feature.search

import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate

data class SearchUiState(
    val query: String = "",
    val resultState: SearchResultState = SearchResultState.Idle,
    val candidates: List<VehicleCandidate> = emptyList(),
    val history: List<SearchHistoryItem> = emptyList(),
    val isListening: Boolean = false,
    val voiceFailure: VoiceInputFailure? = null,
)

sealed interface SearchResultState {
    data object Idle : SearchResultState

    data object AwaitingInput : SearchResultState

    data object Loading : SearchResultState

    data object Empty : SearchResultState

    data class Error(val reason: SearchFailure) : SearchResultState
}

enum class SearchFailure {
    SessionExpired,
    ServiceUnavailable,
}

sealed interface SearchEvent {
    data class OpenVehicle(val vehicleId: Long) : SearchEvent

    data object LaunchSystemVoiceRecognition : SearchEvent
}
