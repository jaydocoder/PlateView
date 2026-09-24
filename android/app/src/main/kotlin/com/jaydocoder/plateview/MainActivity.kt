package com.jaydocoder.plateview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import com.jaydocoder.plateview.feature.update.AppUpdateViewModel
import com.jaydocoder.plateview.feature.auth.AppSessionViewModel
import com.jaydocoder.plateview.data.workorder.WechatAttachmentCacheSyncScheduler
import java.io.File
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appUpdateViewModel: AppUpdateViewModel by viewModels()
    private val appSessionViewModel: AppSessionViewModel by viewModels()
    @Inject lateinit var attachmentCacheSyncScheduler: WechatAttachmentCacheSyncScheduler
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var networkWasLost: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        attachmentCacheSyncScheduler.scheduleImmediate()
    }

    override fun onStart() {
        super.onStart()
        appSessionViewModel.setForeground(true)
        registerNetworkRecoveryCallback()
    }

    override fun onStop() {
        unregisterNetworkRecoveryCallback()
        appSessionViewModel.setForeground(false)
        super.onStop()
    }

    private fun registerNetworkRecoveryCallback() {
        if (networkCallback != null) return
        val manager = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (networkWasLost) {
                    networkWasLost = false
                    appSessionViewModel.onNetworkAvailable()
                }
            }

            override fun onLost(network: Network) {
                networkWasLost = true
            }
        }
        manager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
        networkCallback = callback
    }

    private fun unregisterNetworkRecoveryCallback() {
        val callback = networkCallback ?: return
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback) }
        networkCallback = null
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
