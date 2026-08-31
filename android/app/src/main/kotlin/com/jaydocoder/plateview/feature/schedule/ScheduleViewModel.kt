package com.jaydocoder.plateview.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.domain.schedule.ScheduleRepository
import com.jaydocoder.plateview.domain.schedule.ScheduleMonth
import com.jaydocoder.plateview.domain.schedule.ScheduleWeek
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val week: ScheduleWeek? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val currentUserId: Long? = null,
    val month: ScheduleMonth? = null,
    val selectedMonth: YearMonth? = null,
    val monthLoading: Boolean = false,
    val monthError: String? = null,
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(private val repository: ScheduleRepository, private val sessionProvider: AuthSessionProvider) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()
    private var weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    init { load() }
    fun previousWeek() { weekStart = weekStart.minusWeeks(1); load() }
    fun nextWeek() { weekStart = weekStart.plusWeeks(1); load() }
    fun today() { weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); load() }
    fun selectWeekNumber(number: Int) { weekStart = FIRST_WEEK.plusWeeks((number - 1).toLong()); load() }
    fun selectMonth(month: YearMonth) = viewModelScope.launch {
        val session = sessionProvider.session.first()
        _uiState.update { it.copy(selectedMonth = month, monthLoading = true, monthError = null) }
        if (session == null || !session.scheduleEnabled) {
            _uiState.update { it.copy(monthError = "当前账号未加入排班", monthLoading = false) }
            return@launch
        }
        runCatching { repository.getMonth(session.accessToken, month) }
            .onSuccess { value -> _uiState.update { it.copy(month = value, monthLoading = false, monthError = null, currentUserId = session.userId) } }
            .onFailure { error -> _uiState.update { it.copy(monthLoading = false, monthError = error.message ?: "月历读取失败") } }
    }
    private fun load() = viewModelScope.launch {
        val session = sessionProvider.session.first()
        if (session == null || !session.scheduleEnabled) { _uiState.value = ScheduleUiState(error = "当前账号未加入排班"); return@launch }
        _uiState.update { it.copy(loading = true, error = null, currentUserId = session.userId) }
        runCatching { repository.getWeek(session.accessToken, weekStart) }
            .onSuccess { _uiState.value = ScheduleUiState(week = it, loading = false, currentUserId = session.userId) }
            .onFailure {
                _uiState.value = ScheduleUiState(
                    error = it.message ?: "排班读取失败",
                    loading = false,
                    currentUserId = session.userId,
                )
            }
    }
    private companion object { val FIRST_WEEK: LocalDate = LocalDate.of(2026, 7, 27) }
}
