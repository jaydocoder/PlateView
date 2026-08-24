package com.jaydocoder.plateview.feature.profile

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.R
import com.jaydocoder.plateview.feature.auth.AvatarCacheEntry
import java.io.File

@Composable
fun ProfileRoute(
    onNavigateUp: () -> Unit,
    onOpenAdmin: (() -> Unit)?,
    onCheckForUpdate: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    ProfileScreen(
        state = state,
        onNavigateUp = onNavigateUp,
        onOpenAdmin = onOpenAdmin,
        onCheckForUpdate = onCheckForUpdate,
        onLogout = onLogout,
        onUploadAvatar = viewModel::uploadAvatar,
        onDeleteAvatar = viewModel::deleteAvatar,
        onSaveProfile = viewModel::updateProfile,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    state: ProfileUiState,
    onNavigateUp: () -> Unit,
    onOpenAdmin: (() -> Unit)?,
    onCheckForUpdate: () -> Unit,
    onLogout: () -> Unit,
    onUploadAvatar: (Uri) -> Unit,
    onDeleteAvatar: () -> Unit,
    onSaveProfile: (String, String?, String?) -> Unit,
) {
    val context = LocalContext.current
    var showAccountSettings by remember { mutableStateOf(false) }
    var accountAction by remember { mutableStateOf<ProfileAccountAction?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onUploadAvatar) }

    if (showAccountSettings) {
        AccountSettingsScreen(
            username = state.username,
            onNavigateUp = { showAccountSettings = false },
            onSave = { username, currentPassword, password ->
                onSaveProfile(username, currentPassword, password)
                showAccountSettings = false
            },
        )
        return
    }

    accountAction?.let { action ->
        AccountActionDialog(
            action = action,
            onDismiss = { accountAction = null },
            onConfirm = {
                accountAction = null
                onLogout()
            },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("我的") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                ProfileIdentityHeader(
                    state = state,
                    onChooseAvatar = { picker.launch(SUPPORTED_MIME_TYPES) },
                    onDeleteAvatar = onDeleteAvatar,
                    onEditAccount = { showAccountSettings = true },
                )
            }
            item {
                ProfileSection(title = "账号资料") {
                    ProfileActionRow(
                        title = "账号与安全",
                        subtitle = "修改用户名或登录密码",
                        icon = Icons.Outlined.Person,
                        onClick = { showAccountSettings = true },
                    )
                }
            }
            onOpenAdmin?.let { openAdmin ->
                item {
                    ProfileSection(title = "管理") {
                        ProfileActionRow(
                            title = "管理工作台",
                            subtitle = "车辆档案、导入任务与操作审计",
                            icon = Icons.Outlined.AdminPanelSettings,
                            onClick = openAdmin,
                        )
                    }
                }
            }
            item {
                ProfileSection(title = "应用") {
                    ProfileActionRow(
                        title = "系统更新",
                        subtitle = "检查并下载最新版本",
                        icon = Icons.Outlined.SystemUpdate,
                        onClick = onCheckForUpdate,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ProfileActionRow(
                        title = "项目源码",
                        subtitle = "在浏览器中打开 GitHub 项目",
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        onClick = {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/jaydocoder/PlateView"),
                                ),
                            )
                        },
                    )
                }
            }
            item {
                ProfileSection(title = "账户操作") {
                    ProfileActionRow(
                        title = "切换账号",
                        subtitle = "使用另一账号进入车辆核验",
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        testTag = "profile_switch_account_action",
                        onClick = { accountAction = ProfileAccountAction.Switch },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ProfileActionRow(
                        title = "退出登录",
                        subtitle = "退出当前账号并回到登录页",
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        testTag = "profile_logout_action",
                        destructive = true,
                        onClick = { accountAction = ProfileAccountAction.Logout },
                    )
                }
            }
            state.error?.let { error ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileIdentityHeader(
    state: ProfileUiState,
    onChooseAvatar: () -> Unit,
    onDeleteAvatar: () -> Unit,
    onEditAccount: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(82.dp)) {
                AvatarImage(
                    entry = state.avatar,
                    modifier = Modifier
                        .size(76.dp)
                        .align(Alignment.TopStart)
                        .clickable(onClick = onChooseAvatar),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(48.dp)
                        .clickable(onClick = onChooseAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        shadowElevation = 2.dp,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = "更换头像",
                            modifier = Modifier.padding(7.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("当前账号", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f))
                Text(state.username, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                AssistChip(
                    onClick = {},
                    label = { Text(state.roleLabel) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = null,
                )
                if (state.avatar.file != null) {
                    TextButton(onClick = onDeleteAvatar, contentPadding = PaddingValues(0.dp)) {
                        Text("移除头像")
                    }
                }
            }
            IconButton(onClick = onEditAccount) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑账号资料", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ProfileActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String? = null,
    destructive: Boolean = false,
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = contentColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
    }
}

private enum class ProfileAccountAction {
    Switch,
    Logout,
}

@Composable
private fun AccountActionDialog(
    action: ProfileAccountAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isLogout = action == ProfileAccountAction.Logout
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isLogout) "退出当前账号？" else "切换账号？") },
        text = {
            Text(
                if (isLogout) {
                    "退出后需要重新登录才能继续车辆核验。"
                } else {
                    "切换后将回到登录页，可使用另一账号进入。"
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(if (isLogout) "profile_confirm_logout" else "profile_confirm_switch"),
            ) {
                Text(if (isLogout) "退出登录" else "前往登录")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private enum class AccountEditTarget {
    Username,
    Password,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSettingsScreen(
    username: String,
    onNavigateUp: () -> Unit,
    onSave: (String, String?, String?) -> Unit,
) {
    var target by remember { mutableStateOf<AccountEditTarget?>(null) }
    target?.let { currentTarget ->
        AccountValueEditorScreen(
            target = currentTarget,
            username = username,
            onNavigateUp = { target = null },
            onSave = onSave,
        )
        return
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("账号资料") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回我的页面")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("账号与安全", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("分别管理登录名称和密码，保存后该账号会退出登录。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f))
                    }
                }
            }
            item {
                ProfileSection(title = "登录信息") {
                    ProfileActionRow(
                        title = "用户名",
                        subtitle = username,
                        icon = Icons.Outlined.Person,
                        onClick = { target = AccountEditTarget.Username },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ProfileActionRow(
                        title = "登录密码",
                        subtitle = "修改时需要验证当前密码",
                        icon = Icons.Outlined.Key,
                        onClick = { target = AccountEditTarget.Password },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountValueEditorScreen(
    target: AccountEditTarget,
    username: String,
    onNavigateUp: () -> Unit,
    onSave: (String, String?, String?) -> Unit,
) {
    var editedUsername by remember(username) { mutableStateOf(username) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    val isUsername = target == AccountEditTarget.Username
    val canSave = if (isUsername) editedUsername.isNotBlank() else currentPassword.isNotBlank() && newPassword.isNotBlank()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isUsername) "修改用户名" else "修改密码") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回账号资料")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 5.dp,
            ) {
                Button(
                    onClick = {
                        if (isUsername) {
                            onSave(editedUsername.trim(), null, null)
                        } else {
                            onSave(username, currentPassword, newPassword)
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .testTag("profile_save"),
                ) {
                    Text("保存并重新登录")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (isUsername) "设置新的登录名称" else "设置新的登录密码",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (isUsername) "保存后会退出当前账号，请使用新用户名重新登录。" else "先验证当前密码，保存后请使用新密码重新登录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                        )
                    }
                }
            }
            if (isUsername) {
                item {
                    ProfileEditorField(label = "新用户名") {
                        OutlinedTextField(
                            value = editedUsername,
                            onValueChange = { editedUsername = it },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = profileEditorTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                item {
                    ProfileEditorField(label = "当前密码") {
                        PasswordInput(value = currentPassword, onValueChange = { currentPassword = it })
                    }
                }
                item {
                    ProfileEditorField(label = "新密码") {
                        PasswordInput(value = newPassword, onValueChange = { newPassword = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun PasswordInput(value: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "隐藏密码" else "显示密码",
                )
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = profileEditorTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun profileEditorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
fun AvatarImage(entry: AvatarCacheEntry, modifier: Modifier = Modifier) {
    val file = entry.file
    if (file == null) {
        Image(
            painter = painterResource(R.drawable.ic_plateview_launcher_foreground),
            contentDescription = "用户头像",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(CircleShape),
        )
    } else {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageDrawable(ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)))
                }
            },
            update = { imageView -> imageView.setImageDrawable(ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))) },
            modifier = modifier.clip(CircleShape),
        )
    }
}

private val SUPPORTED_MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")
