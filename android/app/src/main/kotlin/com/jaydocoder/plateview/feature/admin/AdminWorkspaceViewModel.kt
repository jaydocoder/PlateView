package com.jaydocoder.plateview.feature.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.data.admin.AdminImportFileReader
import com.jaydocoder.plateview.data.admin.AdminUserAvatarRepository
import com.jaydocoder.plateview.data.network.AppError
import com.jaydocoder.plateview.data.network.AppErrorKind
import com.jaydocoder.plateview.data.network.AppErrorMapper
import com.jaydocoder.plateview.data.network.AppErrorTelemetry
import com.jaydocoder.plateview.domain.admin.AdminRepository
import com.jaydocoder.plateview.domain.admin.AuditFilter
import com.jaydocoder.plateview.domain.admin.AuditRange
import com.jaydocoder.plateview.domain.admin.AuditResult
import com.jaydocoder.plateview.domain.admin.ImportRowFilter
import com.jaydocoder.plateview.domain.admin.UserCreateCommand
import com.jaydocoder.plateview.domain.admin.UserUpdateCommand
import com.jaydocoder.plateview.domain.admin.WorkOrderCorrectionCommand
import com.jaydocoder.plateview.domain.admin.ClientPolicyUpdateCommand
import com.jaydocoder.plateview.domain.admin.ClientPolicyLimitsCommand
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class AdminWorkspaceViewModel @Inject constructor(
    private val repository: AdminRepository,
    private val sessionProvider: AuthSessionProvider,
    private val importFileReader: AdminImportFileReader,
    private val userAvatarRepository: AdminUserAvatarRepository? = null,
) : ViewModel() {
    private var vehicleSearchJob: Job? = null
    private var vehicleLoadJob: Job? = null
    private var vehicleRequestVersion = 0L
    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: AdminTab) {
        if (tab in setOf(AdminTab.Users, AdminTab.WechatSync, AdminTab.DataAccess) && !_uiState.value.isPrimaryAdministrator) return
        _uiState.update { it.copy(tab = tab, failure = null) }
        refresh()
    }

    fun correctWechatWorkOrder(recordId: Long, orderNumber: String, rawPlate: String) {
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            repository.correctWechatWorkOrder(
                accessToken,
                recordId,
                WorkOrderCorrectionCommand(orderNumber.trim().ifEmpty { null }, rawPlate.trim().ifEmpty { null }),
            )
            refreshWechatSyncOverview(accessToken, isSaving = false)
        }
    }

    fun associateWechatImage(imageId: Long, recordId: Long) {
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            repository.associateWechatImage(accessToken, imageId, recordId)
            refreshWechatSyncOverview(accessToken, isSaving = false)
        }
    }

    fun removeWechatImageAssociation(imageId: Long) {
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            repository.removeWechatImageAssociation(accessToken, imageId)
            refreshWechatSyncOverview(accessToken, isSaving = false)
        }
    }

    fun ignoreWechatImage(imageId: Long) {
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            repository.ignoreWechatImage(accessToken, imageId)
            refreshWechatSyncOverview(accessToken, isSaving = false)
        }
    }

    fun openWechatAttachment(issue: com.jaydocoder.plateview.domain.admin.WechatSyncIssue) {
        val imageId = issue.imageId ?: return
        val cachedOriginal = _uiState.value.wechatAttachmentFiles[imageId]?.takeIf { it.variant == "original" }
        _uiState.update {
            it.copy(
                selectedWechatAttachment = issue,
                isWechatAttachmentLoading = cachedOriginal == null,
                wechatAttachmentFailure = null,
                wechatAttachmentFailures = it.wechatAttachmentFailures - imageId,
            )
        }
        if (cachedOriginal != null) return
        viewModelScope.launch {
            val session = sessionProvider.session.first() ?: return@launch
            runCatching {
                repository.downloadWechatAttachment(
                    session.accessToken,
                    session.userId,
                    imageId,
                    "original",
                    issue.sha256,
                    issue.sourceQuality,
                )
            }
                .onSuccess { attachment ->
                    _uiState.update {
                        it.copy(
                            wechatAttachmentFiles = it.wechatAttachmentFiles + (imageId to attachment),
                            wechatAttachmentFailures = it.wechatAttachmentFailures - imageId,
                            isWechatAttachmentLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isWechatAttachmentLoading = false,
                            wechatAttachmentFailure = AppErrorMapper.map("读取微信附件", error),
                            wechatAttachmentFailures = it.wechatAttachmentFailures + imageId,
                        )
                    }
                }
        }
    }

    fun closeWechatAttachment() {
        _uiState.update { it.copy(selectedWechatAttachment = null, isWechatAttachmentLoading = false, wechatAttachmentFailure = null) }
    }

    fun searchWechatWorkOrders(imageId: Long, keyword: String) {
        if (keyword.trim().length < 2) {
            _uiState.update { it.copy(wechatWorkOrderCandidates = it.wechatWorkOrderCandidates - imageId) }
            return
        }
        launchAdminAction { accessToken ->
            val candidates = repository.searchWechatWorkOrders(accessToken, keyword.trim())
            _uiState.update { it.copy(wechatWorkOrderCandidates = it.wechatWorkOrderCandidates + (imageId to candidates)) }
        }
    }

    fun saveWechatPassageSender(sender: com.jaydocoder.plateview.domain.admin.WechatPassageSender) {
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            repository.saveWechatPassageSender(accessToken, sender)
            _uiState.update { it.copy(wechatPassageSenders = repository.getWechatPassageSenders(accessToken), isSaving = false) }
        }
    }

    fun refresh() {
        if (_uiState.value.tab == AdminTab.Vehicles) {
            refreshVehicles()
            return
        }
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isLoading = true, failure = null) }
            when (_uiState.value.tab) {
                AdminTab.Dashboard -> loadDashboard(accessToken)
                AdminTab.Vehicles -> Unit
                AdminTab.Users -> loadUsers(accessToken)
                AdminTab.Imports -> _uiState.update { it.copy(importBatches = repository.listImportBatches(accessToken)) }
                AdminTab.Audit -> loadAuditEntries(accessToken, reset = true)
                AdminTab.WechatSync -> {
                    val session = sessionProvider.session.first()
                    if (session?.username != "admin" || session.role != "ADMIN") throw IllegalStateException("仅admin账号可以查看微信同步")
                    val overview = repository.getWechatSyncOverview(accessToken)
                    val issues = overview.issues
                    _uiState.update {
                        it.copy(
                            wechatSyncSources = repository.getWechatSyncStatus(accessToken),
                            wechatSyncIssues = issues,
                            totalWechatAttachmentCount = overview.totalAttachmentCount,
                            completedWechatAttachmentCount = overview.completedAttachmentCount,
                            pendingWechatAttachmentCount = overview.pendingAttachmentCount,
                            wechatPassageSenders = repository.getWechatPassageSenders(accessToken),
                        )
                    }
                    viewModelScope.launch {
                        issues.mapNotNull { issue -> issue.imageId?.let { it to issue } }.distinctBy { it.first }.forEach { (imageId, issue) ->
                            runCatching {
                                repository.downloadWechatAttachment(
                                    accessToken,
                                    session.userId,
                                    imageId,
                                    "original",
                                    issue.sha256,
                                    issue.sourceQuality,
                                )
                            }
                                .onSuccess { attachment ->
                                    _uiState.update { state ->
                                        state.copy(
                                            wechatAttachmentFiles = state.wechatAttachmentFiles + (imageId to attachment),
                                            wechatAttachmentFailures = state.wechatAttachmentFailures - imageId,
                                        )
                                    }
                                }
                                .onFailure {
                                    _uiState.update { state ->
                                        state.copy(wechatAttachmentFailures = state.wechatAttachmentFailures + imageId)
                                    }
                                }
                        }
                    }
                }
                AdminTab.DataAccess -> {
                    requirePrimaryAdministrator()
                    loadClientPolicy(accessToken)
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun updateClientPolicyEditor(transform: (ClientPolicyEditorState) -> ClientPolicyEditorState) {
        _uiState.update { state -> state.clientPolicy?.let { state.copy(clientPolicy = transform(it), failure = null) } ?: state }
    }

    fun testApiEndpoint() = launchAdminAction("测试后台地址") { accessToken ->
        requirePrimaryAdministrator()
        val editor = requireNotNull(_uiState.value.clientPolicy)
        val message = repository.testApiEndpoint(accessToken, editor.apiBaseUrl)
        _uiState.update { it.copy(apiEndpointTestMessage = message) }
    }

    fun testUpdateEndpoint() = launchAdminAction("测试更新地址") { accessToken ->
        requirePrimaryAdministrator()
        val editor = requireNotNull(_uiState.value.clientPolicy)
        val message = repository.testUpdateEndpoint(accessToken, editor.updateBaseUrl)
        _uiState.update { it.copy(updateEndpointTestMessage = message) }
    }

    fun saveClientPolicy() = launchAdminAction("保存数据访问策略") { accessToken ->
        requirePrimaryAdministrator()
        val editor = requireNotNull(_uiState.value.clientPolicy)
        _uiState.update { it.copy(isSaving = true, failure = null) }
        val policy = repository.updateClientPolicy(
            accessToken,
            ClientPolicyUpdateCommand(
                vehicleResultLimit = editor.vehicleResultLimit.toIntOrNull() ?: error("匹配车辆数量无效"),
                workOrderResultLimit = editor.workOrderResultLimit.toIntOrNull() ?: error("微信车单数量无效"),
                wechatMessageResultLimit = editor.wechatMessageResultLimit.toIntOrNull() ?: error("微信聊天数量无效"),
                apiBaseUrl = editor.apiBaseUrl,
                updateBaseUrl = editor.updateBaseUrl,
            ),
        )
        _uiState.update { it.copy(clientPolicy = policy.toEditor(), isSaving = false) }
    }

    fun saveClientPolicyLimits() = launchAdminAction("保存首页结果数量", policyAction = PolicySavingAction.LIMITS) { accessToken ->
        requirePrimaryAdministrator()
        val editor = requireNotNull(_uiState.value.clientPolicy)
        _uiState.update { it.copy(isSaving = true, policySavingAction = PolicySavingAction.LIMITS, policySaveFeedback = null, failure = null) }
        val policy = repository.updateClientPolicyLimits(
            accessToken,
            ClientPolicyLimitsCommand(
                vehicleResultLimit = editor.vehicleResultLimit.toIntOrNull() ?: error("匹配车辆数量无效"),
                workOrderResultLimit = editor.workOrderResultLimit.toIntOrNull() ?: error("微信车单数量无效"),
                wechatMessageResultLimit = editor.wechatMessageResultLimit.toIntOrNull() ?: error("微信聊天数量无效"),
            ),
        )
        _uiState.update {
            it.copy(
                clientPolicy = policy.toEditor(),
                isSaving = false,
                policySavingAction = null,
                policySaveFeedback = PolicySaveFeedback("首页结果数量已保存", "三个分类的返回数量已成功更新。", success = true),
            )
        }
    }

    fun saveApiEndpoint() = launchAdminAction("保存后台服务地址", policyAction = PolicySavingAction.API_ENDPOINT) { accessToken ->
        requirePrimaryAdministrator()
        val editor = requireNotNull(_uiState.value.clientPolicy)
        _uiState.update { it.copy(isSaving = true, policySavingAction = PolicySavingAction.API_ENDPOINT, policySaveFeedback = null, failure = null) }
        val policy = repository.updateApiEndpoint(accessToken, editor.apiBaseUrl)
        _uiState.update {
            it.copy(
                clientPolicy = policy.toEditor(),
                isSaving = false,
                policySavingAction = null,
                apiEndpointTestMessage = null,
                policySaveFeedback = PolicySaveFeedback("后台服务地址已保存", "新的后台服务地址已验证并应用。", success = true),
            )
        }
    }

    fun saveUpdateEndpoint() = launchAdminAction("保存APK更新服务地址", policyAction = PolicySavingAction.UPDATE_ENDPOINT) { accessToken ->
        requirePrimaryAdministrator()
        val editor = requireNotNull(_uiState.value.clientPolicy)
        _uiState.update { it.copy(isSaving = true, policySavingAction = PolicySavingAction.UPDATE_ENDPOINT, policySaveFeedback = null, failure = null) }
        val policy = repository.updateUpdateEndpoint(accessToken, editor.updateBaseUrl)
        _uiState.update {
            it.copy(
                clientPolicy = policy.toEditor(),
                isSaving = false,
                policySavingAction = null,
                updateEndpointTestMessage = null,
                policySaveFeedback = PolicySaveFeedback("APK更新服务地址已保存", "新的APK更新服务地址已验证并应用。", success = true),
            )
        }
    }

    fun dismissPolicySaveFeedback() = _uiState.update { it.copy(policySaveFeedback = null) }

    fun requestCacheReset(userId: Long) {
        if (!_uiState.value.isPrimaryAdministrator) return
        _uiState.update { state -> state.copy(pendingCacheResetUser = state.users.firstOrNull { it.id == userId }) }
    }

    fun dismissCacheReset() = _uiState.update { it.copy(pendingCacheResetUser = null) }

    fun confirmCacheReset() = launchAdminAction("发送清缓存指令") { accessToken ->
        requirePrimaryAdministrator()
        val user = requireNotNull(_uiState.value.pendingCacheResetUser)
        _uiState.update { it.copy(isSaving = true) }
        val status = repository.requestUserCacheReset(accessToken, user.id)
        _uiState.update {
            it.copy(
                isSaving = false,
                pendingCacheResetUser = null,
                cacheResetStatuses = it.cacheResetStatuses + (user.id to status),
            )
        }
    }

    private fun requirePrimaryAdministrator() {
        check(_uiState.value.isPrimaryAdministrator) { "仅admin主管理员可以管理数据访问策略" }
    }

    private suspend fun loadClientPolicy(accessToken: String) {
        val policy = repository.getClientPolicy(accessToken)
        _uiState.update {
            it.copy(
                clientPolicy = policy.toEditor(),
                cacheResetStatuses = policy.cacheResetStatuses.associateBy { status -> status.userId },
            )
        }
    }

    fun updateVehicleSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                vehicleSearchQuery = query,
                failure = null,
            )
        }
        refreshVehicles(delayMillis = VEHICLE_SEARCH_DEBOUNCE_MILLIS)
    }

    private suspend fun refreshWechatSyncOverview(accessToken: String, isSaving: Boolean) {
        val overview = repository.getWechatSyncOverview(accessToken)
        _uiState.update {
            it.copy(
                wechatSyncIssues = overview.issues,
                totalWechatAttachmentCount = overview.totalAttachmentCount,
                completedWechatAttachmentCount = overview.completedAttachmentCount,
                pendingWechatAttachmentCount = overview.pendingAttachmentCount,
                wechatSyncIntegrity = overview.integrity,
                wechatCacheStatus = overview.cacheStatus,
                isSaving = isSaving,
            )
        }
    }

    fun updateVehicleStatusFilter(filter: VehicleStatusFilter) {
        if (_uiState.value.vehicleStatusFilter == filter) return
        _uiState.update {
            it.copy(
                vehicleStatusFilter = filter,
                failure = null,
            )
        }
        refreshVehicles()
    }

    fun loadMoreVehicles() {
        if (_uiState.value.tab != AdminTab.Vehicles) return
        val state = _uiState.value
        if (state.isVehiclePageLoading || state.vehicles.size >= state.vehicleTotalCount) return
        val requestVersion = vehicleRequestVersion
        val query = state.vehicleSearchQuery
        val statusFilter = state.vehicleStatusFilter
        val offset = state.vehicles.size
        _uiState.update { it.copy(isVehiclePageLoading = true, failure = null) }
        vehicleLoadJob = launchAdminAction(
            operation = "加载更多车辆档案",
            shouldHandleFailure = { requestVersion == vehicleRequestVersion },
        ) { accessToken ->
            loadVehicles(
                accessToken = accessToken,
                reset = false,
                requestVersion = requestVersion,
                query = query,
                statusFilter = statusFilter,
                offset = offset,
            )
        }
    }

    fun updateAuditRange(range: AuditRange) = updateAuditFilter { it.copy(range = range) }

    fun updateAuditActor(actorId: Long?) = updateAuditFilter { it.copy(actorId = actorId) }

    fun updateAuditActionType(actionType: String?) = updateAuditFilter { it.copy(actionType = actionType) }

    fun updateAuditResult(result: AuditResult) = updateAuditFilter { it.copy(result = result) }

    fun loadMoreAuditEntries() {
        if (_uiState.value.tab != AdminTab.Audit) return
        launchAdminAction { accessToken -> loadAuditEntries(accessToken, reset = false) }
    }

    fun createVehicle() {
        val category = _uiState.value.creatableVehicleCategories.firstOrNull()
        if (category == null) {
            _uiState.update {
                it.copy(
                    failure = adminError(
                        operation = "新建车辆档案",
                        kind = AppErrorKind.Validation,
                        message = "当前账号没有可用的车辆建档权限",
                    ),
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                vehicleEditor = VehicleEditorState(category = category),
                isVehicleEditorLoading = false,
                failure = null,
            )
        }
    }

    fun editVehicle(vehicleId: Long) = launchAdminAction { accessToken ->
        if (_uiState.value.isVehicleEditorLoading) return@launchAdminAction
        _uiState.update { it.copy(isVehicleEditorLoading = true, failure = null) }
        val vehicle = repository.getVehicle(accessToken, vehicleId)
        _uiState.update { it.copy(isVehicleEditorLoading = false, vehicleEditor = vehicle.toEditor()) }
    }

    fun updateVehicleEditor(transform: (VehicleEditorState) -> VehicleEditorState) {
        _uiState.update { state -> state.copy(vehicleEditor = state.vehicleEditor?.let(transform)) }
    }

    fun dismissVehicleEditor() {
        _uiState.update { it.copy(vehicleEditor = null, isVehicleEditorLoading = false) }
    }

    fun saveVehicle() {
        val editor = _uiState.value.vehicleEditor ?: return
        editor.validate()?.let { message ->
            _uiState.update { it.copy(vehicleEditor = editor.copy(error = message)) }
            return
        }
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            if (editor.id == null) {
                repository.createVehicle(accessToken, editor.toCommand())
            } else {
                repository.updateVehicle(accessToken, editor.id, editor.version, editor.toCommand())
            }
            _uiState.update { it.copy(isSaving = false, vehicleEditor = null) }
            refreshVehicles()
        }
    }

    fun requestVehicleStatusChange(
        vehicle: com.jaydocoder.plateview.domain.admin.ManagedVehicleSummary,
        targetStatus: String,
    ) {
        if (vehicle.status == targetStatus) return
        _uiState.update { it.copy(pendingVehicleStatusChange = PendingVehicleStatusChange(vehicle, targetStatus)) }
    }

    fun dismissVehicleStatusChange() {
        _uiState.update { it.copy(pendingVehicleStatusChange = null) }
    }

    fun confirmVehicleStatusChange() {
        val pendingChange = _uiState.value.pendingVehicleStatusChange ?: return
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null, pendingVehicleStatusChange = null) }
            repository.updateVehicleStatus(
                accessToken,
                pendingChange.vehicle.id,
                pendingChange.vehicle.version,
                pendingChange.targetStatus,
            )
            _uiState.update { it.copy(isSaving = false) }
            refreshVehicles()
        }
    }

    fun createUser() {
        _uiState.update { state ->
            state.copy(
                userEditor = UserEditorState(canEditProfile = state.canManageOtherUserProfiles),
                failure = null,
            )
        }
    }

    fun editUser(userId: Long) {
        val user = _uiState.value.users.firstOrNull { it.id == userId } ?: return
        _uiState.update { state ->
            state.copy(userEditor = user.toEditor(state.canManageOtherUserProfiles), failure = null)
        }
    }

    fun updateUserEditor(transform: (UserEditorState) -> UserEditorState) {
        _uiState.update { state -> state.copy(userEditor = state.userEditor?.let(transform)) }
    }

    fun dismissUserEditor() {
        _uiState.update { it.copy(userEditor = null) }
    }

    fun saveUser() {
        val editor = _uiState.value.userEditor ?: return
        editor.validate()?.let { message ->
            _uiState.update { it.copy(userEditor = editor.copy(error = message)) }
            return
        }
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            if (editor.id == null) {
                repository.createUser(
                    accessToken,
                    UserCreateCommand(
                        username = editor.username.trim(),
                        password = editor.password,
                        role = editor.role,
                        realName = editor.realName.trim().takeIf { editor.canEditProfile },
                        scheduleAccessEnabled = editor.scheduleAccessEnabled && editor.canEditProfile,
                    ),
                )
            } else {
                repository.updateUser(
                    accessToken,
                    editor.id,
                    editor.version,
                    UserUpdateCommand(
                        role = editor.role,
                        status = editor.status,
                        username = editor.username.trim().takeIf { editor.canEditProfile && it != editor.originalUsername },
                        password = editor.password.takeIf { editor.canEditProfile && it.isNotBlank() },
                        realName = editor.realName.trim().takeIf { editor.canEditProfile && it != editor.originalRealName },
                        scheduleAccessEnabled = editor.scheduleAccessEnabled.takeIf { editor.canEditProfile && it != editor.originalScheduleAccessEnabled },
                        updatePolicy = editor.updatePolicy.takeIf { editor.canEditProfile && editor.originalUsername != "admin" && it != editor.originalUpdatePolicy },
                        otherLongTermAccessEnabled = editor.otherLongTermAccessEnabled.takeIf { editor.canEditProfile && editor.originalUsername != "admin" && it != editor.originalOtherLongTermAccessEnabled },
                        residentRemarksAccessEnabled = editor.residentRemarksAccessEnabled.takeIf { editor.canEditProfile && editor.originalUsername != "admin" && it != editor.originalResidentRemarksAccessEnabled },
                        wechatWorkOrderAccessEnabled = editor.wechatWorkOrderAccessEnabled.takeIf { editor.canEditProfile && editor.originalUsername != "admin" && it != editor.originalWechatWorkOrderAccessEnabled },
                    ),
                )
            }
            _uiState.update { it.copy(isSaving = false, userEditor = null) }
            loadUsers(accessToken)
        }
    }

    fun uploadUserAvatar(uri: Uri) {
        val editor = _uiState.value.userEditor ?: return
        val avatarRepository = userAvatarRepository ?: return
        if (editor.id == null || !editor.canEditProfile) return
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            val version = avatarRepository.upload(accessToken, editor.id, editor.version, uri)
            _uiState.update { state ->
                state.copy(isSaving = false, userEditor = state.userEditor?.copy(version = version))
            }
            loadUsers(accessToken)
        }
    }

    fun deleteUserAvatar() {
        val editor = _uiState.value.userEditor ?: return
        val avatarRepository = userAvatarRepository ?: return
        if (editor.id == null || !editor.canEditProfile) return
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            val version = avatarRepository.delete(accessToken, editor.id, editor.version)
            _uiState.update { state ->
                state.copy(isSaving = false, userEditor = state.userEditor?.copy(version = version))
            }
            loadUsers(accessToken)
        }
    }

    fun uploadImport(uri: Uri) = launchAdminAction { accessToken ->
        _uiState.update { it.copy(isSaving = true, failure = null) }
        val file = importFileReader.read(uri)
        val batch = repository.previewImport(accessToken, file.fileName, file.content)
        _uiState.update { it.copy(isSaving = false, isImportPageLoading = false, selectedImportBatch = batch) }
        loadImports(accessToken)
    }

    fun openImportBatch(batchId: Long) = launchAdminAction { accessToken ->
        _uiState.update { it.copy(importRowFilter = ImportRowFilter.REVIEW, selectedImportRowDetail = null) }
        loadImportBatch(accessToken, batchId, reset = true)
    }

    fun loadMoreImportRows() {
        val batch = _uiState.value.selectedImportBatch ?: return
        if (_uiState.value.isImportPageLoading || batch.rows.size >= batch.rowTotal) return
        launchAdminAction { accessToken ->
            loadImportBatch(accessToken, batch.id, reset = false)
        }
    }

    fun updateImportRowFilter(filter: ImportRowFilter) {
        val batchId = _uiState.value.selectedImportBatch?.id ?: return
        if (_uiState.value.importRowFilter == filter) return
        _uiState.update {
            it.copy(
                importRowFilter = filter,
                selectedImportRowDetail = null,
                isImportPageLoading = false,
                failure = null,
            )
        }
        launchAdminAction { accessToken -> loadImportBatch(accessToken, batchId, reset = true) }
    }

    fun openImportRowDetail(rowId: Long) {
        val batchId = _uiState.value.selectedImportBatch?.id ?: return
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isImportDetailLoading = true, failure = null) }
            val detail = repository.getImportRowDetail(accessToken, batchId, rowId)
            _uiState.update { it.copy(isImportDetailLoading = false, selectedImportRowDetail = detail) }
        }
    }

    fun dismissImportRowDetail() {
        _uiState.update { it.copy(selectedImportRowDetail = null, isImportDetailLoading = false) }
    }

    fun dismissImportBatch() {
        _uiState.update {
            it.copy(
                selectedImportBatch = null,
                selectedImportRowDetail = null,
                isImportPageLoading = false,
                isImportDetailLoading = false,
            )
        }
    }

    fun updateImportResolution(rowId: Long, resolution: String) {
        val batch = _uiState.value.selectedImportBatch ?: return
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            val updated = repository.updateImportResolution(accessToken, batch.id, rowId, resolution)
            _uiState.update { it.copy(isSaving = false, isImportPageLoading = false, selectedImportBatch = updated, selectedImportRowDetail = null) }
            loadImportBatch(accessToken, batch.id, reset = true)
            loadImports(accessToken)
        }
    }

    fun publishImport() {
        val batch = _uiState.value.selectedImportBatch ?: return
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            val updated = repository.publishImport(accessToken, batch.id)
            _uiState.update { it.copy(isSaving = false, isImportPageLoading = false, selectedImportBatch = updated) }
            loadImports(accessToken)
        }
    }

    fun rollbackImport() {
        val batch = _uiState.value.selectedImportBatch ?: return
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            val updated = repository.rollbackImport(accessToken, batch.id)
            _uiState.update { it.copy(isSaving = false, isImportPageLoading = false, selectedImportBatch = updated) }
            loadImports(accessToken)
        }
    }

    private suspend fun loadDashboard(accessToken: String) {
        val session = sessionProvider.session.first() ?: return
        val capabilities = repository.getVehicleCreationCapabilities(accessToken)
        val vehiclePage = repository.listVehicles(accessToken)
        val isPrimaryAdministrator = session.role == "ADMIN" && session.username == "admin"
        val users = if (isPrimaryAdministrator) repository.listUsers(accessToken) else emptyList()
        val batches = repository.listImportBatches(accessToken)
        if (_uiState.value.tab != AdminTab.Dashboard) return
        _uiState.update {
            it.copy(
                vehicles = vehiclePage.items,
                vehicleTotalCount = vehiclePage.total,
                creatableVehicleCategories = capabilities.creatableCategories,
                canChangeVehicleCategory = capabilities.canChangeVehicleCategory,
                users = users,
                importBatches = batches,
                isPrimaryAdministrator = isPrimaryAdministrator,
            )
        }
    }

    private fun com.jaydocoder.plateview.domain.admin.ClientPolicy.toEditor() = ClientPolicyEditorState(
        revision = revision,
        vehicleResultLimit = vehicleResultLimit.toString(),
        workOrderResultLimit = workOrderResultLimit.toString(),
        wechatMessageResultLimit = wechatMessageResultLimit.toString(),
        apiBaseUrl = apiBaseUrl,
        previousApiBaseUrl = previousApiBaseUrl,
        updateBaseUrl = updateBaseUrl,
        previousUpdateBaseUrl = previousUpdateBaseUrl,
        updatedAt = updatedAt,
        clientCount = clientCount,
        appliedClientCount = appliedClientCount,
        lastConfirmedAt = lastConfirmedAt,
    )

    private fun refreshVehicles(delayMillis: Long = 0L) {
        vehicleSearchJob?.cancel()
        vehicleLoadJob?.cancel()
        val requestVersion = ++vehicleRequestVersion
        val state = _uiState.value
        val query = state.vehicleSearchQuery
        val statusFilter = state.vehicleStatusFilter
        _uiState.update {
            it.copy(
                isLoading = false,
                isVehiclePageLoading = true,
                vehicles = emptyList(),
                vehicleTotalCount = 0,
                failure = null,
            )
        }
        val load = {
            vehicleLoadJob = launchAdminAction(
                operation = "筛选车辆档案",
                shouldHandleFailure = { requestVersion == vehicleRequestVersion },
            ) { accessToken ->
                loadVehicles(
                    accessToken = accessToken,
                    reset = true,
                    requestVersion = requestVersion,
                    query = query,
                    statusFilter = statusFilter,
                    offset = 0,
                )
            }
        }
        if (delayMillis == 0L) {
            load()
        } else {
            vehicleSearchJob = viewModelScope.launch {
                delay(delayMillis)
                load()
            }
        }
    }

    private suspend fun loadVehicleCreationCapabilities(accessToken: String) {
        val capabilities = repository.getVehicleCreationCapabilities(accessToken)
        _uiState.update {
            it.copy(
                creatableVehicleCategories = capabilities.creatableCategories,
                canChangeVehicleCategory = capabilities.canChangeVehicleCategory,
            )
        }
    }

    private suspend fun loadVehicles(
        accessToken: String,
        reset: Boolean,
        requestVersion: Long,
        query: String,
        statusFilter: VehicleStatusFilter,
        offset: Int,
    ) {
        val page = loadVehiclePageWithRetry(
            accessToken = accessToken,
            query = query,
            statusFilter = statusFilter,
            offset = offset,
            requestVersion = requestVersion,
        )
        if (requestVersion != vehicleRequestVersion) return
        _uiState.update { state ->
            if (requestVersion != vehicleRequestVersion) return@update state
            state.copy(
                isLoading = false,
                isVehiclePageLoading = false,
                vehicles = if (reset) page.items else (state.vehicles + page.items).distinctBy { it.id },
                vehicleTotalCount = page.total,
            )
        }
    }

    private suspend fun loadVehiclePageWithRetry(
        accessToken: String,
        query: String,
        statusFilter: VehicleStatusFilter,
        offset: Int,
        requestVersion: Long,
    ) = run {
        var lastFailure: Throwable? = null
        for (attempt in 0 until VEHICLE_READ_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            if (requestVersion != vehicleRequestVersion) throw CancellationException()
            try {
                return@run repository.listVehicles(
                    accessToken = accessToken,
                    keyword = query.trim().ifEmpty { null },
                    status = statusFilter.requestValue,
                    limit = VEHICLE_PAGE_SIZE,
                    offset = offset,
                )
            } catch (failure: Throwable) {
                lastFailure = failure
                val hasNextAttempt = attempt < VEHICLE_READ_ATTEMPTS - 1
                if (!hasNextAttempt || !AppErrorMapper.isRetryableReadFailure(failure)) throw failure
                delay(VEHICLE_READ_RETRY_DELAYS[attempt])
            }
        }
        throw requireNotNull(lastFailure)
    }

    private suspend fun loadUsers(accessToken: String) {
        val users = repository.listUsers(accessToken)
        val session = sessionProvider.session.first() ?: return
        val policy = if (session.role == "ADMIN" && session.username == "admin") {
            runCatching { repository.getClientPolicy(accessToken) }.getOrNull()
        } else {
            null
        }
        val avatars = users.associate { user ->
            user.id to userAvatarRepository?.let { avatarRepository ->
                runCatching {
                    avatarRepository.load(accessToken, user.id, user.avatarVersion, user.hasAvatar)
                }.getOrElse { com.jaydocoder.plateview.feature.auth.AvatarCacheEntry(null, null, user.avatarVersion) }
            }.orEmptyAvatar(user.avatarVersion)
        }
        _uiState.update {
            it.copy(
                users = users,
                userAvatars = avatars,
                canManageOtherUserProfiles = session.role == "ADMIN" && session.username == "admin",
                isPrimaryAdministrator = session.role == "ADMIN" && session.username == "admin",
                cacheResetStatuses = policy?.cacheResetStatuses?.associateBy { status -> status.userId }.orEmpty(),
            )
        }
    }

    private fun com.jaydocoder.plateview.feature.auth.AvatarCacheEntry?.orEmptyAvatar(version: Long) =
        this ?: com.jaydocoder.plateview.feature.auth.AvatarCacheEntry(null, null, version)

    private suspend fun loadImports(accessToken: String) {
        _uiState.update { it.copy(importBatches = repository.listImportBatches(accessToken)) }
    }

    private suspend fun loadImportBatch(accessToken: String, batchId: Long, reset: Boolean) {
        val previousBatch = _uiState.value.selectedImportBatch
        if (!reset && (
                previousBatch == null ||
                    previousBatch.id != batchId ||
                    _uiState.value.isImportPageLoading ||
                    previousBatch.rows.size >= previousBatch.rowTotal
                )
        ) {
            return
        }
        val offset = if (reset) 0 else previousBatch?.rows?.size ?: 0
        val filter = _uiState.value.importRowFilter
        _uiState.update {
            it.copy(
                isLoading = reset,
                isImportPageLoading = true,
                failure = null,
            )
        }
        val page = repository.getImportBatch(
            accessToken = accessToken,
            batchId = batchId,
            limit = IMPORT_PAGE_SIZE,
            offset = offset,
            filter = filter,
        )
        if (_uiState.value.importRowFilter != filter || (!reset && _uiState.value.selectedImportBatch?.id != batchId)) return
        _uiState.update { state ->
            val previousRows = if (reset || state.selectedImportBatch?.id != batchId) emptyList() else state.selectedImportBatch.rows
            state.copy(
                isLoading = false,
                isImportPageLoading = false,
                selectedImportBatch = page.copy(rows = (previousRows + page.rows).distinctBy { it.id }),
            )
        }
    }

    private fun updateAuditFilter(transform: (AuditFilter) -> AuditFilter) {
        if (_uiState.value.tab != AdminTab.Audit) return
        _uiState.update { state ->
            state.copy(
                auditFilter = transform(state.auditFilter),
                auditEntries = emptyList(),
                auditTotalCount = 0,
                isAuditPageLoading = false,
                failure = null,
            )
        }
        launchAdminAction { accessToken -> loadAuditEntries(accessToken, reset = true) }
    }

    private suspend fun loadAuditEntries(accessToken: String, reset: Boolean) {
        val previousState = _uiState.value
        if (!reset && (
                previousState.isAuditPageLoading ||
                    previousState.auditEntries.size >= previousState.auditTotalCount
                )
        ) return
        val filter = previousState.auditFilter
        val offset = if (reset) 0 else previousState.auditEntries.size
        _uiState.update {
            it.copy(
                isLoading = reset && it.auditEntries.isEmpty(),
                isAuditPageLoading = true,
                failure = null,
            )
        }
        val page = repository.listAuditEntries(
            accessToken = accessToken,
            filter = filter,
            limit = AUDIT_PAGE_SIZE,
            offset = offset,
        )
        if (_uiState.value.auditFilter != filter) return
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                isAuditPageLoading = false,
                auditEntries = if (reset) page.items else (state.auditEntries + page.items).distinctBy { it.id },
                auditTotalCount = page.total,
                auditSummary = page.summary,
                auditActors = page.actors,
                auditActionTypes = page.actionTypes,
            )
        }
    }

    private fun launchAdminAction(
        operation: String = "管理操作",
        shouldHandleFailure: () -> Boolean = { true },
        policyAction: PolicySavingAction? = null,
        action: suspend (String) -> Unit,
    ): Job = viewModelScope.launch {
            try {
                val session = sessionProvider.session.first()
                when {
                    session == null -> if (shouldHandleFailure()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSaving = false,
                                policySavingAction = null,
                                policySaveFeedback = policyAction?.let { PolicySaveFeedback(operation, "登录已失效，请重新登录。", success = false) },
                                failure = adminError(operation, AppErrorKind.SessionExpired, "登录已失效，请重新登录"),
                            )
                        }
                    }
                    session.role != "ADMIN" -> if (shouldHandleFailure()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSaving = false,
                                policySavingAction = null,
                                policySaveFeedback = policyAction?.let { PolicySaveFeedback(operation, "当前账号没有执行此操作的权限。", success = false) },
                                failure = adminError(operation, AppErrorKind.PermissionDenied, "当前账号没有执行此操作的权限"),
                            )
                        }
                    }
                    else -> action(session.accessToken)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                if (!shouldHandleFailure()) return@launch
                val mappedError = AppErrorMapper.map(operation, throwable).also(AppErrorTelemetry::report)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaving = false,
                        policySavingAction = null,
                        policySaveFeedback = policyAction?.let { PolicySaveFeedback(operation, mappedError.message, success = false) },
                        isVehicleEditorLoading = false,
                        isVehiclePageLoading = false,
                        isImportPageLoading = false,
                        isImportDetailLoading = false,
                        isAuditPageLoading = false,
                        failure = mappedError,
                    )
                }
                if (throwable is HttpException && throwable.code() == HTTP_UNAUTHORIZED) {
                    sessionProvider.logout()
                }
            }
        }

    private companion object {
        const val VEHICLE_PAGE_SIZE = 100
        const val IMPORT_PAGE_SIZE = 100
        const val AUDIT_PAGE_SIZE = 50
        const val VEHICLE_SEARCH_DEBOUNCE_MILLIS = 250L
        const val VEHICLE_READ_ATTEMPTS = 3
        const val HTTP_UNAUTHORIZED = 401
        val VEHICLE_READ_RETRY_DELAYS = longArrayOf(300L, 900L)
    }
}

private fun adminError(operation: String, kind: AppErrorKind, message: String) = AppError(
    operation = operation,
    kind = kind,
    requestId = java.util.UUID.randomUUID().toString(),
    message = message,
    retryable = false,
)
