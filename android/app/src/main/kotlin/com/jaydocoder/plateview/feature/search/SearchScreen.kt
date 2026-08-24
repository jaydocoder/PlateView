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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.R
import com.jaydocoder.plateview.component.InactiveVehicleContainerColor
import com.jaydocoder.plateview.component.InactiveVehicleStatusBadge
import com.jaydocoder.plateview.component.VehiclePlateBadge
import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.domain.vehicle.formatPlateForDisplay
import com.jaydocoder.plateview.feature.update.UpdateAvailableAction
import com.jaydocoder.plateview.feature.auth.AvatarViewModel
import com.jaydocoder.plateview.feature.profile.AvatarImage
import java.text.DateFormat
import java.util.Date

@Composable
fun SearchRoute(
    onNavigateToVehicle: (Long) -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    avatarViewModel: AvatarViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val avatarState = avatarViewModel.uiState.collectAsStateWithLifecycle().value
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchEvent.OpenVehicle -> onNavigateToVehicle(event.vehicleId)
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
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(onClick = onOpenProfile),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AvatarImage(avatar, Modifier.size(28.dp))
                        Text(
                            text = stringResource(R.string.search_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                shape = RoundedCornerShape(PlateViewDimensions.cornerExtraLarge),
            )
            .testTag("search_input"),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(PlateViewDimensions.cornerExtraLarge),
        tonalElevation = 2.dp,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    stringResource(R.string.search_input_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
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
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            )
        )
    }
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
                    message = stringResource(resultState.reason.messageResource()),
                    isError = true,
                )
                if (resultState.reason == SearchFailure.ServiceUnavailable) {
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
            containerColor = if (candidate.status == "INACTIVE") InactiveVehicleContainerColor else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = PlateViewDimensions.cardElevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PlateViewDimensions.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VehiclePlateBadge(plateNumber = candidate.plateNumber)
            
            Spacer(modifier = Modifier.width(PlateViewDimensions.itemSpacing))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.categoryLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = candidateCategoryColor(candidate.category),
                )
                if (candidate.status == "INACTIVE") {
                    InactiveVehicleStatusBadge(modifier = Modifier.padding(top = 4.dp))
                }
                if (candidate.category == "OTHER_LONG_TERM") {
                    Text(
                        text = "单位名称：${candidate.organizationName.orEmpty().ifBlank { "未填写" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Surface(
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(PlateViewDimensions.cornerSmall),
                ) {
                    Text(
                        text = "核验就绪",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
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

private fun SearchFailure.messageResource(): Int = when (this) {
    SearchFailure.SessionExpired -> R.string.search_session_expired
    SearchFailure.ServiceUnavailable -> R.string.search_service_unavailable
}
