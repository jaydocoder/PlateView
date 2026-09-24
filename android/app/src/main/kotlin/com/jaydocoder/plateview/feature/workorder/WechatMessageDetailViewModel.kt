package com.jaydocoder.plateview.feature.workorder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jaydocoder.plateview.core.navigation.WechatMessageDetailDestination
import com.jaydocoder.plateview.data.network.AppError
import com.jaydocoder.plateview.data.network.AppErrorMapper
import com.jaydocoder.plateview.domain.workorder.WechatMessage
import com.jaydocoder.plateview.domain.workorder.CachedWorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrderAttachment
import com.jaydocoder.plateview.domain.workorder.WorkOrderRepository
import com.jaydocoder.plateview.domain.workorder.AttachmentDownloadState
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import com.jaydocoder.plateview.feature.consistency.CatalogConsistencyStateProvider
import com.jaydocoder.plateview.feature.consistency.CatalogKind
import com.jaydocoder.plateview.feature.consistency.DefaultCatalogConsistencyStateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect

@HiltViewModel
class WechatMessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val sessionProvider: AuthSessionProvider,
    private val consistencyStateProvider: CatalogConsistencyStateProvider = DefaultCatalogConsistencyStateProvider,
) : ViewModel() {
    private val messageId = savedStateHandle.toRoute<WechatMessageDetailDestination>().messageId
    private val _uiState = MutableStateFlow(WechatMessageDetailUiState())
    val uiState: StateFlow<WechatMessageDetailUiState> = _uiState.asStateFlow()
    private val attachmentStateJobs = mutableMapOf<Long, Job>()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.message == null, isRefreshing = it.message != null, error = null) }
            val session = sessionProvider.session.first()
            if (session == null || !session.wechatWorkOrderAccessEnabled) {
                repository.clear(session?.userId)
                _uiState.update {
                    it.copy(isLoading = false, error = AppErrorMapper.map("读取微信聊天记录", IllegalStateException("当前账号没有微信车单访问权限")))
                }
                return@launch
            }
            val cached = runCatching { repository.getCachedWechatMessage(session.userId, messageId) }.getOrNull()
            if (cached != null) showMessage(cached, fromCache = true)
            val freshness = consistencyStateProvider.freshness.value[CatalogKind.WECHAT_MESSAGE]
            if (cached != null && freshness?.isConfirmed() == true) return@launch
            runCatching { repository.refreshWechatMessage(session.accessToken, session.userId, messageId) }
                .onSuccess { message ->
                    showMessage(message, fromCache = false)
                }
                .onFailure { error ->
                    _uiState.update {
                        if (it.message != null) it.copy(isLoading = false, isRefreshing = false, isOfflineCache = true)
                        else it.copy(isLoading = false, isRefreshing = false, error = AppErrorMapper.map("读取微信聊天记录", error))
                    }
                }
        }
    }

    private fun showMessage(message: WechatMessage, fromCache: Boolean) {
        _uiState.update {
            it.copy(isLoading = false, isRefreshing = false, isOfflineCache = fromCache, message = message, error = null)
        }
        message.attachments.forEach { observeAttachmentState(it.id) }
    }

    fun openAttachment(attachment: WorkOrderAttachment) {
        _uiState.update { it.copy(selectedAttachment = attachment) }
        observeAttachmentState(attachment.id)
        if (attachment.availability != "AVAILABLE" && !attachment.thumbnailAvailable && !attachment.previewAvailable) return
        _uiState.value.message?.let { message ->
            loadAttachment(message.id, attachment, attachment.preferredVariant())
        }
    }

    fun loadOriginal() {
        val state = _uiState.value
        val message = state.message ?: return
        val attachment = state.selectedAttachment ?: return
        loadAttachment(message.id, attachment, "original")
    }

    fun closeAttachment() { _uiState.update { it.copy(selectedAttachment = null) } }

    private fun loadAttachment(messageId: Long, attachment: WorkOrderAttachment, variant: String) {
        if (attachment.id in _uiState.value.attachmentLoading) return
        _uiState.update {
            it.copy(
                attachmentLoading = it.attachmentLoading + attachment.id,
                attachmentFailures = it.attachmentFailures - attachment.id,
            )
        }
        viewModelScope.launch {
            val session = sessionProvider.session.first() ?: return@launch
            runCatching { repository.attachment(session.accessToken, session.userId, messageId, attachment, variant) }
                .onSuccess { cached ->
                    _uiState.update { state ->
                        val files = state.attachmentFiles + (attachment.id to preferred(state.attachmentFiles[attachment.id], cached))
                        state.copy(
                            attachmentFiles = files,
                            attachmentLoading = state.attachmentLoading - attachment.id,
                            attachmentFailures = state.attachmentFailures - attachment.id,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(
                            attachmentLoading = state.attachmentLoading - attachment.id,
                            attachmentFailures = state.attachmentFailures + attachment.id,
                        )
                    }
                }
        }
    }

    private fun observeAttachmentState(attachmentId: Long) {
        if (attachmentStateJobs.containsKey(attachmentId)) return
        attachmentStateJobs[attachmentId] = viewModelScope.launch {
            val session = sessionProvider.session.first() ?: return@launch
            repository.observeAttachmentDownload(session.userId, attachmentId).collect { download ->
                _uiState.update { state ->
                    val downloads = if (download == null) state.attachmentDownloads - attachmentId else state.attachmentDownloads + (attachmentId to download)
                    val cached = download?.completedFile()
                    state.copy(
                        attachmentDownloads = downloads,
                        attachmentFiles = if (cached == null) state.attachmentFiles else state.attachmentFiles + (attachmentId to preferred(state.attachmentFiles[attachmentId], cached)),
                        attachmentFailures = if (cached == null) state.attachmentFailures else state.attachmentFailures - attachmentId,
                    )
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

}

private fun WorkOrderAttachment.preferredVariant(): String =
    if (availability == "AVAILABLE") "original" else "thumbnail"

data class WechatMessageDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOfflineCache: Boolean = false,
    val message: WechatMessage? = null,
    val attachmentFiles: Map<Long, CachedWorkOrderImage> = emptyMap(),
    val attachmentLoading: Set<Long> = emptySet(),
    val attachmentFailures: Set<Long> = emptySet(),
    val attachmentDownloads: Map<Long, AttachmentDownloadState> = emptyMap(),
    val selectedAttachment: WorkOrderAttachment? = null,
    val error: AppError? = null,
)
