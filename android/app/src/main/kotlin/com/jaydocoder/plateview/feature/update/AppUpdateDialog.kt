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
import androidx.compose.ui.window.DialogProperties
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.BuildConfig
import com.jaydocoder.plateview.component.glass.GlassSurface
import com.jaydocoder.plateview.component.glass.LiquidGlassDialog
import com.jaydocoder.plateview.domain.update.AppUpdate

@Composable
fun AppUpdateDialog(
    update: AppUpdate,
    downloadState: UpdateDownloadState,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
    onDismiss: () -> Unit,
    forceUpdate: Boolean = false,
) {
    val isDownloading = downloadState is UpdateDownloadState.Downloading
    LiquidGlassDialog(
        onDismissRequest = { if (!isDownloading && !forceUpdate) onDismiss() },
        properties = if (forceUpdate) {
            DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        } else {
            DialogProperties()
        },
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
        ) {
            GlassSurface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                elevated = true,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(if (forceUpdate) "需要更新应用" else "发现新版本", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PlateView", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        text = "v${update.versionName}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Text("当前版本 v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("最新版本 v${update.versionName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (update.releaseNotes.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(update.releaseNotes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
            UpdateDownloadStatus(downloadState)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!isDownloading && !forceUpdate) TextButton(onClick = onDismiss) { Text("稍后处理") }
            when (downloadState) {
                UpdateDownloadState.Idle,
                is UpdateDownloadState.Failed,
                -> Button(onClick = onDownload) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (downloadState is UpdateDownloadState.Failed) "重新下载" else "在线更新")
                }

                is UpdateDownloadState.Downloading -> Button(onClick = {}, enabled = false) {
                    Text("正在下载")
                }

                is UpdateDownloadState.ReadyToInstall -> Button(onClick = { onInstall(downloadState.apkFile) }) {
                    Text("立即安装")
                }
            }
            }
        }
    }
}

@Composable
fun ForceUpdateUnavailableDialog(onRetry: () -> Unit) {
    LiquidGlassDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
        ) {
            GlassSurface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                elevated = true,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Text("无法确认强制更新", style = MaterialTheme.typography.titleLarge)
            Text(
                "当前账号需要更新后才能继续使用。请连接网络后重新检查。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重新检查")
                }
            }
        }
    }
}

@Composable
fun UpdateCheckDialog(
    state: ManualUpdateCheckState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isChecking = state is ManualUpdateCheckState.Checking
    val (icon, title, message) = when (state) {
        ManualUpdateCheckState.Idle -> Triple(Icons.Outlined.SystemUpdate, "软件更新", "请检查应用是否有新版本。")
        ManualUpdateCheckState.Checking -> Triple(Icons.Outlined.SystemUpdate, "正在检查更新", "正在获取最新版本信息，请稍候。")
        ManualUpdateCheckState.Latest -> Triple(Icons.Outlined.TaskAlt, "已是最新版本", "当前版本 v${BuildConfig.VERSION_NAME} 已是最新版本。")
        is ManualUpdateCheckState.Failed -> Triple(Icons.Outlined.ErrorOutline, "检查更新失败", state.message)
    }
    LiquidGlassDialog(onDismissRequest = { if (!isChecking) onDismiss() }) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
        ) {
            GlassSurface(
                shape = MaterialTheme.shapes.medium,
                color = when (state) {
                    is ManualUpdateCheckState.Failed -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                elevated = true,
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
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isChecking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!isChecking) TextButton(onClick = onDismiss) { Text("关闭") }
                if (state is ManualUpdateCheckState.Failed) {
                    Button(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("重新检查")
                    }
                }
            }
        }
    }
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
