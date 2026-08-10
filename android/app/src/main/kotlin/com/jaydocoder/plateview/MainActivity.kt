package com.jaydocoder.plateview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import com.jaydocoder.plateview.feature.update.AppUpdateViewModel
import java.io.File
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appUpdateViewModel: AppUpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlateViewApp(
                updateViewModel = appUpdateViewModel,
                onInstallUpdate = ::installUpdate,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdateViewModel.checkForUpdate()
    }

    private fun installUpdate(apkFile: File) {
        if (!packageManager.canRequestPackageInstalls()) {
            appUpdateViewModel.reportInstallationFailure("请允许 PlateView 安装来自此来源的应用后重试")
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        runCatching {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure {
            appUpdateViewModel.reportInstallationFailure("无法打开系统安装界面，请重新下载后再试")
        }
    }
}
