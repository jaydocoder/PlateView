package com.jaydocoder.plateview.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.data.network.AppError
import com.jaydocoder.plateview.data.network.AppErrorKind
import com.jaydocoder.plateview.data.network.AppErrorMapper
import com.jaydocoder.plateview.data.network.AppErrorTelemetry
import com.jaydocoder.plateview.data.network.rethrowIfCancellation
import com.jaydocoder.plateview.data.network.ClientRuntimePolicyProvider
import com.jaydocoder.plateview.data.network.DefaultClientRuntimePolicyProvider
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
    private val runtimePolicyRepository: ClientRuntimePolicyProvider = DefaultClientRuntimePolicyProvider,
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
            combine(
                query,
                retryVersion,
                runtimePolicyRepository.policy,
                runtimePolicyRepository.cacheMaintenanceActive,
            ) { queryValue, _, _, maintenance -> PlateQueryNormalizer.normalize(queryValue) to maintenance }
                .debounce { (_, maintenance) -> if (maintenance) 0L else QUERY_DEBOUNCE_MILLIS }
                .collectLatest { (normalizedQuery, maintenance) ->
                    if (maintenance) clearSearchForMaintenance() else performSearch(normalizedQuery)
                }
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

        val limits = runtimePolicyRepository.policy.value
        _uiState.update {
            it.copy(
                candidates = emptyList(),
                workOrderCandidates = emptyList(),
                wechatMessages = emptyList(),
                vehicleSectionState = if (limits.vehicleResultLimit > 0) SearchSectionState.Loading else SearchSectionState.Idle,
                workOrderSectionState = if (session.wechatWorkOrderAccessEnabled && limits.workOrderResultLimit > 0) SearchSectionState.Loading else SearchSectionState.Idle,
                wechatMessageSectionState = if (session.wechatWorkOrderAccessEnabled && limits.wechatMessageResultLimit > 0) SearchSectionState.Loading else SearchSectionState.Idle,
                resultState = SearchResultState.Loading,
            )
        }

        supervisorScope {
            val jobs = mutableListOf<kotlinx.coroutines.Job>()
            if (limits.vehicleResultLimit == 0) {
                jobs += launch { vehicleCacheRepository.clearSnapshot() }
            } else jobs += listOf(
                launch {
                    runCatching { vehicleCacheRepository.search(normalizedQuery, limits.vehicleResultLimit) }
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
                    runCatching { vehicleRepository.search(session.accessToken, normalizedQuery).take(limits.vehicleResultLimit) }
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
                if (limits.workOrderResultLimit == 0) jobs += launch { workOrderRepository.clearWorkOrders() } else jobs += launch {
                    runCatching { workOrderRepository.searchCached(normalizedQuery, limits.workOrderResultLimit) }
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
                if (limits.wechatMessageResultLimit == 0) jobs += launch { workOrderRepository.clearMessages() } else jobs += launch {
                    runCatching { workOrderRepository.searchMessagesCached(normalizedQuery, limits.wechatMessageResultLimit) }
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
                if (limits.workOrderResultLimit > 0 || limits.wechatMessageResultLimit > 0) jobs += launch {
                    runCatching { workOrderRepository.searchHomeRemote(session.accessToken, normalizedQuery, maxOf(limits.workOrderResultLimit, limits.wechatMessageResultLimit)) }
                        .onSuccess { remote ->
                            val sectionFailure = AppErrorMapper.map("查询微信记录", IllegalStateException("搜索分区暂时不可用"))
                            _uiState.update {
                                it.copy(
                                    workOrderCandidates = remote.workOrders.take(limits.workOrderResultLimit),
                                    wechatMessages = remote.wechatMessages.take(limits.wechatMessageResultLimit),
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
            if (runtimePolicyRepository.cacheMaintenanceActive.value) return@launch
            val limits = runtimePolicyRepository.policy.value
            if (limits.vehicleResultLimit > 0) {
                runCatching {
                    vehicleCacheRepository.synchronizeCatalog(
                        accessToken = session.accessToken,
                        forceVersionCheck = forceVersionCheck,
                    )
                }.onFailure(Throwable::rethrowIfCancellation)
            } else {
                runCatching { vehicleCacheRepository.clearSnapshot() }
            }
            if (session.wechatWorkOrderAccessEnabled && limits.workOrderResultLimit > 0) {
                runCatching { workOrderRepository.synchronize(session.accessToken, forceVersionCheck) }
                    .onFailure(Throwable::rethrowIfCancellation)
            } else {
                runCatching { workOrderRepository.clearWorkOrders() }
            }
            if (!session.wechatWorkOrderAccessEnabled || limits.wechatMessageResultLimit == 0) {
                runCatching { workOrderRepository.clearMessages() }
            }
        }
    }

    private fun clearSearchForMaintenance() {
        _uiState.update {
            it.copy(
                candidates = emptyList(),
                workOrderCandidates = emptyList(),
                wechatMessages = emptyList(),
                vehicleSectionState = SearchSectionState.Idle,
                workOrderSectionState = SearchSectionState.Idle,
                wechatMessageSectionState = SearchSectionState.Idle,
                resultState = SearchResultState.Idle,
            )
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
