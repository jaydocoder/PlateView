package com.jaydocoder.plateview.feature.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.data.admin.AdminImportFileReader
import com.jaydocoder.plateview.data.admin.AdminUserAvatarRepository
import com.jaydocoder.plateview.domain.admin.AdminRepository
import com.jaydocoder.plateview.domain.admin.AuditFilter
import com.jaydocoder.plateview.domain.admin.AuditRange
import com.jaydocoder.plateview.domain.admin.AuditResult
import com.jaydocoder.plateview.domain.admin.ImportRowFilter
import com.jaydocoder.plateview.domain.admin.UserCreateCommand
import com.jaydocoder.plateview.domain.admin.UserUpdateCommand
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: AdminTab) {
        _uiState.update { it.copy(tab = tab, failure = null) }
        refresh()
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
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun updateVehicleSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                vehicleSearchQuery = query,
                isVehiclePageLoading = false,
                failure = null,
            )
        }
        vehicleSearchJob?.cancel()
        vehicleSearchJob = viewModelScope.launch {
            delay(VEHICLE_SEARCH_DEBOUNCE_MILLIS)
            refreshVehicles()
        }
    }

    fun loadMoreVehicles() {
        if (_uiState.value.tab != AdminTab.Vehicles) return
        launchAdminAction { accessToken ->
            loadVehicles(accessToken, reset = false)
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
            _uiState.update { it.copy(failure = AdminFailure.ServiceUnavailable("正在获取车辆建档权限")) }
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
            loadVehicles(accessToken, reset = true)
        }
    }

    fun requestVehicleDeactivation(vehicle: com.jaydocoder.plateview.domain.admin.ManagedVehicleSummary) {
        _uiState.update { it.copy(pendingVehicleDeactivation = vehicle) }
    }

    fun dismissVehicleDeactivation() {
        _uiState.update { it.copy(pendingVehicleDeactivation = null) }
    }

    fun confirmVehicleDeactivation() {
        val vehicle = _uiState.value.pendingVehicleDeactivation ?: return
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null, pendingVehicleDeactivation = null) }
            repository.deactivateVehicle(accessToken, vehicle.id, vehicle.version)
            _uiState.update { it.copy(isSaving = false) }
            loadVehicles(accessToken, reset = true)
        }
    }

    fun createUser() {
        _uiState.update { it.copy(userEditor = UserEditorState(), failure = null) }
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
                repository.createUser(accessToken, UserCreateCommand(editor.username.trim(), editor.password, editor.role))
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
        val users = repository.listUsers(accessToken)
        val batches = repository.listImportBatches(accessToken)
        _uiState.update {
            it.copy(
                vehicles = vehiclePage.items,
                vehicleTotalCount = vehiclePage.total,
                creatableVehicleCategories = capabilities.creatableCategories,
                canChangeVehicleCategory = capabilities.canChangeVehicleCategory,
                users = users,
                importBatches = batches,
                isPrimaryAdministrator = session.role == "ADMIN" && session.username == "admin",
            )
        }
    }

    private fun refreshVehicles() = launchAdminAction { accessToken ->
        loadVehicleCreationCapabilities(accessToken)
        loadVehicles(accessToken, reset = true)
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

    private suspend fun loadVehicles(accessToken: String, reset: Boolean) {
        val previousState = _uiState.value
        if (!reset && (
                previousState.isVehiclePageLoading ||
                    previousState.vehicles.size >= previousState.vehicleTotalCount
                )
        ) {
            return
        }
        val query = previousState.vehicleSearchQuery
        val offset = if (reset) 0 else previousState.vehicles.size
        _uiState.update {
            it.copy(
                isLoading = reset && it.vehicles.isEmpty(),
                isVehiclePageLoading = true,
                failure = null,
            )
        }
        val page = repository.listVehicles(
            accessToken = accessToken,
            keyword = query.trim().ifEmpty { null },
            limit = VEHICLE_PAGE_SIZE,
            offset = offset,
        )
        if (_uiState.value.vehicleSearchQuery != query) return
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                isVehiclePageLoading = false,
                vehicles = if (reset) page.items else (state.vehicles + page.items).distinctBy { it.id },
                vehicleTotalCount = page.total,
            )
        }
    }

    private suspend fun loadUsers(accessToken: String) {
        val users = repository.listUsers(accessToken)
        val session = sessionProvider.session.first() ?: return
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

    private fun launchAdminAction(action: suspend (String) -> Unit) {
        viewModelScope.launch {
            try {
                val session = sessionProvider.session.first()
                when {
                    session == null -> _uiState.update { it.copy(isLoading = false, isSaving = false, failure = AdminFailure.SessionExpired) }
                    session.role != "ADMIN" -> _uiState.update { it.copy(isLoading = false, isSaving = false, failure = AdminFailure.PermissionDenied) }
                    else -> action(session.accessToken)
                }
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaving = false,
                        isVehicleEditorLoading = false,
                        isVehiclePageLoading = false,
                        isImportPageLoading = false,
                        isImportDetailLoading = false,
                        isAuditPageLoading = false,
                        failure = throwable.toAdminFailure(),
                    )
                }
                if (throwable is HttpException && throwable.code() == HTTP_UNAUTHORIZED) {
                    sessionProvider.logout()
                }
            }
        }
    }

    private companion object {
        const val VEHICLE_PAGE_SIZE = 100
        const val IMPORT_PAGE_SIZE = 100
        const val AUDIT_PAGE_SIZE = 50
        const val VEHICLE_SEARCH_DEBOUNCE_MILLIS = 250L
        const val HTTP_UNAUTHORIZED = 401
    }
}

private fun Throwable.toAdminFailure(): AdminFailure = when {
    this is HttpException && code() == 401 -> AdminFailure.SessionExpired
    this is HttpException && code() == 403 -> AdminFailure.PermissionDenied
    this is HttpException && code() == 409 -> AdminFailure.Conflict
    this is HttpException && code() == 400 -> AdminFailure.Validation(message())
    this is IllegalArgumentException -> AdminFailure.Validation(message)
    else -> AdminFailure.ServiceUnavailable(message)
}
