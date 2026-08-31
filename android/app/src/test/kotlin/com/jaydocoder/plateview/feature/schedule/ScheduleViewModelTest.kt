package com.jaydocoder.plateview.feature.schedule

import com.jaydocoder.plateview.domain.schedule.ScheduleApplication
import com.jaydocoder.plateview.domain.schedule.ScheduleMonth
import com.jaydocoder.plateview.domain.schedule.SchedulePlanningConfiguration
import com.jaydocoder.plateview.domain.schedule.SchedulePlanningConfigurationCommand
import com.jaydocoder.plateview.domain.schedule.ScheduleRepository
import com.jaydocoder.plateview.domain.schedule.ScheduleTemplateCommand
import com.jaydocoder.plateview.domain.schedule.ScheduleTemplateSummary
import com.jaydocoder.plateview.domain.schedule.ScheduleWeek
import com.jaydocoder.plateview.feature.auth.AuthSession
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import com.jaydocoder.plateview.feature.search.MainDispatcherRule
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `选择周数后按第一自然周计算请求日期`() = runTest {
        val repository = FakeScheduleRepository()
        val viewModel = ScheduleViewModel(repository, FakeSessionProvider())
        advanceUntilIdle()

        viewModel.selectWeekNumber(2)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 8, 3), repository.requestedDates.last())
        assertEquals(2, viewModel.uiState.value.week?.weekNumber)
    }

    @Test
    fun `选择月份后立即保存当前月份并请求对应月历`() = runTest {
        val repository = FakeScheduleRepository()
        val viewModel = ScheduleViewModel(repository, FakeSessionProvider())
        advanceUntilIdle()

        val month = YearMonth.of(2026, 9)
        viewModel.selectMonth(month)
        advanceUntilIdle()

        assertEquals(month, repository.requestedMonths.last())
        assertEquals(month, viewModel.uiState.value.selectedMonth)
    }
}

private class FakeSessionProvider : AuthSessionProvider {
    override val session: Flow<AuthSession?> = flowOf(AuthSession("token", "refresh", "operator", "USER", 3, 0, true))
    override suspend fun logout() = Unit
}

private class FakeScheduleRepository : ScheduleRepository {
    val requestedDates = mutableListOf<LocalDate>()
    val requestedMonths = mutableListOf<YearMonth>()
    override suspend fun getWeek(accessToken: String, date: LocalDate): ScheduleWeek { requestedDates += date; return ScheduleWeek(date, ((date.toEpochDay() - LocalDate.of(2026, 7, 27).toEpochDay()) / 7 + 1).toInt(), emptyList()) }
    override suspend fun getMonth(accessToken: String, month: YearMonth): ScheduleMonth { requestedMonths += month; return ScheduleMonth(month, emptyList()) }
    override suspend fun configuration(accessToken: String): SchedulePlanningConfiguration = SchedulePlanningConfiguration(9, emptyList(), emptyList())
    override suspend fun updateConfiguration(accessToken: String, command: SchedulePlanningConfigurationCommand): SchedulePlanningConfiguration = SchedulePlanningConfiguration(command.cycleDays, emptyList(), emptyList())
    override suspend fun listTemplates(accessToken: String): List<ScheduleTemplateSummary> = emptyList()
    override suspend fun createTemplate(accessToken: String, command: ScheduleTemplateCommand): ScheduleTemplateSummary = error("测试不调用")
    override suspend fun updateTemplate(accessToken: String, templateId: Long, command: ScheduleTemplateCommand): ScheduleTemplateSummary = error("测试不调用")
    override suspend fun deleteTemplate(accessToken: String, templateId: Long) = Unit
    override suspend fun preview(accessToken: String, templateId: Long, effectiveFrom: LocalDate): ScheduleWeek = error("测试不调用")
    override suspend fun apply(accessToken: String, templateId: Long, effectiveFrom: LocalDate): ScheduleApplication = error("测试不调用")
}
