package com.jaydocoder.plateview.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.BuildConfig
import com.jaydocoder.plateview.domain.update.AppUpdate

@Composable
fun AppUpdateDialog(
    update: AppUpdate,
    downloadState: UpdateDownloadState,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDownloading = downloadState is UpdateDownloadState.Downloading
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        shape = MaterialTheme.shapes.large,
        icon = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        title = { Text("发现新版本", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PlateView", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "v${update.versionName}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Text(
                    text = "当前版本 v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "最新版本 v${update.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (update.releaseNotes.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = update.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                UpdateDownloadStatus(downloadState)
            }
        },
        confirmButton = {
            when (downloadState) {
                UpdateDownloadState.Idle,
                is UpdateDownloadState.Failed,
                -> Button(onClick = onDownload) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (downloadState is UpdateDownloadState.Failed) "重新下载" else "立即更新")
                }

                is UpdateDownloadState.Downloading -> Button(onClick = {}, enabled = false) {
                    Text("正在下载")
                }

                is UpdateDownloadState.ReadyToInstall -> Button(onClick = { onInstall(downloadState.apkFile) }) {
                    Text("立即安装")
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) { Text("稍后处理") }
            }
        },
    )
}

@Composable
fun UpdateCheckDialog(
    state: ManualUpdateCheckState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isChecking = state is ManualUpdateCheckState.Checking
    val (icon, title, message) = when (state) {
        ManualUpdateCheckState.Idle -> Triple(Icons.Outlined.SystemUpdate, "系统更新", "请检查应用是否有新版本。")
        ManualUpdateCheckState.Checking -> Triple(Icons.Outlined.SystemUpdate, "正在检查更新", "正在获取最新版本信息，请稍候。")
        ManualUpdateCheckState.Latest -> Triple(Icons.Outlined.TaskAlt, "已是最新版本", "当前版本 v${BuildConfig.VERSION_NAME} 已是最新版本。")
        is ManualUpdateCheckState.Failed -> Triple(Icons.Outlined.ErrorOutline, "检查更新失败", state.message)
    }
    AlertDialog(
        onDismissRequest = { if (!isChecking) onDismiss() },
        shape = MaterialTheme.shapes.large,
        icon = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = when (state) {
                    is ManualUpdateCheckState.Failed -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = when (state) {
                        is ManualUpdateCheckState.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing)) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isChecking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (state is ManualUpdateCheckState.Failed) {
                Button(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重新检查")
                }
            }
        },
        dismissButton = {
            if (!isChecking) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun UpdateDownloadStatus(downloadState: UpdateDownloadState) {
    when (downloadState) {
        UpdateDownloadState.Idle -> Text(
            text = "下载完成后将由系统确认安装。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is UpdateDownloadState.Downloading -> {
            val fraction = downloadState.progress.fraction
            val statusText = fraction?.let { "正在下载 ${(it * 100).toInt()}%" } ?: "正在下载更新包"
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            LinearProgressIndicator(
                progress = { fraction ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .semantics { stateDescription = statusText },
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        }

        is UpdateDownloadState.ReadyToInstall -> Text(
            text = "安装包已准备完成。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )

        is UpdateDownloadState.Failed -> Text(
            text = downloadState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
