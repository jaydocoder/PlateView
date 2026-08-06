package com.jaydocoder.plateview.feature.vehicle

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                title = { Text(stringResource(R.string.detail_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (val content = uiState.content) {
                VehicleDetailContent.Loading -> LoadingContent()
                is VehicleDetailContent.Error -> ErrorContent(
                    failure = content.reason,
                    onRetry = onRetry,
                )

                is VehicleDetailContent.Data -> VehicleDetailContent(
                    vehicle = content.vehicle,
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(PlateViewDimensions.itemSpacing))
        Text(
            text = stringResource(R.string.detail_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun ErrorContent(
    failure: VehicleDetailFailure,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(PlateViewDimensions.pageHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(failure.messageResource()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (failure == VehicleDetailFailure.ServiceUnavailable) {
            Spacer(modifier = Modifier.height(24.dp))
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
        
        vehicle.residentProfile?.let { profile ->
            item(key = "resident_profile") {
                DetailSection(
                    title = stringResource(R.string.detail_resident_title),
                    icon = Icons.Outlined.Person,
                    fields = profile.toFields(),
                )
            }
        }
        
        vehicle.longTermProfile?.let { profile ->
            item(key = "long_term_profile") {
                DetailSection(
                    title = stringResource(R.string.detail_long_term_title),
                    icon = Icons.Outlined.Info,
                    fields = profile.toFields(),
                )
            }
        }
        
        if (vehicle.attributes.isNotEmpty() || vehicle.vehicleType != null) {
            item(key = "vehicle_info") {
                DetailSection(
                    title = "车辆信息",
                    icon = Icons.Outlined.DirectionsCar,
                    fields = buildList {
                        vehicle.vehicleType?.let { add(DetailField(stringResource(R.string.detail_vehicle_type), it)) }
                        addAll(vehicle.attributes.map { DetailField(it.label, it.value) })
                    }
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun VehicleIdentityBanner(vehicle: VehicleDetail) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = vehicle.plateNumber,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = vehicle.categoryLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    fields: List<DetailField>,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(PlateViewDimensions.itemSpacing)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            fields.forEachIndexed { index, field ->
                DetailInfoRow(
                    label = field.label,
                    value = field.value,
                    isLast = index == fields.size - 1
                )
            }
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    isLast: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        if (!isLast) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
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
