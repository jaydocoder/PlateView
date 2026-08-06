package com.jaydocoder.plateview.feature.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
                title = { Text("管理员工作台") },
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ScrollableTabRow(selectedTabIndex = uiState.tab.ordinal, edgePadding = PlateViewDimensions.pageHorizontal) {
                AdminTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == uiState.tab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(tab.label()) },
                    )
                }
            }
            uiState.failure?.let { AdminFailureStrip(it) }
            if (uiState.isLoading) {
                LoadingPane()
            } else {
                when (uiState.tab) {
                    AdminTab.Dashboard -> DashboardPane(
                        vehicles = uiState.vehicles,
                        users = uiState.users,
                        imports = uiState.importBatches,
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
            title = { Text("停用车辆") },
            text = { Text("停用后该车不会出现在普通用户的查询结果中。") },
            confirmButton = { Button(onClick = onConfirmVehicleDeactivation) { Text("确认停用") } },
            dismissButton = { TextButton(onClick = onDismissVehicleDeactivation) { Text("取消") } },
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
        CircularProgressIndicator()
        Spacer(Modifier.size(PlateViewDimensions.itemSpacing))
        Text("正在加载管理数据")
    }
}

@Composable
private fun AdminFailureStrip(failure: AdminFailure) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlateViewDimensions.pageHorizontal, vertical = PlateViewDimensions.compactSpacing),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
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
    vehicles: List<ManagedVehicleSummary>,
    users: List<ManagedUser>,
    imports: List<ManagedImportBatchSummary>,
    onTabSelected: (AdminTab) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PlateViewDimensions.pageHorizontal, PlateViewDimensions.pageVertical),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
    ) {
        item(key = "dashboard_summary") {
            Text("数据维护", style = MaterialTheme.typography.titleLarge)
        }
        item(key = "dashboard_vehicle") {
            DashboardEntry("车辆档案", "当前已加载 ${vehicles.size} 条车辆记录", Icons.Outlined.Edit) { onTabSelected(AdminTab.Vehicles) }
        }
        item(key = "dashboard_user") {
            DashboardEntry("账号与角色", "当前已加载 ${users.size} 个账号", Icons.Outlined.Security) { onTabSelected(AdminTab.Users) }
        }
        item(key = "dashboard_import") {
            DashboardEntry("导入任务", "当前已加载 ${imports.size} 个导入批次", Icons.Outlined.FileUpload) { onTabSelected(AdminTab.Imports) }
        }
        item(key = "dashboard_audit") {
            DashboardEntry("操作审计", "查看数据维护和导入操作记录", Icons.Outlined.Security) { onTabSelected(AdminTab.Audit) }
        }
    }
}

@Composable
private fun DashboardEntry(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.fillMaxSize().padding(PlateViewDimensions.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(PlateViewDimensions.itemSpacing))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
    ) {
        item(key = "vehicle_action") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("车辆档案", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Button(onClick = onCreate, enabled = !isSaving, modifier = Modifier.testTag("admin_new_vehicle")) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("新增")
                }
            }
        }
        if (items.isEmpty()) item(key = "vehicle_empty") { EmptyPane("暂无车辆记录") }
        items(items, key = ManagedVehicleSummary::id, contentType = { "admin_vehicle" }) { item ->
            OutlinedCard(onClick = { onEdit(item.id) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.itemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.plateNumber, style = MaterialTheme.typography.titleMedium)
                        Text("${item.categoryLabel} · ${item.statusLabel()}", style = MaterialTheme.typography.bodyMedium)
                        item.vehicleType?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton(onClick = { onEdit(item.id) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑 ${item.plateNumber}")
                    }
                    IconButton(onClick = { onDeactivate(item) }, enabled = item.status == "ACTIVE" && !isSaving) {
                        Icon(Icons.Outlined.Block, contentDescription = "停用 ${item.plateNumber}")
                    }
                }
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
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
    ) {
        item(key = "user_action") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("账号与角色", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Button(onClick = onCreate, enabled = !isSaving, modifier = Modifier.testTag("admin_new_user")) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("新增")
                }
            }
        }
        if (items.isEmpty()) item(key = "user_empty") { EmptyPane("暂无账号记录") }
        items(items, key = ManagedUser::id, contentType = { "admin_user" }) { item ->
            OutlinedCard(onClick = { onEdit(item.id) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.itemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.username, style = MaterialTheme.typography.titleMedium)
                        Text("${item.roleLabel()} · ${item.statusLabel()}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑 ${item.username}")
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
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
    ) {
        item(key = "import_action") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Excel 导入任务", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Button(onClick = onChooseImport, enabled = !isSaving, modifier = Modifier.testTag("admin_select_excel")) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("选择 Excel")
                }
            }
        }
        if (items.isEmpty()) item(key = "import_empty") { EmptyPane("暂无导入批次") }
        items(items, key = ManagedImportBatchSummary::id, contentType = { "admin_import" }) { item ->
            OutlinedCard(onClick = { onOpenBatch(item.id) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                Column(modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.itemSpacing)) {
                    Text(item.sourceFileName, style = MaterialTheme.typography.titleMedium)
                    Text("${item.status} · 共 ${item.totalRows} 行，异常 ${item.errorRows} 行", style = MaterialTheme.typography.bodyMedium)
                    item.createdAt?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
    ) {
        item(key = "audit_title") { Text("操作审计", style = MaterialTheme.typography.titleLarge) }
        if (items.isEmpty()) item(key = "audit_empty") { EmptyPane("暂无审计记录") }
        items(items, key = ManagedAuditEntry::id, contentType = { "admin_audit" }) { item ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = PlateViewDimensions.compactSpacing)) {
                Text("${item.actionType} · ${item.resultStatus}", style = MaterialTheme.typography.titleMedium)
                Text("${item.actorUsername ?: "系统"} · ${item.targetType}${item.targetId?.let { " #$it" }.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                Text(item.createdAt, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(modifier = Modifier.padding(top = PlateViewDimensions.compactSpacing))
            }
        }
    }
}

@Composable
private fun EmptyPane(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(vertical = PlateViewDimensions.sectionSpacing),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
        title = { Text(if (editor.id == null) "新增车辆" else "编辑车辆") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
            ) {
                item { EditorTextField("车牌号", editor.plateNumber) { value -> onChanged { it.copy(plateNumber = value, error = null) } } }
                item { ChoiceField("车辆类别", editor.category, VEHICLE_CATEGORIES) { value -> onChanged { it.copy(category = value, error = null) } } }
                item { ChoiceField("车辆状态", editor.status, VEHICLE_STATUSES) { value -> onChanged { it.copy(status = value) } } }
                item { EditorTextField("车辆类型", editor.vehicleType) { value -> onChanged { it.copy(vehicleType = value) } } }
                if (editor.isResident) {
                    item { Text("村民核验资料", style = MaterialTheme.typography.titleSmall) }
                    item { EditorTextField("姓名", editor.ownerName) { value -> onChanged { it.copy(ownerName = value, error = null) } } }
                    item { EditorTextField("身份证号", editor.identityCardNumber) { value -> onChanged { it.copy(identityCardNumber = value, error = null) } } }
                    item { EditorTextField("联系方式", editor.contactPhone) { value -> onChanged { it.copy(contactPhone = value) } } }
                } else {
                    item { Text("长期车辆通行资料", style = MaterialTheme.typography.titleSmall) }
                    item { EditorTextField("单位名称", editor.organizationName) { value -> onChanged { it.copy(organizationName = value) } } }
                    item { EditorTextField("通行人员", editor.passHolder) { value -> onChanged { it.copy(passHolder = value) } } }
                    item { EditorTextField("通行说明", editor.passageDetails, singleLine = false) { value -> onChanged { it.copy(passageDetails = value) } } }
                }
                item { Text("附加信息", style = MaterialTheme.typography.titleSmall) }
                item { EditorTextField("车辆用途", editor.vehicleUse) { value -> onChanged { it.copy(vehicleUse = value) } } }
                item { EditorTextField("通行区域", editor.passageArea) { value -> onChanged { it.copy(passageArea = value) } } }
                item { EditorTextField("职务", editor.position) { value -> onChanged { it.copy(position = value) } } }
                item { EditorTextField("品牌型号", editor.brandModel) { value -> onChanged { it.copy(brandModel = value) } } }
                item { EditorTextField("核载人数", editor.approvedCapacity) { value -> onChanged { it.copy(approvedCapacity = value) } } }
                item { EditorTextField("号牌颜色", editor.plateColor) { value -> onChanged { it.copy(plateColor = value) } } }
                item { EditorTextField("备注", editor.remarks, singleLine = false) { value -> onChanged { it.copy(remarks = value) } } }
                editor.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = !isSaving) { Text(if (isSaving) "正在保存" else "保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("取消") } },
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
        title = { Text(if (editor.isCreate) "新增账号" else "维护账号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing)) {
                if (editor.isCreate) {
                    EditorTextField("账号名称", editor.username) { value -> onChanged { it.copy(username = value, error = null) } }
                    OutlinedTextField(
                        value = editor.password,
                        onValueChange = { value -> onChanged { it.copy(password = value, error = null) } },
                        label = { Text("初始密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(editor.username, style = MaterialTheme.typography.titleMedium)
                }
                ChoiceField("角色", editor.role, USER_ROLES) { value -> onChanged { it.copy(role = value) } }
                ChoiceField("状态", editor.status, USER_STATUSES) { value -> onChanged { it.copy(status = value) } }
                editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = !isSaving) { Text(if (isSaving) "正在保存" else "保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("取消") } },
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
        title = { Text("导入批次 #${batch.id}") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
            ) {
                item {
                    Column {
                        Text(batch.sourceFileName, style = MaterialTheme.typography.titleSmall)
                        Text("${batch.status} · 可发布 ${batch.stats.publishableRows} 行 · 待处理 ${batch.stats.pendingReviewRows} 行")
                    }
                }
                items(batch.rows, key = ManagedImportRow::id, contentType = { "import_row" }) { row ->
                    ImportRowItem(row, isSaving, onResolution)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing)) {
                if (batch.status == "PUBLISHED") {
                    OutlinedButton(onClick = onRollback, enabled = !isSaving) { Text("回滚") }
                } else {
                    Button(onClick = onPublish, enabled = !isSaving && batch.stats.pendingReviewRows == 0 && batch.stats.publishableRows > 0) {
                        Text("确认发布")
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("关闭") } },
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
        Text(row.plateNumber ?: "未识别车牌", style = MaterialTheme.typography.titleSmall)
        Text("${row.sourceSheetName} 第 ${row.sourceRowNumber} 行 · ${row.resolution}", style = MaterialTheme.typography.bodySmall)
        row.warningMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        row.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (row.resolution == "PENDING") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing)) {
                OutlinedButton(onClick = { onResolution(row.id, "PUBLISH") }, enabled = !isSaving) { Text("发布此行") }
                TextButton(onClick = { onResolution(row.id, "SKIP") }, enabled = !isSaving) { Text("跳过") }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = PlateViewDimensions.compactSpacing))
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option.value,
                    onClick = { onSelected(option.value) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

private fun AdminTab.label(): String = when (this) {
    AdminTab.Dashboard -> "概览"
    AdminTab.Vehicles -> "车辆"
    AdminTab.Users -> "账号"
    AdminTab.Imports -> "导入"
    AdminTab.Audit -> "审计"
}

private fun AdminFailure.message(): String = when (this) {
    AdminFailure.SessionExpired -> "登录状态已失效，请重新登录"
    AdminFailure.PermissionDenied -> "当前账号没有管理员权限"
    AdminFailure.Conflict -> "数据已被其他管理员修改，请刷新后重试"
    is AdminFailure.Validation -> message ?: "请检查填写内容"
    is AdminFailure.ServiceUnavailable -> "无法连接管理服务，请稍后重试"
}

private fun ManagedVehicleSummary.statusLabel(): String = if (status == "ACTIVE") "启用" else "已停用"
private fun ManagedUser.roleLabel(): String = if (role == "ADMIN") "管理员" else "普通用户"
private fun ManagedUser.statusLabel(): String = if (status == "ACTIVE") "启用" else "已停用"

private data class ChoiceOption(val value: String, val label: String)

private val VEHICLE_CATEGORIES = listOf(
    ChoiceOption("RESIDENT", "村民车辆"),
    ChoiceOption("SCENIC_UNIT", "驻景区单位"),
    ChoiceOption("SCENIC_ENTERPRISE", "驻景区企业"),
    ChoiceOption("CADRE", "干部车辆"),
    ChoiceOption("KANAS_TOURISM_DEVELOPMENT", "旅游发展公司"),
)
private val VEHICLE_STATUSES = listOf(ChoiceOption("ACTIVE", "启用"), ChoiceOption("INACTIVE", "停用"))
private val USER_ROLES = listOf(ChoiceOption("USER", "普通用户"), ChoiceOption("ADMIN", "管理员"))
private val USER_STATUSES = listOf(ChoiceOption("ACTIVE", "启用"), ChoiceOption("DISABLED", "停用"))

private const val EXCEL_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val LEGACY_EXCEL_MIME_TYPE = "application/vnd.ms-excel"
