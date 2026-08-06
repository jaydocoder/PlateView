package com.jaydocoder.plateview.feature.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.domain.admin.ManagedAuditEntry
import com.jaydocoder.plateview.domain.admin.ManagedImportBatch
import com.jaydocoder.plateview.domain.admin.ManagedImportBatchSummary
import com.jaydocoder.plateview.domain.admin.ManagedImportRow
import com.jaydocoder.plateview.domain.admin.ManagedUser
import com.jaydocoder.plateview.domain.admin.ManagedVehicleSummary

@Composable
fun AdminWorkspaceRoute(
    onNavigateUp: () -> Unit,
    viewModel: AdminWorkspaceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::uploadImport) },
    )
    AdminWorkspaceScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onTabSelected = viewModel::selectTab,
        onRefresh = viewModel::refresh,
        onCreateVehicle = viewModel::createVehicle,
        onEditVehicle = viewModel::editVehicle,
        onVehicleEditorChanged = viewModel::updateVehicleEditor,
        onDismissVehicleEditor = viewModel::dismissVehicleEditor,
        onSaveVehicle = viewModel::saveVehicle,
        onDeactivateVehicle = viewModel::requestVehicleDeactivation,
        onDismissVehicleDeactivation = viewModel::dismissVehicleDeactivation,
        onConfirmVehicleDeactivation = viewModel::confirmVehicleDeactivation,
        onCreateUser = viewModel::createUser,
        onEditUser = viewModel::editUser,
        onUserEditorChanged = viewModel::updateUserEditor,
        onDismissUserEditor = viewModel::dismissUserEditor,
        onSaveUser = viewModel::saveUser,
        onChooseImport = { documentPicker.launch(arrayOf(EXCEL_MIME_TYPE, LEGACY_EXCEL_MIME_TYPE)) },
        onOpenImportBatch = viewModel::openImportBatch,
        onDismissImportBatch = viewModel::dismissImportBatch,
        onImportResolution = viewModel::updateImportResolution,
        onPublishImport = viewModel::publishImport,
        onRollbackImport = viewModel::rollbackImport,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminWorkspaceScreen(
    uiState: AdminUiState,
    onNavigateUp: () -> Unit,
    onTabSelected: (AdminTab) -> Unit,
    onRefresh: () -> Unit,
    onCreateVehicle: () -> Unit,
    onEditVehicle: (Long) -> Unit,
    onVehicleEditorChanged: ((VehicleEditorState) -> VehicleEditorState) -> Unit,
    onDismissVehicleEditor: () -> Unit,
    onSaveVehicle: () -> Unit,
    onDeactivateVehicle: (ManagedVehicleSummary) -> Unit,
    onDismissVehicleDeactivation: () -> Unit,
    onConfirmVehicleDeactivation: () -> Unit,
    onCreateUser: () -> Unit,
    onEditUser: (Long) -> Unit,
    onUserEditorChanged: ((UserEditorState) -> UserEditorState) -> Unit,
    onDismissUserEditor: () -> Unit,
    onSaveUser: () -> Unit,
    onChooseImport: () -> Unit,
    onOpenImportBatch: (Long) -> Unit,
    onDismissImportBatch: () -> Unit,
    onImportResolution: (Long, String) -> Unit,
    onPublishImport: () -> Unit,
    onRollbackImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("管理员工作台", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回车辆查询")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !uiState.isLoading && !uiState.isSaving) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新当前页面")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
            ScrollableTabRow(
                selectedTabIndex = uiState.tab.ordinal,
                edgePadding = PlateViewDimensions.pageHorizontal,
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    Box(
                        Modifier
                            .padding(horizontal = 12.dp)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    )
                }
            ) {
                AdminTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == uiState.tab,
                        onClick = { onTabSelected(tab) },
                        text = { 
                            Text(
                                tab.label(), 
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (tab == uiState.tab) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        unselectedContentColor = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            uiState.failure?.let { AdminFailureStrip(it) }
            
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading) {
                    LoadingPane()
                } else {
                    when (uiState.tab) {
                        AdminTab.Dashboard -> DashboardPane(
                            vehiclesCount = uiState.vehicles.size,
                            usersCount = uiState.users.size,
                            importsCount = uiState.importBatches.size,
                            onTabSelected = onTabSelected,
                        )

                        AdminTab.Vehicles -> VehiclesPane(
                            items = uiState.vehicles,
                            isSaving = uiState.isSaving,
                            onCreate = onCreateVehicle,
                            onEdit = onEditVehicle,
                            onDeactivate = onDeactivateVehicle,
                        )

                        AdminTab.Users -> UsersPane(
                            items = uiState.users,
                            isSaving = uiState.isSaving,
                            onCreate = onCreateUser,
                            onEdit = onEditUser,
                        )

                        AdminTab.Imports -> ImportsPane(
                            items = uiState.importBatches,
                            isSaving = uiState.isSaving,
                            onChooseImport = onChooseImport,
                            onOpenBatch = onOpenImportBatch,
                        )

                        AdminTab.Audit -> AuditPane(items = uiState.auditEntries)
                    }
                }
            }
        }
    }

    // Dialogs with updated styling
    uiState.vehicleEditor?.let { editor ->
        VehicleEditorDialog(
            editor = editor,
            isSaving = uiState.isSaving,
            onChanged = onVehicleEditorChanged,
            onDismiss = onDismissVehicleEditor,
            onSave = onSaveVehicle,
        )
    }
    uiState.userEditor?.let { editor ->
        UserEditorDialog(
            editor = editor,
            isSaving = uiState.isSaving,
            onChanged = onUserEditorChanged,
            onDismiss = onDismissUserEditor,
            onSave = onSaveUser,
        )
    }
    uiState.pendingVehicleDeactivation?.let { vehicle ->
        AlertDialog(
            onDismissRequest = onDismissVehicleDeactivation,
            title = { Text("确认停用车辆") },
            text = { Text("停用后车牌 [${vehicle.plateNumber}] 将无法在普通查询结果中显示，您可以随时重新启用。") },
            confirmButton = { 
                Button(
                    onClick = onConfirmVehicleDeactivation,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认停用") } 
            },
            dismissButton = { TextButton(onClick = onDismissVehicleDeactivation) { Text("取消") } },
            shape = RoundedCornerShape(PlateViewDimensions.cornerLarge)
        )
    }
    uiState.selectedImportBatch?.let { batch ->
        ImportBatchDialog(
            batch = batch,
            isSaving = uiState.isSaving,
            onDismiss = onDismissImportBatch,
            onResolution = onImportResolution,
            onPublish = onPublishImport,
            onRollback = onRollbackImport,
        )
    }
}

@Composable
private fun LoadingPane() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp)
        Spacer(Modifier.size(PlateViewDimensions.itemSpacing))
        Text("正在同步管理数据...", color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AdminFailureStrip(failure: AdminFailure) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlateViewDimensions.pageHorizontal, vertical = PlateViewDimensions.compactSpacing),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
    ) {
        Text(
            text = failure.message(),
            modifier = Modifier.padding(PlateViewDimensions.itemSpacing),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DashboardPane(
    vehiclesCount: Int,
    usersCount: Int,
    importsCount: Int,
    onTabSelected: (AdminTab) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PlateViewDimensions.pageHorizontal, PlateViewDimensions.pageVertical),
        horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
    ) {
        item {
            DashboardCard("车辆档案", "$vehiclesCount 条记录", Icons.Outlined.VerifiedUser, MaterialTheme.colorScheme.primary) { onTabSelected(AdminTab.Vehicles) }
        }
        item {
            DashboardCard("账号管理", "$usersCount 个用户", Icons.Outlined.SupervisorAccount, MaterialTheme.colorScheme.secondary) { onTabSelected(AdminTab.Users) }
        }
        item {
            DashboardCard("导入任务", "$importsCount 个批次", Icons.Outlined.FileUpload, MaterialTheme.colorScheme.tertiary) { onTabSelected(AdminTab.Imports) }
        }
        item {
            DashboardCard("操作审计", "安全日志", Icons.Outlined.Security, MaterialTheme.colorScheme.outline) { onTabSelected(AdminTab.Audit) }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.height(140.dp),
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = color)
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun VehiclesPane(
    items: List<ManagedVehicleSummary>,
    isSaving: Boolean,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeactivate: (ManagedVehicleSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PlateViewDimensions.pageHorizontal, PlateViewDimensions.pageVertical),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("所有车辆", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onCreate, 
                    enabled = !isSaving, 
                    shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新增")
                }
            }
        }
        if (items.isEmpty()) item { EmptyPane("暂无车辆记录") }
        items(items, key = ManagedVehicleSummary::id) { item ->
            AdminVehicleItem(item, onEdit, onDeactivate, isSaving)
        }
    }
}

@Composable
private fun AdminVehicleItem(
    item: ManagedVehicleSummary,
    onEdit: (Long) -> Unit,
    onDeactivate: (ManagedVehicleSummary) -> Unit,
    isSaving: Boolean
) {
    ElevatedCard(
        onClick = { onEdit(item.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.plateNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (item.status == "ACTIVE") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            item.statusLabel(), 
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.status == "ACTIVE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(item.categoryLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            IconButton(onClick = { onEdit(item.id) }) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { onDeactivate(item) }, enabled = item.status == "ACTIVE" && !isSaving) {
                Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun UsersPane(
    items: List<ManagedUser>,
    isSaving: Boolean,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PlateViewDimensions.pageHorizontal, PlateViewDimensions.pageVertical),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("系统账号", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = onCreate, enabled = !isSaving) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新增")
                }
            }
        }
        if (items.isEmpty()) item { EmptyPane("暂无账号记录") }
        items(items, key = ManagedUser::id) { item ->
            ElevatedCard(
                onClick = { onEdit(item.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Text(item.username.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${item.roleLabel()} · ${item.statusLabel()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun ImportsPane(
    items: List<ManagedImportBatchSummary>,
    isSaving: Boolean,
    onChooseImport: () -> Unit,
    onOpenBatch: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PlateViewDimensions.pageHorizontal, PlateViewDimensions.pageVertical),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("数据导入", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = onChooseImport, enabled = !isSaving) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("上传 Excel")
                }
            }
        }
        if (items.isEmpty()) item { EmptyPane("暂无导入任务") }
        items(items, key = ManagedImportBatchSummary::id) { item ->
            ElevatedCard(
                onClick = { onOpenBatch(item.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(item.createdAt ?: "未知时间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(item.sourceFileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Text("共 ${item.totalRows} 行", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(12.dp))
                        Text("异常 ${item.errorRows} 行", style = MaterialTheme.typography.bodySmall, color = if (item.errorRows > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditPane(items: List<ManagedAuditEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PlateViewDimensions.pageHorizontal, PlateViewDimensions.pageVertical),
    ) {
        item { Text("安全审计日志", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp)) }
        if (items.isEmpty()) item { EmptyPane("暂无审计记录") }
        items(items, key = ManagedAuditEntry::id) { item ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(item.actionType, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(item.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${item.actorUsername ?: "系统"} 对 ${item.targetType}${item.targetId?.let { " #$it" }.orEmpty()} 执行了操作",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("状态: ${item.resultStatus}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun EmptyPane(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun VehicleEditorDialog(
    editor: VehicleEditorState,
    isSaving: Boolean,
    onChanged: ((VehicleEditorState) -> VehicleEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.id == null) "新增车辆档案" else "编辑车辆信息", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
            ) {
                item { EditorTextField("车牌号码", editor.plateNumber) { value -> onChanged { it.copy(plateNumber = value, error = null) } } }
                item { ChoiceField("所属类别", editor.category, VEHICLE_CATEGORIES) { value -> onChanged { it.copy(category = value, error = null) } } }
                item { ChoiceField("当前状态", editor.status, VEHICLE_STATUSES) { value -> onChanged { it.copy(status = value) } } }
                item { EditorTextField("车辆型号", editor.vehicleType) { value -> onChanged { it.copy(vehicleType = value) } } }
                
                if (editor.isResident) {
                    item { Text("人员信息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    item { EditorTextField("姓名", editor.ownerName) { value -> onChanged { it.copy(ownerName = value, error = null) } } }
                    item { EditorTextField("身份证号", editor.identityCardNumber) { value -> onChanged { it.copy(identityCardNumber = value, error = null) } } }
                    item { EditorTextField("手机号码", editor.contactPhone) { value -> onChanged { it.copy(contactPhone = value) } } }
                } else {
                    item { Text("通行权限信息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    item { EditorTextField("单位名称", editor.organizationName) { value -> onChanged { it.copy(organizationName = value) } } }
                    item { EditorTextField("通行持有人", editor.passHolder) { value -> onChanged { it.copy(passHolder = value) } } }
                    item { EditorTextField("通行事由", editor.passageDetails, singleLine = false) { value -> onChanged { it.copy(passageDetails = value) } } }
                }
                
                editor.error?.let { message -> 
                    item { 
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                            Text(message, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        }
                    } 
                }
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = !isSaving) { Text(if (isSaving) "正在同步..." else "提交保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("取消") } },
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge)
    )
}

@Composable
private fun UserEditorDialog(
    editor: UserEditorState,
    isSaving: Boolean,
    onChanged: ((UserEditorState) -> UserEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.isCreate) "创建新账号" else "维护账号信息", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing)) {
                if (editor.isCreate) {
                    EditorTextField("用户名", editor.username) { value -> onChanged { it.copy(username = value, error = null) } }
                    OutlinedTextField(
                        value = editor.password,
                        onValueChange = { value -> onChanged { it.copy(password = value, error = null) } },
                        label = { Text("登录密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)
                    )
                } else {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)) {
                        Text(editor.username, modifier = Modifier.padding(16.dp).fillMaxWidth(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                ChoiceField("分配角色", editor.role, USER_ROLES) { value -> onChanged { it.copy(role = value) } }
                ChoiceField("账号状态", editor.status, USER_STATUSES) { value -> onChanged { it.copy(status = value) } }
                editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = !isSaving) { Text("保存更新") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("放弃") } },
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge)
    )
}

@Composable
private fun ImportBatchDialog(
    batch: ManagedImportBatch,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onResolution: (Long, String) -> Unit,
    onPublish: () -> Unit,
    onRollback: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("数据预览与核对", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
            ) {
                item {
                    Column {
                        Text(batch.sourceFileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("当前状态: ${batch.status}", color = MaterialTheme.colorScheme.primary)
                        Text("待处理: ${batch.stats.pendingReviewRows} 行 | 可发布: ${batch.stats.publishableRows} 行", style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(batch.rows, key = ManagedImportRow::id) { row ->
                    ImportRowItem(row, isSaving, onResolution)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (batch.status == "PUBLISHED") {
                    OutlinedButton(onClick = onRollback, enabled = !isSaving) { Text("撤销发布") }
                } else {
                    Button(
                        onClick = onPublish, 
                        enabled = !isSaving && batch.stats.pendingReviewRows == 0 && batch.stats.publishableRows > 0
                    ) { Text("正式发布数据") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("关闭预览") } },
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge)
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ImportRowItem(
    row: ManagedImportRow,
    isSaving: Boolean,
    onResolution: (Long, String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                Text(row.plateNumber ?: "???", modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(8.dp))
            Text("L${row.sourceRowNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        
        if (!row.warningMessage.isNullOrBlank()) Text(row.warningMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        if (!row.errorMessage.isNullOrBlank()) Text(row.errorMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        
        if (row.resolution == "PENDING") {
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onResolution(row.id, "PUBLISH") }, 
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("通过", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { onResolution(row.id, "SKIP") }, 
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("跳过", fontSize = 12.sp) }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun EditorTextField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ChoiceField(
    label: String,
    selected: String,
    options: List<ChoiceOption>,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option.value,
                    onClick = { onSelected(option.value) },
                    label = { Text(option.label) },
                    shape = RoundedCornerShape(PlateViewDimensions.cornerSmall)
                )
            }
        }
    }
}

private fun AdminTab.label(): String = when (this) {
    AdminTab.Dashboard -> "概览"
    AdminTab.Vehicles -> "车辆档案"
    AdminTab.Users -> "账号角色"
    AdminTab.Imports -> "数据导入"
    AdminTab.Audit -> "审计日志"
}

private fun AdminFailure.message(): String = when (this) {
    AdminFailure.SessionExpired -> "登录已过期，请重新登录"
    AdminFailure.PermissionDenied -> "权限不足，拒绝访问"
    AdminFailure.Conflict -> "数据发生冲突，请尝试刷新"
    is AdminFailure.Validation -> message ?: "输入验证失败"
    is AdminFailure.ServiceUnavailable -> "管理服务暂时不可用"
}

private fun ManagedVehicleSummary.statusLabel(): String = if (status == "ACTIVE") "已启用" else "停用中"
private fun ManagedUser.roleLabel(): String = if (role == "ADMIN") "管理员" else "核验员"
private fun ManagedUser.statusLabel(): String = if (status == "ACTIVE") "正常" else "已禁用"

private data class ChoiceOption(val value: String, val label: String)

private val VEHICLE_CATEGORIES = listOf(
    ChoiceOption("RESIDENT", "村民车辆"),
    ChoiceOption("SCENIC_UNIT", "驻景区单位"),
    ChoiceOption("SCENIC_ENTERPRISE", "驻景区企业"),
    ChoiceOption("CADRE", "干部车辆"),
    ChoiceOption("KANAS_TOURISM_DEVELOPMENT", "旅游公司"),
)
private val VEHICLE_STATUSES = listOf(ChoiceOption("ACTIVE", "启用"), ChoiceOption("INACTIVE", "停用"))
private val USER_ROLES = listOf(ChoiceOption("USER", "普通用户"), ChoiceOption("ADMIN", "管理员"))
private val USER_STATUSES = listOf(ChoiceOption("ACTIVE", "启用"), ChoiceOption("DISABLED", "停用"))

private const val EXCEL_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val LEGACY_EXCEL_MIME_TYPE = "application/vnd.ms-excel"
