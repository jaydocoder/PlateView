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
class WechatMessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkOrderRepository,
    private val sessionProvider: AuthSessionProvider,
) : ViewModel() {
    private val messageId = savedStateHandle.toRoute<WechatMessageDetailDestination>().messageId
    private val _uiState = MutableStateFlow(WechatMessageDetailUiState())
    val uiState: StateFlow<WechatMessageDetailUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val session = sessionProvider.session.first()
            if (session == null || !session.wechatWorkOrderAccessEnabled) {
                repository.clear(session?.userId)
                _uiState.update {
                    it.copy(isLoading = false, error = AppErrorMapper.map("读取微信聊天记录", IllegalStateException("当前账号没有微信车单访问权限")))
                }
                return@launch
            }
            runCatching { repository.getMessageDetail(session.accessToken, messageId) }
                .onSuccess { message ->
                    _uiState.update { it.copy(isLoading = false, message = message) }
                    message.attachments
                        .filter { it.availability in setOf("AVAILABLE", "THUMBNAIL_ONLY") }
                        .forEach { attachment ->
                            loadAttachment(message.id, attachment, attachment.preferredVariant())
                        }
                }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, error = AppErrorMapper.map("读取微信聊天记录", error)) } }
        }
    }

    fun openAttachment(attachment: WorkOrderAttachment) {
        _uiState.update { it.copy(selectedAttachment = attachment) }
        _uiState.value.message?.let { message ->
            loadAttachment(message.id, attachment, attachment.preferredVariant())
        }
    }

    fun loadOriginal() {
        val state = _uiState.value
        val message = state.message ?: return
        val attachment = state.selectedAttachment ?: return
        loadAttachment(message.id, attachment, attachment.preferredVariant())
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
    val message: WechatMessage? = null,
    val attachmentFiles: Map<Long, CachedWorkOrderImage> = emptyMap(),
    val attachmentLoading: Set<Long> = emptySet(),
    val attachmentFailures: Set<Long> = emptySet(),
    val selectedAttachment: WorkOrderAttachment? = null,
    val error: AppError? = null,
)
