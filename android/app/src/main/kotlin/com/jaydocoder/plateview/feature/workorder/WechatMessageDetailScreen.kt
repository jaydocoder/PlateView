package com.jaydocoder.plateview.feature.workorder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jaydocoder.plateview.domain.workorder.CachedWorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrderAttachment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.component.VehiclePlateBadge
import com.jaydocoder.plateview.component.glass.GlassSurface
import com.jaydocoder.plateview.component.rememberCurrentBeijingTime
import com.jaydocoder.plateview.domain.workorder.displayLabel
import com.jaydocoder.plateview.domain.workorder.resolveWechatMessagePassageState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WechatMessageDetailRoute(
    onNavigateUp: () -> Unit,
    viewModel: WechatMessageDetailViewModel = hiltViewModel(),
) {
    WechatMessageDetailScreen(viewModel.uiState.collectAsStateWithLifecycle().value, onNavigateUp, viewModel::refresh, viewModel::openAttachment, viewModel::loadOriginal, viewModel::closeAttachment)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WechatMessageDetailScreen(
    state: WechatMessageDetailUiState,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    onOpenAttachment: (WorkOrderAttachment) -> Unit,
    onLoadOriginal: () -> Unit,
    onCloseAttachment: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("微信聊天记录") },
                navigationIcon = { IconButton(onClick = onNavigateUp) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(state.error.message, color = MaterialTheme.colorScheme.error)
                androidx.compose.material3.TextButton(onClick = onRetry) { Text("重试") }
            }
            state.message != null -> {
                val message = state.message
                val passageState = resolveWechatMessagePassageState(message, rememberCurrentBeijingTime())
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(PlateViewDimensions.pageHorizontal, PlateViewDimensions.pageVertical),
                    verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
                ) {
                    item { MessagePanel("微信原始内容") { Text(message.rawContent, style = MaterialTheme.typography.bodyLarge) } }
                    item {
                        MessagePanel("微信来源") {
                            Text(message.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(message.sourceName, style = MaterialTheme.typography.bodyMedium)
                            Text(formatTime(message.sentAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (message.plateNumbers.isNotEmpty()) item {
                        MessagePanel("识别到的车牌") {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                message.plateNumbers.forEach { plate ->
                                    VehiclePlateBadge(plateNumber = plate)
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                        }
                    }
                    passageState?.let {
                        item {
                            MessagePanel("通行状态") {
                                Text(
                                    it.displayLabel(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (it.name == "VALID") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (message.attachments.isNotEmpty()) item {
                        MessagePanel("相关附件") {
                            message.attachments.forEach { attachment ->
                                val cached = state.attachmentFiles[attachment.id]
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onOpenAttachment(attachment) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (cached != null && attachment.kind != "PDF") {
                                        AsyncImage(
                                            model = cached.file,
                                            contentDescription = "微信图片缩略图",
                                            modifier = Modifier.width(72.dp).height(72.dp),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else if (cached != null && attachment.kind == "PDF" && cached.variant != "original") {
                                        AsyncImage(
                                            model = cached.file,
                                            contentDescription = "PDF首页缩略图",
                                            modifier = Modifier.width(72.dp).height(72.dp),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(if (attachment.kind == "PDF") Icons.Outlined.PictureAsPdf else Icons.Outlined.Image, contentDescription = null)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(attachment.fileName ?: if (attachment.kind == "PDF") "PDF 文件" else "微信图片")
                                        Text(
                                            when {
                                                attachment.kind == "PDF" -> "${attachment.pageCount ?: 1} 页 · 点击预览"
                                                cached != null -> "点击查看大图"
                                                attachment.availability != "AVAILABLE" -> "原图暂不可用，采集器将继续重试"
                                                else -> "正在加载图片预览"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(Icons.Outlined.AttachFile, contentDescription = "打开附件")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.selectedAttachment?.let { attachment ->
        val cached = state.attachmentFiles[attachment.id]
        androidx.compose.ui.window.Dialog(onDismissRequest = onCloseAttachment) {
            GlassSurface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(PlateViewDimensions.cornerLarge), elevated = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(attachment.fileName ?: "微信附件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (cached == null) {
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    } else if (attachment.kind == "PDF") {
                        PdfAttachmentPreview(cached, attachment.pageCount ?: 1)
                    } else {
                        AsyncImage(cached.file, "微信图片预览", Modifier.fillMaxWidth().height(360.dp), contentScale = ContentScale.Fit)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (cached?.variant != "original") TextButton(onClick = onLoadOriginal) { Text("查看原文件") }
                        TextButton(onClick = onCloseAttachment) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfAttachmentPreview(cached: CachedWorkOrderImage, pageCount: Int) {
    if (cached.variant != "original") {
        AsyncImage(
            model = cached.file,
            contentDescription = "PDF首页预览",
            modifier = Modifier.fillMaxWidth().height(360.dp),
            contentScale = ContentScale.Fit,
        )
        return
    }
    var page by remember(cached.file) { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PdfPageImage(cached.file, page)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("第 ${page + 1} / $pageCount 页", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(enabled = page > 0, onClick = { page-- }) { Text("上一页") }
                TextButton(enabled = page + 1 < pageCount, onClick = { page++ }) { Text("下一页") }
            }
        }
    }
}

@Composable
private fun PdfPageImage(file: java.io.File, page: Int) {
    var scale by remember(file, page) { mutableStateOf(1f) }
    var offsetX by remember(file, page) { mutableStateOf(0f) }
    var offsetY by remember(file, page) { mutableStateOf(0f) }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context -> android.widget.ImageView(context).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } },
        update = { view ->
            runCatching {
                android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                        if (page < renderer.pageCount) renderer.openPage(page).use { pdfPage ->
                            val bitmap = android.graphics.Bitmap.createBitmap(1100, 1500, android.graphics.Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            pdfPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            view.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
            .pointerInput(file, page) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
    )
}

@Composable
private fun MessagePanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(PlateViewDimensions.cornerLarge), elevated = true) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

private fun formatTime(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
}.getOrDefault(value)
