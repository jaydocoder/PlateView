package com.jaydocoder.plateview.feature.workorder

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.R
import com.jaydocoder.plateview.component.CompatFlowRow
import com.jaydocoder.plateview.component.VehiclePlateBadge
import com.jaydocoder.plateview.component.ZoomableAttachmentViewer
import com.jaydocoder.plateview.component.rememberCurrentBeijingTime
import com.jaydocoder.plateview.component.glass.LiquidGlassDialog
import com.jaydocoder.plateview.component.glass.GlassSurface
import com.jaydocoder.plateview.domain.workorder.WorkOrder
import com.jaydocoder.plateview.domain.workorder.WorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrderPassageState
import com.jaydocoder.plateview.domain.workorder.displayLabel
import com.jaydocoder.plateview.domain.workorder.extractWorkOrderPlateNumbers
import com.jaydocoder.plateview.domain.workorder.formatWorkOrderPeople
import com.jaydocoder.plateview.domain.workorder.resolveWorkOrderPassageState
import com.jaydocoder.plateview.domain.workorder.resolvedSenderName
import com.jaydocoder.plateview.domain.workorder.selectWorkOrderCandidatePlate
import com.jaydocoder.plateview.domain.workorder.selectWorkOrderDetailHeaderPlates
import com.jaydocoder.plateview.domain.vehicle.formatPlateForDisplay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WorkOrderDetailRoute(onNavigateUp: () -> Unit, viewModel: WorkOrderDetailViewModel = hiltViewModel()) {
    WorkOrderDetailScreen(
        uiState = viewModel.uiState.collectAsStateWithLifecycle().value,
        onNavigateUp = onNavigateUp,
        onRetry = viewModel::refresh,
        onOpenImage = viewModel::openImage,
        onLoadOriginal = viewModel::loadOriginal,
        onCloseImage = viewModel::closeImage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderDetailScreen(
    uiState: WorkOrderDetailUiState,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    onOpenImage: (WorkOrderImage) -> Unit,
    onLoadOriginal: () -> Unit,
    onCloseImage: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("微信车单", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateUp) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.error.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("重新加载") }
                }
                uiState.record != null -> WorkOrderContent(uiState, onOpenImage)
            }
        }
    }
    uiState.selectedImage?.let { selected ->
        val cached = uiState.imageFiles[selected.id]
        LiquidGlassDialog(onDismissRequest = onCloseImage) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("车单图片", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onCloseImage) { Icon(Icons.Outlined.Close, "关闭图片") }
                }
                ZoomableAttachmentViewer(
                    file = cached?.file,
                    kind = selected.kind,
                    variant = cached?.variant ?: "preview",
                    pageCount = selected.pageCount ?: 1,
                )
                if (cached?.variant != "original") {
                    TextButton(onClick = onLoadOriginal, modifier = Modifier.align(Alignment.End)) { Text("查看原图") }
                }
            }
        }
    }
}

@Composable
private fun WorkOrderContent(uiState: WorkOrderDetailUiState, onOpenImage: (WorkOrderImage) -> Unit) {
    val record = requireNotNull(uiState.record)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("work_order_detail_list"),
        contentPadding = PaddingValues(
            start = PlateViewDimensions.pageHorizontal,
            top = PlateViewDimensions.compactSpacing,
            end = PlateViewDimensions.pageHorizontal,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
    ) {
        item { WorkOrderHeader(record, uiState.sourceQuery) }
        item {
            WorkOrderSection("微信原始内容", Icons.Outlined.Description) {
                Text(
                    record.rawContent,
                    modifier = Modifier.testTag("work_order_raw_content"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        item {
            DetailPanel(
                title = "微信来源",
                icon = Icons.Outlined.Forum,
                fields = listOf(
                    "微信群" to record.sourceName,
                    "发送者" to record.resolvedSenderName(),
                    "发送时间" to formatBeijingTime(record.sentAt),
                ),
            )
        }
        item {
            PassageInformation(record)
        }
        if (record.people.isNotEmpty()) {
            item {
                WorkOrderSection("人员信息", Icons.Outlined.Person) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("work_order_people_text"),
                        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Text(
                            formatWorkOrderPeople(record.people).joinToString("\n"),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        item {
            DetailPanel(
                title = "事由与备注",
                icon = Icons.Outlined.Description,
                fields = listOf("事由" to record.reason, "备注" to record.remarks),
            )
        }
        if (record.images.isNotEmpty()) {
            item {
                WorkOrderSection("相关图片", Icons.Outlined.Image) {
                    record.images.forEach { image ->
                        val file = uiState.imageFiles[image.id]?.file
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clickable(enabled = file != null) { onOpenImage(image) }
                                .testTag("work_order_image_${image.id}"),
                            shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                        ) {
                            if (file != null) {
                                AsyncImage(file, "车单相关图片", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Image, "图片加载中", Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (uiState.history.size > 1) {
            item {
                WorkOrderSection("历史版本", Icons.Outlined.History) {
                    uiState.history.forEachIndexed { index, version ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(if (index == 0) "当前版本" else "历史版本 ${index + 1}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatBeijingTime(version.sentAt)} · ${if (version.status == "VOID") "已作废" else "有效记录"}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkOrderHeader(record: WorkOrder, sourceQuery: String) {
    val selectedPlate = selectWorkOrderCandidatePlate(
        rawPlate = record.rawPlate,
        orderNumber = record.orderNumber,
        query = sourceQuery,
        rawContent = record.rawContent,
    )
    val passageState = resolveWorkOrderPassageState(record, rememberCurrentBeijingTime(), selectedPlate)
    val plateNumbers = selectWorkOrderDetailHeaderPlates(
        rawPlate = record.rawPlate,
        rawContent = record.rawContent,
        orderNumber = record.orderNumber,
        query = sourceQuery,
    )
    GlassSurface(
        Modifier.fillMaxWidth().testTag("work_order_header"),
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
        elevated = true,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.8f)) {
            Image(
                painter = painterResource(R.drawable.vehicle_detail_mountain_road),
                contentDescription = "景区道路背景",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(14.dp)
                    .testTag("work_order_identity_glass_panel"),
                shape = RoundedCornerShape(24.dp),
                elevated = false,
                color = Color.White,
                opacity = 0.5f,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("微信车单 · 单号", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                record.orderNumber ?: "未识别",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Surface(
                            color = when (passageState) {
                                WorkOrderPassageState.VALID -> MaterialTheme.colorScheme.primaryContainer
                                WorkOrderPassageState.NOT_STARTED,
                                WorkOrderPassageState.OUTSIDE_ALLOWED_HOURS,
                                WorkOrderPassageState.UNKNOWN,
                                -> MaterialTheme.colorScheme.tertiaryContainer
                                WorkOrderPassageState.EXPIRED,
                                WorkOrderPassageState.VOID,
                                WorkOrderPassageState.AREA_MISMATCH,
                                -> MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = when (passageState) {
                                WorkOrderPassageState.VALID -> MaterialTheme.colorScheme.onPrimaryContainer
                                WorkOrderPassageState.NOT_STARTED,
                                WorkOrderPassageState.OUTSIDE_ALLOWED_HOURS,
                                WorkOrderPassageState.UNKNOWN,
                                -> MaterialTheme.colorScheme.onTertiaryContainer
                                WorkOrderPassageState.EXPIRED,
                                WorkOrderPassageState.VOID,
                                WorkOrderPassageState.AREA_MISMATCH,
                                -> MaterialTheme.colorScheme.onErrorContainer
                            },
                            shape = RoundedCornerShape(PlateViewDimensions.cornerSmall),
                        ) {
                            Text(
                                passageState.displayLabel(),
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    if (plateNumbers.isNotEmpty()) {
                        WorkOrderPlateRow(
                            plateNumbers = plateNumbers,
                            emphasized = plateNumbers.size == 1,
                            modifier = Modifier.testTag("work_order_header_plate_row"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassageInformation(record: WorkOrder) {
    val plateNumbers = extractWorkOrderPlateNumbers(record.rawPlate, record.rawContent)
    val compactFields = listOf(
        "车型" to record.vehicleType,
        "人数" to record.declaredPeople?.let { "${it}人" },
        "时间" to record.rawValidTime,
        "地点" to record.location,
        "方式" to record.verificationMethod,
    ).filter { !it.second.isNullOrBlank() }
    WorkOrderSection("通行信息", Icons.Outlined.DirectionsCar) {
        if (plateNumbers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("车牌", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = plateNumbers.joinToString("、", transform = ::formatPlateForDisplay),
                    modifier = Modifier.fillMaxWidth().testTag("work_order_passage_plate_text"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        compactFields.chunked(2).forEach { rowFields ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowFields.forEach { (label, value) ->
                    WorkOrderField(label, value.orEmpty(), Modifier.weight(1f))
                }
                if (rowFields.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WorkOrderPlateRow(
    plateNumbers: List<String>,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    CompatFlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        plateNumbers.forEach { plate ->
            VehiclePlateBadge(plateNumber = plate, emphasized = emphasized, compact = !emphasized)
        }
    }
}

@Composable
private fun DetailPanel(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    fields: List<Pair<String, String?>>,
) {
    val visible = fields.filter { !it.second.isNullOrBlank() }
    if (visible.isEmpty()) return
    WorkOrderSection(title, icon) {
        visible.forEach { (label, value) ->
            WorkOrderField(label, value.orEmpty())
        }
    }
}

@Composable
private fun WorkOrderSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(PlateViewDimensions.cornerLarge), elevated = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun WorkOrderField(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("work_order_field_$label"),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatBeijingTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.parse(value))
}.getOrDefault(value)
