package com.jaydocoder.plateview.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.R
import com.jaydocoder.plateview.component.InactiveVehicleStatusBadge
import com.jaydocoder.plateview.component.VehiclePlateBadge
import com.jaydocoder.plateview.component.rememberCurrentBeijingTime
import com.jaydocoder.plateview.component.glass.GlassSurface
import com.jaydocoder.plateview.component.glass.LiquidGlassInput
import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.domain.vehicle.formatPlateForDisplay
import com.jaydocoder.plateview.domain.workorder.WorkOrder
import com.jaydocoder.plateview.domain.workorder.WechatMessage
import com.jaydocoder.plateview.domain.workorder.WorkOrderPassageState
import com.jaydocoder.plateview.domain.workorder.displayLabel
import com.jaydocoder.plateview.domain.workorder.extractWorkOrderPassageTimeRemark
import com.jaydocoder.plateview.domain.workorder.resolveWorkOrderPassageState
import com.jaydocoder.plateview.domain.workorder.resolveWechatMessagePassageState
import com.jaydocoder.plateview.domain.workorder.selectWorkOrderCandidatePlate
import com.jaydocoder.plateview.feature.auth.AvatarViewModel
import com.jaydocoder.plateview.feature.profile.AvatarImage
import java.text.DateFormat
import java.util.Date

@Composable
fun SearchRoute(
    onNavigateToVehicle: (Long) -> Unit,
    onNavigateToWorkOrder: (Long, String) -> Unit,
    onNavigateToWechatMessage: (Long) -> Unit,
    onNavigateToProfile: () -> Unit,
    onScreenVisible: () -> Unit = {},
    onScreenHidden: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    avatarViewModel: AvatarViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val avatarState = avatarViewModel.uiState.collectAsStateWithLifecycle().value
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        onScreenVisible()
        onDispose(onScreenHidden)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchEvent.OpenVehicle -> onNavigateToVehicle(event.vehicleId)
                is SearchEvent.OpenWorkOrder -> onNavigateToWorkOrder(event.recordId, event.query)
                is SearchEvent.OpenWechatMessage -> onNavigateToWechatMessage(event.messageId)
            }
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SearchScreen(
        uiState = uiState,
        onQueryChanged = viewModel::updateQuery,
        onCandidateSelected = viewModel::selectCandidate,
        onWorkOrderSelected = viewModel::selectWorkOrder,
        onWechatMessageSelected = viewModel::selectWechatMessage,
        onHistorySelected = viewModel::selectHistory,
        onDeleteHistory = viewModel::deleteHistory,
        onClearHistory = viewModel::clearHistory,
        onRetry = viewModel::retrySearch,
        avatar = avatarState.entry,
        onOpenProfile = onNavigateToProfile,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onCandidateSelected: (VehicleCandidate) -> Unit,
    onWorkOrderSelected: (WorkOrder) -> Unit = {},
    onWechatMessageSelected: (WechatMessage) -> Unit = {},
    onHistorySelected: (SearchHistoryItem) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onRetry: () -> Unit,
    avatar: com.jaydocoder.plateview.feature.auth.AvatarCacheEntry,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.testTag("search_top_bar"),
                title = {
                    Row(
                        modifier = Modifier.testTag("search_title_group"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onOpenProfile,
                            modifier = Modifier.testTag("search_profile_avatar"),
                        ) {
                            AvatarImage(
                                avatar,
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "车辆核验",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
            SearchBar(
                query = uiState.query,
                onQueryChanged = onQueryChanged,
                modifier = Modifier.padding(
                    horizontal = PlateViewDimensions.pageHorizontal,
                    vertical = PlateViewDimensions.pageVertical,
                ),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = PlateViewDimensions.pageHorizontal,
                    end = PlateViewDimensions.pageHorizontal,
                    bottom = PlateViewDimensions.pageVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
            ) {
            item(key = "search_feedback") {
                SearchFeedback(
                    resultState = uiState.resultState,
                    onRetry = onRetry,
                )
            }

            if (uiState.candidates.isNotEmpty()) {
                item(key = "candidate_heading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionTitle(
                            text = stringResource(R.string.search_candidates_title),
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(PlateViewDimensions.cornerSmall),
                        ) {
                            Text(
                                text = "${uiState.candidates.size} 条",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                items(
                    items = uiState.candidates,
                    key = VehicleCandidate::id,
                    contentType = { "vehicle_candidate" },
                ) { candidate ->
                    VehicleCandidateRow(
                        candidate = candidate,
                        onSelected = onCandidateSelected,
                    )
                }
            }

            if (uiState.workOrderCandidates.isNotEmpty()) {
                item(key = "work_order_heading") {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(text = "微信车单", modifier = Modifier.weight(1f))
                        Text("${uiState.workOrderCandidates.size} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(
                    items = uiState.workOrderCandidates,
                    key = WorkOrder::id,
                    contentType = { "work_order_candidate" },
                ) { candidate ->
                    WorkOrderCandidateRow(candidate, uiState.query, onWorkOrderSelected)
                }
            }

            if (uiState.wechatMessages.isNotEmpty()) {
                item(key = "wechat_message_heading") {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(text = "微信聊天记录", modifier = Modifier.weight(1f))
                        Text("${uiState.wechatMessages.size} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(
                    items = uiState.wechatMessages,
                    key = WechatMessage::id,
                    contentType = { "wechat_message_candidate" },
                ) { message ->
                    WechatMessageCandidateRow(message, onWechatMessageSelected)
                }
            }

            if (uiState.history.isNotEmpty()) {
                item(key = "history_heading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = PlateViewDimensions.sectionSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionTitle(
                            text = stringResource(R.string.search_history_title),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearHistory) {
                            Text(
                                text = stringResource(R.string.search_history_clear),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                items(
                    items = uiState.history,
                    key = SearchHistoryItem::id,
                    contentType = { "search_history" },
                ) { item ->
                    SearchHistoryRow(
                        item = item,
                        onSelected = onHistorySelected,
                        onDelete = onDeleteHistory,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun WechatMessageCandidateRow(message: WechatMessage, onSelected: (WechatMessage) -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().clickable { onSelected(message) }.testTag("wechat_message_${message.id}"),
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
        elevated = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                message.plateNumbers.firstOrNull()?.let { plate ->
                    VehiclePlateBadge(plateNumber = plate, compact = true)
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    WechatSenderName(message.displayName)
                    resolveWechatMessagePassageState(message, rememberCurrentBeijingTime())?.let { state ->
                        CandidateCompactBadge(
                            text = state.displayLabel(),
                            containerColor = when (state) {
                                WorkOrderPassageState.VALID -> MaterialTheme.colorScheme.primaryContainer
                                WorkOrderPassageState.EXPIRED,
                                WorkOrderPassageState.VOID,
                                WorkOrderPassageState.AREA_MISMATCH,
                                -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.tertiaryContainer
                            },
                            contentColor = when (state) {
                                WorkOrderPassageState.VALID -> MaterialTheme.colorScheme.onPrimaryContainer
                                WorkOrderPassageState.EXPIRED,
                                WorkOrderPassageState.VOID,
                                WorkOrderPassageState.AREA_MISMATCH,
                                -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        text = "${message.sourceName} · ${formatWechatMessageTime(message.sentAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "查看微信聊天详情", tint = MaterialTheme.colorScheme.outline)
            }
            Text(
                text = message.matchedSnippet,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun WechatSenderName(displayName: String) {
    val isDirector = displayName.trim() == "孙主任"
    Text(
        text = displayName,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = if (isDirector) 20.sp else MaterialTheme.typography.titleMedium.fontSize,
            fontWeight = if (isDirector) FontWeight.Bold else FontWeight.SemiBold,
        ),
        color = if (isDirector) Color(0xFFB87800) else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

private fun formatWechatMessageTime(value: String): String = runCatching {
    java.time.Instant.parse(value).atZone(java.time.ZoneId.of("Asia/Shanghai"))
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
}.getOrDefault(value)

@Composable
private fun WorkOrderCandidateRow(candidate: WorkOrder, query: String, onSelected: (WorkOrder) -> Unit) {
    val selectedPlate = selectWorkOrderCandidatePlate(
        rawPlate = candidate.rawPlate,
        orderNumber = candidate.orderNumber,
        query = query,
        rawContent = candidate.rawContent,
    )
    val passageTimeRemark = extractWorkOrderPassageTimeRemark(candidate.remarks)
    GlassSurface(
        modifier = Modifier.fillMaxWidth().clickable { onSelected(candidate) }.testTag("work_order_${candidate.id}"),
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
        elevated = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (selectedPlate != null) {
                    VehiclePlateBadge(
                        plateNumber = selectedPlate,
                        compact = true,
                        modifier = Modifier.testTag("work_order_plate_${candidate.id}"),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    CandidatePrimaryText(
                        text = candidate.orderNumber ?: "未识别",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    WorkOrderStatusBadge(
                        candidate,
                        Modifier.padding(top = 4.dp).testTag("work_order_status_${candidate.id}"),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "查看车单详情", tint = MaterialTheme.colorScheme.outline)
            }
            val supportingInformation = listOfNotNull(
                candidate.rawValidTime?.takeIf(String::isNotBlank),
                candidate.location?.takeIf(String::isNotBlank),
                candidate.sourceName.takeIf(String::isNotBlank),
                passageTimeRemark,
            )
            if (supportingInformation.isNotEmpty()) {
                Text(
                    text = supportingInformation.joinToString(" · "),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WorkOrderStatusBadge(workOrder: WorkOrder, modifier: Modifier = Modifier) {
    val passageState = resolveWorkOrderPassageState(workOrder, rememberCurrentBeijingTime())
    val containerColor = when (passageState) {
        WorkOrderPassageState.VALID -> MaterialTheme.colorScheme.primaryContainer
        WorkOrderPassageState.NOT_STARTED,
        WorkOrderPassageState.OUTSIDE_ALLOWED_HOURS,
        WorkOrderPassageState.UNKNOWN,
        -> MaterialTheme.colorScheme.tertiaryContainer
        WorkOrderPassageState.EXPIRED,
        WorkOrderPassageState.VOID,
        WorkOrderPassageState.AREA_MISMATCH,
        -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (passageState) {
        WorkOrderPassageState.VALID -> MaterialTheme.colorScheme.onPrimaryContainer
        WorkOrderPassageState.NOT_STARTED,
        WorkOrderPassageState.OUTSIDE_ALLOWED_HOURS,
        WorkOrderPassageState.UNKNOWN,
        -> MaterialTheme.colorScheme.onTertiaryContainer
        WorkOrderPassageState.EXPIRED,
        WorkOrderPassageState.VOID,
        WorkOrderPassageState.AREA_MISMATCH,
        -> MaterialTheme.colorScheme.onErrorContainer
    }
    CandidateCompactBadge(
        text = passageState.displayLabel(),
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
    )
}

@Composable
private fun CandidateCompactBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(PlateViewDimensions.cornerSmall),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun CandidatePrimaryText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
    )
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassInput(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier
            .fillMaxWidth()
            .testTag("search_input"),
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(
                    onClick = { onQueryChanged("") },
                    modifier = Modifier.testTag("search_clear_action"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "清空车牌输入",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
    )
}

@Composable
private fun SearchFeedback(
    resultState: SearchResultState,
    onRetry: () -> Unit,
) {
    AnimatedVisibility(
        visible = resultState != SearchResultState.Idle,
        enter = fadeIn() + slideInVertically()
    ) {
        when (resultState) {
            SearchResultState.Idle -> Unit
            SearchResultState.AwaitingInput -> StatusStrip(
                message = stringResource(R.string.search_awaiting_input),
                isError = false,
            )

            SearchResultState.Loading -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = PlateViewDimensions.compactSpacing),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(PlateViewDimensions.compactSpacing))
                Text(
                    text = stringResource(R.string.search_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            SearchResultState.Empty -> StatusStrip(
                message = stringResource(R.string.search_empty),
                isError = false,
            )

            is SearchResultState.Error -> Column(
                verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
            ) {
                StatusStrip(
                    message = resultState.error.message,
                    isError = true,
                )
                Text(
                    text = "诊断编号：${resultState.error.requestId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (resultState.error.retryable) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_retry"),
                    ) {
                        Text(stringResource(R.string.search_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusStrip(
    message: String,
    isError: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
    ) {
        Row(
            modifier = Modifier.padding(PlateViewDimensions.itemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun VehicleCandidateRow(
    candidate: VehicleCandidate,
    onSelected: (VehicleCandidate) -> Unit,
) {
    ElevatedCard(
        onClick = { onSelected(candidate) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PlateViewDimensions.candidateMinimumHeight)
            .testTag("candidate_${candidate.id}"),
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = PlateViewDimensions.cardElevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PlateViewDimensions.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VehiclePlateBadge(
                plateNumber = candidate.plateNumber,
                compact = true,
                plateColor = candidate.plateColor,
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                CandidatePrimaryText(
                    text = candidate.categoryLabel,
                    color = candidateCategoryColor(candidate.category),
                )
                if (candidate.status == "STRICT_CHECK" || candidate.status == "BLACKLISTED" || candidate.status == "INACTIVE") {
                    InactiveVehicleStatusBadge(candidate.status, modifier = Modifier.padding(top = 4.dp))
                }
                if (candidate.category == "OTHER_LONG_TERM") {
                    Text(
                        text = "单位名称：${candidate.organizationName.orEmpty().ifBlank { "未填写" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                CandidateCompactBadge(
                    text = "核验就绪",
                    modifier = Modifier.padding(top = 4.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun candidateCategoryColor(category: String): Color = when (category) {
    "RESIDENT" -> MaterialTheme.colorScheme.secondary
    "SCENIC_UNIT" -> MaterialTheme.colorScheme.primary
    "SCENIC_ENTERPRISE" -> MaterialTheme.colorScheme.tertiary
    "CADRE" -> MaterialTheme.colorScheme.onSurfaceVariant
    "KANAS_TOURISM_DEVELOPMENT" -> MaterialTheme.colorScheme.onPrimaryContainer
    "OTHER_LONG_TERM" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun SearchHistoryRow(
    item: SearchHistoryItem,
    onSelected: (SearchHistoryItem) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Surface(
        onClick = { onSelected(item) },
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = formatPlateForDisplay(item.plateNumber),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${item.categoryLabel} · ${formatSearchTime(item.searchedAtEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = { onDelete(item.id) }) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(
                        R.string.search_history_delete,
                        item.plateNumber,
                    ),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun formatSearchTime(timestamp: Long): String = androidx.compose.runtime.remember(timestamp) {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
}
