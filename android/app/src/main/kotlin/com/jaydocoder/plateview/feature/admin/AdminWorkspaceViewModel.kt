package com.jaydocoder.plateview.feature.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.data.admin.AdminImportFileReader
import com.jaydocoder.plateview.domain.admin.AdminRepository
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
                AdminTab.Users -> _uiState.update { it.copy(users = repository.listUsers(accessToken)) }
                AdminTab.Imports -> _uiState.update { it.copy(importBatches = repository.listImportBatches(accessToken)) }
                AdminTab.Audit -> _uiState.update { it.copy(auditEntries = repository.listAuditEntries(accessToken)) }
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

    fun createVehicle() {
        _uiState.update { it.copy(vehicleEditor = VehicleEditorState(), failure = null) }
    }

    fun editVehicle(vehicleId: Long) = launchAdminAction { accessToken ->
        _uiState.update { it.copy(isLoading = true, failure = null) }
        val vehicle = repository.getVehicle(accessToken, vehicleId)
        _uiState.update { it.copy(isLoading = false, vehicleEditor = vehicle.toEditor()) }
    }

    fun updateVehicleEditor(transform: (VehicleEditorState) -> VehicleEditorState) {
        _uiState.update { state -> state.copy(vehicleEditor = state.vehicleEditor?.let(transform)) }
    }

    fun dismissVehicleEditor() {
        _uiState.update { it.copy(vehicleEditor = null) }
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
        _uiState.update { it.copy(userEditor = user.toEditor(), failure = null) }
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
                repository.updateUser(accessToken, editor.id, editor.version, UserUpdateCommand(editor.role, editor.status))
            }
            _uiState.update { it.copy(isSaving = false, userEditor = null) }
            loadUsers(accessToken)
        }
    }

    fun uploadImport(uri: Uri) = launchAdminAction { accessToken ->
        _uiState.update { it.copy(isSaving = true, failure = null) }
        val file = importFileReader.read(uri)
        val batch = repository.previewImport(accessToken, file.fileName, file.content)
        _uiState.update { it.copy(isSaving = false, selectedImportBatch = batch) }
        loadImports(accessToken)
    }

    fun openImportBatch(batchId: Long) = launchAdminAction { accessToken ->
        _uiState.update { it.copy(isLoading = true, failure = null) }
        val batch = repository.getImportBatch(accessToken, batchId)
        _uiState.update { it.copy(isLoading = false, selectedImportBatch = batch) }
    }

    fun dismissImportBatch() {
        _uiState.update { it.copy(selectedImportBatch = null) }
    }

    fun updateImportResolution(rowId: Long, resolution: String) {
        val batch = _uiState.value.selectedImportBatch ?: return
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            val updated = repository.updateImportResolution(accessToken, batch.id, rowId, resolution)
            _uiState.update { it.copy(isSaving = false, selectedImportBatch = updated) }
            loadImports(accessToken)
        }
    }

    fun publishImport() {
        val batch = _uiState.value.selectedImportBatch ?: return
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            val updated = repository.publishImport(accessToken, batch.id)
            _uiState.update { it.copy(isSaving = false, selectedImportBatch = updated) }
            loadImports(accessToken)
        }
    }

    fun rollbackImport() {
        val batch = _uiState.value.selectedImportBatch ?: return
        launchAdminAction { accessToken ->
            _uiState.update { it.copy(isSaving = true, failure = null) }
            val updated = repository.rollbackImport(accessToken, batch.id)
            _uiState.update { it.copy(isSaving = false, selectedImportBatch = updated) }
            loadImports(accessToken)
        }
    }

    private suspend fun loadDashboard(accessToken: String) {
        val vehiclePage = repository.listVehicles(accessToken)
        val users = repository.listUsers(accessToken)
        val batches = repository.listImportBatches(accessToken)
        _uiState.update {
            it.copy(
                vehicles = vehiclePage.items,
                vehicleTotalCount = vehiclePage.total,
                users = users,
                importBatches = batches,
            )
        }
    }

    private fun refreshVehicles() = launchAdminAction { accessToken ->
        loadVehicles(accessToken, reset = true)
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
        _uiState.update { it.copy(users = repository.listUsers(accessToken)) }
    }

    private suspend fun loadImports(accessToken: String) {
        _uiState.update { it.copy(importBatches = repository.listImportBatches(accessToken)) }
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
                        isVehiclePageLoading = false,
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
