package com.jaydocoder.plateview.feature.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.data.statistics.VehicleCategoryPoint
import com.jaydocoder.plateview.data.statistics.VehicleQueryHistoryItem
import com.jaydocoder.plateview.data.statistics.VehicleStatistics
import com.jaydocoder.plateview.domain.vehicle.formatPlateForDisplay
import java.text.DateFormat
import java.util.Date

private val CategoryLabels = mapOf(
    "RESIDENT" to "村民车辆",
    "SCENIC_UNIT" to "驻景区单位车辆",
    "SCENIC_ENTERPRISE" to "驻景区企业车辆",
    "CADRE" to "干部车辆",
    "KANAS_TOURISM_DEVELOPMENT" to "喀旅公司车辆",
    "OTHER_LONG_TERM" to "其他长期通行车辆",
)

@Composable
fun StatisticsRoute(viewModel: StatisticsViewModel = hiltViewModel()) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    StatisticsScreen(
        state = state,
        onRange = viewModel::selectRange,
        onCategory = viewModel::selectCategory,
        onScope = viewModel::selectScope,
    )
}

@Composable
internal fun StatisticsScreen(
    state: StatisticsUiState,
    onRange: (StatisticsRange) -> Unit,
    onCategory: (String?) -> Unit,
    onScope: (StatisticsScope) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("查询统计", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                FilterRow(StatisticsRange.entries, state.range, StatisticsRange::label, onRange)
                CategorySelector(selected = state.category, onSelected = onCategory)
                if (state.isAdministrator) FilterRow(StatisticsScope.entries, state.scope, StatisticsScope::label, onScope)
                if (state.scope == StatisticsScope.ME && state.pendingSyncCount > 0) {
                    Text(
                        "有 ${state.pendingSyncCount} 条查询记录等待同步",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        when {
            state.loading -> item { LoadingState() }
            state.error != null -> item { EmptyState(state.error) }
            state.statistics == null || state.statistics.totalQueries == 0L -> item { EmptyState("当前条件下还没有查询记录") }
            else -> statisticsContent(state.statistics, state.category, state.history)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.statisticsContent(
    statistics: VehicleStatistics,
    category: String?,
    history: List<VehicleQueryHistoryItem>,
) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("查询总数", statistics.totalQueries.toString(), Modifier.weight(1f))
            MetricCard("不同车牌", statistics.distinctPlates.toString(), Modifier.weight(1f))
            MetricCard("活跃用户", statistics.activeUsers.toString(), Modifier.weight(1f))
        }
    }
    if (category == null) item {
        ChartCard("类别占比") { CategoryChart(statistics.categories) }
    } else {
        item { QueryHistoryCard(history) }
    }
}

@Composable
private fun CategorySelector(selected: String?, onSelected: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.let { CategoryLabels[it] } ?: "全部类别"
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("statistics_category_selector"),
        ) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("全部类别") },
                onClick = { expanded = false; onSelected(null) },
                modifier = Modifier.testTag("statistics_category_option_all"),
            )
            CategoryLabels.forEach { (value, categoryLabel) ->
                DropdownMenuItem(
                    text = { Text(categoryLabel) },
                    onClick = { expanded = false; onSelected(value) },
                    modifier = Modifier.testTag("statistics_category_option_$value"),
                )
            }
        }
    }
}

@Composable
private fun <T> FilterRow(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values, key = { label(it) }) { value ->
            FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label(value)) })
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun CategoryChart(points: List<VehicleCategoryPoint>) {
    val palette = listOf(Color(0xFF087B8A), Color(0xFF1C604B), Color(0xFFD59A36), Color(0xFF5C7E9E), Color(0xFFB83E4A), Color(0xFF737D5C))
    val total = points.sumOf { it.queryCount }.coerceAtLeast(1L)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(150.dp)) {
            var start = -90f
            points.forEachIndexed { index, point ->
                val sweep = point.queryCount.toFloat() / total * 360f
                drawArc(
                    color = palette[index % palette.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(12.dp.toPx(), 12.dp.toPx()),
                    size = Size(size.width - 24.dp.toPx(), size.height - 24.dp.toPx()),
                    style = Stroke(26.dp.toPx()),
                )
                start += sweep
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            points.take(6).forEachIndexed { index, point ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.size(10.dp).background(palette[index % palette.size], CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text("${CategoryLabels[point.category] ?: point.category} ${point.queryCount}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun QueryHistoryCard(items: List<VehicleQueryHistoryItem>) {
    ChartCard("查询记录") {
        if (items.isEmpty()) {
            Text("当前筛选条件下还没有查询记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.plateNumber?.let(::formatPlateForDisplay) ?: "车辆档案 #${item.vehicleId}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${CategoryLabels[item.category] ?: item.category} · ${formatQueryTime(item.occurredAtEpochMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatQueryTime(value: Long): String = remember(value) {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
}

@Composable
private fun LoadingState() = Row(Modifier.fillMaxWidth().padding(48.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }

@Composable
private fun EmptyState(message: String) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
    Text(message, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
}
