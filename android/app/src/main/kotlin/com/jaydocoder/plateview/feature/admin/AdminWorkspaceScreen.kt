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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.domain.admin.AuditFilter
import com.jaydocoder.plateview.domain.admin.AuditRange
import com.jaydocoder.plateview.domain.admin.AuditResult
import com.jaydocoder.plateview.domain.admin.ImportRowFilter
import com.jaydocoder.plateview.domain.admin.ManagedAuditActor
import com.jaydocoder.plateview.domain.admin.ManagedAuditEntry
import com.jaydocoder.plateview.domain.admin.ManagedAuditSummary
import com.jaydocoder.plateview.domain.admin.ManagedImportBatch
import com.jaydocoder.plateview.domain.admin.ManagedImportBatchSummary
import com.jaydocoder.plateview.domain.admin.ManagedImportRow
import com.jaydocoder.plateview.domain.admin.ManagedImportDiffSection
import com.jaydocoder.plateview.domain.admin.ManagedImportRowDetail
import com.jaydocoder.plateview.domain.admin.ManagedUser
import com.jaydocoder.plateview.domain.admin.ManagedVehicleSummary
import com.jaydocoder.plateview.feature.update.UpdateAvailableAction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun AdminWorkspaceRoute(
    onNavigateUp: () -> Unit,
    onOpenUpdate: (() -> Unit)? = null,
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
        onVehicleSearchQueryChanged = viewModel::updateVehicleSearchQuery,
        onLoadMoreVehicles = viewModel::loadMoreVehicles,
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
        onLoadMoreImportRows = viewModel::loadMoreImportRows,
        onImportResolution = viewModel::updateImportResolution,
        onImportFilterChanged = viewModel::updateImportRowFilter,
        onOpenImportRowDetail = viewModel::openImportRowDetail,
        onDismissImportRowDetail = viewModel::dismissImportRowDetail,
        onPublishImport = viewModel::publishImport,
        onRollbackImport = viewModel::rollbackImport,
        onAuditRangeChanged = viewModel::updateAuditRange,
        onAuditActorChanged = viewModel::updateAuditActor,
        onAuditActionTypeChanged = viewModel::updateAuditActionType,
        onAuditResultChanged = viewModel::updateAuditResult,
        onLoadMoreAuditEntries = viewModel::loadMoreAuditEntries,
        onOpenUpdate = onOpenUpdate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminWorkspaceScreen(
    uiState: AdminUiState,
    onNavigateUp: () -> Unit,
    onTabSelected: (AdminTab) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onVehicleSearchQueryChanged: (String) -> Unit = {},
    onLoadMoreVehicles: () -> Unit = {},
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
    onLoadMoreImportRows: () -> Unit = {},
    onImportResolution: (Long, String) -> Unit,
    onImportFilterChanged: (ImportRowFilter) -> Unit = {},
    onOpenImportRowDetail: (Long) -> Unit = {},
    onDismissImportRowDetail: () -> Unit = {},
    onPublishImport: () -> Unit,
    onRollbackImport: () -> Unit,
    onAuditRangeChanged: (AuditRange) -> Unit = {},
    onAuditActorChanged: (Long?) -> Unit = {},
    onAuditActionTypeChanged: (String?) -> Unit = {},
    onAuditResultChanged: (AuditResult) -> Unit = {},
    onLoadMoreAuditEntries: () -> Unit = {},
    onOpenUpdate: (() -> Unit)? = null,
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
                    onOpenUpdate?.let { openUpdate ->
                        UpdateAvailableAction(onClick = openUpdate)
                    }
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
                            vehiclesCount = uiState.vehicleTotalCount,
                            usersCount = uiState.users.size,
                            importsCount = uiState.importBatches.size,
                            onTabSelected = onTabSelected,
                        )

                        AdminTab.Vehicles -> VehiclesPane(
                            items = uiState.vehicles,
                            searchQuery = uiState.vehicleSearchQuery,
                            totalCount = uiState.vehicleTotalCount,
                            isPageLoading = uiState.isVehiclePageLoading,
                            isSaving = uiState.isSaving,
                            onSearchQueryChanged = onVehicleSearchQueryChanged,
                            onLoadMore = onLoadMoreVehicles,
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

                        AdminTab.Audit -> AuditPane(
                            items = uiState.auditEntries,
                            filter = uiState.auditFilter,
                            summary = uiState.auditSummary,
                            totalCount = uiState.auditTotalCount,
                            actors = uiState.auditActors,
                            actionTypes = uiState.auditActionTypes,
                            isPageLoading = uiState.isAuditPageLoading,
                            onRangeChanged = onAuditRangeChanged,
                            onActorChanged = onAuditActorChanged,
                            onActionTypeChanged = onAuditActionTypeChanged,
                            onResultChanged = onAuditResultChanged,
                            onLoadMore = onLoadMoreAuditEntries,
                        )
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
    if (uiState.isVehicleEditorLoading) {
        VehicleEditorLoadingDialog()
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
        VehicleDeactivationDialog(
            vehicle = vehicle,
            onDismiss = onDismissVehicleDeactivation,
            onConfirm = onConfirmVehicleDeactivation,
        )
    }
    uiState.selectedImportBatch?.let { batch ->
        ImportBatchDialog(
            batch = batch,
            isSaving = uiState.isSaving,
            isPageLoading = uiState.isImportPageLoading,
            filter = uiState.importRowFilter,
            onFilterChanged = onImportFilterChanged,
            onDismiss = onDismissImportBatch,
            onLoadMore = onLoadMoreImportRows,
            onResolution = onImportResolution,
            onOpenDetail = onOpenImportRowDetail,
            onPublish = onPublishImport,
            onRollback = onRollbackImport,
        )
    }
    uiState.selectedImportRowDetail?.let { detail ->
        ImportRowDetailDialog(
            detail = detail,
            isSaving = uiState.isSaving,
            onDismiss = onDismissImportRowDetail,
            onResolution = onImportResolution,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = color)
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "打开$title",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
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
    searchQuery: String,
    totalCount: Int,
    isPageLoading: Boolean,
    isSaving: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onLoadMore: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeactivate: (ManagedVehicleSummary) -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            items.isNotEmpty() && lastVisibleIndex >= layoutInfo.totalItemsCount - LOAD_MORE_TRIGGER_DISTANCE
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("admin_vehicle_archive"),
        contentPadding = PaddingValues(PlateViewDimensions.pageHorizontal, PlateViewDimensions.pageVertical),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing)) {
                AdminPaneHeading(
                    title = "车辆档案",
                    description = "按车牌核对、维护景区通行车辆",
                    metric = "$totalCount 条档案",
                    icon = Icons.Outlined.VerifiedUser,
                    metricModifier = Modifier.testTag("admin_vehicle_total"),
                )
                Button(
                    onClick = onCreate,
                    enabled = !isSaving,
                    modifier = Modifier.align(Alignment.End).testTag("admin_new_vehicle"),
                    shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新增")
                }
            }
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_vehicle_search"),
                label = { Text("按车牌号检索档案") },
                placeholder = { Text("输入任意车牌字符") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChanged("") },
                            modifier = Modifier.testTag("admin_vehicle_search_clear"),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "清除车牌检索")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
            )
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "已加载 ${items.size} / $totalCount 条",
                modifier = Modifier.padding(top = PlateViewDimensions.compactSpacing),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (items.isEmpty() && !isPageLoading) item {
            EmptyPane(if (searchQuery.isBlank()) "暂无车辆记录" else "未找到匹配的车辆档案")
        }
        items(items, key = ManagedVehicleSummary::id) { item ->
            AdminVehicleItem(item, onEdit, onDeactivate, isSaving)
        }
        if (isPageLoading) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = PlateViewDimensions.compactSpacing)
                        .testTag("admin_vehicle_load_more"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                    Text("正在加载车辆档案", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (items.isNotEmpty() && items.size < totalCount) {
            item {
                Text(
                    text = "继续下滑加载更多档案",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = PlateViewDimensions.compactSpacing)
                        .testTag("admin_vehicle_load_hint"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.plateNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                    AdminStatusBadge(item.statusLabel(), item.status == "ACTIVE")
                }
                Spacer(Modifier.height(PlateViewDimensions.tinySpacing))
                Text(item.categoryLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.vehicleType?.takeIf(String::isNotBlank)?.let { type ->
                    Text(type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
            IconButton(onClick = { onEdit(item.id) }) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑 ${item.plateNumber}", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { onDeactivate(item) }, enabled = item.status == "ACTIVE" && !isSaving) {
                Icon(Icons.Outlined.Block, contentDescription = "停用 ${item.plateNumber}", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
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
            Column(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing)) {
                AdminPaneHeading(
                    title = "系统账号",
                    description = "维护核验人员与管理员角色",
                    metric = "${items.size} 个账号",
                    icon = Icons.Outlined.SupervisorAccount,
                )
                Button(onClick = onCreate, enabled = !isSaving) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新增账号")
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
                        Text(item.roleLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    AdminStatusBadge(item.statusLabel(), item.status == "ACTIVE")
                    Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑 ${item.username}", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
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
            Column(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing)) {
                AdminPaneHeading(
                    title = "数据导入",
                    description = "上传、核对并发布车辆资料",
                    metric = "${items.size} 个批次",
                    icon = Icons.Outlined.FileUpload,
                )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.sourceFileName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AdminStatusBadge(item.importStatusLabel(), item.status == "PUBLISHED")
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditPane(
    items: List<ManagedAuditEntry>,
    filter: AuditFilter,
    summary: ManagedAuditSummary,
    totalCount: Int,
    actors: List<ManagedAuditActor>,
    actionTypes: List<String>,
    isPageLoading: Boolean,
    onRangeChanged: (AuditRange) -> Unit,
    onActorChanged: (Long?) -> Unit,
    onActionTypeChanged: (String?) -> Unit,
    onResultChanged: (AuditResult) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val hasMoreItems = items.size < totalCount
    val shouldLoadMore by remember(listState, items.size, totalCount, isPageLoading) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            items.isNotEmpty() && hasMoreItems && !isPageLoading &&
                lastVisibleIndex >= listState.layoutInfo.totalItemsCount - LOAD_MORE_TRIGGER_DISTANCE
        }
    }
    LaunchedEffect(listState, shouldLoadMore) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PlateViewDimensions.pageHorizontal, PlateViewDimensions.pageVertical),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
    ) {
        item(key = "audit_heading") {
            AdminPaneHeading(
                title = "操作审计",
                description = "${filter.range.label}内的管理操作与异常追踪",
                metric = "$totalCount 条记录",
                icon = Icons.Outlined.Security,
            )
        }
        item(key = "audit_summary") {
            AuditSummaryRow(summary)
        }
        item(key = "audit_ranges") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing)) {
                items(AuditRange.entries, key = AuditRange::name) { range ->
                    FilterChip(
                        selected = range == filter.range,
                        onClick = { onRangeChanged(range) },
                        label = { Text(range.label) },
                    )
                }
            }
        }
        item(key = "audit_selectors") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
            ) {
                AuditSelector(
                    modifier = Modifier.weight(1f),
                    label = filter.actorId?.let { id -> actors.firstOrNull { it.id == id }?.username ?: "指定用户" } ?: "全部用户",
                    options = listOf(AuditSelection<Long?>(null, "全部用户")) + actors.map { AuditSelection<Long?>(it.id, it.username ?: "系统") },
                    onSelected = onActorChanged,
                )
                AuditSelector(
                    modifier = Modifier.weight(1f),
                    label = filter.actionType ?: "全部操作",
                    options = listOf(AuditSelection<String?>(null, "全部操作")) + actionTypes.map { AuditSelection<String?>(it, it) },
                    onSelected = onActionTypeChanged,
                )
            }
        }
        item(key = "audit_results") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing)) {
                items(AuditResult.entries, key = AuditResult::name) { result ->
                    FilterChip(
                        selected = result == filter.result,
                        onClick = { onResultChanged(result) },
                        label = { Text(result.label) },
                        leadingIcon = if (result == AuditResult.ABNORMAL) {
                            { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        } else null,
                    )
                }
            }
        }
        if (items.isEmpty() && !isPageLoading) {
            item(key = "audit_empty") { EmptyPane("当前筛选条件下暂无审计记录") }
        }
        items(items, key = ManagedAuditEntry::id) { item -> AuditEntryItem(item) }
        if (isPageLoading) {
            item(key = "audit_loading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.compactSpacing),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                    Text("正在加载审计记录", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (hasMoreItems) {
            item(key = "audit_load_hint") {
                Text(
                    text = "继续下滑加载更多记录",
                    modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.compactSpacing),
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AuditSummaryRow(summary: ManagedAuditSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
    ) {
        AuditMetric("总记录", summary.total, MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
        AuditMetric("正常", summary.successCount, MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f))
        AuditMetric("异常", summary.abnormalCount, MaterialTheme.colorScheme.errorContainer, Modifier.weight(1f))
        AuditMetric("操作人", summary.activeActorCount, MaterialTheme.colorScheme.tertiaryContainer, Modifier.weight(1f))
    }
}

@Composable
private fun AuditMetric(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = color, shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private data class AuditSelection<T>(val value: T, val label: String)

@Composable
private fun <T> AuditSelector(
    label: String,
    options: List<AuditSelection<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, maxLines = 1, modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { expanded = false; onSelected(option.value) },
                )
            }
        }
    }
}

@Composable
private fun AuditEntryItem(item: ManagedAuditEntry) {
    val isAbnormal = item.resultStatus == "FAILURE" || item.resultStatus == "DENIED"
    val containerColor = if (isAbnormal) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isAbnormal) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = PlateViewDimensions.compactSpacing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(PlateViewDimensions.cornerSmall)) {
                Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isAbnormal) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(item.resultStatus, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
            Text(item.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(6.dp))
        Text(item.actionType, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "${item.actorUsername ?: "系统"} · ${item.targetType}${item.targetId?.let { " #$it" }.orEmpty()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
private fun AdminPaneHeading(
    title: String,
    description: String,
    metric: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    metricModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Spacer(Modifier.width(PlateViewDimensions.itemSpacing))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            modifier = metricModifier,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        ) {
            Text(metric, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AdminStatusBadge(label: String, isPositive: Boolean) {
    val containerColor = if (isPositive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isPositive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(PlateViewDimensions.cornerSmall)) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isPositive) Icons.Outlined.CheckCircle else Icons.Outlined.Block,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
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
    AdminEditorDialog(
        title = if (editor.id == null) "新增车辆档案" else "编辑车辆档案",
        subtitle = "按车辆类别完成通行资料核验",
        identity = editor.plateNumber.ifBlank { "待录入车牌" },
        icon = Icons.Outlined.VerifiedUser,
        isSaving = isSaving,
        onDismiss = onDismiss,
        onSave = onSave,
        saveLabel = "保存档案",
    ) {
        item { EditorSectionHeading("车辆信息", "车牌、分类和启用状态") }
        item { EditorTextField("车牌号码", editor.plateNumber) { value -> onChanged { it.copy(plateNumber = value, error = null) } } }
        item { ChoiceField("所属类别", editor.category, VEHICLE_CATEGORIES) { value -> onChanged { it.copy(category = value, error = null) } } }
        item { ChoiceField("当前状态", editor.status, VEHICLE_STATUSES) { value -> onChanged { it.copy(status = value) } } }
        item { EditorTextField("车辆型号", editor.vehicleType) { value -> onChanged { it.copy(vehicleType = value) } } }

        if (editor.isResident) {
            item { EditorSectionHeading("身份核验", "用于核对村民车辆归属") }
            item { EditorTextField("姓名", editor.ownerName) { value -> onChanged { it.copy(ownerName = value, error = null) } } }
            item { EditorTextField("身份证号", editor.identityCardNumber) { value -> onChanged { it.copy(identityCardNumber = value, error = null) } } }
            item { EditorTextField("手机号码", editor.contactPhone) { value -> onChanged { it.copy(contactPhone = value) } } }
        } else {
            item { EditorSectionHeading("单位与通行", "用于核对长期通行车辆") }
            item { EditorTextField("单位名称", editor.organizationName) { value -> onChanged { it.copy(organizationName = value) } } }
            item { EditorTextField("通行持有人", editor.passHolder) { value -> onChanged { it.copy(passHolder = value) } } }
            item { EditorTextField("通行事由", editor.passageDetails, singleLine = false) { value -> onChanged { it.copy(passageDetails = value) } } }
        }

        item { EditorSectionHeading("补充资料", "方便现场核对和通行放行") }
        item { EditorTextField("车辆用途", editor.vehicleUse) { value -> onChanged { it.copy(vehicleUse = value) } } }
        item { EditorTextField("通行区域", editor.passageArea) { value -> onChanged { it.copy(passageArea = value) } } }
        item { EditorTextField("所属位置", editor.position) { value -> onChanged { it.copy(position = value) } } }
        item { EditorTextField("品牌型号", editor.brandModel) { value -> onChanged { it.copy(brandModel = value) } } }
        item { EditorTextField("核定载客数", editor.approvedCapacity) { value -> onChanged { it.copy(approvedCapacity = value) } } }
        item { EditorTextField("车牌颜色", editor.plateColor) { value -> onChanged { it.copy(plateColor = value) } } }
        item { EditorTextField("备注", editor.remarks, singleLine = false) { value -> onChanged { it.copy(remarks = value) } } }
        editor.error?.let { message -> item { EditorErrorMessage(message) } }
    }
}

@Composable
private fun UserEditorDialog(
    editor: UserEditorState,
    isSaving: Boolean,
    onChanged: ((UserEditorState) -> UserEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AdminEditorDialog(
        title = if (editor.isCreate) "创建新账号" else "维护账号信息",
        subtitle = "角色和启用状态会立即生效",
        identity = editor.username.ifBlank { "待创建账号" },
        icon = Icons.Outlined.SupervisorAccount,
        isSaving = isSaving,
        onDismiss = onDismiss,
        onSave = onSave,
        saveLabel = if (editor.isCreate) "创建账号" else "保存账号",
    ) {
        if (editor.isCreate) {
            item { EditorSectionHeading("账号凭据", "创建后请妥善保存登录密码") }
            item { EditorTextField("用户名", editor.username) { value -> onChanged { it.copy(username = value, error = null) } } }
            item {
                OutlinedTextField(
                    value = editor.password,
                    onValueChange = { value -> onChanged { it.copy(password = value, error = null) } },
                    label = { Text("登录密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                )
            }
        }
        item { EditorSectionHeading("访问权限", "决定可访问的管理范围") }
        item { ChoiceField("分配角色", editor.role, USER_ROLES) { value -> onChanged { it.copy(role = value) } } }
        item { ChoiceField("账号状态", editor.status, USER_STATUSES) { value -> onChanged { it.copy(status = value) } } }
        editor.error?.let { message -> item { EditorErrorMessage(message) } }
    }
}

@Composable
private fun AdminEditorDialog(
    title: String,
    subtitle: String,
    identity: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PlateViewDimensions.pageHorizontal)
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(PlateViewDimensions.cornerExtraLarge),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(PlateViewDimensions.itemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = CircleShape,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp).size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(PlateViewDimensions.itemSpacing))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    TextButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f),
                ) {
                    Text(
                        identity,
                        modifier = Modifier.padding(horizontal = PlateViewDimensions.itemSpacing, vertical = PlateViewDimensions.compactSpacing),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = PlateViewDimensions.itemSpacing, vertical = PlateViewDimensions.itemSpacing),
                    verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
                    content = content,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PlateViewDimensions.itemSpacing),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("取消") }
                    Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                    Button(onClick = onSave, enabled = !isSaving) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                            Text("正在保存")
                        } else {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                            Text(saveLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleEditorLoadingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(PlateViewDimensions.itemSpacing * 1.5f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(PlateViewDimensions.itemSpacing))
                Column {
                    Text("正在读取车辆档案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("即将打开编辑页面", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VehicleDeactivationDialog(
    vehicle: ManagedVehicleSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(PlateViewDimensions.cornerExtraLarge),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(PlateViewDimensions.pageHorizontal)) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Outlined.Block,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(PlateViewDimensions.itemSpacing))
                Text("停用车辆档案", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(PlateViewDimensions.tinySpacing))
                Text(
                    "${vehicle.plateNumber} 将不再出现在普通查询结果中。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(PlateViewDimensions.itemSpacing))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("保留档案") }
                    Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("确认停用") }
                }
            }
        }
    }
}

@Composable
private fun EditorErrorMessage(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
    ) {
        Row(
            modifier = Modifier.padding(PlateViewDimensions.compactSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun EditorSectionHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.tinySpacing)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ImportBatchDialog(
    batch: ManagedImportBatch,
    isSaving: Boolean,
    isPageLoading: Boolean,
    filter: ImportRowFilter,
    onFilterChanged: (ImportRowFilter) -> Unit,
    onDismiss: () -> Unit,
    onLoadMore: () -> Unit,
    onResolution: (Long, String) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onPublish: () -> Unit,
    onRollback: () -> Unit,
) {
    val listState = rememberLazyListState()
    val hasMoreRows = batch.rows.size < batch.rowTotal
    val shouldLoadMore by remember(listState, batch.rows.size, batch.rowTotal, isPageLoading) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            batch.rows.isNotEmpty() &&
                hasMoreRows &&
                !isPageLoading &&
                lastVisibleIndex >= layoutInfo.totalItemsCount - LOAD_MORE_TRIGGER_DISTANCE
        }
    }

    LaunchedEffect(listState, batch.id, batch.rows.size, batch.rowTotal, isPageLoading) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("数据差异核对", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
            ) {
                item {
                    Column {
                        Text(batch.sourceFileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("当前状态: ${batch.status}", color = MaterialTheme.colorScheme.primary)
                        if (batch.status == "ROLLED_BACK") {
                            Text("该批次已经撤销，可重新发布", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "新增 ${batch.stats.newRows} | 更新 ${batch.stats.updateRows} | 恢复 ${batch.stats.reactivateRows} | 待失效 ${batch.stats.deactivateRows}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "异常 ${batch.stats.errorRows} | 已隐藏完全一致 ${batch.stats.duplicateRows} | 待确认 ${batch.stats.pendingReviewRows}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            text = "已加载 ${batch.rows.size} / ${batch.rowTotal} 条需核对记录",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        LazyRow(
                            modifier = Modifier.padding(top = PlateViewDimensions.compactSpacing),
                            horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
                        ) {
                            items(ImportRowFilter.entries, key = ImportRowFilter::name) { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { onFilterChanged(option) },
                                    label = { Text(option.label) },
                                    enabled = !isSaving,
                                )
                            }
                        }
                    }
                }
                items(batch.rows, key = ManagedImportRow::id) { row ->
                    ImportRowItem(row, isSaving, onOpenDetail)
                }
                if (isPageLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = PlateViewDimensions.compactSpacing)
                                .testTag("admin_import_load_more"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                            Text("正在加载导入记录", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (hasMoreRows) {
                    item {
                        Text(
                            text = "继续下滑加载更多记录",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = PlateViewDimensions.compactSpacing)
                                .testTag("admin_import_load_hint"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (batch.status == "PUBLISHED") {
                OutlinedButton(
                    onClick = { onRollback() },
                    enabled = !isSaving,
                    modifier = Modifier.testTag("admin_rollback_import"),
                ) {
                    Text("撤销发布")
                }
            } else {
                Button(
                    onClick = { onPublish() },
                    enabled = !isSaving && batch.stats.pendingReviewRows == 0 && batch.stats.publishableRows > 0,
                    modifier = Modifier.testTag("admin_publish_import"),
                ) {
                    Text(if (batch.status == "ROLLED_BACK") "重新发布数据" else "正式发布数据")
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
    onOpenDetail: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = row.importActionColor(), shape = RoundedCornerShape(4.dp)) {
                Text(row.plateNumber ?: "???", modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(8.dp))
            Text(row.importActionLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            if (row.sourceRowNumber > 0) {
                Spacer(Modifier.width(6.dp))
                Text("第${row.sourceRowNumber}行", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        row.primarySubject?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!row.warningMessage.isNullOrBlank()) Text(row.warningMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        if (!row.errorMessage.isNullOrBlank()) Text(row.errorMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(
            onClick = { onOpenDetail(row.id) },
            enabled = !isSaving,
            modifier = Modifier.padding(top = 4.dp).testTag("admin_import_row_detail_${row.id}"),
        ) { Text(row.importDetailLabel()) }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun ImportRowDetailDialog(
    detail: ManagedImportRowDetail,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onResolution: (Long, String) -> Unit,
) {
    val row = detail.row
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.importDetailLabel(), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing)) {
                item {
                    Text(row.plateNumber ?: "未识别车牌", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(row.importActionLabel(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    row.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    row.warningMessage?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                }
                items(detail.sections, key = ManagedImportDiffSection::title) { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(section.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        section.fields.forEach { field ->
                            Column {
                                Text(field.label, style = MaterialTheme.typography.labelMedium)
                                Text("原值：${field.before.displayImportValue()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                Text("新值：${field.after.displayImportValue()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (detail.sourceValues.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Excel 源字段", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            detail.sourceValues.forEach { value ->
                                Text("${value.label}：${value.value}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (row.resolution == "PENDING") {
                Button(
                    onClick = { onResolution(row.id, "PUBLISH") },
                    enabled = !isSaving,
                    modifier = Modifier.testTag("admin_import_confirm_${row.id}"),
                ) { Text(row.importConfirmLabel()) }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = {
            if (row.resolution == "PENDING") {
                OutlinedButton(onClick = { onResolution(row.id, "SKIP") }, enabled = !isSaving) {
                    Text(row.importSkipLabel())
                }
            }
        },
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
    )
}

private fun ManagedImportRow.importActionLabel(): String = when (plannedAction) {
    "CREATE" -> "新增档案"
    "UPDATE" -> "字段更新"
    "REACTIVATE" -> "恢复有效"
    "DEACTIVATE" -> "待失效"
    else -> if (resultStatus == "ERROR") "解析异常" else "待核对"
}

private fun ManagedImportRow.importDetailLabel(): String = when (plannedAction) {
    "UPDATE", "REACTIVATE" -> "查看更新详情"
    "DEACTIVATE" -> "查看失效详情"
    "CREATE" -> "查看新增详情"
    else -> "查看问题详情"
}

private fun ManagedImportRow.importConfirmLabel(): String = when (plannedAction) {
    "UPDATE" -> "确认更新"
    "REACTIVATE" -> "确认恢复"
    "DEACTIVATE" -> "确认失效"
    else -> "确认新增"
}

private fun ManagedImportRow.importSkipLabel(): String = when (plannedAction) {
    "DEACTIVATE" -> "保留有效"
    "REACTIVATE" -> "保持失效"
    else -> "跳过"
}

@Composable
private fun ManagedImportRow.importActionColor(): Color = when (plannedAction) {
    "DEACTIVATE" -> MaterialTheme.colorScheme.errorContainer
    "UPDATE", "REACTIVATE" -> MaterialTheme.colorScheme.tertiaryContainer
    "CREATE" -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.errorContainer
}

private fun String?.displayImportValue(): String = this?.takeIf(String::isNotBlank) ?: "未填写"

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
private fun ManagedImportBatchSummary.importStatusLabel(): String = when (status) {
    "VALIDATED" -> "待发布"
    "PUBLISHED" -> "已发布"
    "ROLLED_BACK" -> "已撤销"
    else -> status
}

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
private const val LOAD_MORE_TRIGGER_DISTANCE = 4
