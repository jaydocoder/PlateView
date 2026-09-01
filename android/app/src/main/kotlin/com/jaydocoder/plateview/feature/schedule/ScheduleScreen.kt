package com.jaydocoder.plateview.feature.schedule

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.domain.schedule.ScheduleShift
import com.jaydocoder.plateview.domain.schedule.ScheduleShiftType
import com.jaydocoder.plateview.domain.schedule.ScheduleMonth
import com.jaydocoder.plateview.domain.schedule.ScheduleWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val WEEK_PICKER_LIMIT = 5200
private const val MONTH_PICKER_YEAR_MIN = 2020
private const val MONTH_PICKER_YEAR_MAX = 2100
private val scheduleTimeColumnWidth = 38.dp
private val schedulePersonNameColors = listOf(
    Color(0xFFFFE7A8),
    Color(0xFFC9F2FF),
    Color(0xFFFFD9EC),
    Color(0xFFD8F8D4),
    Color(0xFFE9D7FF),
    Color(0xFFFFDEC9),
    Color(0xFFD5F4E9),
    Color(0xFFFFE0A8),
    Color(0xFFDCE5FF),
    Color(0xFFFFD6D1),
    Color(0xFFD7F1FF),
    Color(0xFFE8F1C4),
)

@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleScreen(state, viewModel::previousWeek, viewModel::nextWeek, viewModel::today, viewModel::selectWeekNumber, viewModel::selectMonth)
}

@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
    onWeekNumber: (Int) -> Unit,
    onMonthSelected: (YearMonth) -> Unit = {},
) {
    var selectorOpen by remember { mutableStateOf(false) }
    var monthCalendarOpen by rememberSaveable { mutableStateOf(false) }
    var showAllSchedules by rememberSaveable { mutableStateOf(false) }
    val selectedMonth = state.selectedMonth ?: state.month?.month ?: YearMonth.from(state.week?.weekStart ?: LocalDate.now())
    val displayedWeek = remember(state.week, state.currentUserId, showAllSchedules) {
        state.week?.let { week ->
            if (showAllSchedules) {
                week
            } else {
                week.copy(shifts = week.shifts.filter { shift ->
                    shift.persons.any { it.id == state.currentUserId }
                })
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScheduleHeader(
            week = state.week,
            showAllSchedules = showAllSchedules,
            onShowAllSchedulesChanged = { showAllSchedules = it },
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onToday = onToday,
            onOpenMonth = {
                monthCalendarOpen = true
                onMonthSelected(selectedMonth)
            },
        ) { selectorOpen = true }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            displayedWeek != null -> ScheduleWeekGrid(displayedWeek)
        }
    }
    if (selectorOpen) {
        WeekPickerDialog(
            currentWeek = state.week?.weekNumber ?: 1,
            onDismiss = { selectorOpen = false },
            onConfirm = { weekNumber ->
                onWeekNumber(weekNumber)
                selectorOpen = false
            },
        )
    }
    if (monthCalendarOpen) {
        MonthCalendarDialog(
            selectedMonth = selectedMonth,
            month = state.month?.takeIf { it.month == selectedMonth },
            loading = state.monthLoading,
            error = state.monthError,
            onMonthSelected = onMonthSelected,
            onDismiss = { monthCalendarOpen = false },
        )
    }
}

@Composable
private fun ScheduleHeader(
    week: ScheduleWeek?,
    showAllSchedules: Boolean,
    onShowAllSchedulesChanged: (Boolean) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
    onOpenMonth: () -> Unit,
    onOpenSelector: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("排班", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.width(112.dp).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScheduleDisplaySegment(
                            label = "我的",
                            selected = !showAllSchedules,
                            onClick = { onShowAllSchedulesChanged(false) },
                            modifier = Modifier.weight(1f).testTag("schedule_display_mine"),
                        )
                        ScheduleDisplaySegment(
                            label = "全部",
                            selected = showAllSchedules,
                            onClick = { onShowAllSchedulesChanged(true) },
                            modifier = Modifier.weight(1f).testTag("schedule_display_all"),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToday) { Icon(Icons.Outlined.Today, "回到本周") }
                IconButton(onClick = onOpenMonth, modifier = Modifier.testTag("schedule_month_selector")) { Icon(Icons.Outlined.DateRange, "按月查看排班") }
                IconButton(onClick = onOpenSelector, modifier = Modifier.testTag("schedule_week_selector")) { Icon(Icons.Outlined.ViewWeek, "按周选择排班") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onPreviousWeek, modifier = Modifier.testTag("schedule_previous_week")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "上一周") }
                Text(
                    week?.let { "第${it.weekNumber}周 · ${it.weekRangeLabel()}" } ?: "正在读取排班",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                IconButton(onClick = onNextWeek, modifier = Modifier.testTag("schedule_next_week")) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "下一周") }
            }
        }
    }
}

@Composable
private fun ScheduleDisplaySegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun MonthCalendarDialog(
    selectedMonth: YearMonth,
    month: ScheduleMonth?,
    loading: Boolean,
    error: String?,
    onMonthSelected: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(24.dp),
    title = { Text("月历") },
    text = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MonthYearWheelPicker(selectedMonth, onMonthSelected)
            when {
                loading -> Box(
                    Modifier.fillMaxWidth().height(276.dp).testTag("schedule_month_loading"),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 24.dp))
                month != null -> MonthGrid(month)
                else -> Box(
                    Modifier.fillMaxWidth().height(276.dp).testTag("schedule_month_loading"),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
)

@Composable
private fun MonthYearWheelPicker(selectedMonth: YearMonth, onMonthSelected: (YearMonth) -> Unit) {
    val years = remember { (MONTH_PICKER_YEAR_MIN..MONTH_PICKER_YEAR_MAX).toList() }
    val months = remember { (1..12).toList() }
    var selectedYear by remember(selectedMonth) { mutableIntStateOf(selectedMonth.year) }
    var selectedMonthValue by remember(selectedMonth) { mutableIntStateOf(selectedMonth.monthValue) }
    val yearState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedYear - MONTH_PICKER_YEAR_MIN).coerceIn(0, years.lastIndex))
    val monthState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedMonthValue - 1).coerceIn(0, months.lastIndex))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelPicker(
            values = years,
            selectedValue = selectedYear,
            label = { "${it}年" },
            state = yearState,
            modifier = Modifier.testTag("schedule_month_year_wheel"),
        ) { year ->
            selectedYear = year
            onMonthSelected(YearMonth.of(year, selectedMonthValue))
        }
        WheelPicker(
            values = months,
            selectedValue = selectedMonthValue,
            label = { "${it}月" },
            state = monthState,
            modifier = Modifier.testTag("schedule_month_month_wheel"),
        ) { month ->
            selectedMonthValue = month
            onMonthSelected(YearMonth.of(selectedYear, month))
        }
    }
}

@Composable
private fun WheelPicker(
    values: List<Int>,
    selectedValue: Int,
    label: (Int) -> String,
    state: LazyListState,
    modifier: Modifier,
    onSelected: (Int) -> Unit,
) {
    val selectedIndex = values.indexOf(selectedValue).coerceAtLeast(0)
    val view = LocalView.current
    var lastSelectedIndex by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        lastSelectedIndex = selectedIndex
        state.animateScrollToItem(selectedIndex)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val selectedIndex = state.closestCenteredItemIndex() ?: return@collect
                if (selectedIndex != lastSelectedIndex) {
                    lastSelectedIndex = selectedIndex
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSelected(values[selectedIndex])
                }
            }
        }
    }
    LazyColumn(
        state = state,
        modifier = modifier
            .width(96.dp)
            .height(144.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 48.dp),
    ) {
        items(count = values.size, key = { values[it] }) { index ->
            val value = values[index]
            val selected = value == selectedValue
            val cylinderProgress = state.cylinderProgress(index)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .graphicsLayer {
                        val distance = kotlin.math.abs(cylinderProgress)
                        alpha = (1f - distance * 0.48f).coerceIn(0.42f, 1f)
                        scaleX = 1f - distance * 0.08f
                        rotationX = -cylinderProgress * 10f
                        cameraDistance = 8f * density
                    }
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(value),
                    style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun LazyListState.closestCenteredItemIndex(): Int? {
    val layoutInfo = layoutInfo
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    return layoutInfo.visibleItemsInfo.minByOrNull { item ->
        kotlin.math.abs(item.offset + item.size / 2 - viewportCenter)
    }?.index
}

private fun LazyListState.cylinderProgress(index: Int): Float {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    return ((info.offset + info.size / 2f - center) / info.size).coerceIn(-1.25f, 1.25f)
}

@Composable
private fun MonthGrid(month: ScheduleMonth) {
    val firstOffset = month.month.atDay(1).dayOfWeek.value - 1
    val cells = List(firstOffset) { null } + month.days + List((7 - (firstOffset + month.days.size) % 7) % 7) { null }
    Column(Modifier.fillMaxWidth().testTag("schedule_month_grid"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                Text(weekday, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        cells.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { day -> MonthDayCell(day, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MonthDayCell(day: com.jaydocoder.plateview.domain.schedule.ScheduleMonthDay?, modifier: Modifier) = Box(
    modifier
        .height(42.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)),
    contentAlignment = Alignment.Center,
) {
    if (day != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
            Text(
                text = if (day.hasShift) "班" else "休",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (day.hasShift) Color(0xFFC73B3B) else Color(0xFF2A8C5A),
            )
        }
    }
}

@Composable
private fun WeekPickerDialog(currentWeek: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var selectedWeek by remember(currentWeek) { mutableIntStateOf(currentWeek.coerceIn(1, WEEK_PICKER_LIMIT)) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedWeek - 1).coerceIn(0, WEEK_PICKER_LIMIT - 1))
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("选择排班周") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("第${selectedWeek}周", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                ThreeWeekWheel(
                    current = selectedWeek,
                    state = listState,
                    modifier = Modifier.fillMaxWidth().testTag("schedule_week_wheel"),
                    onSelected = { selectedWeek = it },
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(selectedWeek) }, shape = RoundedCornerShape(18.dp)) { Text("查看") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ThreeWeekWheel(
    current: Int,
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier,
    onSelected: (Int) -> Unit,
) {
    val values = remember { (1..WEEK_PICKER_LIMIT).toList() }
    val selectedIndex = (current - 1).coerceIn(0, values.lastIndex)
    val view = LocalView.current
    LaunchedEffect(selectedIndex) { state.animateScrollToItem(selectedIndex) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val info = state.layoutInfo
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                val index = info.visibleItemsInfo.minByOrNull { item -> kotlin.math.abs(item.offset + item.size / 2 - center) }?.index ?: return@collect
                val week = (index + 1).coerceIn(1, WEEK_PICKER_LIMIT)
                if (week != current) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSelected(week)
                }
            }
        }
    }
    LazyColumn(
        state = state,
        modifier = modifier
            .height(144.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 48.dp),
    ) {
        items(count = WEEK_PICKER_LIMIT, key = { it }) { index ->
            val week = index + 1
            val selected = week == current
            val cylinderProgress = state.cylinderProgress(index)
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .graphicsLayer {
                        val distance = kotlin.math.abs(cylinderProgress)
                        alpha = (1f - distance * 0.48f).coerceIn(0.42f, 1f)
                        scaleX = 1f - distance * 0.08f
                        rotationX = -cylinderProgress * 10f
                        cameraDistance = 8f * density
                    }
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "第${week}周",
                    style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScheduleWeekGrid(week: ScheduleWeek) {
    val dates = (0L..6L).map(week.weekStart::plusDays)
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        val dayWidth = (maxWidth - scheduleTimeColumnWidth) / 7
        Column(modifier = Modifier.fillMaxSize().testTag("schedule_week_grid")) {
            Row(modifier = Modifier.fillMaxWidth().height(scheduleGridHeaderHeight)) {
                MonthHeader(week.weekStart)
                dates.forEach { date ->
                    DateHeader(date, dayWidth, isToday = date == LocalDate.now())
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val shiftAreaHeight = maxHeight.coerceAtLeast(scheduleMinimumShiftRowHeight * 4)
                val shiftRowHeight = (shiftAreaHeight / 4) * scheduleShiftRowHeightMultiplier
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    ScheduleShiftType.entries.forEach { type ->
                        Row(modifier = Modifier.fillMaxWidth().height(shiftRowHeight)) {
                            TimeCell(type)
                            dates.forEach { date ->
                                ShiftCell(
                                    type = type,
                                    shift = week.shifts.firstOrNull { it.date == date && it.type == type },
                                    width = dayWidth,
                                    modifier = Modifier.testTag("schedule_shift_${type.name}_${date}"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(date: LocalDate) = Box(
    Modifier
        .width(scheduleTimeColumnWidth)
        .fillMaxHeight()
        .padding(2.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)),
    contentAlignment = Alignment.Center,
) {
    Text("${date.monthValue}月", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
}

@Composable
private fun DateHeader(date: LocalDate, width: Dp, isToday: Boolean) = Box(
    Modifier
        .width(width)
        .fillMaxHeight()
        .padding(2.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(
            if (isToday) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        )
        .then(
            if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            else Modifier,
        )
        .testTag(if (isToday) "schedule_today_header" else "schedule_date_header_${date}"),
    contentAlignment = Alignment.Center,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${date.dayOfMonth}日",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            date.weekdayLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeCell(type: ScheduleShiftType) = Box(
    Modifier
        .width(scheduleTimeColumnWidth)
        .fillMaxHeight()
        .padding(2.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f))
        .testTag("schedule_time_${type.name}"),
    contentAlignment = Alignment.Center,
) {
    val time = if (type == ScheduleShiftType.NIGHT) "" else type.timeLabel.substringBefore('-')
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (time.isNotBlank()) {
            Text(
                time,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Text(
            type.axisLabel(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShiftCell(type: ScheduleShiftType, shift: ScheduleShift?, width: Dp, modifier: Modifier = Modifier) = Box(
    modifier
        .width(width)
        .fillMaxHeight()
        .padding(2.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(type.color().copy(alpha = if (shift == null) 0.10f else 0.88f))
        .padding(horizontal = 1.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center,
) {
    if (shift != null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            shift.persons.forEach { person ->
                Text(
                    text = person.realName,
                    modifier = Modifier.testTag("schedule_person_${person.id}"),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = schedulePersonNameColor(person.id),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

internal fun schedulePersonNameColor(personId: Long): Color = schedulePersonNameColors[(personId % schedulePersonNameColors.size).toInt()]

private fun ScheduleShiftType.axisLabel() = when (this) {
    ScheduleShiftType.MORNING -> "早班"
    ScheduleShiftType.AFTERNOON -> "晚班"
    ScheduleShiftType.SMALL_NIGHT -> "小夜"
    ScheduleShiftType.NIGHT -> "大夜"
}

private fun ScheduleShiftType.color() = when (this) {
    ScheduleShiftType.MORNING -> Color(0xFF2C9A7B)
    ScheduleShiftType.AFTERNOON -> Color(0xFF2779C9)
    ScheduleShiftType.SMALL_NIGHT -> Color(0xFFD28D2E)
    ScheduleShiftType.NIGHT -> Color(0xFF755B9C)
}

private fun LocalDate.weekdayLabel() = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

private const val scheduleShiftRowHeightMultiplier = 1f
private val scheduleGridHeaderHeight = 52.dp
private val scheduleMinimumShiftRowHeight = 76.dp
private val dateFormatter = DateTimeFormatter.ofPattern("M月d日")
private val dateFormatterWithYear = DateTimeFormatter.ofPattern("yyyy年M月d日")

private fun ScheduleWeek.weekRangeLabel(): String {
    val weekEnd = weekStart.plusDays(6)
    return if (weekStart.year == weekEnd.year) {
        "${weekStart.format(dateFormatterWithYear)}-${weekEnd.format(dateFormatter)}"
    } else {
        "${weekStart.format(dateFormatterWithYear)}-${weekEnd.format(dateFormatterWithYear)}"
    }
}
