package com.jaydocoder.plateview.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AuthenticatedScreen(session: AuthSession, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("已登录：${session.username}", style = MaterialTheme.typography.headlineSmall)
        Text(if (session.role == "ADMIN") "管理员工作台准备中" else "车辆查询准备中", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
    }
}
