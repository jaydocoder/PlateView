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
import kotlinx.coroutines.Job
import kotlin.random.Random
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

    private var validationJob: Job? = null

    fun setForeground(foreground: Boolean) {
        if (!foreground) {
            validationJob?.cancel()
            validationJob = null
            return
        }
        if (validationJob?.isActive == true) return
        validationJob = viewModelScope.launch {
            while (isActive) {
                validateCurrentSession()
                delay(sessionValidationDelayMillis())
            }
        }
    }

    fun logout() = viewModelScope.launch { authRepository.logout() }

    fun onNetworkAvailable() = viewModelScope.launch {
        val currentSession = authRepository.session.first() ?: return@launch
        runCatching { authRepository.checkCatalogState(currentSession) }
            .onFailure { authRepository.reportValidationFailure("NETWORK_RECOVERY_CHECK_FAILED") }
    }

    private suspend fun validateCurrentSession() {
        val currentSession = authRepository.session.first() ?: return
        try {
            authRepository.validateSession(currentSession)
        } catch (error: HttpException) {
            if (error.code() == HTTP_UNAUTHORIZED) {
                authRepository.logout()
            } else {
                authRepository.reportValidationFailure("HTTP_${error.code()}")
            }
        } catch (_: IOException) {
            authRepository.reportValidationFailure("NETWORK_UNAVAILABLE")
        } catch (_: Throwable) {
            authRepository.reportValidationFailure("PROFILE_VALIDATION_FAILED")
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val MINIMUM_VALIDATION_INTERVAL_MILLIS = 12_000L
        const val MAXIMUM_VALIDATION_INTERVAL_MILLIS = 18_000L
    }
}

internal fun sessionValidationDelayMillis(
    nextLong: (Long, Long) -> Long = Random::nextLong,
): Long = nextLong(12_000L, 18_001L)
