package com.jaydocoder.plateview.feature.search

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                    Column {
                        Text(
                            text = stringResource(R.string.search_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.search_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    onOpenAdmin?.let { openAdmin ->
                        IconButton(onClick = openAdmin) {
                            Icon(
                                imageVector = Icons.Outlined.AdminPanelSettings,
                                contentDescription = "打开管理员工作台",
                            )
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = stringResource(R.string.search_logout),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = PlateViewDimensions.pageHorizontal,
                vertical = PlateViewDimensions.pageVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
        ) {
            item(key = "search_input") {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input"),
                    label = { Text(stringResource(R.string.search_input_label)) },
                    placeholder = { Text(stringResource(R.string.search_input_placeholder)) },
                    trailingIcon = {
                        if (uiState.isListening) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(PlateViewDimensions.compactSpacing)
                                    .size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(onClick = onVoiceInput) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = stringResource(R.string.search_voice_start),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                )
            }

            if (uiState.isListening) {
                item(key = "voice_listening") {
                    StatusStrip(
                        message = stringResource(R.string.search_voice_listening),
                        isError = false,
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
                    SectionTitle(text = stringResource(R.string.search_candidates_title))
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
                            Text(stringResource(R.string.search_history_clear))
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

@Composable
private fun SearchFeedback(
    resultState: SearchResultState,
    onRetry: () -> Unit,
) {
    when (resultState) {
        SearchResultState.Idle -> Unit
        SearchResultState.AwaitingInput -> StatusStrip(
            message = stringResource(R.string.search_awaiting_input),
            isError = false,
        )

        SearchResultState.Loading -> Row(
            modifier = Modifier.padding(vertical = PlateViewDimensions.compactSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(PlateViewDimensions.compactSpacing))
            Text(
                text = stringResource(R.string.search_loading),
                style = MaterialTheme.typography.bodyMedium,
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
                    modifier = Modifier.testTag("search_retry"),
                ) {
                    Text(stringResource(R.string.search_retry))
                }
            }
        }
    }
}

@Composable
private fun StatusStrip(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(PlateViewDimensions.itemSpacing),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun VehicleCandidateRow(
    candidate: VehicleCandidate,
    onSelected: (VehicleCandidate) -> Unit,
) {
    OutlinedCard(
        onClick = { onSelected(candidate) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PlateViewDimensions.candidateMinimumHeight)
            .testTag("candidate_${candidate.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PlateViewDimensions.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = candidate.plateNumber,
                    modifier = Modifier.padding(
                        horizontal = PlateViewDimensions.platePaddingHorizontal,
                        vertical = PlateViewDimensions.platePaddingVertical,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(modifier = Modifier.width(PlateViewDimensions.itemSpacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.categoryLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        R.string.search_candidate_description,
                        candidate.plateNumber,
                        candidate.categoryLabel,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
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
    OutlinedCard(
        onClick = { onSelected(item) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PlateViewDimensions.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = PlateViewDimensions.itemSpacing),
            ) {
                Text(text = item.plateNumber, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.search_history_metadata,
                        item.categoryLabel,
                        formatSearchTime(item.searchedAtEpochMillis),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onDelete(item.id) }) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(
                        R.string.search_history_delete,
                        item.plateNumber,
                    ),
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

private fun VoiceInputFailure.messageResource(): Int = when (this) {
    VoiceInputFailure.PermissionDenied -> R.string.search_voice_permission_denied
    VoiceInputFailure.ServiceUnavailable -> R.string.search_voice_service_unavailable
    VoiceInputFailure.NoMatch -> R.string.search_voice_no_match
    VoiceInputFailure.RecognitionFailed -> R.string.search_voice_failed
}
