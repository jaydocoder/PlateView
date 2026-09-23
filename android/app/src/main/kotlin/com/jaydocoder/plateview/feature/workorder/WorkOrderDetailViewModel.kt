package com.jaydocoder.plateview.feature.workorder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jaydocoder.plateview.core.navigation.WorkOrderDetailDestination
import com.jaydocoder.plateview.data.network.AppError
import com.jaydocoder.plateview.data.network.AppErrorMapper
import com.jaydocoder.plateview.domain.workorder.CachedWorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrder
import com.jaydocoder.plateview.domain.workorder.WorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrderRepository
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WorkOrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val sessionProvider: AuthSessionProvider,
) : ViewModel() {
    private val destination = savedStateHandle.toRoute<WorkOrderDetailDestination>()
    private val recordId = destination.recordId
    private val _uiState = MutableStateFlow(WorkOrderDetailUiState(sourceQuery = destination.query))
    val uiState: StateFlow<WorkOrderDetailUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.record == null, isRefreshing = it.record != null, error = null) }
            val session = sessionProvider.session.first()
            if (session == null || !session.wechatWorkOrderAccessEnabled) {
                repository.clear(session?.userId)
                _uiState.update { it.copy(isLoading = false, error = AppErrorMapper.map("读取微信车单", IllegalStateException("当前账号没有微信车单访问权限"))) }
                return@launch
            }
            val cached = runCatching { repository.getCachedWorkOrder(session.userId, recordId) }.getOrNull()
            if (cached != null) showRecord(cached, fromCache = true)
            runCatching { repository.refreshWorkOrder(session.accessToken, session.userId, recordId) }
                .onSuccess { record ->
                    showRecord(record, fromCache = false)
                    viewModelScope.launch {
                        runCatching { repository.getHistory(session.accessToken, recordId) }
                            .onSuccess { history -> _uiState.update { it.copy(history = history) } }
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        if (it.record != null) it.copy(isLoading = false, isRefreshing = false, isOfflineCache = true)
                        else it.copy(isLoading = false, isRefreshing = false, error = AppErrorMapper.map("读取微信车单", error))
                    }
                }
        }
    }

    private fun showRecord(record: WorkOrder, fromCache: Boolean) {
        _uiState.update {
            it.copy(isLoading = false, isRefreshing = false, isOfflineCache = fromCache, record = record, error = null)
        }
    }

    fun openImage(image: WorkOrderImage) {
        _uiState.update { it.copy(selectedImage = image, imageFailures = it.imageFailures - image.id) }
        _uiState.value.record?.let { record -> loadImage(record, image, image.preferredVariant()) }
    }

    fun loadOriginal() {
        val state = _uiState.value
        val record = state.record ?: return
        val image = state.selectedImage ?: return
        loadImage(record, image, "original")
    }

    fun closeImage() { _uiState.update { it.copy(selectedImage = null) } }

    private fun loadImage(record: WorkOrder, image: WorkOrderImage, variant: String) {
        _uiState.update { it.copy(imageFailures = it.imageFailures - image.id) }
        viewModelScope.launch {
            val session = sessionProvider.session.first() ?: return@launch
            runCatching { repository.image(session.accessToken, session.userId, record.id, image, variant) }
                .onSuccess { cached ->
                    _uiState.update { state ->
                        val files = state.imageFiles + (image.id to preferred(state.imageFiles[image.id], cached))
                        state.copy(imageFiles = files)
                    }
                }
                .onFailure {
                    _uiState.update { state -> state.copy(imageFailures = state.imageFailures + image.id) }
                }
        }
    }

    private fun preferred(current: CachedWorkOrderImage?, incoming: CachedWorkOrderImage): CachedWorkOrderImage = when {
        current == null -> incoming
        rank(incoming.variant) >= rank(current.variant) -> incoming
        else -> current
    }

    private fun rank(variant: String) = when (variant) { "original" -> 3; "preview" -> 2; else -> 1 }

}

private fun WorkOrderImage.preferredVariant(): String =
    if (availability == "AVAILABLE") "original" else "thumbnail"

data class WorkOrderDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOfflineCache: Boolean = false,
    val sourceQuery: String = "",
    val record: WorkOrder? = null,
    val history: List<WorkOrder> = emptyList(),
    val imageFiles: Map<Long, CachedWorkOrderImage> = emptyMap(),
    val imageFailures: Set<Long> = emptySet(),
    val selectedImage: WorkOrderImage? = null,
    val error: AppError? = null,
)
