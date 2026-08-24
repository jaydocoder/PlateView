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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SystemUpdateAlt
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
import androidx.compose.ui.graphics.Color
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
    var showLogoutConfirmation by remember { mutableStateOf(false) }
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

    if (showLogoutConfirmation) {
        LogoutConfirmationDialog(
            onDismiss = { showLogoutConfirmation = false },
            onConfirm = {
                showLogoutConfirmation = false
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column {
                    ProfileActionRow(
                        title = "账号与安全",
                        icon = Icons.Outlined.ManageAccounts,
                        tone = ProfileActionTone.Primary,
                        onClick = { showAccountSettings = true },
                    )
                    onOpenAdmin?.let { openAdmin ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ProfileActionRow(
                            title = "管理工作台",
                            icon = Icons.Outlined.DashboardCustomize,
                            tone = ProfileActionTone.Secondary,
                            onClick = openAdmin,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ProfileActionRow(
                        title = "系统更新",
                        icon = Icons.Outlined.SystemUpdateAlt,
                        tone = ProfileActionTone.Primary,
                        onClick = onCheckForUpdate,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ProfileActionRow(
                        title = "项目源码",
                        icon = Icons.Outlined.Code,
                        tone = ProfileActionTone.Tertiary,
                        onClick = {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/jaydocoder/PlateView"),
                                ),
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ProfileActionRow(
                        title = "退出登录",
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        testTag = "profile_logout_action",
                        destructive = true,
                        onClick = { showLogoutConfirmation = true },
                    )
                    }
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
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 1.dp,
    ) {
        Box {
            Box(
                modifier = Modifier.matchParentSize(),
            ) {
                Image(
                    painter = painterResource(R.drawable.profile_header_mountain_road),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.14f)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 14.dp, top = 38.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(88.dp)) {
                AvatarImage(
                    entry = state.avatar,
                    modifier = Modifier
                        .size(82.dp)
                        .align(Alignment.TopStart)
                        .clickable(onClick = onChooseAvatar),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(44.dp)
                        .clickable(onClick = onChooseAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(30.dp),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        shadowElevation = 2.dp,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = "更换头像",
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(state.username, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    AssistChip(
                        onClick = {},
                        label = { Text(state.roleLabel) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        border = null,
                    )
                    if (state.avatar.file != null) {
                        TextButton(onClick = onDeleteAvatar, contentPadding = PaddingValues(0.dp)) {
                            Text("移除头像")
                        }
                    }
                }
                Surface(
                    onClick = onEditAccount,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "编辑账号资料",
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileActionRow(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tone: ProfileActionTone = ProfileActionTone.Neutral,
    testTag: String? = null,
    destructive: Boolean = false,
) {
    val palette = profileActionPalette(tone, destructive)
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = palette.container,
            contentColor = palette.content,
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(11.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = contentColor)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
    }
}

private enum class ProfileActionTone {
    Primary,
    Secondary,
    Tertiary,
    Neutral,
}

private data class ProfileActionPalette(
    val container: Color,
    val content: Color,
)

@Composable
private fun profileActionPalette(tone: ProfileActionTone, destructive: Boolean): ProfileActionPalette {
    if (destructive) {
        return ProfileActionPalette(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
    }
    return when (tone) {
        ProfileActionTone.Primary -> ProfileActionPalette(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        ProfileActionTone.Secondary -> ProfileActionPalette(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        ProfileActionTone.Tertiary -> ProfileActionPalette(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        ProfileActionTone.Neutral -> ProfileActionPalette(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LogoutConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出当前账号？") },
        text = { Text("退出后需要重新登录才能继续车辆核验。") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("profile_confirm_logout"),
            ) {
                Text("退出登录")
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column {
                    ProfileActionRow(
                        title = "用户名",
                        icon = Icons.Outlined.Person,
                        onClick = { target = AccountEditTarget.Username },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ProfileActionRow(
                        title = "登录密码",
                        icon = Icons.Outlined.Key,
                        onClick = { target = AccountEditTarget.Password },
                    )
                    }
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
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
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
