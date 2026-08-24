package com.jaydocoder.plateview.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AvatarUiState(
    val entry: AvatarCacheEntry = AvatarCacheEntry(null, null, 0L),
)

@HiltViewModel
class AvatarViewModel @Inject constructor(
    private val sessionProvider: AuthSessionProvider,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AvatarUiState())
    val uiState: StateFlow<AvatarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionProvider.session.collectLatest { session ->
                if (session == null) {
                    _uiState.value = AvatarUiState()
                    return@collectLatest
                }
                launch { runCatching { avatarRepository.synchronize(session) } }
                avatarRepository.observe(session.userId).collect { entry -> _uiState.value = AvatarUiState(entry) }
            }
        }
    }

    fun synchronize() = viewModelScope.launch {
        sessionProvider.session.first()?.let { session -> runCatching { avatarRepository.synchronize(session) } }
    }
}
