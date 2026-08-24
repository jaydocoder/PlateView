package com.jaydocoder.plateview.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.data.statistics.StatisticsRepository
import com.jaydocoder.plateview.data.statistics.VehicleQueryHistoryItem
import com.jaydocoder.plateview.data.statistics.VehicleStatistics
import com.jaydocoder.plateview.feature.auth.AuthSession
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val range: StatisticsRange = StatisticsRange.TODAY,
    val category: String? = null,
    val scope: StatisticsScope = StatisticsScope.ME,
    val statistics: VehicleStatistics? = null,
    val history: List<VehicleQueryHistoryItem> = emptyList(),
    val pendingSyncCount: Long = 0L,
    val loading: Boolean = true,
    val error: String? = null,
    val isAdministrator: Boolean = false,
)

enum class StatisticsRange(val label: String) { TODAY("今天"), SEVEN_DAYS("近 7 天"), THIRTY_DAYS("近 30 天") }
enum class StatisticsScope(val label: String) { ME("我的统计"), ALL("全员统计") }

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: StatisticsRepository,
    private val sessionProvider: AuthSessionProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState = _uiState.asStateFlow()

    init { refresh() }

    fun selectRange(value: StatisticsRange) { _uiState.value = _uiState.value.copy(range = value); refresh() }
    fun selectCategory(value: String?) { _uiState.value = _uiState.value.copy(category = value); refresh() }
    fun selectScope(value: StatisticsScope) { _uiState.value = _uiState.value.copy(scope = value); refresh() }

    fun refresh() = viewModelScope.launch {
        val session = sessionProvider.session.first() ?: return@launch
        val filters = _uiState.value
        _uiState.value = filters.copy(loading = true, error = null, isAdministrator = session.role == "ADMIN")
        runCatching {
            val usesServer = filters.scope == StatisticsScope.ALL && session.role == "ADMIN"
            val statistics = if (usesServer) {
                repository.serverStatistics(session, filters.range.name, filters.category, filters.scope.name)
            } else {
                repository.localStatistics(session, filters.range.name, filters.category)
            }
            val history = if (filters.category == null) {
                emptyList()
            } else if (usesServer) {
                repository.serverHistory(session, filters.range.name, filters.category, filters.scope.name)
            } else {
                repository.localHistory(session, filters.range.name, filters.category)
            }
            Triple(statistics, history, repository.pendingSyncCount(session))
        }.onSuccess { (statistics, history, pendingSyncCount) ->
            _uiState.value = _uiState.value.copy(
                statistics = statistics,
                history = history,
                pendingSyncCount = pendingSyncCount,
                loading = false,
            )
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                history = emptyList(),
                loading = false,
                error = error.message ?: "统计数据加载失败",
            )
        }
    }
}
