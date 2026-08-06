package com.jaydocoder.plateview.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.history.SearchHistoryRepository
import com.jaydocoder.plateview.domain.vehicle.PlateQueryNormalizer
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.domain.vehicle.VehicleRepository
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val historyRepository: SearchHistoryRepository,
    private val sessionProvider: AuthSessionProvider,
    private val voiceRecognizer: VoiceRecognizer,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val retryVersion = MutableStateFlow(0)
    private val _uiState = MutableStateFlow(SearchUiState())
    private val _events = MutableSharedFlow<SearchEvent>()

    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    val events: Flow<SearchEvent> = _events.asSharedFlow()

    init {
        observeQuery()
        observeHistory()
    }

    fun updateQuery(value: String) {
        query.value = value
        _uiState.update {
            it.copy(
                query = value,
                voiceFailure = null,
            )
        }
    }

    fun retrySearch() {
        retryVersion.update(Int::inc)
    }

    fun selectCandidate(candidate: VehicleCandidate) {
        viewModelScope.launch {
            sessionProvider.session.first()?.let { session ->
                runCatching { historyRepository.save(session.username, candidate) }
            }
            _events.emit(SearchEvent.OpenVehicle(candidate.id))
        }
    }

    fun selectHistory(item: SearchHistoryItem) {
        viewModelScope.launch {
            _events.emit(SearchEvent.OpenVehicle(item.vehicleId))
        }
    }

    fun deleteHistory(historyId: Long) {
        viewModelScope.launch {
            sessionProvider.session.first()?.let { session ->
                historyRepository.delete(session.username, historyId)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            sessionProvider.session.first()?.let { session ->
                historyRepository.clear(session.username)
            }
        }
    }

    fun startVoiceInput() {
        _uiState.update { it.copy(isListening = true, voiceFailure = null) }
        voiceRecognizer.start(
            onResult = { recognizedText ->
                _uiState.update { it.copy(isListening = false) }
                updateQuery(recognizedText)
            },
            onFailure = { failure ->
                _uiState.update {
                    it.copy(
                        isListening = false,
                        voiceFailure = failure,
                    )
                }
            },
        )
    }

    fun onVoicePermissionDenied() {
        _uiState.update {
            it.copy(
                isListening = false,
                voiceFailure = VoiceInputFailure.PermissionDenied,
            )
        }
    }

    override fun onCleared() {
        voiceRecognizer.release()
        super.onCleared()
    }

    private fun observeQuery() {
        viewModelScope.launch {
            combine(query, retryVersion) { queryValue, _ -> queryValue }
                .map(PlateQueryNormalizer::normalize)
                .debounce(QUERY_DEBOUNCE_MILLIS)
                .collectLatest { normalizedQuery -> performSearch(normalizedQuery) }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            sessionProvider.session
                .flatMapLatest { session ->
                    session?.let { historyRepository.observe(it.username) } ?: flowOf(emptyList())
                }
                .collect { history -> _uiState.update { it.copy(history = history) } }
        }
    }

    private suspend fun performSearch(normalizedQuery: String) {
        if (normalizedQuery.length < PlateQueryNormalizer.minimumSearchLength) {
            _uiState.update {
                it.copy(
                    candidates = emptyList(),
                    resultState = if (it.query.isBlank()) {
                        SearchResultState.Idle
                    } else {
                        SearchResultState.AwaitingInput
                    },
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                candidates = emptyList(),
                resultState = SearchResultState.Loading,
            )
        }
        val session = sessionProvider.session.first()
        if (session == null) {
            _uiState.update {
                it.copy(resultState = SearchResultState.Error(SearchFailure.SessionExpired))
            }
            return
        }

        try {
            val candidates = vehicleRepository.search(session.accessToken, normalizedQuery)
            _uiState.update {
                it.copy(
                    candidates = candidates,
                    resultState = if (candidates.isEmpty()) {
                        SearchResultState.Empty
                    } else {
                        SearchResultState.Idle
                    },
                )
            }
        } catch (throwable: Throwable) {
            if (throwable is HttpException && throwable.code() == HTTP_UNAUTHORIZED) {
                sessionProvider.logout()
                _uiState.update {
                    it.copy(resultState = SearchResultState.Error(SearchFailure.SessionExpired))
                }
            } else {
                _uiState.update {
                    it.copy(resultState = SearchResultState.Error(SearchFailure.ServiceUnavailable))
                }
            }
        }
    }

    private companion object {
        const val QUERY_DEBOUNCE_MILLIS = 250L
        const val HTTP_UNAUTHORIZED = 401
    }
}
