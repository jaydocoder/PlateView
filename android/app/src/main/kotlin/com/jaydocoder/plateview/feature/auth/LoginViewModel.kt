package com.jaydocoder.plateview.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.data.network.AppErrorMapper
import com.jaydocoder.plateview.data.network.AppErrorTelemetry
import com.jaydocoder.plateview.data.network.displayText
import com.jaydocoder.plateview.data.network.rethrowIfCancellation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateUsername(value: String) = _uiState.update { it.copy(username = value, message = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, message = null) }
    fun login() = viewModelScope.launch {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) return@launch
        _uiState.update { it.copy(isLoading = true, message = null) }
        runCatching { authRepository.login(state.username, state.password) }
            .onSuccess { _uiState.update { it.copy(isLoading = false, password = "", message = "登录成功") } }
            .onFailure { throwable ->
                throwable.rethrowIfCancellation()
                val message = if (throwable is retrofit2.HttpException && throwable.code() == 401) {
                    "账号或密码不正确"
                } else {
                    AppErrorMapper.map("登录", throwable).also(AppErrorTelemetry::report).displayText()
                }
                _uiState.update { it.copy(isLoading = false, message = message) }
            }
    }
}
