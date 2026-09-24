package com.jaydocoder.plateview.feature.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.jaydocoder.plateview.component.CompatFlowRow
import com.jaydocoder.plateview.component.AttachmentViewerDialog
import com.jaydocoder.plateview.component.AttachmentThumbnail
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.compose.material.icons.outlined.VerifiedUser
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.jaydocoder.plateview.domain.admin.WechatSyncIssue
import com.jaydocoder.plateview.domain.admin.WechatSyncSource
import com.jaydocoder.plateview.feature.update.UpdateAvailableAction
import com.jaydocoder.plateview.component.glass.GlassSurface
import com.jaydocoder.plateview.component.glass.GlassPill
import com.jaydocoder.plateview.component.glass.LiquidGlassDialog
import com.jaydocoder.plateview.component.glass.LiquidGlassInput
import com.jaydocoder.plateview.domain.admin.UserUpdatePolicy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val auditTimeFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy年MM月dd日 HH:mm:ss")
    .withZone(ZoneId.of("Asia/Shanghai"))

@Composable
fun AdminWorkspaceRoute(
    onNavigateUp: () -> Unit,
    onOpenUpdate: (() -> Unit)? = null,
    onOpenSchedulePlanner: () -> Unit = {},
    viewModel: AdminWorkspaceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::uploadImport) },
    )
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::uploadUserAvatar) },
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
        onVehicleStatusFilterChanged = viewModel::updateVehicleStatusFilter,
        onRequestVehicleStatusChange = viewModel::requestVehicleStatusChange,
        onDismissVehicleStatusChange = viewModel::dismissVehicleStatusChange,
        onConfirmVehicleStatusChange = viewModel::confirmVehicleStatusChange,
        onCreateUser = viewModel::createUser,
        onEditUser = viewModel::editUser,
        onUserEditorChanged = viewModel::updateUserEditor,
        onDismissUserEditor = viewModel::dismissUserEditor,
        onSaveUser = viewModel::saveUser,
        onChooseUserAvatar = { avatarPicker.launch(SUPPORTED_AVATAR_MIME_TYPES) },
        onDeleteUserAvatar = viewModel::deleteUserAvatar,
        onRequestCacheReset = viewModel::requestCacheReset,
        onDismissCacheReset = viewModel::dismissCacheReset,
        onConfirmCacheReset = viewModel::confirmCacheReset,
        onClientPolicyChanged = viewModel::updateClientPolicyEditor,
        onTestApiEndpoint = viewModel::testApiEndpoint,
        onTestUpdateEndpoint = viewModel::testUpdateEndpoint,
        onSaveClientPolicyLimits = viewModel::saveClientPolicyLimits,
        onSaveApiEndpoint = viewModel::saveApiEndpoint,
        onSaveUpdateEndpoint = viewModel::saveUpdateEndpoint,
        onDismissPolicySaveFeedback = viewModel::dismissPolicySaveFeedback,
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
        onCorrectWechatWorkOrder = viewModel::correctWechatWorkOrder,
        onAssociateWechatImage = viewModel::associateWechatImage,
        onRemoveWechatImageAssociation = viewModel::removeWechatImageAssociation,
        onIgnoreWechatImage = viewModel::ignoreWechatImage,
        onOpenWechatAttachment = viewModel::openWechatAttachment,
        onCloseWechatAttachment = viewModel::closeWechatAttachment,
        onSearchWechatWorkOrders = viewModel::searchWechatWorkOrders,
        onSaveWechatPassageSender = viewModel::saveWechatPassageSender,
        onOpenUpdate = onOpenUpdate,
        onOpenSchedulePlanner = onOpenSchedulePlanner,
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
    onDeactivateVehicle: (ManagedVehicleSummary) -> Unit = {},
    onDismissVehicleDeactivation: () -> Unit = {},
    onConfirmVehicleDeactivation: () -> Unit = {},
    onVehicleStatusFilterChanged: (VehicleStatusFilter) -> Unit = {},
    onRequestVehicleStatusChange: (ManagedVehicleSummary, String) -> Unit = { _, _ -> },
    onDismissVehicleStatusChange: () -> Unit = {},
    onConfirmVehicleStatusChange: () -> Unit = {},
    onCreateUser: () -> Unit,
    onEditUser: (Long) -> Unit,
    onUserEditorChanged: ((UserEditorState) -> UserEditorState) -> Unit,
    onDismissUserEditor: () -> Unit,
    onSaveUser: () -> Unit,
    onChooseUserAvatar: () -> Unit = {},
    onDeleteUserAvatar: () -> Unit = {},
    onRequestCacheReset: (Long) -> Unit = {},
    onDismissCacheReset: () -> Unit = {},
    onConfirmCacheReset: () -> Unit = {},
    onClientPolicyChanged: ((ClientPolicyEditorState) -> ClientPolicyEditorState) -> Unit = {},
    onTestApiEndpoint: () -> Unit = {},
    onTestUpdateEndpoint: () -> Unit = {},
    onSaveClientPolicyLimits: () -> Unit = {},
    onSaveApiEndpoint: () -> Unit = {},
    onSaveUpdateEndpoint: () -> Unit = {},
    onDismissPolicySaveFeedback: () -> Unit = {},
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
    onCorrectWechatWorkOrder: (Long, String, String) -> Unit = { _, _, _ -> },
    onAssociateWechatImage: (Long, Long) -> Unit = { _, _ -> },
    onRemoveWechatImageAssociation: (Long) -> Unit = {},
    onIgnoreWechatImage: (Long) -> Unit = {},
    onOpenWechatAttachment: (WechatSyncIssue) -> Unit = {},
    onCloseWechatAttachment: () -> Unit = {},
    onSearchWechatWorkOrders: (Long, String) -> Unit = { _, _ -> },
    onSaveWechatPassageSender: (com.jaydocoder.plateview.domain.admin.WechatPassageSender) -> Unit = {},
    onOpenUpdate: (() -> Unit)? = null,
    onOpenSchedulePlanner: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier.testTag("admin_top_bar"),
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
                    containerColor = Color.Transparent,
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
            val visibleTabs = AdminTab.entries.filter {
                it !in setOf(AdminTab.WechatSync, AdminTab.DataAccess) || uiState.isPrimaryAdministrator
            }
            ScrollableTabRow(
                selectedTabIndex = visibleTabs.indexOf(uiState.tab).coerceAtLeast(0),
                edgePadding = PlateViewDimensions.pageHorizontal,
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val selectedPosition = tabPositions[visibleTabs.indexOf(uiState.tab).coerceAtLeast(0)]
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = selectedPosition.left)
                                .width(selectedPosition.width)
                                .height(3.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                ),
                        )
                    }
                }
            ) {
                visibleTabs.forEach { tab ->
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
            
            uiState.failure?.let { AdminFailureStrip(it, onRefresh) }
            
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading) {
                    LoadingPane()
                } else {
                    when (uiState.tab) {
                        AdminTab.Dashboard -> DashboardPane(
                            vehiclesCount = uiState.vehicleTotalCount,
                            usersCount = uiState.dashboardUserCount,
                            importsCount = uiState.dashboardImportCount,
                            showSchedulePlanner = uiState.showSchedulePlanner,
                            showWechatSync = uiState.showWechatSync,
                            showPrimaryAdministration = uiState.isPrimaryAdministrator,
                            onTabSelected = onTabSelected,
                            onOpenSchedulePlanner = onOpenSchedulePlanner,
                        )

                        AdminTab.Vehicles -> VehiclesPane(
                            items = uiState.vehicles,
                            searchQuery = uiState.vehicleSearchQuery,
                            statusFilter = uiState.vehicleStatusFilter,
                            totalCount = uiState.vehicleTotalCount,
                            isPageLoading = uiState.isVehiclePageLoading,
                            isSaving = uiState.isSaving,
                            onSearchQueryChanged = onVehicleSearchQueryChanged,
                            onStatusFilterChanged = onVehicleStatusFilterChanged,
                            onLoadMore = onLoadMoreVehicles,
                            onCreate = onCreateVehicle,
                            onEdit = onEditVehicle,
                            onStatusChange = onRequestVehicleStatusChange,
                        )

                        AdminTab.Users -> UsersPane(
                            items = uiState.users,
                            avatars = uiState.userAvatars,
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

                        AdminTab.WechatSync -> if (uiState.isPrimaryAdministrator) {
                            WechatSyncPane(
                                items = uiState.wechatSyncSources,
                                issues = uiState.wechatSyncIssues,
                                attachmentFiles = uiState.wechatAttachmentFiles,
                                attachmentFailures = uiState.wechatAttachmentFailures,
                                totalAttachmentCount = uiState.totalWechatAttachmentCount,
                                completedAttachmentCount = uiState.completedWechatAttachmentCount,
                                pendingAttachmentCount = uiState.pendingWechatAttachmentCount,
                                integrity = uiState.wechatSyncIntegrity,
                                cacheStatus = uiState.wechatCacheStatus,
                                workOrderCandidates = uiState.wechatWorkOrderCandidates,
                                senders = uiState.wechatPassageSenders,
                                isSaving = uiState.isSaving,
                                onCorrectWorkOrder = onCorrectWechatWorkOrder,
                                onAssociateImage = onAssociateWechatImage,
                                onRemoveImageAssociation = onRemoveWechatImageAssociation,
                                onIgnoreImage = onIgnoreWechatImage,
                                onOpenAttachment = onOpenWechatAttachment,
                                onSearchWorkOrders = onSearchWechatWorkOrders,
                                onSavePassageSender = onSaveWechatPassageSender,
                            )
                        }

                        AdminTab.DataAccess -> if (uiState.isPrimaryAdministrator) {
                            DataAccessPane(
                                editor = uiState.clientPolicy,
                                apiTestMessage = uiState.apiEndpointTestMessage,
                                updateTestMessage = uiState.updateEndpointTestMessage,
                                onChanged = onClientPolicyChanged,
                                onTestApiEndpoint = onTestApiEndpoint,
                                onTestUpdateEndpoint = onTestUpdateEndpoint,
                                onSaveLimits = onSaveClientPolicyLimits,
                                onSaveApi = onSaveApiEndpoint,
                                onSaveUpdate = onSaveUpdateEndpoint,
                                policySavingAction = uiState.policySavingAction,
                            )
                        }
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
            creatableCategories = uiState.creatableVehicleCategories,
            canChangeCategory = uiState.canChangeVehicleCategory,
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
            avatar = editor.id?.let(uiState.userAvatars::get),
            onChanged = onUserEditorChanged,
            onDismiss = onDismissUserEditor,
            onSave = onSaveUser,
            onChooseAvatar = onChooseUserAvatar,
            onDeleteAvatar = onDeleteUserAvatar,
            onRequestCacheReset = onRequestCacheReset,
            cacheResetStatus = editor.id?.let(uiState.cacheResetStatuses::get),
        )
    }
    uiState.pendingVehicleStatusChange?.let { pendingChange ->
        VehicleStatusChangeDialog(
            pendingChange = pendingChange,
            onDismiss = onDismissVehicleStatusChange,
            onConfirm = onConfirmVehicleStatusChange,
        )
    }
    uiState.pendingCacheResetUser?.let { user ->
        LiquidGlassDialog(onDismissRequest = onDismissCacheReset) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("清除客户端缓存", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("将向 ${user.username} 的所有设备发送清缓存指令。登录状态和服务器地址会保留，数据随后自动重建。")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissCacheReset) { Text("取消") }
                    Button(onClick = onConfirmCacheReset, enabled = !uiState.isSaving) { Text("确认发送") }
                }
            }
        }
    }
    uiState.policySaveFeedback?.let { feedback ->
        LiquidGlassDialog(onDismissRequest = onDismissPolicySaveFeedback) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = feedback.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (feedback.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(feedback.message)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismissPolicySaveFeedback) { Text("知道了") }
                }
            }
        }
    }
    uiState.selectedWechatAttachment?.let { issue ->
        val attachment = issue.imageId?.let(uiState.wechatAttachmentFiles::get)
        AttachmentViewerDialog(
            title = issue.fileName ?: if (issue.attachmentKind == "PDF") "PDF附件" else "微信图片",
            file = attachment?.file,
            kind = issue.attachmentKind ?: "IMAGE",
            variant = attachment?.variant ?: "original",
            pageCount = issue.pageCount ?: 1,
            failureMessage = uiState.wechatAttachmentFailure?.message,
            onDismissRequest = onCloseWechatAttachment,
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
private fun AdminFailureStrip(
    failure: com.jaydocoder.plateview.data.network.AppError,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlateViewDimensions.pageHorizontal, vertical = PlateViewDimensions.compactSpacing),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
    ) {
        Column(modifier = Modifier.padding(PlateViewDimensions.itemSpacing)) {
            Text(failure.operation, style = MaterialTheme.typography.labelLarge)
            Text(failure.message, style = MaterialTheme.typography.bodyMedium)
            Text("诊断编号：${failure.requestId}", style = MaterialTheme.typography.labelSmall)
            if (failure.retryable) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

@Composable
private fun DashboardPane(
    vehiclesCount: Int,
    usersCount: Int,
    importsCount: Int,
    showSchedulePlanner: Boolean,
    showWechatSync: Boolean,
    showPrimaryAdministration: Boolean,
    onTabSelected: (AdminTab) -> Unit,
    onOpenSchedulePlanner: () -> Unit,
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
        if (showSchedulePlanner) {
            item {
                DashboardCard("排班规划", "模板", Icons.Outlined.CalendarMonth, MaterialTheme.colorScheme.primary) { onOpenSchedulePlanner() }
            }
        }
        if (showWechatSync) {
            item {
                DashboardCard(
                    title = "微信同步",
                    subtitle = "车单与图片",
                    icon = Icons.Outlined.Sync,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("admin_dashboard_wechat_sync"),
                ) {
                    onTabSelected(AdminTab.WechatSync)
                }
            }
        }
        if (showPrimaryAdministration) item {
            DashboardCard("数据访问控制", "数量与服务地址", Icons.Outlined.Security, MaterialTheme.colorScheme.tertiary) {
                onTabSelected(AdminTab.DataAccess)
            }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = modifier
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
        elevated = true,
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
    statusFilter: VehicleStatusFilter,
    totalCount: Int,
    isPageLoading: Boolean,
    isSaving: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onStatusFilterChanged: (VehicleStatusFilter) -> Unit,
    onLoadMore: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onStatusChange: (ManagedVehicleSummary, String) -> Unit,
) {
    val listState = rememberLazyListState()
    val hasMoreItems = items.size < totalCount
    val shouldLoadMore by remember(listState, items.size, totalCount, isPageLoading) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            items.isNotEmpty() && hasMoreItems && !isPageLoading &&
                lastVisibleIndex >= layoutInfo.totalItemsCount - LOAD_MORE_TRIGGER_DISTANCE
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }

    LaunchedEffect(searchQuery, statusFilter) {
        listState.scrollToItem(0)
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
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                elevated = true,
            ) {
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                )
            }
        }
        item {
            VehicleStatusFilterSelector(selected = statusFilter, onSelected = onStatusFilterChanged)
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
            EmptyPane(if (searchQuery.isBlank()) "当前筛选条件下暂无车辆档案" else "未找到匹配的车辆档案")
        }
        items(items, key = ManagedVehicleSummary::id) { item ->
            AdminVehicleItem(item, onEdit, onStatusChange, isSaving)
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
    onStatusChange: (ManagedVehicleSummary, String) -> Unit,
    isSaving: Boolean
) {
    var showActions by rememberSaveable(item.id) { androidx.compose.runtime.mutableStateOf(false) }
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
                    Text(
                        item.plateNumber,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                    AdminStatusBadge(item.statusLabel(), item.status.statusTone())
                }
                Spacer(Modifier.height(PlateViewDimensions.tinySpacing))
                Text(item.categoryLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.vehicleType?.takeIf(String::isNotBlank)?.let { type ->
                    Text(type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
            Box {
                IconButton(onClick = { showActions = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "更多操作 ${item.plateNumber}",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑档案") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            showActions = false
                            onEdit(item.id)
                        },
                    )
                    if (item.status != "ACTIVE") {
                        DropdownMenuItem(
                            text = { Text("启用档案") },
                            leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                            enabled = !isSaving,
                            onClick = {
                                showActions = false
                                onStatusChange(item, "ACTIVE")
                            },
                        )
                    }
                    if (item.status != "STRICT_CHECK") {
                        DropdownMenuItem(
                            text = { Text("标记严查", color = StrictCheckActionColor) },
                            leadingIcon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = StrictCheckActionColor) },
                            enabled = !isSaving,
                            onClick = {
                                showActions = false
                                onStatusChange(item, "STRICT_CHECK")
                            },
                        )
                    }
                    if (item.status != "BLACKLISTED") {
                        DropdownMenuItem(
                            text = { Text("拉黑档案", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            enabled = !isSaving,
                            onClick = {
                                showActions = false
                                onStatusChange(item, "BLACKLISTED")
                            },
                        )
                    }
                    if (item.status != "DELETED") {
                        DropdownMenuItem(
                            text = { Text("删除档案", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            enabled = !isSaving,
                            onClick = {
                                showActions = false
                                onStatusChange(item, "DELETED")
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleStatusFilterSelector(
    selected: VehicleStatusFilter,
    onSelected: (VehicleStatusFilter) -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
    ) {
        Column(
            modifier = Modifier.padding(PlateViewDimensions.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.tinySpacing),
        ) {
            Text(
                "档案状态",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().testTag("admin_vehicle_status_filter"),
                horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
            ) {
                items(VehicleStatusFilter.entries, key = VehicleStatusFilter::name) { filter ->
                    GlassPill(
                        selected = filter == selected,
                        modifier = Modifier.testTag("admin_vehicle_status_filter_${filter.name}"),
                        onClick = { onSelected(filter) },
                    ) {
                        Text(
                            text = filter.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (filter == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersPane(
    items: List<ManagedUser>,
    avatars: Map<Long, com.jaydocoder.plateview.feature.auth.AvatarCacheEntry>,
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
                    com.jaydocoder.plateview.feature.profile.AvatarImage(
                        entry = avatars[item.id] ?: com.jaydocoder.plateview.feature.auth.AvatarCacheEntry(null, null, item.avatarVersion),
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(item.roleLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    AdminStatusBadge(
                        item.statusLabel(),
                        if (item.status == "ACTIVE") AdminStatusTone.Positive else AdminStatusTone.Warning,
                    )
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
    val sortedItems = remember(items) { items.sortedByDescending { it.createdAt.orEmpty() } }
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
        items(sortedItems, key = ManagedImportBatchSummary::id) { item ->
            GlassSurface(
                modifier = Modifier.fillMaxWidth().clickable { onOpenBatch(item.id) },
                shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
                elevated = true,
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
                        AdminStatusBadge(
                            item.status.importStatusLabel(),
                            if (item.status == "PUBLISHED") AdminStatusTone.Positive else AdminStatusTone.Neutral,
                        )
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
        modifier = Modifier.fillMaxSize().testTag("admin_audit_list"),
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
                    label = filter.actionType?.auditActionLabel() ?: "全部操作",
                    options = listOf(AuditSelection<String?>(null, "全部操作")) + actionTypes.map { AuditSelection<String?>(it, it.auditActionLabel()) },
                    onSelected = onActionTypeChanged,
                    testTag = "audit_action_selector",
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
    testTag: String? = null,
) {
    var expanded by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Box(modifier = if (testTag == null) modifier else modifier.testTag(testTag)) {
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
                    Text(item.resultStatus.auditResultLabel(), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
            Text(
                formatAuditTime(item.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.actionType.auditActionLabel(),
            modifier = Modifier.testTag("audit_entry_action_${item.id}"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${item.actorUsername ?: "系统"} · ${item.targetType.auditTargetLabel()}${item.targetId?.let { " #$it" }.orEmpty()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

private fun formatAuditTime(value: String): String = runCatching {
    auditTimeFormatter.format(Instant.parse(value))
}.getOrElse { value }

private fun String.auditActionLabel(): String = when (this) {
    "ADMIN_ACCESS" -> "尝试访问管理功能"
    "AUDIT_LIST" -> "查看审计日志"
    "IMPORT_LIST" -> "查看导入批次"
    "IMPORT_PREVIEW" -> "预览导入数据"
    "IMPORT_VIEW" -> "查看导入批次"
    "IMPORT_VIEW_DETAIL" -> "查看导入差异"
    "IMPORT_RESOLUTION" -> "确认导入差异"
    "IMPORT_PUBLISH" -> "正式发布导入"
    "IMPORT_ROLLBACK" -> "撤销导入发布"
    "LOGIN" -> "登录应用"
    "LOGOUT" -> "退出登录"
    "USER_LIST" -> "查看账号列表"
    "USER_CREATE" -> "创建账号"
    "USER_UPDATE" -> "更新账号资料"
    "USER_UPDATE_POLICY" -> "更新升级策略"
    "USER_DATA_ACCESS_UPDATE" -> "更新数据访问权限"
    "USER_AVATAR_UPDATE" -> "更新账号头像"
    "USER_AVATAR_DELETE" -> "删除账号头像"
    "VEHICLE_LIST" -> "查看车辆档案"
    "VEHICLE_CREATE" -> "新增车辆档案"
    "VEHICLE_VIEW", "VEHICLE_DETAIL_VIEW" -> "查看车辆详情"
    "VEHICLE_UPDATE" -> "更新车辆档案"
    "VEHICLE_STATUS_ACTIVE" -> "设为启用"
    "VEHICLE_STATUS_STRICT_CHECK" -> "标记严查"
    "VEHICLE_STATUS_BLACKLISTED" -> "设为拉黑"
    "VEHICLE_STATUS_INACTIVE" -> "设为失效"
    "VEHICLE_STATUS_DELETED" -> "删除车辆档案"
    "SCHEDULE_ADMIN_ACCESS" -> "尝试管理排班"
    "SCHEDULE_CONFIGURATION_UPDATE" -> "更新排班配置"
    "SCHEDULE_TEMPLATE_APPLY" -> "应用排班模板"
    "SCHEDULE_TEMPLATE_CREATE" -> "创建排班模板"
    "SCHEDULE_TEMPLATE_DELETE" -> "删除排班模板"
    "SCHEDULE_TEMPLATE_UPDATE" -> "更新排班模板"
    else -> "其他操作"
}

private fun String.auditTargetLabel(): String = when (this) {
    "AUTH" -> "认证"
    "AUDIT" -> "审计日志"
    "IMPORT_BATCH" -> "导入批次"
    "IMPORT_ROW" -> "导入记录"
    "SCHEDULE" -> "排班"
    "SCHEDULE_TEMPLATE" -> "排班模板"
    "SESSION" -> "会话"
    "USER" -> "账号"
    "VEHICLE" -> "车辆档案"
    else -> "管理对象"
}

private fun String.auditResultLabel(): String = when (this) {
    "SUCCESS" -> "成功"
    "FAILURE" -> "失败"
    "DENIED" -> "已拒绝"
    else -> "异常"
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
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        ) {
            Text(metric, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AdminStatusBadge(label: String, tone: AdminStatusTone) {
    val containerColor = when (tone) {
        AdminStatusTone.Positive -> MaterialTheme.colorScheme.primaryContainer
        AdminStatusTone.Caution -> StrictCheckContainerColor
        AdminStatusTone.Warning -> MaterialTheme.colorScheme.errorContainer
        AdminStatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (tone) {
        AdminStatusTone.Positive -> MaterialTheme.colorScheme.onPrimaryContainer
        AdminStatusTone.Caution -> StrictCheckActionColor
        AdminStatusTone.Warning -> MaterialTheme.colorScheme.onErrorContainer
        AdminStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(PlateViewDimensions.cornerSmall)) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when (tone) {
                    AdminStatusTone.Positive -> Icons.Outlined.CheckCircle
                    AdminStatusTone.Caution -> Icons.Outlined.ErrorOutline
                    else -> Icons.Outlined.Block
                },
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
    creatableCategories: List<String>,
    canChangeCategory: Boolean,
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
        item { EditorSectionHeading("车辆信息", "车牌与分类") }
        item { EditorTextField("车牌号码", editor.plateNumber) { value -> onChanged { it.copy(plateNumber = value, error = null) } } }
        item {
            if (editor.id != null && !canChangeCategory) {
                ReadOnlyChoiceField("所属类别", editor.category.vehicleCategoryLabel())
            } else {
                val options = VEHICLE_CATEGORIES.filter { it.value in creatableCategories }
                ChoiceField("所属类别", editor.category, options) { value -> onChanged { it.copy(category = value, error = null) } }
            }
        }
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
    avatar: com.jaydocoder.plateview.feature.auth.AvatarCacheEntry?,
    onChanged: ((UserEditorState) -> UserEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onChooseAvatar: () -> Unit,
    onDeleteAvatar: () -> Unit,
    onRequestCacheReset: (Long) -> Unit,
    cacheResetStatus: com.jaydocoder.plateview.domain.admin.CacheResetStatus?,
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
            if (editor.canEditProfile) {
                item { EditorSectionHeading("账号资料", "仅主管理员可设置") }
                item { EditorTextField("真实姓名", editor.realName) { value -> onChanged { it.copy(realName = value, error = null) } } }
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("显示排班功能", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("新账号默认关闭", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Switch(
                                checked = editor.scheduleAccessEnabled,
                                onCheckedChange = { enabled -> onChanged { it.copy(scheduleAccessEnabled = enabled, error = null) } },
                                enabled = !isSaving,
                                modifier = Modifier.testTag("admin_schedule_access_switch"),
                            )
                        }
                    }
                }
            }
        } else if (editor.canEditProfile) {
            item { EditorSectionHeading("账号资料", "修改后目标账号需要重新登录") }
            item { EditorTextField("用户名", editor.username) { value -> onChanged { it.copy(username = value, error = null) } } }
            item { EditorTextField("真实姓名", editor.realName) { value -> onChanged { it.copy(realName = value, error = null) } } }
            item {
                OutlinedTextField(
                    value = editor.password,
                    onValueChange = { value -> onChanged { it.copy(password = value, error = null) } },
                    label = { Text("新登录密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.jaydocoder.plateview.feature.profile.AvatarImage(
                        entry = avatar ?: com.jaydocoder.plateview.feature.auth.AvatarCacheEntry(null, null, 0L),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onChooseAvatar, enabled = !isSaving) { Text("更换头像") }
                    if (avatar?.file != null) {
                        TextButton(onClick = onDeleteAvatar, enabled = !isSaving) { Text("删除") }
                    }
                }
            }
            if (editor.originalUsername != "admin") {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("显示排班功能", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("切换后该账号需要重新登录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Switch(
                                checked = editor.scheduleAccessEnabled,
                                onCheckedChange = { enabled -> onChanged { it.copy(scheduleAccessEnabled = enabled, error = null) } },
                                enabled = !isSaving,
                                modifier = Modifier.testTag("admin_schedule_access_switch"),
                            )
                        }
                    }
                }
                if (editor.id != null && editor.canEditProfile) {
                    item {
                        UserEditorSwitch(
                            title = "更新策略",
                            description = "关闭时用户自行选择更新；开启后必须在线更新",
                            checked = editor.updatePolicy == UserUpdatePolicy.FORCED,
                            enabled = !isSaving,
                            testTag = "admin_update_policy_switch",
                            onCheckedChange = { enabled ->
                                onChanged { it.copy(updatePolicy = if (enabled) UserUpdatePolicy.FORCED else UserUpdatePolicy.OPTIONAL, error = null) }
                            },
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing)) {
                            Text("车辆数据访问范围", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("关闭后，目标账号重新登录并同步目录时不会再收到对应数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            UserEditorSwitch(
                                title = "其他长期通行车辆",
                                description = "允许查询和缓存其他长期通行车辆",
                                checked = editor.otherLongTermAccessEnabled,
                                enabled = !isSaving,
                                testTag = "admin_other_long_term_access_switch",
                                onCheckedChange = { enabled -> onChanged { it.copy(otherLongTermAccessEnabled = enabled, error = null) } },
                            )
                            UserEditorSwitch(
                                title = "村民车辆备注",
                                description = "允许查看和缓存村民车辆的备注内容",
                                checked = editor.residentRemarksAccessEnabled,
                                enabled = !isSaving,
                                testTag = "admin_resident_remarks_access_switch",
                                onCheckedChange = { enabled -> onChanged { it.copy(residentRemarksAccessEnabled = enabled, error = null) } },
                            )
                            UserEditorSwitch(
                                title = "微信车单数据",
                                description = "允许查询微信群车单、完整人员信息和相关图片",
                                checked = editor.wechatWorkOrderAccessEnabled,
                                enabled = !isSaving,
                                testTag = "admin_wechat_work_order_access_switch",
                                onCheckedChange = { enabled -> onChanged { it.copy(wechatWorkOrderAccessEnabled = enabled, error = null) } },
                            )
                            requireNotNull(editor.id).let { userId ->
                                OutlinedButton(
                                    onClick = { onRequestCacheReset(userId) },
                                    enabled = !isSaving,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("清除该账号客户端缓存")
                                }
                                cacheResetStatus?.let { status ->
                                    Text(
                                        text = "最近清理：${status.status.cacheResetStatusLabel()} · ${status.completedClientCount}/${status.expectedClientCount} 台设备",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { EditorSectionHeading("访问权限", "决定可访问的管理范围") }
        val canEditTargetAccess = editor.canEditProfile || editor.originalUsername != "admin"
        item {
            ChoiceField("分配角色", editor.role, USER_ROLES, enabled = canEditTargetAccess) { value ->
                onChanged { it.copy(role = value) }
            }
        }
        item {
            ChoiceField("账号状态", editor.status, USER_STATUSES, enabled = canEditTargetAccess) { value ->
                onChanged { it.copy(status = value) }
            }
        }
        editor.error?.let { message -> item { EditorErrorMessage(message) } }
    }
}

@Composable
private fun UserEditorSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.testTag(testTag),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    AdminFullScreenWorkspace(
        title = title,
        subtitle = subtitle,
        onDismiss = { if (!isSaving) onDismiss() },
        bottomBar = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("取消") }
            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
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
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = PlateViewDimensions.pageHorizontal)
                .testTag("admin_editor_content"),
            contentPadding = PaddingValues(vertical = PlateViewDimensions.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                ) {
                    Row(
                        modifier = Modifier.padding(PlateViewDimensions.itemSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = CircleShape,
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp).size(22.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("正在编辑", style = MaterialTheme.typography.labelMedium)
                            Text(identity, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun VehicleEditorLoadingDialog() {
    LiquidGlassDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
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

@Composable
private fun VehicleStatusChangeDialog(
    pendingChange: PendingVehicleStatusChange,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val vehicle = pendingChange.vehicle
    val targetStatus = pendingChange.targetStatus
    val title = when (targetStatus) {
        "ACTIVE" -> "启用车辆档案"
        "STRICT_CHECK" -> "标记车辆严查"
        "BLACKLISTED" -> "拉黑车辆档案"
        "DELETED" -> "删除车辆档案"
        else -> "更新车辆状态"
    }
    val description = when (targetStatus) {
        "ACTIVE" -> "${vehicle.plateNumber} 将恢复为启用状态，可正常核验。"
        "STRICT_CHECK" -> "${vehicle.plateNumber} 仍可在首页查询，并提示核实三证合一、车辆信息与驾驶人员信息。"
        "BLACKLISTED" -> "${vehicle.plateNumber} 仍可在首页查询，但会明确标注为已拉黑。"
        "DELETED" -> "${vehicle.plateNumber} 将从首页查询、车辆目录和详情中隐藏，管理记录会保留。"
        else -> "确认更新 ${vehicle.plateNumber} 的车辆状态。"
    }
    LiquidGlassDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(PlateViewDimensions.pageHorizontal)) {
            GlassSurface(
                shape = CircleShape,
                color = when (targetStatus) {
                    "ACTIVE" -> MaterialTheme.colorScheme.secondaryContainer
                    "STRICT_CHECK" -> StrictCheckContainerColor
                    else -> MaterialTheme.colorScheme.errorContainer
                },
                elevated = true,
            ) {
                Icon(
                    when (targetStatus) {
                        "ACTIVE" -> Icons.Outlined.CheckCircle
                        "STRICT_CHECK" -> Icons.Outlined.ErrorOutline
                        "DELETED" -> Icons.Outlined.DeleteOutline
                        else -> Icons.Outlined.Block
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                    tint = when (targetStatus) {
                        "ACTIVE" -> MaterialTheme.colorScheme.primary
                        "STRICT_CHECK" -> StrictCheckActionColor
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
            Spacer(Modifier.height(PlateViewDimensions.itemSpacing))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(PlateViewDimensions.tinySpacing))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(PlateViewDimensions.itemSpacing))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(PlateViewDimensions.compactSpacing))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.testTag("admin_confirm_vehicle_status"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (targetStatus) {
                            "STRICT_CHECK" -> StrictCheckActionColor
                            "BLACKLISTED", "DELETED" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                    ),
                ) {
                    Text(
                        when (targetStatus) {
                            "ACTIVE" -> "确认启用"
                            "STRICT_CHECK" -> "确认标记严查"
                            "BLACKLISTED" -> "确认拉黑"
                            else -> "确认删除"
                        },
                    )
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
    val sortedRows = remember(batch.rows) {
        batch.rows.sortedBy { row -> if (row.resolution == "PENDING") 0 else 1 }
    }
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

    AdminFullScreenWorkspace(
        title = "数据差异核对",
        subtitle = "",
        onDismiss = onDismiss,
        showBottomBarDivider = false,
        bottomBar = {
            if (batch.status == "PUBLISHED") {
                OutlinedButton(
                    onClick = onRollback,
                    enabled = !isSaving,
                    modifier = Modifier.testTag("admin_rollback_import"),
                ) { Text("撤销发布") }
            } else {
                Button(
                    onClick = onPublish,
                    enabled = !isSaving && batch.stats.pendingReviewRows == 0 && batch.stats.publishableRows > 0,
                    modifier = Modifier.testTag("admin_publish_import"),
                ) {
                    Text(if (batch.status == "ROLLED_BACK") "重新发布数据" else "正式发布数据")
                }
            }
        },
    ) { contentPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("admin_import_rows"),
                contentPadding = PaddingValues(
                    start = PlateViewDimensions.pageHorizontal,
                    end = PlateViewDimensions.pageHorizontal,
                    top = contentPadding.calculateTopPadding() + PlateViewDimensions.itemSpacing,
                    bottom = contentPadding.calculateBottomPadding() + PlateViewDimensions.itemSpacing,
                ),
                verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.tinySpacing)) {
                        Text(
                            batch.sourceFileName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            batch.status.importStatusLabel(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ImportBatchChangeSummary(batch)
                        ImportFilterSelector(
                            selected = filter,
                            enabled = !isSaving,
                            onSelected = onFilterChanged,
                        )
                    }
                }
                items(sortedRows, key = ManagedImportRow::id) { row ->
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
    }
}

@Composable
private fun ImportBatchChangeSummary(batch: ManagedImportBatch) {
    val changes = listOfNotNull(
        batch.stats.newRows.takeIf { it > 0 }?.let { "新增 $it" },
        batch.stats.updateRows.takeIf { it > 0 }?.let { "更新 $it" },
        batch.stats.reactivateRows.takeIf { it > 0 }?.let { "恢复 $it" },
        batch.stats.deactivateRows.takeIf { it > 0 }?.let { "待失效 $it" },
        batch.stats.errorRows.takeIf { it > 0 }?.let { "异常 $it" },
        "待确认 ${batch.stats.pendingReviewRows}",
    )
    if (changes.isNotEmpty()) {
        Text(
            changes.joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportFilterControl(
    option: ImportRowFilter,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val modifier = Modifier.testTag("admin_import_filter_${option.name}")
    if (enabled) {
        GlassPill(
            selected = selected,
            modifier = modifier,
            onClick = onClick,
        ) {
            Text(
                text = option.label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        GlassSurface(
            modifier = modifier,
            shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = option.label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportFilterSelector(
    selected: ImportRowFilter,
    enabled: Boolean,
    onSelected: (ImportRowFilter) -> Unit,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PlateViewDimensions.compactSpacing),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
    ) {
        Column(
            modifier = Modifier.padding(PlateViewDimensions.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
        ) {
            Text(
                text = "核对类型",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_import_filter_selector"),
                horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
            ) {
                items(ImportRowFilter.entries, key = ImportRowFilter::name) { option ->
                    ImportFilterControl(
                        option = option,
                        selected = selected == option,
                        enabled = enabled,
                        onClick = { onSelected(option) },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ImportRowItem(
    row: ManagedImportRow,
    isSaving: Boolean,
    onOpenDetail: (Long) -> Unit,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 122.dp)
            .clickable(enabled = !isSaving) { onOpenDetail(row.id) }
            .testTag("admin_import_row_${row.id}"),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        elevated = row.resolution == "PENDING",
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.itemSpacing)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.resolution == "PENDING") {
                    IceLakePendingDot(contentDescription = "待核对")
                    Spacer(Modifier.width(8.dp))
                }
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
            row.primarySubject?.let { Text(it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            row.warningMessage?.takeIf(String::isNotBlank)?.let { Text(it, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            row.errorMessage?.takeIf(String::isNotBlank)?.let { Text(it, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Text(row.importDetailLabel(), modifier = Modifier.padding(top = 10.dp).testTag("admin_import_row_detail_${row.id}"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
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
    AdminFullScreenWorkspace(
        title = row.importDetailLabel(),
        subtitle = row.plateNumber ?: "未识别车牌",
        onDismiss = onDismiss,
        showBottomBarDivider = false,
        bottomBar = {
            if (row.resolution == "PENDING") {
                OutlinedButton(
                    onClick = { onResolution(row.id, "SKIP") },
                    enabled = !isSaving,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(row.importSkipLabel())
                }
                Button(
                    onClick = { onResolution(row.id, "PUBLISH") },
                    enabled = !isSaving,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("admin_import_confirm_${row.id}"),
                ) { Text(row.importConfirmLabel()) }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    ) { contentPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = PlateViewDimensions.pageHorizontal,
                    end = PlateViewDimensions.pageHorizontal,
                    top = contentPadding.calculateTopPadding() + PlateViewDimensions.itemSpacing,
                    bottom = contentPadding.calculateBottomPadding() + PlateViewDimensions.itemSpacing,
                ),
                verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
            ) {
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = row.importActionColor(),
                        opacity = 0.72f,
                        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                        elevated = true,
                    ) {
                        Column(modifier = Modifier.padding(PlateViewDimensions.itemSpacing)) {
                            Text(row.importActionLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (row.sourceRowNumber > 0) {
                                Text("Excel 第${row.sourceRowNumber}行", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    row.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    row.warningMessage?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                }
                items(detail.sections, key = ManagedImportDiffSection::title) { section ->
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        opacity = 0.48f,
                        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                        elevated = true,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(PlateViewDimensions.itemSpacing),
                            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
                        ) {
                            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            section.fields.forEach { field ->
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(field.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        ImportDiffValue(
                                            label = "原值",
                                            value = field.before,
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            emphasized = false,
                                        )
                                        ImportDiffValue(
                                            label = "新值",
                                            value = field.after,
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.primary,
                                            emphasized = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (detail.sourceValues.isNotEmpty()) {
                    item {
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            opacity = 0.48f,
                            shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                            elevated = true,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(PlateViewDimensions.itemSpacing),
                                verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
                            ) {
                                Text("Excel 源字段", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                detail.sourceValues.forEach { value ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text(
                                            text = value.label,
                                            modifier = Modifier.width(84.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = value.value,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun ImportDiffValue(
    label: String,
    value: String?,
    modifier: Modifier,
    color: Color,
    emphasized: Boolean,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color.copy(alpha = if (emphasized) 1f else 0.82f),
        )
        Text(
            text = value.displayImportValue(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AdminFullScreenWorkspace(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    showBottomBarDivider: Boolean = true,
    bottomBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                if (subtitle.isNotBlank()) {
                                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                            }
                        },
                    )
                },
                bottomBar = {
                    Column {
                        if (showBottomBarDivider) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        CompatFlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding()
                                .padding(
                                    start = PlateViewDimensions.itemSpacing,
                                    top = PlateViewDimensions.compactSpacing,
                                    end = PlateViewDimensions.itemSpacing,
                                    bottom = 48.dp,
                                )
                                .testTag("admin_bottom_actions"),
                            horizontalArrangement = Arrangement.End,
                            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
                        ) {
                            bottomBar()
                        }
                    }
                },
                content = content,
            )
        }
    }
}

private val IceLakePendingColor = Color(0xFF3B8878)

@Composable
private fun IceLakePendingDot(contentDescription: String) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .background(IceLakePendingColor, CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .testTag("import_pending_dot"),
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
    LiquidGlassInput(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ChoiceField(
    label: String,
    selected: String,
    options: List<ChoiceOption>,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        CompatFlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option.value,
                    onClick = { if (enabled) onSelected(option.value) },
                    enabled = enabled,
                    label = { Text(option.label) },
                    shape = RoundedCornerShape(PlateViewDimensions.cornerSmall)
                )
            }
        }
    }
}

@Composable
private fun ReadOnlyChoiceField(label: String, value: String) {
    LiquidGlassInput(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = false,
    )
}

private fun AdminTab.label(): String = when (this) {
    AdminTab.Dashboard -> "概览"
    AdminTab.Vehicles -> "车辆档案"
    AdminTab.Users -> "账号角色"
    AdminTab.Imports -> "数据导入"
    AdminTab.Audit -> "审计日志"
    AdminTab.WechatSync -> "微信同步"
    AdminTab.DataAccess -> "数据访问控制"
}

private fun String.cacheResetStatusLabel(): String = when (this) {
    "WAITING" -> "等待设备"
    "SENT" -> "已发送"
    "PARTIAL" -> "部分完成"
    "COMPLETED" -> "全部完成"
    else -> "处理中"
}

@Composable
private fun DataAccessPane(
    editor: ClientPolicyEditorState?,
    apiTestMessage: String?,
    updateTestMessage: String?,
    onChanged: ((ClientPolicyEditorState) -> ClientPolicyEditorState) -> Unit,
    onTestApiEndpoint: () -> Unit,
    onTestUpdateEndpoint: () -> Unit,
    onSaveLimits: () -> Unit,
    onSaveApi: () -> Unit,
    onSaveUpdate: () -> Unit,
    policySavingAction: PolicySavingAction?,
) {
    if (editor == null) {
        LoadingPane()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PlateViewDimensions.pageHorizontal, PlateViewDimensions.pageVertical),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
    ) {
        item {
            AdminPaneHeading(
                title = "数据访问控制",
                description = "统一控制搜索数量、后台地址和更新下载地址",
                metric = "策略版本 ${editor.revision}",
                icon = Icons.Outlined.Security,
            )
        }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth(), elevated = true) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("首页结果数量", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("范围 0 至 50；0 表示普通账号禁止访问，admin 回退为 8 条。", style = MaterialTheme.typography.bodySmall)
                    PolicyNumberField("匹配车辆", editor.vehicleResultLimit) { value -> onChanged { it.copy(vehicleResultLimit = value) } }
                    PolicyNumberField("微信车单", editor.workOrderResultLimit) { value -> onChanged { it.copy(workOrderResultLimit = value) } }
                    PolicyNumberField("微信聊天记录", editor.wechatMessageResultLimit) { value -> onChanged { it.copy(wechatMessageResultLimit = value) } }
                    val limitsSaving = policySavingAction == PolicySavingAction.LIMITS
                    Button(
                        onClick = onSaveLimits,
                        enabled = !limitsSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (limitsSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("保存首页结果数量")
                        }
                    }
                }
            }
        }
        item {
            EndpointPolicyCard(
                title = "后台服务地址",
                value = editor.apiBaseUrl,
                previous = editor.previousApiBaseUrl,
                testMessage = apiTestMessage,
                isSaving = policySavingAction == PolicySavingAction.API_ENDPOINT,
                onValueChanged = { value -> onChanged { it.copy(apiBaseUrl = value) } },
                onTest = onTestApiEndpoint,
                onSave = onSaveApi,
                policySavingAction = policySavingAction,
            )
        }
        item {
            EndpointPolicyCard(
                title = "APK 更新服务地址",
                value = editor.updateBaseUrl,
                previous = editor.previousUpdateBaseUrl,
                testMessage = updateTestMessage,
                isSaving = policySavingAction == PolicySavingAction.UPDATE_ENDPOINT,
                onValueChanged = { value -> onChanged { it.copy(updateBaseUrl = value) } },
                onTest = onTestUpdateEndpoint,
                onSave = onSaveUpdate,
                policySavingAction = policySavingAction,
            )
        }
        item {
            Text("客户端领取：${editor.appliedClientCount}/${editor.clientCount} · 更新时间 ${editor.updatedAt}", style = MaterialTheme.typography.bodySmall)
            Text("最近确认：${editor.lastConfirmedAt?.let(::formatAuditTime) ?: "尚无客户端确认"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PolicyNumberField(label: String, value: String, onValueChanged: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.isEmpty() || input.all(Char::isDigit)) onValueChanged(input.take(2)) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerExtraLarge),
    )
}

@Composable
private fun EndpointPolicyCard(
    title: String,
    value: String,
    previous: String?,
    testMessage: String?,
    isSaving: Boolean,
    onValueChanged: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    policySavingAction: PolicySavingAction?,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), elevated = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(PlateViewDimensions.cornerExtraLarge),
            )
            previous?.let { Text("上一可用地址：$it", style = MaterialTheme.typography.bodySmall) }
            testMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTest, enabled = !isSaving) { Text("测试连接") }
                Button(onClick = onSave, enabled = !isSaving) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("保存并应用")
                    }
                }
            }
        }
    }
}

private fun actionForEndpoint(title: String): PolicySavingAction =
    if (title == "后台服务地址") PolicySavingAction.API_ENDPOINT else PolicySavingAction.UPDATE_ENDPOINT

@Composable
private fun WechatSyncPane(
    items: List<WechatSyncSource>,
    issues: List<WechatSyncIssue>,
    attachmentFiles: Map<Long, com.jaydocoder.plateview.domain.admin.CachedAdminAttachment>,
    attachmentFailures: Set<Long>,
    totalAttachmentCount: Int,
    completedAttachmentCount: Int,
    pendingAttachmentCount: Int,
    integrity: com.jaydocoder.plateview.domain.admin.WechatSyncIntegrity,
    cacheStatus: com.jaydocoder.plateview.domain.admin.WechatCacheStatusSummary,
    workOrderCandidates: Map<Long, List<com.jaydocoder.plateview.domain.admin.WechatWorkOrderSearchItem>>,
    senders: List<com.jaydocoder.plateview.domain.admin.WechatPassageSender>,
    isSaving: Boolean,
    onCorrectWorkOrder: (Long, String, String) -> Unit,
    onAssociateImage: (Long, Long) -> Unit,
    onRemoveImageAssociation: (Long) -> Unit,
    onIgnoreImage: (Long) -> Unit,
    onOpenAttachment: (WechatSyncIssue) -> Unit,
    onSearchWorkOrders: (Long, String) -> Unit,
    onSavePassageSender: (com.jaydocoder.plateview.domain.admin.WechatPassageSender) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("admin_wechat_sync_page"),
        contentPadding = PaddingValues(PlateViewDimensions.pageHorizontal),
        verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.itemSpacing),
    ) {
        item {
            Text("微信同步状态", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("电脑开机并登录微信后自动追赶未同步消息。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(PlateViewDimensions.cornerLarge), elevated = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("同步完整性", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("状态：${integrity.status}", color = if (integrity.status == "一致") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                    Text("未确认批次：${integrity.unconfirmedBatchCount} · 待重试附件：${integrity.retryTaskCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("仅元数据附件：${integrity.metadataOnlyAttachmentCount} · 失败任务：${integrity.failedTaskCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("客户端：${cacheStatus.clientCount} · 已缓存：${cacheStatus.completedCount} · 待下载：${cacheStatus.pendingCount} · 失败：${cacheStatus.failedCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("已完成 PDF：${cacheStatus.completedPdfCount} · 等待原文件：${cacheStatus.sourceUnavailableCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("已缓存大小：${cacheStatus.totalBytes / 1024 / 1024} MB", style = MaterialTheme.typography.bodyMedium)
                    cacheStatus.clients.take(8).forEach { client ->
                        Text(
                            "${if (client.current) "当前设备" else "设备 ${client.clientInstanceId.take(8)}"}：完成 ${client.completedCount} · PDF ${client.completedPdfCount} · 待下载 ${client.pendingCount} · 失败 ${client.failedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(items, key = { it.sourceKey }) { source ->
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(PlateViewDimensions.cornerLarge), elevated = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(source.displayName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(source.status.syncStatusLabel(), color = source.status.syncStatusColor(), fontWeight = FontWeight.SemiBold)
                    }
                    Text("最后心跳：${source.lastHeartbeatAt?.let(::formatAuditTime) ?: "尚未连接"}", style = MaterialTheme.typography.bodyMedium)
                    Text("最后上传：${source.lastUploadedAt?.let(::formatAuditTime) ?: "尚未上传"}", style = MaterialTheme.typography.bodyMedium)
                    if (source.backlogCount > 0) Text("等待同步：${source.backlogCount} 条", color = MaterialTheme.colorScheme.tertiary)
                    source.errorCode?.let { Text("异常：$it", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (items.isEmpty()) item { Text("采集电脑尚未上报同步状态", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Text("放行发送者", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("使用稳定微信账号识别业务放行消息，并配置应用内显示称呼。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(senders, key = { it.senderUsername }) { sender ->
            var alias by rememberSaveable(sender.senderUsername, sender.displayAlias) { androidx.compose.runtime.mutableStateOf(sender.displayAlias) }
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(PlateViewDimensions.cornerLarge), elevated = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sender.originalDisplayName ?: sender.senderUsername, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(sender.senderUsername, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LiquidGlassInput(value = alias, onValueChange = { alias = it }, label = { Text("应用显示称呼") }, singleLine = true)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Switch(
                            checked = sender.enabled,
                            onCheckedChange = { onSavePassageSender(sender.copy(displayAlias = alias.trim().ifEmpty { sender.displayAlias }, enabled = it)) },
                        )
                        Text(if (sender.enabled) "已启用通行语义识别" else "已停用通行语义识别", Modifier.padding(start = 8.dp).weight(1f))
                        Button(onClick = { onSavePassageSender(sender.copy(displayAlias = alias.trim())) }, enabled = !isSaving && alias.isNotBlank()) { Text("保存") }
                    }
                }
            }
        }
        item {
            Text("需要处理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (issues.isEmpty()) "当前没有上传异常、无法搜索消息或图片关联冲突" else "仅列出确实需要主管理员处理的异常",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttachmentAssociationMetric("关联文件总数", totalAttachmentCount, Modifier.weight(1f))
                AttachmentAssociationMetric("已完成", completedAttachmentCount, Modifier.weight(1f))
                AttachmentAssociationMetric("待处理", pendingAttachmentCount, Modifier.weight(1f))
            }
        }
        items(issues, key = { "${it.type}-${it.recordId}-${it.imageId}" }) { issue ->
            var orderNumber by rememberSaveable(issue.recordId) { androidx.compose.runtime.mutableStateOf("") }
            var plateNumber by rememberSaveable(issue.recordId) { androidx.compose.runtime.mutableStateOf("") }
                    var targetRecordId by rememberSaveable(issue.imageId) { androidx.compose.runtime.mutableStateOf("") }
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(PlateViewDimensions.cornerLarge), elevated = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(issue.type.syncIssueLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${issue.sourceName} · ${formatAuditTime(issue.sentAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(issue.summary, style = MaterialTheme.typography.bodyLarge)
                    issue.recordId?.let { Text("车单记录：$it", style = MaterialTheme.typography.labelLarge) }
                    issue.imageId?.let { Text("附件记录：$it", style = MaterialTheme.typography.labelLarge) }
                    issue.fileName?.let { Text("文件：$it", style = MaterialTheme.typography.bodyMedium) }
                    issue.imageId?.let { imageId ->
                        val original = attachmentFiles[imageId]
                        if (original != null || imageId !in attachmentFailures) {
                            AttachmentThumbnail(
                                file = original?.file,
                                kind = issue.attachmentKind ?: "IMAGE",
                                variant = original?.variant ?: "original",
                                contentDescription = if (issue.attachmentKind == "PDF") "PDF首页缩略图" else "附件原图",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 360.dp)
                                    .testTag("wechat_attachment_preview_$imageId")
                                    .clickable { onOpenAttachment(issue) },
                            )
                        } else {
                            OutlinedButton(
                                onClick = { onOpenAttachment(issue) },
                                modifier = Modifier.fillMaxWidth().testTag("wechat_attachment_retry_$imageId"),
                            ) {
                                Text("原文件加载失败，重新加载")
                            }
                        }
                    }
                    if (issue.recordId != null) {
                        LiquidGlassInput(
                            value = orderNumber,
                            onValueChange = { orderNumber = it },
                            label = { Text("修正单号") },
                            singleLine = true,
                        )
                        LiquidGlassInput(
                            value = plateNumber,
                            onValueChange = { plateNumber = it },
                            label = { Text("修正车牌") },
                            singleLine = true,
                        )
                        Button(
                            onClick = { onCorrectWorkOrder(issue.recordId, orderNumber, plateNumber) },
                            enabled = !isSaving && (orderNumber.isNotBlank() || plateNumber.isNotBlank()),
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("保存结构化字段") }
                    }
                    if (issue.imageId != null && issue.type in setOf("ATTACHMENT_CONFLICT", "IMAGE_CONFLICT")) {
                        Text("选择下方候选后会立即关联", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        issue.candidates.forEach { candidate ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(candidate.orderNumber ?: "未识别单号", fontWeight = FontWeight.Bold)
                                    Text(candidate.summary, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                                    Text(formatAuditTime(candidate.sentAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(
                                        onClick = { onAssociateImage(issue.imageId, candidate.recordId) },
                                        enabled = !isSaving,
                                        modifier = Modifier.align(Alignment.End),
                                    ) { Text("关联到 ${candidate.orderNumber ?: "此车单"}") }
                                }
                            }
                        }
                        LiquidGlassInput(
                            value = targetRecordId,
                            onValueChange = { targetRecordId = it.filter(Char::isDigit) },
                            label = { Text("输入单号查找其他车单") },
                            singleLine = true,
                        )
                        workOrderCandidates[issue.imageId]?.forEach { candidate ->
                            OutlinedButton(onClick = { onAssociateImage(issue.imageId, candidate.recordId) }, enabled = !isSaving) {
                                Text("${candidate.orderNumber ?: "未识别单号"} · ${formatAuditTime(candidate.sentAt)}", maxLines = 1)
                            }
                        }
                        Button(
                            onClick = { onSearchWorkOrders(issue.imageId, targetRecordId) },
                            enabled = !isSaving && targetRecordId.trim().length >= 2,
                        ) { Text("搜索候选车单") }
                    }
                    if (issue.imageId != null && issue.type != "ATTACHMENT_UNAVAILABLE") {
                        OutlinedButton(onClick = { onRemoveImageAssociation(issue.imageId) }, enabled = !isSaving) {
                            Text("解除当前关联")
                        }
                    }
                    if (issue.imageId != null) {
                        TextButton(onClick = { onIgnoreImage(issue.imageId) }, enabled = !isSaving) { Text("暂不关联") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentAssociationMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PlateViewDimensions.cornerSmall),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun String.syncIssueLabel(): String = when (this) {
    "UNSEARCHABLE_MESSAGE" -> "车单字段无法搜索"
    "ATTACHMENT_CONFLICT" -> "附件存在多个关联候选"
    "IMAGE_CONFLICT" -> "图片存在多个关联候选"
    "ATTACHMENT_UNAVAILABLE" -> "附件原文件暂不可用"
    else -> "微信同步异常"
}

private fun String.syncStatusLabel(): String = when (this) {
    "HEALTHY" -> "同步正常"
    "CATCHING_UP" -> "正在追赶"
    "WECHAT_NOT_READY" -> "微信未就绪"
    "KEY_MISSING" -> "缺少密钥"
    "UPLOAD_FAILED" -> "上传失败"
    else -> "电脑离线"
}

@Composable
private fun String.syncStatusColor(): Color = when (this) {
    "HEALTHY" -> MaterialTheme.colorScheme.primary
    "CATCHING_UP" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private enum class AdminStatusTone { Positive, Caution, Warning, Neutral }

private val StrictCheckActionColor = Color(0xFF9A6700)
private val StrictCheckContainerColor = Color(0xFFFFF1C7)

private fun ManagedVehicleSummary.statusLabel(): String = when (status) {
    "ACTIVE" -> "已启用"
    "STRICT_CHECK" -> "严查"
    "BLACKLISTED" -> "已拉黑"
    "INACTIVE" -> "已停用（已失效）"
    "DELETED" -> "已删除"
    else -> status
}

private fun String.statusTone(): AdminStatusTone = when (this) {
    "ACTIVE" -> AdminStatusTone.Positive
    "STRICT_CHECK" -> AdminStatusTone.Caution
    "BLACKLISTED", "INACTIVE", "DELETED" -> AdminStatusTone.Warning
    else -> AdminStatusTone.Neutral
}
private fun ManagedUser.roleLabel(): String = if (role == "ADMIN") "管理员" else "核验员"
private fun ManagedUser.statusLabel(): String = if (status == "ACTIVE") "正常" else "已禁用"
private fun String.importStatusLabel(): String = when (this) {
    "VALIDATED" -> "待发布"
    "PUBLISHED" -> "已发布"
    "ROLLED_BACK" -> "已撤销"
    else -> this
}

private data class ChoiceOption(val value: String, val label: String)

private fun String.vehicleCategoryLabel(): String = VEHICLE_CATEGORIES
    .firstOrNull { it.value == this }
    ?.label
    ?: this

private val VEHICLE_CATEGORIES = listOf(
    ChoiceOption("RESIDENT", "村民车辆"),
    ChoiceOption("SCENIC_UNIT", "驻景区单位"),
    ChoiceOption("SCENIC_ENTERPRISE", "驻景区企业"),
    ChoiceOption("CADRE", "干部车辆"),
    ChoiceOption("KANAS_TOURISM_DEVELOPMENT", "喀旅公司车辆"),
    ChoiceOption("OTHER_LONG_TERM", "其他长期通行车辆"),
)
private val USER_ROLES = listOf(ChoiceOption("USER", "普通用户"), ChoiceOption("ADMIN", "管理员"))
private val USER_STATUSES = listOf(ChoiceOption("ACTIVE", "启用"), ChoiceOption("DISABLED", "停用"))

private const val EXCEL_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val LEGACY_EXCEL_MIME_TYPE = "application/vnd.ms-excel"
private val SUPPORTED_AVATAR_MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")
private const val LOAD_MORE_TRIGGER_DISTANCE = 4
