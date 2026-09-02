package com.jaydocoder.plateview.feature.vehicle

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.R
import com.jaydocoder.plateview.component.InactiveVehicleContainerColor
import com.jaydocoder.plateview.component.InactiveVehicleContentColor
import com.jaydocoder.plateview.component.InactiveVehicleStatusBadge
import com.jaydocoder.plateview.component.VehiclePlateBadge
import com.jaydocoder.plateview.domain.vehicle.LongTermProfile
import com.jaydocoder.plateview.domain.vehicle.ResidentProfile
import com.jaydocoder.plateview.domain.vehicle.VehicleAttribute
import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
import com.jaydocoder.plateview.feature.update.UpdateAvailableAction

@Composable
fun VehicleDetailRoute(
    onNavigateUp: () -> Unit,
    onOpenUpdate: (() -> Unit)? = null,
    viewModel: VehicleDetailViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    VehicleDetailScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onRetry = viewModel::refresh,
        onOpenUpdate = onOpenUpdate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    uiState: VehicleDetailUiState,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenUpdate: (() -> Unit)? = null,
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
                actions = {
                    onOpenUpdate?.let { openUpdate ->
                        UpdateAvailableAction(onClick = openUpdate)
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
                    isCached = content.isCached,
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
    isCached: Boolean,
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
        if (isCached) {
            item(key = "cache_notice") {
                Text(
                    text = stringResource(R.string.detail_cached_notice),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (vehicle.status == "INACTIVE") {
            item(key = "inactive_notice") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = InactiveVehicleContainerColor,
                    contentColor = InactiveVehicleContentColor,
                    shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InactiveVehicleStatusBadge()
                        Text("该车辆档案已停用，不具备有效通行核验资格", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
    ) {
        Box(modifier = Modifier.aspectRatio(2f)) {
            Image(
                painter = painterResource(R.drawable.vehicle_detail_mountain_road),
                contentDescription = "景区道路插画",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 18.dp),
            ) {
                VehiclePlateBadge(
                    plateNumber = vehicle.plateNumber,
                    emphasized = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = vehicle.categoryLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (vehicle.status == "INACTIVE") "已拉黑 / 已停用" else "通行档案核验信息",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vehicle.status == "INACTIVE") InactiveVehicleContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
            
            Spacer(modifier = Modifier.height(14.dp))

            fields.forEachIndexed { index, field ->
                DetailInfoCell(
                    label = field.label,
                    value = field.value,
                    displayMode = field.displayMode,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (index != fields.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailInfoCell(
    label: String,
    value: String,
    displayMode: DetailFieldDisplayMode,
    modifier: Modifier = Modifier,
) {
    val isInline = displayMode == DetailFieldDisplayMode.Inline ||
        (displayMode == DetailFieldDisplayMode.Auto && value.isShortDetailValue())
    Surface(
        modifier = modifier.testTag("vehicle_detail_field_$label"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        if (isInline) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.width(64.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = value,
                    modifier = if (displayMode == DetailFieldDisplayMode.Inline) {
                        Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    } else {
                        Modifier.weight(1f)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = if (displayMode == DetailFieldDisplayMode.Inline) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private data class DetailField(
    val label: String,
    val value: String,
    val displayMode: DetailFieldDisplayMode = DetailFieldDisplayMode.Auto,
)

private enum class DetailFieldDisplayMode {
    Auto,
    Inline,
    Stacked,
}

private fun String.isShortDetailValue(): Boolean {
    if (contains('\n')) return false

    val wideCharacterCount = count { character -> character.code > 0x7F }
    return if (wideCharacterCount == 0) length <= 12 else length <= 9
}

@Composable
private fun ResidentProfile.toFields(): List<DetailField> = listOfNotNull(
    DetailField(stringResource(R.string.detail_owner_name), ownerName.displayDetailValue()),
    DetailField(
        label = stringResource(R.string.detail_identity_card),
        value = identityCardNumber.displayDetailValue(),
        displayMode = DetailFieldDisplayMode.Inline,
    ),
    contactPhone?.let { DetailField(stringResource(R.string.detail_contact_phone), it) },
    remarks?.let { DetailField(stringResource(R.string.detail_remarks), it) },
)

private fun String?.displayDetailValue(): String = this?.takeIf(String::isNotBlank) ?: "未填写"

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
