package com.jaydocoder.plateview.feature.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jaydocoder.plateview.core.navigation.VehicleDetailDestination
import com.jaydocoder.plateview.data.statistics.QueryEventSyncScheduler
import com.jaydocoder.plateview.data.statistics.StatisticsRepository
import com.jaydocoder.plateview.domain.vehicle.VehicleCacheRepository
import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
import com.jaydocoder.plateview.domain.vehicle.VehicleRepository
import com.jaydocoder.plateview.feature.auth.AuthSession
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
    private val vehicleCacheRepository: VehicleCacheRepository,
    private val statisticsRepository: StatisticsRepository,
    private val queryEventSyncScheduler: QueryEventSyncScheduler,
    private val sessionProvider: AuthSessionProvider,
) : ViewModel() {
    private val vehicleId = savedStateHandle.toRoute<VehicleDetailDestination>().vehicleId
    private val _uiState = MutableStateFlow(VehicleDetailUiState())
    private var queryEventRecorded = false

    val uiState: StateFlow<VehicleDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val session = sessionProvider.session.first()
            if (session == null) {
                _uiState.update {
                    it.copy(content = VehicleDetailContent.Error(VehicleDetailFailure.SessionExpired))
                }
                return@launch
            }

            val cached = runCatching {
                vehicleCacheRepository.getDetail(vehicleId)
            }.getOrNull()
            if (cached != null) {
                recordQueryOnce(session, cached.vehicle)
                _uiState.update { it.copy(content = VehicleDetailContent.Data(cached.vehicle, isCached = true)) }
                return@launch
            }

            try {
                _uiState.update { it.copy(content = VehicleDetailContent.Loading) }
                val vehicle = vehicleRepository.getVehicle(session.accessToken, vehicleId)
                recordQueryOnce(session, vehicle)
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

    private suspend fun recordQueryOnce(session: AuthSession, vehicle: VehicleDetail) {
        if (queryEventRecorded) return
        statisticsRepository.recordQuery(session, vehicle)
        queryEventRecorded = true
        queryEventSyncScheduler.requestImmediateSync()
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
