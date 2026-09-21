package com.jaydocoder.plateview.feature.workorder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import com.jaydocoder.plateview.component.AttachmentThumbnail
import com.jaydocoder.plateview.domain.workorder.WorkOrderAttachment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.component.VehiclePlateBadge
import com.jaydocoder.plateview.component.ZoomableAttachmentViewer
import com.jaydocoder.plateview.component.glass.GlassSurface
import com.jaydocoder.plateview.component.rememberCurrentBeijingTime
import com.jaydocoder.plateview.domain.workorder.displayLabel
import com.jaydocoder.plateview.domain.workorder.hasAttachmentPlaceholderContent
import com.jaydocoder.plateview.domain.workorder.resolveWechatMessagePassageState
import com.jaydocoder.plateview.domain.workorder.resolvedSenderName
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
internal fun WechatMessageDetailScreen(
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
                    item {
                        MessagePanel("微信原始内容") {
                            if (message.hasAttachmentPlaceholderContent()) {
                                AttachmentPreviewList(
                                    attachments = message.attachments,
                                    files = state.attachmentFiles,
                                    prominent = true,
                                    onOpenAttachment = onOpenAttachment,
                                )
                            } else {
                                Text(message.rawContent, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    item {
                        MessagePanel("微信来源") {
                            Text("发送者：${message.resolvedSenderName()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("微信群：${message.sourceName}", style = MaterialTheme.typography.bodyMedium)
                            Text("发送时间：${formatTime(message.sentAt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            AttachmentPreviewList(
                                attachments = message.attachments,
                                files = state.attachmentFiles,
                                prominent = false,
                                onOpenAttachment = onOpenAttachment,
                            )
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
                    } else {
                        ZoomableAttachmentViewer(
                            file = cached.file,
                            kind = attachment.kind,
                            variant = cached.variant,
                            pageCount = attachment.pageCount ?: 1,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCloseAttachment) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewList(
    attachments: List<WorkOrderAttachment>,
    files: Map<Long, com.jaydocoder.plateview.domain.workorder.CachedWorkOrderImage>,
    prominent: Boolean,
    onOpenAttachment: (WorkOrderAttachment) -> Unit,
) {
    attachments.forEach { attachment ->
        val cached = files[attachment.id]
        Column(
            modifier = Modifier.fillMaxWidth().clickable { onOpenAttachment(attachment) },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (attachment.availability == "AVAILABLE") {
                AttachmentThumbnail(
                    file = cached?.file,
                    kind = attachment.kind,
                    variant = cached?.variant ?: "original",
                    contentDescription = if (attachment.kind == "PDF") "PDF首页缩略图" else "微信图片缩略图",
                    modifier = Modifier.fillMaxWidth().height(if (prominent) 220.dp else 150.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (attachment.kind == "PDF") Icons.Outlined.PictureAsPdf else Icons.Outlined.Image,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(attachment.fileName ?: if (attachment.kind == "PDF") "PDF 文件" else "原始微信图片")
                    Text(
                        when {
                            attachment.kind == "PDF" && cached?.variant == "original" -> "${attachment.pageCount ?: 1} 页 · 原文件已缓存"
                            attachment.kind == "PDF" -> "正在缓存原 PDF 文件"
                            cached?.variant == "original" -> "原始微信图片已缓存"
                            attachment.availability != "AVAILABLE" -> "原图暂不可用，采集器将继续重试"
                            else -> "正在缓存原始微信图片"
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
