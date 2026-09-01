package com.jaydocoder.plateview.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.domain.schedule.ScheduleAssignmentCommand
import com.jaydocoder.plateview.domain.schedule.SchedulePlanningConfiguration
import com.jaydocoder.plateview.domain.schedule.SchedulePlanningConfigurationCommand
import com.jaydocoder.plateview.domain.schedule.ScheduleRepository
import com.jaydocoder.plateview.domain.schedule.ScheduleShiftType
import com.jaydocoder.plateview.domain.schedule.ScheduleTemplateCommand
import com.jaydocoder.plateview.domain.schedule.ScheduleTemplateSummary
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SchedulePlannerState(
    val templates: List<ScheduleTemplateSummary> = emptyList(),
    val configuration: SchedulePlanningConfiguration? = null,
    val editor: ScheduleTemplateEditor? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val applicationSuccessMessage: String? = null,
)

data class ScheduleTemplateEditor(
    val id: Long? = null,
    val name: String = "",
    val cycleDays: Int = 9,
    val participantIds: Set<Long> = emptySet(),
    val selectedDay: Int = 1,
    val assignments: Map<Pair<Int, ScheduleShiftType>, List<Long>> = emptyMap(),
    val effectiveFrom: LocalDate = LocalDate.now(),
) {
    fun people(day: Int, type: ScheduleShiftType): List<Long> = assignments[day to type].orEmpty()
    fun command() = ScheduleTemplateCommand(
        name,
        cycleDays,
        participantIds.sorted(),
        (1..cycleDays).flatMap { day -> ScheduleShiftType.entries.map { type -> ScheduleAssignmentCommand(day, type, people(day, type)) } },
    )
}

@HiltViewModel
class SchedulePlannerViewModel @Inject constructor(private val repository: ScheduleRepository, private val sessionProvider: AuthSessionProvider) : ViewModel() {
    private val _uiState = MutableStateFlow(SchedulePlannerState())
    val uiState: StateFlow<SchedulePlannerState> = _uiState.asStateFlow()
    init { refresh() }
    fun refresh() = action { token -> reload(token) }
    private suspend fun reload(token: String) {
        _uiState.update { it.copy(loading = true, error = null) }
        val configuration = repository.configuration(token)
        _uiState.update { it.copy(templates = repository.listTemplates(token), configuration = configuration, loading = false) }
    }
    fun newTemplate() {
        _uiState.update { it.copy(editor = ScheduleTemplateEditor(), error = null) }
    }
    fun editTemplate(template: ScheduleTemplateSummary) = action { token ->
        _uiState.update { it.copy(loading = true, error = null) }
        val preview = repository.preview(token, template.id, LocalDate.of(2026, 8, 2))
        val assignments = preview.shifts.groupBy { it.date.toEpochDay() - LocalDate.of(2026, 8, 2).toEpochDay() + 1 }.flatMap { (day, shifts) -> shifts.map { day.toInt() to it.type to it.persons.map { person -> person.id } } }.toMap()
        _uiState.update {
            it.copy(
                loading = false,
                editor = ScheduleTemplateEditor(
                    id = template.id,
                    name = template.name,
                    cycleDays = template.cycleDays,
                    participantIds = template.participantIds.toSet(),
                    assignments = assignments,
                    effectiveFrom = template.effectiveFrom ?: LocalDate.now(),
                ),
            )
        }
    }
    fun updateEditor(transform: (ScheduleTemplateEditor) -> ScheduleTemplateEditor) { _uiState.update { state -> state.copy(editor = state.editor?.let(transform), error = null) } }
    fun dismissEditor() { _uiState.update { it.copy(editor = null, error = null) } }
    fun saveTemplate() {
        val editor = _uiState.value.editor ?: return
        action { token ->
            _uiState.update { it.copy(saving = true, error = null) }
            if (editor.id == null) repository.createTemplate(token, editor.command()) else repository.updateTemplate(token, editor.id, editor.command())
            _uiState.update { it.copy(saving = false, editor = null) }
            refresh()
        }
    }
    fun applyTemplate(templateId: Long, date: LocalDate) = action { token ->
        _uiState.update { it.copy(loading = true, error = null) }
        val application = repository.apply(token, templateId, date)
        _uiState.update {
            it.copy(
                applicationSuccessMessage = "模板已从${application.effectiveFrom.format(APPLICATION_DATE_FORMAT)}开始生效",
            )
        }
        reload(token)
    }
    fun consumeApplicationSuccessMessage() { _uiState.update { it.copy(applicationSuccessMessage = null) } }
    fun deleteTemplate(templateId: Long) = action { token -> repository.deleteTemplate(token, templateId); refresh() }
    fun updateConfiguration(cycleDays: Int, participantIds: Set<Long>) = action { token ->
        _uiState.update { it.copy(saving = true, error = null) }
        val configuration = repository.updateConfiguration(token, SchedulePlanningConfigurationCommand(cycleDays, participantIds.sorted()))
        _uiState.update { it.copy(configuration = configuration, saving = false) }
    }
    private fun action(block: suspend (String) -> Unit) = viewModelScope.launch {
        val session = sessionProvider.session.first()
        if (session == null || session.username != "admin" || session.role != "ADMIN") { _uiState.update { it.copy(loading = false, error = "仅admin账号可以管理排班") }; return@launch }
        runCatching { block(session.accessToken) }.onFailure { error -> _uiState.update { it.copy(loading = false, saving = false, error = error.message ?: "排班操作失败") } }
    }

    private companion object {
        val APPLICATION_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
    }
}
