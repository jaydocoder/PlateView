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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulePlannerViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `应用模板后显示服务端确认的生效日期`() = runTest {
        val repository = ApplyingScheduleRepository()
        val viewModel = SchedulePlannerViewModel(repository, AdminSessionProvider())
        advanceUntilIdle()

        viewModel.applyTemplate(1, LocalDate.of(2026, 9, 1))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 1), repository.appliedDate)
        assertEquals("模板已从2026年9月1日开始生效", viewModel.uiState.value.applicationSuccessMessage)
    }
}

private class AdminSessionProvider : AuthSessionProvider {
    override val session: Flow<AuthSession?> = flowOf(AuthSession("token", "refresh", "admin", "ADMIN", 1, 0, true))
    override suspend fun logout() = Unit
}

private class ApplyingScheduleRepository : ScheduleRepository {
    var appliedDate: LocalDate? = null

    override suspend fun getWeek(accessToken: String, date: LocalDate): ScheduleWeek = ScheduleWeek(date, 1, emptyList())
    override suspend fun getMonth(accessToken: String, month: YearMonth): ScheduleMonth = ScheduleMonth(month, emptyList())
    override suspend fun configuration(accessToken: String) = SchedulePlanningConfiguration(9, emptyList(), emptyList())
    override suspend fun updateConfiguration(accessToken: String, command: SchedulePlanningConfigurationCommand) = SchedulePlanningConfiguration(command.cycleDays, emptyList(), emptyList())
    override suspend fun listTemplates(accessToken: String): List<ScheduleTemplateSummary> = emptyList()
    override suspend fun createTemplate(accessToken: String, command: ScheduleTemplateCommand): ScheduleTemplateSummary = error("测试不调用")
    override suspend fun updateTemplate(accessToken: String, templateId: Long, command: ScheduleTemplateCommand): ScheduleTemplateSummary = error("测试不调用")
    override suspend fun deleteTemplate(accessToken: String, templateId: Long) = Unit
    override suspend fun preview(accessToken: String, templateId: Long, effectiveFrom: LocalDate): ScheduleWeek = error("测试不调用")
    override suspend fun apply(accessToken: String, templateId: Long, effectiveFrom: LocalDate): ScheduleApplication {
        appliedDate = effectiveFrom
        return ScheduleApplication(1, templateId, 1, effectiveFrom)
    }
}
