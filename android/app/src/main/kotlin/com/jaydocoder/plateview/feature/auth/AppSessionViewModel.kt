package com.jaydocoder.plateview.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val session: StateFlow<AuthSession?> = authRepository.session.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    init {
        viewModelScope.launch {
            while (isActive) {
                validateCurrentSession()
                delay(SESSION_VALIDATION_INTERVAL_MILLIS)
            }
        }
    }

    fun logout() = viewModelScope.launch { authRepository.logout() }

    private suspend fun validateCurrentSession() {
        val currentSession = authRepository.session.first() ?: return
        try {
            authRepository.validateSession(currentSession)
        } catch (error: HttpException) {
            if (error.code() == HTTP_UNAUTHORIZED) {
                authRepository.logout()
            }
        } catch (_: IOException) {
            // 网络暂时不可用时保留当前会话，下一轮继续校验。
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val SESSION_VALIDATION_INTERVAL_MILLIS = 15_000L
    }
}
