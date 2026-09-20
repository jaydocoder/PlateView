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
import kotlinx.coroutines.launch
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

        val localWorkOrders = if (session.wechatWorkOrderAccessEnabled) {
            runCatching { workOrderRepository.searchCached(normalizedQuery) }.getOrDefault(emptyList())
        } else {
            runCatching { workOrderRepository.clear(session.userId) }
            emptyList()
        }
        _uiState.update { it.copy(workOrderCandidates = localWorkOrders) }
        if (session.wechatWorkOrderAccessEnabled) {
            val localMessages = runCatching { workOrderRepository.searchMessagesCached(normalizedQuery) }.getOrDefault(emptyList())
            _uiState.update { it.copy(wechatMessages = localMessages) }
            runCatching { workOrderRepository.searchRemote(session.accessToken, normalizedQuery) }
                .onSuccess { records -> _uiState.update { it.copy(workOrderCandidates = records) } }
                .onFailure { error ->
                    error.rethrowIfCancellation()
                    if (error is HttpException && error.code() == HTTP_FORBIDDEN) workOrderRepository.clear(session.userId)
                }
            runCatching { workOrderRepository.searchMessagesRemote(session.accessToken, normalizedQuery) }
                .onSuccess { page -> _uiState.update { it.copy(wechatMessages = page.records) } }
                .onFailure { error ->
                    error.rethrowIfCancellation()
                    if (error is HttpException && error.code() == HTTP_FORBIDDEN) workOrderRepository.clear(session.userId)
                }
            runCatching { workOrderRepository.synchronize(session.accessToken) }.onFailure(Throwable::rethrowIfCancellation)
        } else {
            _uiState.update { it.copy(wechatMessages = emptyList()) }
        }

        val localCandidates = runCatching {
            vehicleCacheRepository.search(normalizedQuery)
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            emptyList()
        }
        if (localCandidates.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    candidates = localCandidates,
                    resultState = SearchResultState.Idle,
                )
            }
            runCatching {
                vehicleCacheRepository.synchronizeCatalog(
                    accessToken = session.accessToken,
                )
            }.onSuccess {
                val refreshedCandidates = runCatching {
                    vehicleCacheRepository.search(normalizedQuery)
                }.getOrElse { error ->
                    error.rethrowIfCancellation()
                    emptyList()
                }
                _uiState.update {
                    it.copy(candidates = refreshedCandidates)
                }
            }.onFailure { throwable ->
                throwable.rethrowIfCancellation()
                if (throwable is HttpException && throwable.code() == HTTP_UNAUTHORIZED) {
                    sessionProvider.logout()
                }
            }
            return
        }

        _uiState.update {
            it.copy(
                candidates = emptyList(),
            resultState = if (it.workOrderCandidates.isEmpty() && it.wechatMessages.isEmpty()) SearchResultState.Loading else SearchResultState.Idle,
            )
        }
        val remoteResult = runCatching {
            vehicleRepository.search(session.accessToken, normalizedQuery)
        }
        remoteResult.exceptionOrNull()?.rethrowIfCancellation()
        if (remoteResult.isSuccess) {
            val candidates = remoteResult.getOrThrow()
            _uiState.update {
                it.copy(
                    candidates = candidates,
                    resultState = if (candidates.isEmpty() && it.workOrderCandidates.isEmpty() && it.wechatMessages.isEmpty()) SearchResultState.Empty else SearchResultState.Idle,
                )
            }
        }
        val synchronizationResult = runCatching {
            vehicleCacheRepository.synchronizeCatalog(
                accessToken = session.accessToken,
            )
        }
        synchronizationResult.exceptionOrNull()?.rethrowIfCancellation()
        val synchronizedCandidates = runCatching {
            vehicleCacheRepository.search(normalizedQuery)
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            emptyList()
        }
        when {
            synchronizedCandidates.isNotEmpty() -> _uiState.update {
                it.copy(candidates = synchronizedCandidates, resultState = SearchResultState.Idle)
            }

            remoteResult.isSuccess -> return

            _uiState.value.workOrderCandidates.isNotEmpty() || _uiState.value.wechatMessages.isNotEmpty() -> _uiState.update {
                it.copy(resultState = SearchResultState.Idle)
            }

            else -> handleSearchFailure(remoteResult.exceptionOrNull() ?: synchronizationResult.exceptionOrNull())
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
