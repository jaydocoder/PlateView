package com.jaydocoder.plateview.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaydocoder.plateview.component.glass.LiquidGlassDialog
import java.io.File

@Composable
fun AttachmentViewerDialog(
    title: String,
    file: File?,
    kind: String,
    variant: String,
    pageCount: Int,
    failureMessage: String? = null,
    progress: Float? = null,
    statusText: String? = null,
    onRetry: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val viewportHeight = (screenHeight * if (kind == "PDF") 0.48f else 0.56f).coerceIn(240.dp, 420.dp)
    LiquidGlassDialog(onDismissRequest = onDismissRequest) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).testTag("attachment_viewer_dialog"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭附件预览")
                }
            }
            if (failureMessage != null) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(failureMessage, color = MaterialTheme.colorScheme.error)
                    onRetry?.let { retry -> TextButton(onClick = retry) { Text("重新加载") } }
                }
            } else if (file == null) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(statusText ?: "正在准备原文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                ZoomableAttachmentViewer(
                    file = file,
                    kind = kind,
                    variant = variant,
                    pageCount = pageCount,
                    viewportHeight = viewportHeight,
                )
            }
        }
    }
}
