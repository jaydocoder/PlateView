package com.jaydocoder.plateview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.feature.auth.AppSessionViewModel
import com.jaydocoder.plateview.feature.auth.AuthenticatedScreen
import androidx.compose.ui.Modifier
import com.jaydocoder.plateview.feature.auth.LoginScreen

@Composable
fun PlateViewApp() {
    val viewModel: AppSessionViewModel = hiltViewModel()
    val session = viewModel.session.collectAsStateWithLifecycle().value
    PlateViewTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (session == null) LoginScreen() else AuthenticatedScreen(session, viewModel::logout)
        }
    }
}
