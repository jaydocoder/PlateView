package com.jaydocoder.plateview.feature.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.data.statistics.VehicleCategoryPoint
import com.jaydocoder.plateview.data.statistics.VehicleQueryHistoryItem
import com.jaydocoder.plateview.data.statistics.VehicleStatistics
import com.jaydocoder.plateview.data.statistics.VehicleTopPlatePoint
import com.jaydocoder.plateview.component.VehiclePlateBadge
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

private val CategoryPalette = listOf(
    Color(0xFF087B8A),
    Color(0xFF1C604B),
    Color(0xFFD59A36),
    Color(0xFF5C7E9E),
    Color(0xFFB83E4A),
    Color(0xFF737D5C),
)

@Composable
fun StatisticsRoute(
    onNavigateToVehicle: (Long) -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    StatisticsScreen(
        state = state,
        onRange = viewModel::selectRange,
        onCategory = viewModel::selectCategory,
        onScope = viewModel::selectScope,
        onNavigateToVehicle = onNavigateToVehicle,
    )
}

@Composable
internal fun StatisticsScreen(
    state: StatisticsUiState,
    onRange: (StatisticsRange) -> Unit,
    onCategory: (String?) -> Unit,
    onScope: (StatisticsScope) -> Unit,
    onNavigateToVehicle: (Long) -> Unit = {},
) {
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var historySearchFocused by rememberSaveable { mutableStateOf(false) }
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
                TimeRangeSelector(selected = state.range, onSelected = onRange)
                CategorySelector(selected = state.category, onSelected = onCategory)
                if (state.isAdministrator) {
                    val scopes = if (state.canViewAllStatistics) StatisticsScope.entries else listOf(StatisticsScope.ME)
                    FilterRow(scopes, state.scope, StatisticsScope::label, onScope)
                }
                if (state.scope == StatisticsScope.ME && state.pendingSyncCount > 0) {
                    Text(
                        "有 ${state.pendingSyncCount} 条查询记录等待同步",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HistorySearchField(
                    query = historyQuery,
                    onQueryChanged = { historyQuery = it },
                    onFocusedChanged = { historySearchFocused = it },
                )
            }
        }
        when {
            state.loading -> item { LoadingState() }
            state.error != null -> item { EmptyState(state.error) }
            state.statistics == null || state.statistics.totalQueries == 0L -> item { EmptyState("当前条件下还没有查询记录") }
            else -> statisticsContent(
                statistics = state.statistics,
                category = state.category,
                history = state.history,
                historyQuery = historyQuery,
                showOverview = !historySearchFocused && historyQuery.isBlank(),
                onNavigateToVehicle = onNavigateToVehicle,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.statisticsContent(
    statistics: VehicleStatistics,
    category: String?,
    history: List<VehicleQueryHistoryItem>,
    historyQuery: String,
    showOverview: Boolean,
    onNavigateToVehicle: (Long) -> Unit,
) {
    if (category == null && showOverview) {
        item {
            ChartCard("查询最多的车牌") {
                TopPlateRanking(statistics.topPlates, history, onNavigateToVehicle)
            }
        }
        item {
            ChartCard("类别查询数量") { CategoryColumnChart(statistics.categories) }
        }
    }
    item { QueryHistoryCard(history, historyQuery, onNavigateToVehicle) }
}

@Composable
private fun TopPlateRanking(
    points: List<VehicleTopPlatePoint>,
    history: List<VehicleQueryHistoryItem>,
    onNavigateToVehicle: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("statistics_top_plate_ranking"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        points.take(5).forEachIndexed { index, point ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable {
                    history.firstOrNull { it.plateNumber == point.plateNumber }?.let { onNavigateToVehicle(it.vehicleId) }
                }.testTag("statistics_top_plate_${point.plateNumber}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.width(20.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                VehiclePlateBadge(
                    plateNumber = point.plateNumber,
                    compact = true,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${point.queryCount} 次",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
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
private fun HistorySearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onFocusedChanged: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusedChanged(it.isFocused) }.testTag("statistics_history_search"),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Search, contentDescription = "搜索历史") },
        label = { Text("搜索历史车牌") },
        placeholder = { Text("输入车牌号") },
    )
}

@Composable
private fun TimeRangeSelector(
    selected: StatisticsRange,
    onSelected: (StatisticsRange) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("statistics_time_range_selector"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatisticsRange.entries.forEach { range ->
            val isSelected = range == selected
            Surface(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).clickable { onSelected(range) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = range.label,
                        modifier = Modifier.padding(horizontal = 2.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
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
private fun CategoryColumnChart(points: List<VehicleCategoryPoint>) {
    val countsByCategory = points.associate { it.category to it.queryCount }
    val categoryPoints = CategoryLabels.keys.map { category ->
        VehicleCategoryPoint(category, countsByCategory[category] ?: 0L)
    }
    val largestCount = categoryPoints.maxOfOrNull(VehicleCategoryPoint::queryCount)?.coerceAtLeast(1L) ?: 1L
    val shortLabels = listOf("村民", "单位", "企业", "干部", "喀旅", "其他")
    val chartHeight = 190.dp
    val axisColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier.fillMaxWidth().height(chartHeight + 34.dp).testTag("statistics_category_count_chart"),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(28.dp).height(chartHeight),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text(largestCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text((largestCount / 2).toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
                Canvas(Modifier.fillMaxSize()) {
                    val baseY = size.height - 1.dp.toPx()
                    val topY = 1.dp.toPx()
                    drawLine(axisColor, Offset(0f, topY), Offset(0f, baseY), 1.dp.toPx())
                    drawLine(axisColor, Offset(0f, baseY), Offset(size.width, baseY), 1.dp.toPx())
                    drawLine(gridColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
                }
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    categoryPoints.forEachIndexed { index, point ->
                        val fraction = point.queryCount.toFloat() / largestCount
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(fraction.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                .background(CategoryPalette[index % CategoryPalette.size])
                                .testTag("statistics_category_count_${point.category}"),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                shortLabels.forEach { label ->
                    Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun QueryHistoryCard(
    items: List<VehicleQueryHistoryItem>,
    query: String,
    onNavigateToVehicle: (Long) -> Unit,
) {
    ChartCard("查询记录") {
        val normalizedQuery = query.trim().filterNot { it.isWhitespace() || it == '·' }
        val filteredItems = items.filter { item ->
            normalizedQuery.isBlank() || item.plateNumber?.filterNot { it.isWhitespace() || it == '·' }?.contains(normalizedQuery, ignoreCase = true) == true
        }
        if (filteredItems.isEmpty()) {
            Text("当前筛选条件下还没有查询记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onNavigateToVehicle(item.vehicleId) }
                            .testTag("statistics_history_vehicle_${item.vehicleId}")
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.plateNumber != null) {
                            VehiclePlateBadge(plateNumber = item.plateNumber, compact = true)
                        } else {
                            Text("车辆档案 #${item.vehicleId}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = formatQueryTime(item.occurredAtEpochMillis),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
