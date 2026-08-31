package com.jaydocoder.plateview.domain.schedule

import java.time.LocalDate
import java.time.YearMonth

enum class ScheduleShiftType(val label: String, val timeLabel: String) {
    MORNING("早班", "08:00-14:00"),
    AFTERNOON("晚班", "14:00-20:00"),
    SMALL_NIGHT("小夜班", "20:00-22:00"),
    NIGHT("大夜班", "20:00-次日08:00"),
}

data class SchedulePerson(val id: Long, val username: String, val realName: String)
data class ScheduleParticipant(val id: Long, val username: String, val realName: String, val status: String)
data class SchedulePlanningConfiguration(val cycleDays: Int, val participants: List<ScheduleParticipant>, val candidates: List<ScheduleParticipant>)
data class ScheduleShift(val date: LocalDate, val type: ScheduleShiftType, val persons: List<SchedulePerson>)
data class ScheduleWeek(val weekStart: LocalDate, val weekNumber: Int, val shifts: List<ScheduleShift>)
data class ScheduleMonthDay(val date: LocalDate, val hasShift: Boolean)
data class ScheduleMonth(val month: YearMonth, val days: List<ScheduleMonthDay>)
data class ScheduleTemplateSummary(
    val id: Long,
    val name: String,
    val versionId: Long,
    val versionNumber: Int,
    val cycleDays: Int,
    val participantIds: List<Long>,
    val effectiveFrom: LocalDate?,
    val status: String,
)
data class SchedulePlanningConfigurationCommand(val cycleDays: Int, val participantIds: List<Long>)
data class ScheduleTemplateCommand(
    val name: String,
    val cycleDays: Int,
    val participantIds: List<Long>,
    val assignments: List<ScheduleAssignmentCommand>,
)
data class ScheduleAssignmentCommand(val cycleDay: Int, val type: ScheduleShiftType, val accountIds: List<Long>)
data class ScheduleApplication(val id: Long, val templateId: Long, val versionNumber: Int, val effectiveFrom: LocalDate)
