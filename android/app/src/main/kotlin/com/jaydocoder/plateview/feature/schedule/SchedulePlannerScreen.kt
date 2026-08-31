@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.jaydocoder.plateview.feature.schedule

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.domain.schedule.ScheduleParticipant
import com.jaydocoder.plateview.domain.schedule.ScheduleShiftType
import com.jaydocoder.plateview.domain.schedule.ScheduleTemplateSummary
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePlannerRoute(onNavigateUp: () -> Unit, viewModel: SchedulePlannerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SchedulePlannerScreen(
        state = state,
        onNavigateUp = onNavigateUp,
        onNew = viewModel::newTemplate,
        onEdit = viewModel::editTemplate,
        onChanged = viewModel::updateEditor,
        onSave = viewModel::saveTemplate,
        onDismiss = viewModel::dismissEditor,
        onApply = viewModel::applyTemplate,
        onDelete = viewModel::deleteTemplate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePlannerScreen(
    state: SchedulePlannerState,
    onNavigateUp: () -> Unit,
    onNew: () -> Unit,
    onEdit: (ScheduleTemplateSummary) -> Unit,
    onChanged: ((ScheduleTemplateEditor) -> ScheduleTemplateEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onApply: (Long, LocalDate) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var applicationTarget by remember { mutableStateOf<ScheduleTemplateSummary?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editor == null) "排班规划" else "编辑模板") },
                navigationIcon = {
                    IconButton(onClick = if (state.editor == null) onNavigateUp else onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.editor == null) {
            TemplateList(state, onNew, onEdit, { applicationTarget = it }, onDelete, Modifier.padding(padding))
        } else {
            TemplateEditor(
                editor = state.editor,
                candidates = state.configuration?.candidates.orEmpty(),
                saving = state.saving,
                error = state.error,
                onChanged = onChanged,
                onSave = onSave,
                modifier = Modifier.padding(padding),
            )
        }
    }
    applicationTarget?.let { template ->
        ApplyTemplateDateDialog(
            onDismiss = { applicationTarget = null },
            onApply = { date -> onApply(template.id, date); applicationTarget = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyTemplateDateDialog(onDismiss: () -> Unit, onApply: (LocalDate) -> Unit) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.now().toEpochDay() * MILLIS_PER_DAY,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { pickerState.selectedDateMillis?.let { onApply(LocalDate.ofEpochDay(it / MILLIS_PER_DAY)) } },
                enabled = pickerState.selectedDateMillis != null,
                shape = RoundedCornerShape(14.dp),
            ) { Text("确认应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(20.dp),
    ) {
        DatePicker(
            state = pickerState,
            showModeToggle = false,
            title = { Text("选择应用日期", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
        )
    }
}

@Composable
private fun TemplateList(
    state: SchedulePlannerState,
    onNew: () -> Unit,
    onEdit: (ScheduleTemplateSummary) -> Unit,
    onRequestApply: (ScheduleTemplateSummary) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier,
) = LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(PlateViewDimensions.pageHorizontal, PlateViewDimensions.pageVertical),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("模板", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = onNew, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("新模板")
            }
        }
    }
    state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
    if (!state.loading && state.templates.isEmpty()) item { Text("暂无模板") }
    items(state.templates, key = { it.id }) { item ->
        ElevatedCard(
            shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    TemplateStatusBadge(item.status)
                }
                IconButton(onClick = { onEdit(item) }) { Icon(Icons.Outlined.Edit, "编辑模板") }
                IconButton(onClick = { onRequestApply(item) }) { Icon(Icons.Outlined.EditCalendar, "选择应用日期") }
                IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Outlined.DeleteOutline, "删除模板", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun TemplateStatusBadge(status: String) {
    val active = status == "ACTIVE"
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = if (active) "启用" else "停用",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TemplateEditor(
    editor: ScheduleTemplateEditor,
    candidates: List<ScheduleParticipant>,
    saving: Boolean,
    error: String?,
    onChanged: ((ScheduleTemplateEditor) -> ScheduleTemplateEditor) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier,
) {
    var memberQuery by remember(editor.id) { mutableStateOf("") }
    var cyclePickerOpen by remember { mutableStateOf(false) }
    val selectedPeople = candidates.filter { it.id in editor.participantIds }
    val matchingPeople = remember(candidates, memberQuery) {
        val keyword = memberQuery.trim()
        candidates.filter { keyword.isEmpty() || it.realName.contains(keyword, ignoreCase = true) }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = editor.name,
                onValueChange = { value -> onChanged { it.copy(name = value) } },
                label = { Text("模板名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("循环天数", fontWeight = FontWeight.SemiBold)
                        Text("${editor.cycleDays}天", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { cyclePickerOpen = true }) { Text("选择") }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("排班成员", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = memberQuery,
                        onValueChange = { memberQuery = it },
                        label = { Text("搜索真实姓名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("schedule_member_search"),
                        shape = RoundedCornerShape(14.dp),
                    )
                    if (selectedPeople.isNotEmpty()) {
                        Text("已选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            selectedPeople.forEach { person ->
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        onChanged { value ->
                                            val ids = value.participantIds - person.id
                                            value.copy(
                                                participantIds = ids,
                                                assignments = value.assignments.mapValues { (_, people) -> people - person.id },
                                            )
                                        }
                                    },
                                    label = { Text(person.realName) },
                                )
                            }
                        }
                    }
                    Text("按真实姓名添加", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        matchingPeople.filter { it.id !in editor.participantIds }.forEach { person ->
                            FilterChip(
                                selected = false,
                                onClick = { onChanged { value -> value.copy(participantIds = value.participantIds + person.id) } },
                                label = { Text(person.realName) },
                            )
                        }
                    }
                    if (matchingPeople.isEmpty()) Text("没有匹配的真实姓名", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..editor.cycleDays).forEach { day ->
                    FilterChip(
                        selected = editor.selectedDay == day,
                        onClick = { onChanged { it.copy(selectedDay = day) } },
                        label = { Text("第${day}天") },
                    )
                }
            }
        }
        ScheduleShiftType.entries.forEach { type ->
            item { ShiftPeopleEditor(editor, type, selectedPeople, onChanged) }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(
                onClick = onSave,
                enabled = !saving && editor.participantIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (saving) "正在保存" else "保存模板") }
        }
    }
    if (cyclePickerOpen) {
        CycleDayPickerDialog(
            current = editor.cycleDays,
            onDismiss = { cyclePickerOpen = false },
            onConfirm = { days ->
                onChanged { value ->
                    value.copy(
                        cycleDays = days,
                        selectedDay = value.selectedDay.coerceAtMost(days),
                        assignments = value.assignments.filterKeys { (day, _) -> day <= days },
                    )
                }
                cyclePickerOpen = false
            },
        )
    }
}

@Composable
private fun CycleDayPickerDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var selected by remember(current) { mutableIntStateOf(current) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceIn(0, 14))
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("选择循环天数") },
        text = {
            ThreeItemWheel(
                values = (1..15).toList(),
                selected = selected,
                state = listState,
                label = { "${it}天" },
                modifier = Modifier.fillMaxWidth().testTag("schedule_cycle_day_wheel"),
                onSelected = { selected = it },
            )
        },
        confirmButton = { Button(onClick = { onConfirm(selected) }, shape = RoundedCornerShape(14.dp)) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ThreeItemWheel(
    values: List<Int>,
    selected: Int,
    state: androidx.compose.foundation.lazy.LazyListState,
    label: (Int) -> String,
    modifier: Modifier,
    onSelected: (Int) -> Unit,
) {
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val view = LocalView.current
    LaunchedEffect(selectedIndex) {
        state.animateScrollToItem(selectedIndex)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val index = state.closestCenteredItemIndex(values.size) ?: return@collect
                val value = values[index]
                if (value != selected) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSelected(value)
                }
            }
        }
    }
    LazyColumn(
        state = state,
        modifier = modifier
            .height(144.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 48.dp),
    ) {
        items(values.size, key = { values[it] }) { index ->
            val value = values[index]
            val isSelected = value == selected
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
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(value),
                    style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListState.closestCenteredItemIndex(itemCount: Int): Int? {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo
        .minByOrNull { item -> kotlin.math.abs(item.offset + item.size / 2 - center) }
        ?.index
        ?.takeIf { it in 0 until itemCount }
}

private fun androidx.compose.foundation.lazy.LazyListState.cylinderProgress(index: Int): Float {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    return ((info.offset + info.size / 2f - center) / info.size).coerceIn(-1.25f, 1.25f)
}

@Composable
private fun ShiftPeopleEditor(
    editor: ScheduleTemplateEditor,
    type: ScheduleShiftType,
    participants: List<ScheduleParticipant>,
    onChanged: ((ScheduleTemplateEditor) -> ScheduleTemplateEditor) -> Unit,
) = ElevatedCard(shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${type.label} · ${type.timeLabel}", fontWeight = FontWeight.SemiBold)
        if (participants.isEmpty()) {
            Text("请先选择排班成员", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                participants.forEach { person ->
                    val selected = person.id in editor.people(editor.selectedDay, type)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onChanged { value ->
                                val key = value.selectedDay to type
                                val selectedPeople = value.people(value.selectedDay, type).toMutableSet()
                                if (selected) selectedPeople.remove(person.id) else if (selectedPeople.size < 3) selectedPeople.add(person.id)
                                value.copy(assignments = value.assignments + (key to selectedPeople))
                            }
                        },
                        label = { Text(person.realName) },
                    )
                }
            }
        }
    }
}
