package com.jaydocoder.plateview.feature.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.R
import com.jaydocoder.plateview.domain.vehicle.LongTermProfile
import com.jaydocoder.plateview.domain.vehicle.ResidentProfile
import com.jaydocoder.plateview.domain.vehicle.VehicleAttribute
import com.jaydocoder.plateview.domain.vehicle.VehicleDetail

@Composable
fun VehicleDetailRoute(
    onNavigateUp: () -> Unit,
    viewModel: VehicleDetailViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    VehicleDetailScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onRetry = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    uiState: VehicleDetailUiState,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        when (val content = uiState.content) {
            VehicleDetailContent.Loading -> LoadingContent(modifier = Modifier.padding(innerPadding))
            is VehicleDetailContent.Error -> ErrorContent(
                failure = content.reason,
                onRetry = onRetry,
                modifier = Modifier.padding(innerPadding),
            )

            is VehicleDetailContent.Data -> VehicleDetailContent(
                vehicle = content.vehicle,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PlateViewDimensions.pageHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.width(PlateViewDimensions.itemSpacing))
        Text(
            text = stringResource(R.string.detail_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ErrorContent(
    failure: VehicleDetailFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PlateViewDimensions.pageHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(failure.messageResource()),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (failure == VehicleDetailFailure.ServiceUnavailable) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.detail_retry))
            }
        }
    }
}

@Composable
private fun VehicleDetailContent(
    vehicle: VehicleDetail,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = PlateViewDimensions.pageHorizontal,
            vertical = PlateViewDimensions.pageVertical,
        ),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
    ) {
        item(key = "identity") {
            VehicleIdentityBanner(vehicle = vehicle)
        }
        vehicle.vehicleType?.let { vehicleType ->
            item(key = "vehicle_type") {
                DetailInfoRow(
                    label = stringResource(R.string.detail_vehicle_type),
                    value = vehicleType,
                )
            }
        }
        vehicle.residentProfile?.let { profile ->
            item(key = "resident_profile") {
                DetailSection(
                    title = stringResource(R.string.detail_resident_title),
                    fields = profile.toFields(),
                )
            }
        }
        vehicle.longTermProfile?.let { profile ->
            item(key = "long_term_profile") {
                DetailSection(
                    title = stringResource(R.string.detail_long_term_title),
                    fields = profile.toFields(),
                )
            }
        }
        if (vehicle.attributes.isNotEmpty()) {
            item(key = "attributes_title") {
                Text(
                    text = stringResource(R.string.detail_other_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(
                items = vehicle.attributes,
                key = { attribute -> "${attribute.label}:${attribute.value}" },
                contentType = { "vehicle_attribute" },
            ) { attribute ->
                DetailInfoRow(label = attribute.label, value = attribute.value)
            }
        }
    }
}

@Composable
private fun VehicleIdentityBanner(vehicle: VehicleDetail) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(PlateViewDimensions.pageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(PlateViewDimensions.itemSpacing))
            Column {
                Text(text = vehicle.plateNumber, style = MaterialTheme.typography.headlineMedium)
                Text(text = vehicle.categoryLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    fields: List<DetailField>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        fields.forEach { field ->
            DetailInfoRow(label = field.label, value = field.value)
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(top = PlateViewDimensions.compactSpacing))
    }
}

private data class DetailField(
    val label: String,
    val value: String,
)

@Composable
private fun ResidentProfile.toFields(): List<DetailField> = listOfNotNull(
    DetailField(stringResource(R.string.detail_owner_name), ownerName),
    DetailField(stringResource(R.string.detail_identity_card), identityCardNumber),
    contactPhone?.let { DetailField(stringResource(R.string.detail_contact_phone), it) },
    remarks?.let { DetailField(stringResource(R.string.detail_remarks), it) },
)

@Composable
private fun LongTermProfile.toFields(): List<DetailField> = listOfNotNull(
    organizationName?.let { DetailField(stringResource(R.string.detail_organization_name), it) },
    passHolder?.let { DetailField(stringResource(R.string.detail_pass_holder), it) },
    passageDetails?.let { DetailField(stringResource(R.string.detail_passage_details), it) },
    remarks?.let { DetailField(stringResource(R.string.detail_remarks), it) },
)

private fun VehicleDetailFailure.messageResource(): Int = when (this) {
    VehicleDetailFailure.SessionExpired -> R.string.detail_session_expired
    VehicleDetailFailure.VehicleNotFound -> R.string.detail_not_found
    VehicleDetailFailure.ServiceUnavailable -> R.string.detail_service_unavailable
}
