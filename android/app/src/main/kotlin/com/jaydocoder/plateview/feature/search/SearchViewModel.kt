package com.jaydocoder.plateview.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.data.network.AppError
import com.jaydocoder.plateview.data.network.AppErrorKind
import com.jaydocoder.plateview.data.network.AppErrorMapper
import com.jaydocoder.plateview.data.network.AppErrorTelemetry
import com.jaydocoder.plateview.data.network.rethrowIfCancellation
import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.history.SearchHistoryRepository
import com.jaydocoder.plateview.domain.vehicle.PlateQueryNormalizer
import com.jaydocoder.plateview.domain.vehicle.VehicleCacheRepository
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.domain.vehicle.VehicleRepository
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import com.jaydocoder.plateview.domain.workorder.WorkOrder
import com.jaydocoder.plateview.domain.workorder.WorkOrderRepository
import com.jaydocoder.plateview.domain.workorder.WechatMessage
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import retrofit2.HttpException

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val vehicleCacheRepository: VehicleCacheRepository,
    private val historyRepository: SearchHistoryRepository,
    private val sessionProvider: AuthSessionProvider,
    private val workOrderRepository: WorkOrderRepository,
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
        syncCatalogInBackground()
    }

    fun updateQuery(value: String) {
        query.value = value
        _uiState.update { it.copy(query = value) }
    }

    fun retrySearch() {
        retryVersion.update(Int::inc)
    }

    fun onAppForeground() {
        syncCatalogInBackground(forceVersionCheck = true)
    }

    fun selectCandidate(candidate: VehicleCandidate) {
        viewModelScope.launch {
            sessionProvider.session.first()?.let { session ->
                runCatching { historyRepository.save(session.username, candidate) }
                    .onFailure(Throwable::rethrowIfCancellation)
            }
            _events.emit(SearchEvent.OpenVehicle(candidate.id))
        }
    }

    fun selectWorkOrder(candidate: WorkOrder) {
        viewModelScope.launch { _events.emit(SearchEvent.OpenWorkOrder(candidate.id, _uiState.value.query)) }
    }

    fun selectWechatMessage(candidate: WechatMessage) {
        viewModelScope.launch { _events.emit(SearchEvent.OpenWechatMessage(candidate.id)) }
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
        if (normalizedQuery.isBlank()) {
            _uiState.update {
                it.copy(
                    candidates = emptyList(),
                    workOrderCandidates = emptyList(),
                    wechatMessages = emptyList(),
                    vehicleSectionState = SearchSectionState.Idle,
                    workOrderSectionState = SearchSectionState.Idle,
                    wechatMessageSectionState = SearchSectionState.Idle,
                    resultState = if (it.query.isBlank()) {
                        SearchResultState.Idle
                    } else {
                        SearchResultState.AwaitingInput
                    },
                )
            }
            return
        }

        val session = sessionProvider.session.first()
        if (session == null) {
            _uiState.update {
                it.copy(resultState = SearchResultState.Error(searchError(AppErrorKind.SessionExpired, "登录已失效，请重新登录")))
            }
            return
        }

        _uiState.update {
            it.copy(
                candidates = emptyList(),
                workOrderCandidates = emptyList(),
                wechatMessages = emptyList(),
                vehicleSectionState = SearchSectionState.Loading,
                workOrderSectionState = if (session.wechatWorkOrderAccessEnabled) SearchSectionState.Loading else SearchSectionState.Idle,
                wechatMessageSectionState = if (session.wechatWorkOrderAccessEnabled) SearchSectionState.Loading else SearchSectionState.Idle,
                resultState = SearchResultState.Loading,
            )
        }

        supervisorScope {
            val jobs = mutableListOf(
                launch {
                    runCatching { vehicleCacheRepository.search(normalizedQuery).take(MAXIMUM_RESULTS_PER_SECTION) }
                        .onSuccess { local ->
                            if (local.isNotEmpty()) _uiState.update {
                                if (it.vehicleSectionState is SearchSectionState.Loading) {
                                    it.copy(candidates = local, resultState = SearchResultState.Idle)
                                } else {
                                    it
                                }
                            }
                        }
                        .onFailure(Throwable::rethrowIfCancellation)
                },
                launch {
                    runCatching { vehicleRepository.search(session.accessToken, normalizedQuery).take(MAXIMUM_RESULTS_PER_SECTION) }
                        .onSuccess { remote ->
                            _uiState.update {
                                it.copy(
                                    candidates = remote,
                                    vehicleSectionState = if (remote.isEmpty()) SearchSectionState.Empty else SearchSectionState.Success,
                                )
                            }
                        }
                        .onFailure { error ->
                            error.rethrowIfCancellation()
                            if (error is HttpException && error.code() == HTTP_UNAUTHORIZED) sessionProvider.logout()
                            _uiState.update { it.copy(vehicleSectionState = SearchSectionState.Error(AppErrorMapper.map("查询匹配车辆", error))) }
                        }
                },
            )

            if (!session.wechatWorkOrderAccessEnabled) {
                jobs += launch { runCatching { workOrderRepository.clear(session.userId) } }
            } else {
                jobs += launch {
                    runCatching { workOrderRepository.searchCached(normalizedQuery).take(MAXIMUM_RESULTS_PER_SECTION) }
                        .onSuccess { local ->
                            if (local.isNotEmpty()) _uiState.update {
                                if (it.workOrderSectionState is SearchSectionState.Loading) {
                                    it.copy(workOrderCandidates = local, resultState = SearchResultState.Idle)
                                } else {
                                    it
                                }
                            }
                        }
                        .onFailure(Throwable::rethrowIfCancellation)
                }
                jobs += launch {
                    runCatching { workOrderRepository.searchMessagesCached(normalizedQuery).take(MAXIMUM_RESULTS_PER_SECTION) }
                        .onSuccess { local ->
                            if (local.isNotEmpty()) _uiState.update {
                                if (it.wechatMessageSectionState is SearchSectionState.Loading) {
                                    it.copy(wechatMessages = local, resultState = SearchResultState.Idle)
                                } else {
                                    it
                                }
                            }
                        }
                        .onFailure(Throwable::rethrowIfCancellation)
                }
                jobs += launch {
                    runCatching { workOrderRepository.searchHomeRemote(session.accessToken, normalizedQuery) }
                        .onSuccess { remote ->
                            val sectionFailure = AppErrorMapper.map("查询微信记录", IllegalStateException("搜索分区暂时不可用"))
                            _uiState.update {
                                it.copy(
                                    workOrderCandidates = remote.workOrders.take(MAXIMUM_RESULTS_PER_SECTION),
                                    wechatMessages = remote.wechatMessages.take(MAXIMUM_RESULTS_PER_SECTION),
                                    workOrderSectionState = when {
                                        remote.workOrderFailed -> SearchSectionState.Error(sectionFailure)
                                        remote.workOrders.isEmpty() -> SearchSectionState.Empty
                                        else -> SearchSectionState.Success
                                    },
                                    wechatMessageSectionState = when {
                                        remote.wechatMessageFailed -> SearchSectionState.Error(sectionFailure)
                                        remote.wechatMessages.isEmpty() -> SearchSectionState.Empty
                                        else -> SearchSectionState.Success
                                    },
                                )
                            }
                        }
                        .onFailure { error ->
                            error.rethrowIfCancellation()
                            if (error is HttpException && error.code() == HTTP_FORBIDDEN) workOrderRepository.clear(session.userId)
                            val mapped = AppErrorMapper.map("查询微信记录", error)
                            _uiState.update {
                                it.copy(
                                    workOrderSectionState = SearchSectionState.Error(mapped),
                                    wechatMessageSectionState = SearchSectionState.Error(mapped),
                                )
                            }
                        }
                }
            }
            jobs.joinAll()
        }
        syncCatalogInBackground()
        updateOverallSearchState()
    }

    private fun updateOverallSearchState() {
        _uiState.update { state ->
            val hasResults = state.candidates.isNotEmpty() || state.workOrderCandidates.isNotEmpty() || state.wechatMessages.isNotEmpty()
            val errors = listOf(state.vehicleSectionState, state.workOrderSectionState, state.wechatMessageSectionState)
                .filterIsInstance<SearchSectionState.Error>()
            state.copy(
                resultState = when {
                    hasResults -> SearchResultState.Idle
                    errors.isNotEmpty() -> SearchResultState.Error(errors.first().error)
                    else -> SearchResultState.Empty
                },
            )
        }
    }

    private fun syncCatalogInBackground(forceVersionCheck: Boolean = false) {
        viewModelScope.launch {
            val session = sessionProvider.session.first() ?: return@launch
            runCatching {
                vehicleCacheRepository.synchronizeCatalog(
                    accessToken = session.accessToken,
                    forceVersionCheck = forceVersionCheck,
                )
            }.onFailure(Throwable::rethrowIfCancellation)
            if (session.wechatWorkOrderAccessEnabled) {
                runCatching { workOrderRepository.synchronize(session.accessToken, forceVersionCheck) }
                    .onFailure(Throwable::rethrowIfCancellation)
            } else {
                runCatching { workOrderRepository.clear(session.userId) }
            }
        }
    }

    private suspend fun handleSearchFailure(throwable: Throwable?) {
        throwable?.rethrowIfCancellation()
        val error = AppErrorMapper.map("查询车辆", throwable ?: IllegalStateException("查询未完成"))
        if (error.kind == AppErrorKind.SessionExpired) {
            sessionProvider.logout()
        }
        AppErrorTelemetry.report(error)
        _uiState.update { it.copy(resultState = SearchResultState.Error(error)) }
    }

    private companion object {
        const val QUERY_DEBOUNCE_MILLIS = 250L
        const val MAXIMUM_RESULTS_PER_SECTION = 8
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}

private fun searchError(kind: AppErrorKind, message: String) = AppError(
    operation = "查询车辆",
    kind = kind,
    requestId = java.util.UUID.randomUUID().toString(),
    message = message,
    retryable = false,
)
