package com.jaydocoder.plateview.feature.search

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.R
import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import java.text.DateFormat
import java.util.Date

@Composable
fun SearchRoute(
    onNavigateToVehicle: (Long) -> Unit,
    onNavigateToAdmin: (() -> Unit)?,
    onLogout: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput()
        } else {
            viewModel.onVoicePermissionDenied()
        }
    }

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
        onVoiceInput = {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                viewModel.startVoiceInput()
            } else {
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onCandidateSelected = viewModel::selectCandidate,
        onHistorySelected = viewModel::selectHistory,
        onDeleteHistory = viewModel::deleteHistory,
        onClearHistory = viewModel::clearHistory,
        onRetry = viewModel::retrySearch,
        onOpenAdmin = onNavigateToAdmin,
        onLogout = onLogout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onVoiceInput: () -> Unit,
    onCandidateSelected: (VehicleCandidate) -> Unit,
    onHistorySelected: (SearchHistoryItem) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onRetry: () -> Unit,
    onOpenAdmin: (() -> Unit)?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_plateview_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.padding(5.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.search_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.search_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                },
                actions = {
                    onOpenAdmin?.let { openAdmin ->
                        IconButton(onClick = openAdmin) {
                            Icon(
                                imageVector = Icons.Outlined.AdminPanelSettings,
                                contentDescription = "打开管理员工作台",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = stringResource(R.string.search_logout),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
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
                onVoiceInput = onVoiceInput,
                isListening = uiState.isListening,
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
            if (uiState.isListening) {
                item(key = "voice_listening") {
                    StatusStrip(
                        message = stringResource(R.string.search_voice_listening),
                        isError = false,
                        icon = Icons.Outlined.Mic
                    )
                }
            }

            uiState.voiceFailure?.let { failure ->
                item(key = "voice_failure") {
                    StatusStrip(
                        message = stringResource(failure.messageResource()),
                        isError = true,
                    )
                }
            }

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
    onVoiceInput: () -> Unit,
    isListening: Boolean,
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
                if (isListening) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(PlateViewDimensions.compactSpacing)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onVoiceInput) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = stringResource(R.string.search_voice_start),
                            tint = MaterialTheme.colorScheme.primary
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
            PlateComponent(number = candidate.plateNumber)
            
            Spacer(modifier = Modifier.width(PlateViewDimensions.itemSpacing))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.categoryLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
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
                    text = item.plateNumber,
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
private fun PlateComponent(number: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(PlateViewDimensions.cornerSmall),
        modifier = Modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f),
                shape = RoundedCornerShape(PlateViewDimensions.cornerSmall),
            )
            .shadow(1.dp, RoundedCornerShape(PlateViewDimensions.cornerSmall)),
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(
                text = number,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
            )
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

private fun VoiceInputFailure.messageResource(): Int = when (this) {
    VoiceInputFailure.PermissionDenied -> R.string.search_voice_permission_denied
    VoiceInputFailure.ServiceUnavailable -> R.string.search_voice_service_unavailable
    VoiceInputFailure.NoMatch -> R.string.search_voice_no_match
    VoiceInputFailure.RecognitionFailed -> R.string.search_voice_failed
}
