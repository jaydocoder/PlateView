package com.jaydocoder.plateview

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.jaydocoder.plateview.domain.schedule.ScheduleMonth
import com.jaydocoder.plateview.domain.schedule.ScheduleMonthDay
import com.jaydocoder.plateview.domain.schedule.ScheduleParticipant
import com.jaydocoder.plateview.domain.schedule.SchedulePerson
import com.jaydocoder.plateview.domain.schedule.SchedulePlanningConfiguration
import com.jaydocoder.plateview.domain.schedule.ScheduleShift
import com.jaydocoder.plateview.domain.schedule.ScheduleShiftType
import com.jaydocoder.plateview.domain.schedule.ScheduleTemplateSummary
import com.jaydocoder.plateview.domain.schedule.ScheduleWeek
import com.jaydocoder.plateview.feature.schedule.SchedulePlannerScreen
import com.jaydocoder.plateview.feature.schedule.SchedulePlannerState
import com.jaydocoder.plateview.feature.schedule.ScheduleScreen
import com.jaydocoder.plateview.feature.schedule.ScheduleTemplateEditor
import com.jaydocoder.plateview.feature.schedule.ScheduleUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScheduleScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 周表显示七天且小夜班无需额外滚动即可查看() {
        val weekStart = LocalDate.of(2026, 8, 31)
        val people = listOf(SchedulePerson(3, "schedule-3", "刘添"), SchedulePerson(4, "schedule-4", "许志川"), SchedulePerson(5, "schedule-5", "穆拉迪力"))
        composeRule.setContent {
            PlateViewTheme {
                ScheduleScreen(
                    state = ScheduleUiState(week = ScheduleWeek(weekStart, 6, listOf(ScheduleShift(weekStart, ScheduleShiftType.SMALL_NIGHT, people))), loading = false, currentUserId = 3),
                    onPreviousWeek = {}, onNextWeek = {}, onToday = {}, onWeekNumber = {},
                )
            }
        }

        composeRule.onNodeWithTag("schedule_week_grid").assertIsDisplayed()
        composeRule.onNodeWithText("第6周 · 2026年8月31日-9月6日").assertIsDisplayed()
        listOf("31日", "1日", "2日", "3日", "4日", "5日", "6日").forEach { day -> composeRule.onAllNodesWithText(day).assertCountEquals(1) }
        composeRule.onNodeWithTag("schedule_shift_SMALL_NIGHT_2026-08-31").assertIsDisplayed()
        listOf("刘添", "许志川", "穆拉迪力").forEach { name -> composeRule.onNodeWithText(name).assertIsDisplayed() }
        composeRule.onAllNodesWithText("20:00").assertCountEquals(1)
    }

    @Test
    fun 今日日期使用强调色且班次名只显示在时间轴() {
        val weekStart = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        val person = SchedulePerson(3, "schedule-3", "刘添")
        composeRule.setContent {
            PlateViewTheme {
                ScheduleScreen(
                    state = ScheduleUiState(
                        week = ScheduleWeek(
                            weekStart,
                            1,
                            ScheduleShiftType.entries.map { type -> ScheduleShift(weekStart, type, listOf(person)) },
                        ),
                        loading = false,
                        currentUserId = 3,
                    ),
                    onPreviousWeek = {}, onNextWeek = {}, onToday = {}, onWeekNumber = {},
                )
            }
        }

        composeRule.onNodeWithTag("schedule_today_header").assertIsDisplayed()
        listOf("早班", "晚班", "小夜", "大夜").forEach { label -> composeRule.onAllNodesWithText(label).assertCountEquals(1) }
        ScheduleShiftType.entries.forEach { type -> composeRule.onNodeWithTag("schedule_time_${type.name}").assertIsDisplayed() }
    }

    @Test
    fun 周数选择器使用可滚动的圆角列表() {
        composeRule.setContent {
            PlateViewTheme {
                ScheduleScreen(
                    state = ScheduleUiState(week = ScheduleWeek(LocalDate.of(2026, 8, 31), 6, emptyList()), loading = false),
                    onPreviousWeek = {}, onNextWeek = {}, onToday = {}, onWeekNumber = {},
                )
            }
        }

        composeRule.onNodeWithTag("schedule_week_selector").performClick()
        composeRule.onNodeWithTag("schedule_week_wheel").assertIsDisplayed()
        composeRule.onAllNodesWithText("第6周").assertCountEquals(2)
    }

    @Test
    fun 默认仅显示我的班次并可切换为全部排班() {
        val weekStart = LocalDate.of(2026, 8, 31)
        val mine = SchedulePerson(3, "schedule-3", "刘添")
        val coworker = SchedulePerson(4, "schedule-4", "许志川")
        composeRule.setContent {
            PlateViewTheme {
                ScheduleScreen(
                    state = ScheduleUiState(week = ScheduleWeek(weekStart, 6, listOf(ScheduleShift(weekStart, ScheduleShiftType.MORNING, listOf(mine, coworker)), ScheduleShift(weekStart, ScheduleShiftType.AFTERNOON, listOf(coworker)))), loading = false, currentUserId = 3),
                    onPreviousWeek = {}, onNextWeek = {}, onToday = {}, onWeekNumber = {},
                )
            }
        }

        composeRule.onNodeWithTag("schedule_display_mine").assertIsSelected()
        composeRule.onNodeWithText("刘添").assertIsDisplayed()
        composeRule.onNodeWithText("许志川").assertIsDisplayed()
        composeRule.onAllNodesWithText("许志川").assertCountEquals(1)
        composeRule.onNodeWithTag("schedule_display_all").performClick()
        composeRule.onNodeWithTag("schedule_display_all").assertIsSelected()
        composeRule.onAllNodesWithText("许志川").assertCountEquals(2)
    }

    @Test
    fun 模板列表显示状态并提供编辑和日期选择操作() {
        val template = ScheduleTemplateSummary(1, "demo01", 1, 1, 9, listOf(3), null, "INACTIVE")
        composeRule.setContent {
            PlateViewTheme {
                SchedulePlannerScreen(SchedulePlannerState(templates = listOf(template), loading = false), {}, {}, {}, {}, {}, {}, { _, _ -> }, {})
            }
        }

        composeRule.onNodeWithText("模板").assertIsDisplayed()
        composeRule.onNodeWithText("停用").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("编辑模板").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("选择应用日期").assertIsDisplayed()
    }

    @Test
    fun 模板应用成功后显示实际生效日期提示() {
        composeRule.setContent {
            PlateViewTheme {
                SchedulePlannerScreen(
                    state = SchedulePlannerState(loading = false, applicationSuccessMessage = "模板已从2026年9月1日开始生效"),
                    onNavigateUp = {}, onNew = {}, onEdit = {}, onChanged = {}, onSave = {}, onDismiss = {}, onApply = { _, _ -> }, onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("模板已从2026年9月1日开始生效").assertIsDisplayed()
    }

    @Test
    fun 模板内可按真实姓名搜索成员并打开循环天数滚轮() {
        val liu = ScheduleParticipant(3, "schedule-3", "刘添", "ACTIVE")
        val xu = ScheduleParticipant(4, "schedule-4", "许志川", "ACTIVE")
        var latestEditor: ScheduleTemplateEditor? = null
        composeRule.setContent {
            PlateViewTheme {
                SchedulePlannerScreen(
                    state = SchedulePlannerState(configuration = SchedulePlanningConfiguration(9, emptyList(), listOf(liu, xu)), editor = ScheduleTemplateEditor(), loading = false),
                    onNavigateUp = {}, onNew = {}, onEdit = {},
                    onChanged = { transform -> latestEditor = transform(ScheduleTemplateEditor()) },
                    onSave = {}, onDismiss = {}, onApply = { _, _ -> }, onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("schedule_member_search").performTextInput("许志")
        composeRule.onNodeWithText("许志川").performClick()
        composeRule.onNodeWithText("选择").performClick()
        composeRule.onNodeWithTag("schedule_cycle_day_wheel").assertIsDisplayed()
        assertEquals(setOf(4L), latestEditor?.participantIds)
    }

    @Test
    fun 月历直接显示班休并提供年份月份滚轮() {
        val month = YearMonth.of(2026, 8)
        composeRule.setContent {
            PlateViewTheme {
                ScheduleScreen(
                    state = ScheduleUiState(
                        week = ScheduleWeek(month.atDay(1), 1, emptyList()),
                        month = ScheduleMonth(month, listOf(ScheduleMonthDay(month.atDay(1), true), ScheduleMonthDay(month.atDay(2), false))),
                        selectedMonth = month,
                        loading = false,
                    ),
                    onPreviousWeek = {}, onNextWeek = {}, onToday = {}, onWeekNumber = {}, onMonthSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("按月查看排班").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("按周选择排班").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_month_selector").performClick()
        composeRule.onNodeWithTag("schedule_month_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_month_year_wheel").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_month_month_wheel").assertIsDisplayed()
        composeRule.onNodeWithText("班").assertIsDisplayed()
        composeRule.onNodeWithText("休").assertIsDisplayed()
    }

    @Test
    fun 月历加载态保留在同一弹层内() {
        val month = YearMonth.of(2026, 8)
        composeRule.setContent {
            PlateViewTheme {
                ScheduleScreen(
                    state = ScheduleUiState(
                        week = ScheduleWeek(month.atDay(1), 1, emptyList()),
                        selectedMonth = month,
                        monthLoading = true,
                        loading = false,
                    ),
                    onPreviousWeek = {}, onNextWeek = {}, onToday = {}, onWeekNumber = {}, onMonthSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag("schedule_month_selector").performClick()
        composeRule.onNodeWithText("月历").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_month_loading").assertIsDisplayed()
        composeRule.onAllNodesWithText("正在读取月历").assertCountEquals(0)
    }
}
