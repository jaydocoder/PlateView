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
            _uiState.update { it.copy(isLoading = true, error = null) }
            val session = sessionProvider.session.first()
            if (session == null || !session.wechatWorkOrderAccessEnabled) {
                repository.clear(session?.userId)
                _uiState.update { it.copy(isLoading = false, error = AppErrorMapper.map("读取微信车单", IllegalStateException("当前账号没有微信车单访问权限"))) }
                return@launch
            }
            runCatching { repository.getDetail(session.accessToken, recordId) }
                .onSuccess { record ->
                    _uiState.update { it.copy(isLoading = false, record = record) }
                    record.images.filter(WorkOrderImage::thumbnailAvailable).forEach { loadImage(record, it, "thumbnail") }
                    record.images.filter(WorkOrderImage::previewAvailable).take(PREVIEW_PREFETCH_LIMIT).forEach {
                        loadImage(record, it, "preview")
                    }
                    viewModelScope.launch {
                        runCatching { repository.getHistory(session.accessToken, recordId) }
                            .onSuccess { history -> _uiState.update { it.copy(history = history) } }
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, error = AppErrorMapper.map("读取微信车单", error)) } }
        }
    }

    fun openImage(image: WorkOrderImage) {
        _uiState.update { it.copy(selectedImage = image) }
        _uiState.value.record?.let { record -> loadImage(record, image, if (image.previewAvailable) "preview" else "original") }
    }

    fun loadOriginal() {
        val state = _uiState.value
        val record = state.record ?: return
        val image = state.selectedImage ?: return
        loadImage(record, image, "original")
    }

    fun closeImage() { _uiState.update { it.copy(selectedImage = null) } }

    private fun loadImage(record: WorkOrder, image: WorkOrderImage, variant: String) {
        viewModelScope.launch {
            val session = sessionProvider.session.first() ?: return@launch
            runCatching { repository.image(session.accessToken, session.userId, record.id, image, variant) }
                .onSuccess { cached ->
                    _uiState.update { state ->
                        val files = state.imageFiles + (image.id to preferred(state.imageFiles[image.id], cached))
                        state.copy(imageFiles = files)
                    }
                }
        }
    }

    private fun preferred(current: CachedWorkOrderImage?, incoming: CachedWorkOrderImage): CachedWorkOrderImage = when {
        current == null -> incoming
        rank(incoming.variant) >= rank(current.variant) -> incoming
        else -> current
    }

    private fun rank(variant: String) = when (variant) { "original" -> 3; "preview" -> 2; else -> 1 }

    private companion object {
        const val PREVIEW_PREFETCH_LIMIT = 3
    }
}

data class WorkOrderDetailUiState(
    val isLoading: Boolean = true,
    val sourceQuery: String = "",
    val record: WorkOrder? = null,
    val history: List<WorkOrder> = emptyList(),
    val imageFiles: Map<Long, CachedWorkOrderImage> = emptyMap(),
    val selectedImage: WorkOrderImage? = null,
    val error: AppError? = null,
)
