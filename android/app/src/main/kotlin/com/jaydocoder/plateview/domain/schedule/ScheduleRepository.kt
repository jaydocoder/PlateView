package com.jaydocoder.plateview.domain.schedule

import java.time.LocalDate
import java.time.YearMonth

interface ScheduleRepository {
    suspend fun getWeek(accessToken: String, date: LocalDate): ScheduleWeek
    suspend fun getMonth(accessToken: String, month: YearMonth): ScheduleMonth
    suspend fun configuration(accessToken: String): SchedulePlanningConfiguration
    suspend fun updateConfiguration(accessToken: String, command: SchedulePlanningConfigurationCommand): SchedulePlanningConfiguration
    suspend fun listTemplates(accessToken: String): List<ScheduleTemplateSummary>
    suspend fun createTemplate(accessToken: String, command: ScheduleTemplateCommand): ScheduleTemplateSummary
    suspend fun updateTemplate(accessToken: String, templateId: Long, command: ScheduleTemplateCommand): ScheduleTemplateSummary
    suspend fun deleteTemplate(accessToken: String, templateId: Long)
    suspend fun preview(accessToken: String, templateId: Long, effectiveFrom: LocalDate): ScheduleWeek
    suspend fun apply(accessToken: String, templateId: Long, effectiveFrom: LocalDate): ScheduleApplication
}
