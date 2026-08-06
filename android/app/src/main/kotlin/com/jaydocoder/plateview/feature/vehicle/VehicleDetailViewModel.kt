package com.jaydocoder.plateview.feature.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jaydocoder.plateview.core.navigation.VehicleDetailDestination
import com.jaydocoder.plateview.domain.vehicle.VehicleRepository
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val sessionProvider: AuthSessionProvider,
) : ViewModel() {
    private val vehicleId = savedStateHandle.toRoute<VehicleDetailDestination>().vehicleId
    private val _uiState = MutableStateFlow(VehicleDetailUiState())

    val uiState: StateFlow<VehicleDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(content = VehicleDetailContent.Loading) }
            val session = sessionProvider.session.first()
            if (session == null) {
                _uiState.update {
                    it.copy(content = VehicleDetailContent.Error(VehicleDetailFailure.SessionExpired))
                }
                return@launch
            }

            try {
                val vehicle = vehicleRepository.getVehicle(session.accessToken, vehicleId)
                _uiState.update { it.copy(content = VehicleDetailContent.Data(vehicle)) }
            } catch (throwable: Throwable) {
                val failure = when {
                    throwable is HttpException && throwable.code() == HTTP_UNAUTHORIZED -> {
                        sessionProvider.logout()
                        VehicleDetailFailure.SessionExpired
                    }

                    throwable is HttpException && throwable.code() == HTTP_NOT_FOUND -> {
                        VehicleDetailFailure.VehicleNotFound
                    }

                    else -> VehicleDetailFailure.ServiceUnavailable
                }
                _uiState.update { it.copy(content = VehicleDetailContent.Error(failure)) }
            }
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
