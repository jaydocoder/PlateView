package com.jaydocoder.plateview.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.data.statistics.StatisticsRepository
import com.jaydocoder.plateview.data.statistics.VehicleQueryHistoryPage
import com.jaydocoder.plateview.data.statistics.VehicleQueryHistoryItem
import com.jaydocoder.plateview.data.statistics.VehicleStatistics
import com.jaydocoder.plateview.data.network.AppErrorMapper
import com.jaydocoder.plateview.data.network.AppErrorTelemetry
import com.jaydocoder.plateview.data.network.displayText
import com.jaydocoder.plateview.data.network.rethrowIfCancellation
import com.jaydocoder.plateview.feature.auth.AuthSession
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val range: StatisticsRange = StatisticsRange.TODAY,
    val category: String? = null,
    val scope: StatisticsScope = StatisticsScope.ME,
    val statistics: VehicleStatistics? = null,
    val history: List<VehicleQueryHistoryItem> = emptyList(),
    val historyQuery: String = "",
    val historyTotal: Int = 0,
    val isHistoryPageLoading: Boolean = false,
    val historyLoadError: String? = null,
    val pendingSyncCount: Long = 0L,
    val loading: Boolean = true,
    val error: String? = null,
    val isAdministrator: Boolean = false,
    val canViewAllStatistics: Boolean = false,
)

enum class StatisticsRange(val label: String) { TODAY("今天"), SEVEN_DAYS("近 7 天"), THIRTY_DAYS("近 30 天"), ALL_TIME("全部时间") }
enum class StatisticsScope(val label: String) { ME("我的统计"), ALL("全员统计") }

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: StatisticsRepository,
    private val sessionProvider: AuthSessionProvider,
) : ViewModel() {
    private var refreshJob: Job? = null
    private var historyLoadJob: Job? = null
    private var historyRequestVersion = 0L
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState = _uiState.asStateFlow()

    init { refresh() }

    fun selectRange(value: StatisticsRange) { _uiState.value = _uiState.value.copy(range = value); refresh() }
    fun selectCategory(value: String?) { _uiState.value = _uiState.value.copy(category = value); refresh() }
    fun selectScope(value: StatisticsScope) {
        if (value == StatisticsScope.ALL && !_uiState.value.canViewAllStatistics) return
        _uiState.value = _uiState.value.copy(scope = value)
        refresh()
    }

    fun updateHistoryQuery(query: String) {
        if (_uiState.value.historyQuery == query) return
        _uiState.value = _uiState.value.copy(historyQuery = query)
        refresh(delayMillis = HISTORY_SEARCH_DEBOUNCE_MILLIS)
    }

    fun loadMoreHistory() {
        val state = _uiState.value
        if (state.loading || state.isHistoryPageLoading || state.history.size >= state.historyTotal) return
        val requestVersion = historyRequestVersion
        val offset = state.history.size
        _uiState.value = state.copy(isHistoryPageLoading = true, historyLoadError = null)
        historyLoadJob = viewModelScope.launch {
            val session = sessionProvider.session.first() ?: return@launch
            runCatching {
                loadHistoryPage(session, _uiState.value, offset)
            }.onSuccess { page ->
                if (requestVersion != historyRequestVersion) return@onSuccess
                _uiState.value = _uiState.value.copy(
                    history = _uiState.value.history + page.items,
                    historyTotal = page.total,
                    isHistoryPageLoading = false,
                )
            }.onFailure { error ->
                error.rethrowIfCancellation()
                if (requestVersion != historyRequestVersion) return@onFailure
                val appError = AppErrorMapper.map("加载更多查询记录", error)
                AppErrorTelemetry.report(appError)
                _uiState.value = _uiState.value.copy(
                    isHistoryPageLoading = false,
                    historyLoadError = appError.displayText(),
                )
            }
        }
    }

    fun refresh(delayMillis: Long = 0L) {
        val requestVersion = ++historyRequestVersion
        refreshJob?.cancel()
        historyLoadJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (delayMillis > 0L) delay(delayMillis)
            val session = sessionProvider.session.first() ?: return@launch
            val filters = _uiState.value
            val canViewAllStatistics = session.username == "admin" && session.role == "ADMIN"
            val scope = filters.scope.takeIf { it != StatisticsScope.ALL || canViewAllStatistics } ?: StatisticsScope.ME
            val effectiveFilters = filters.copy(
                scope = scope,
                loading = true,
                error = null,
                history = emptyList(),
                historyTotal = 0,
                isHistoryPageLoading = false,
                historyLoadError = null,
                isAdministrator = session.role == "ADMIN",
                canViewAllStatistics = canViewAllStatistics,
            )
            _uiState.value = effectiveFilters
            runCatching {
                val usesServer = effectiveFilters.scope == StatisticsScope.ALL && canViewAllStatistics
                val statistics = if (usesServer) {
                    repository.serverStatistics(session, effectiveFilters.range.name, effectiveFilters.category, effectiveFilters.scope.name)
                } else {
                    repository.localStatistics(session, effectiveFilters.range.name, effectiveFilters.category)
                }
                val history = loadHistoryPage(session, effectiveFilters, offset = 0)
                Triple(statistics, history, repository.pendingSyncCount(session))
            }.onSuccess { (statistics, history, pendingSyncCount) ->
                if (requestVersion != historyRequestVersion) return@onSuccess
                _uiState.value = _uiState.value.copy(
                    statistics = statistics,
                    history = history.items,
                    historyTotal = history.total,
                    pendingSyncCount = pendingSyncCount,
                    loading = false,
                )
            }.onFailure { error ->
                error.rethrowIfCancellation()
                if (requestVersion != historyRequestVersion) return@onFailure
                val appError = AppErrorMapper.map("加载查询统计", error)
                AppErrorTelemetry.report(appError)
                _uiState.value = _uiState.value.copy(
                    history = emptyList(),
                    loading = false,
                    error = appError.displayText(),
                )
            }
        }
    }

    private suspend fun loadHistoryPage(
        session: AuthSession,
        state: StatisticsUiState,
        offset: Int,
    ): VehicleQueryHistoryPage {
        val usesServer = state.scope == StatisticsScope.ALL && state.canViewAllStatistics
        return if (usesServer) {
            repository.serverHistoryPage(
                session,
                state.range.name,
                state.category,
                state.scope.name,
                state.historyQuery,
                HISTORY_PAGE_SIZE,
                offset,
            )
        } else {
            repository.localHistoryPage(
                session,
                state.range.name,
                state.category,
                state.historyQuery,
                HISTORY_PAGE_SIZE,
                offset,
            )
        }
    }

    private companion object {
        const val HISTORY_PAGE_SIZE = 20
        const val HISTORY_SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
