package com.jaydocoder.plateview.feature.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.jaydocoder.plateview.component.CompatFlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.data.statistics.VehicleCategoryPoint
import com.jaydocoder.plateview.data.statistics.VehicleQueryHistoryItem
import com.jaydocoder.plateview.data.statistics.VehicleStatistics
import com.jaydocoder.plateview.data.statistics.VehicleTopPlatePoint
import com.jaydocoder.plateview.component.VehiclePlateBadge
import com.jaydocoder.plateview.component.glass.GlassPill
import com.jaydocoder.plateview.component.glass.GlassSurface
import com.jaydocoder.plateview.component.glass.LiquidGlassInput
import com.jaydocoder.plateview.component.glass.LiquidGlassSegmentedControl
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow

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
        onHistoryQueryChanged = viewModel::updateHistoryQuery,
        onLoadMoreHistory = viewModel::loadMoreHistory,
        onNavigateToVehicle = onNavigateToVehicle,
    )
}

@Composable
internal fun StatisticsScreen(
    state: StatisticsUiState,
    onRange: (StatisticsRange) -> Unit,
    onCategory: (String?) -> Unit,
    onScope: (StatisticsScope) -> Unit,
    onHistoryQueryChanged: (String) -> Unit = {},
    onLoadMoreHistory: () -> Unit = {},
    onNavigateToVehicle: (Long) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val hasMoreHistory = state.history.size < state.historyTotal
    val shouldLoadMore by remember(listState, state.history.size, state.historyTotal, state.isHistoryPageLoading) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.history.isNotEmpty() && hasMoreHistory && !state.isHistoryPageLoading &&
                lastVisibleIndex >= listState.layoutInfo.totalItemsCount - HISTORY_LOAD_TRIGGER_DISTANCE
        }
    }

    LaunchedEffect(listState, shouldLoadMore) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMoreHistory() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = PlateViewDimensions.pageHorizontal,
                    end = PlateViewDimensions.pageHorizontal,
                    top = PlateViewDimensions.pageVertical,
                    bottom = PlateViewDimensions.compactSpacing,
                ),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
        ) {
            Text("查询统计", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            HistorySearchField(
                query = state.historyQuery,
                onQueryChanged = onHistoryQueryChanged,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).testTag("statistics_content_list"),
            contentPadding = PaddingValues(
                start = PlateViewDimensions.pageHorizontal,
                end = PlateViewDimensions.pageHorizontal,
                bottom = PlateViewDimensions.pageVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
        ) {
            item(key = "statistics_filters") {
                StatisticsFilterPanel(
                    state = state,
                    onRange = onRange,
                    onCategory = onCategory,
                    onScope = onScope,
                )
            }
            when {
                state.loading -> item { LoadingState() }
                state.error != null -> item { EmptyState(state.error) }
                state.statistics == null || state.statistics.totalQueries == 0L -> item { EmptyState("当前条件下还没有查询记录") }
                else -> statisticsContent(
                    statistics = state.statistics,
                    category = state.category,
                    history = state.history,
                    historyQuery = state.historyQuery,
                    showOverview = state.historyQuery.isBlank(),
                    historyTotal = state.historyTotal,
                    isHistoryPageLoading = state.isHistoryPageLoading,
                    historyLoadError = state.historyLoadError,
                    onNavigateToVehicle = onNavigateToVehicle,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.statisticsContent(
    statistics: VehicleStatistics,
    category: String?,
    history: List<VehicleQueryHistoryItem>,
    historyQuery: String,
    showOverview: Boolean,
    historyTotal: Int,
    isHistoryPageLoading: Boolean,
    historyLoadError: String?,
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
    item(key = "statistics_history_heading") {
        Text("查询记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    if (history.isEmpty()) {
        item(key = "statistics_history_empty") {
            EmptyState(if (historyQuery.isBlank()) "当前条件下还没有查询记录" else "未找到匹配的查询记录")
        }
    } else {
        items(
            items = history,
            key = { item -> "${item.vehicleId}-${item.occurredAtEpochMillis}" },
            contentType = { "statistics_history" },
        ) { item ->
            QueryHistoryRow(item, onNavigateToVehicle)
        }
    }
    if (isHistoryPageLoading) {
        item(key = "statistics_history_loading_more") { LoadingMoreHistory() }
    } else if (historyLoadError != null) {
        item(key = "statistics_history_load_error") { EmptyState(historyLoadError) }
    } else if (history.isNotEmpty() && history.size < historyTotal) {
        item(key = "statistics_history_load_hint") {
            Text(
                "继续下滑加载更早记录",
                modifier = Modifier.fillMaxWidth().padding(vertical = PlateViewDimensions.compactSpacing),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StatisticsFilterPanel(
    state: StatisticsUiState,
    onRange: (StatisticsRange) -> Unit,
    onCategory: (String?) -> Unit,
    onScope: (StatisticsScope) -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().testTag("statistics_filter_panel"),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
    ) {
        Column(
            modifier = Modifier.padding(PlateViewDimensions.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
        ) {
            Text("统计条件", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        }
    }
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
) {
    LiquidGlassInput(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("statistics_history_search"),
        label = { Text("搜索历史车牌") },
        placeholder = { Text("输入车牌号") },
        leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Search, contentDescription = "搜索历史") },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    androidx.compose.material3.Icon(Icons.Outlined.Close, contentDescription = "清空历史车牌搜索")
                }
            }
        },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TimeRangeSelector(
    selected: StatisticsRange,
    onSelected: (StatisticsRange) -> Unit,
) {
    LiquidGlassSegmentedControl(
        modifier = Modifier.fillMaxWidth().testTag("statistics_time_range_selector"),
    ) {
        StatisticsRange.entries.forEach { range ->
            val isSelected = range == selected
            GlassPill(
                selected = isSelected,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .testTag("statistics_time_range_${range.name}"),
                onClick = { onSelected(range) },
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = range.label,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> FilterRow(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    CompatFlowRow(
        horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.tinySpacing),
    ) {
        values.forEach { value ->
            FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label(value)) })
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        elevated = true,
    ) {
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
                                .fillMaxHeight()
                                .testTag("statistics_category_count_${point.category}"),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction.coerceIn(0.12f, 1f)),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Text(
                                    text = point.queryCount.toString(),
                                    modifier = Modifier.testTag("statistics_category_value_${point.category}"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                        .background(CategoryPalette[index % CategoryPalette.size]),
                                )
                            }
                        }
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
private fun QueryHistoryRow(
    item: VehicleQueryHistoryItem,
    onNavigateToVehicle: (Long) -> Unit,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PlateViewDimensions.cornerMedium))
                .clickable { onNavigateToVehicle(item.vehicleId) }
                .testTag("statistics_history_vehicle_${item.vehicleId}")
                .padding(PlateViewDimensions.compactSpacing),
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

@Composable
private fun LoadingMoreHistory() = Row(
    modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.compactSpacing),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
) {
    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
    Text("正在加载更早记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun formatQueryTime(value: Long): String = remember(value) {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
}

@Composable
private fun LoadingState() = Row(Modifier.fillMaxWidth().padding(48.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }

@Composable
private fun EmptyState(message: String) = GlassSurface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Text(message, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
}

private const val HISTORY_LOAD_TRIGGER_DISTANCE = 3
