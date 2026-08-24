package com.jaydocoder.plateview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.core.navigation.AuthenticatedNavigation
import com.jaydocoder.plateview.feature.auth.AppSessionViewModel
import com.jaydocoder.plateview.feature.auth.LoginScreen
import com.jaydocoder.plateview.feature.update.AppUpdateDialog
import com.jaydocoder.plateview.feature.update.AppUpdateViewModel
import com.jaydocoder.plateview.feature.update.UpdateCheckDialog
import com.jaydocoder.plateview.feature.update.UpdateAvailableAction
import java.io.File

@Composable
fun PlateViewApp(
    updateViewModel: AppUpdateViewModel = hiltViewModel(),
    onInstallUpdate: (File) -> Unit = {},
) {
    val viewModel: AppSessionViewModel = hiltViewModel()
    val session = viewModel.session.collectAsStateWithLifecycle().value
    val updateState = updateViewModel.uiState.collectAsStateWithLifecycle().value
    PlateViewTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (session == null) {
                    LoginScreen()
                    if (updateState.update != null) {
                        UpdateAvailableAction(
                            onClick = updateViewModel::openUpdateDialog,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(PlateViewDimensions.compactSpacing),
                        )
                    }
                } else {
                    AuthenticatedNavigation(
                        role = session.role,
                        onLogout = viewModel::logout,
                        onOpenUpdate = updateState.update?.let { updateViewModel::openUpdateDialog },
                        onCheckForUpdate = updateViewModel::checkForUpdateFromUser,
                    )
                }
            }
        }
        updateState.update?.takeIf { updateState.isUpdateDialogVisible }?.let { update ->
            AppUpdateDialog(
                update = update,
                downloadState = updateState.downloadState,
                onDownload = updateViewModel::downloadUpdate,
                onInstall = onInstallUpdate,
                onDismiss = updateViewModel::dismissUpdateDialog,
            )
        }
        if (updateState.isManualCheckDialogVisible) {
            UpdateCheckDialog(
                state = updateState.manualCheckState,
                onRetry = updateViewModel::checkForUpdateFromUser,
                onDismiss = updateViewModel::dismissManualCheckDialog,
            )
        }
    }
}
