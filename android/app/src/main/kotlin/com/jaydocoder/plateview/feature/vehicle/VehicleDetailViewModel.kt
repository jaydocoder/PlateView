package com.jaydocoder.plateview.feature.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.data.network.AppError
import com.jaydocoder.plateview.data.network.AppErrorKind
import com.jaydocoder.plateview.data.network.AppErrorMapper
import com.jaydocoder.plateview.data.network.AppErrorTelemetry
import com.jaydocoder.plateview.data.network.rethrowIfCancellation
import androidx.navigation.toRoute
import com.jaydocoder.plateview.core.navigation.VehicleDetailDestination
import com.jaydocoder.plateview.data.statistics.QueryEventSyncScheduler
import com.jaydocoder.plateview.data.statistics.StatisticsRepository
import com.jaydocoder.plateview.domain.vehicle.VehicleCacheRepository
import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
import com.jaydocoder.plateview.domain.vehicle.VehicleRepository
import com.jaydocoder.plateview.feature.auth.AuthSession
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import com.jaydocoder.plateview.feature.consistency.CatalogConsistencyStateProvider
import com.jaydocoder.plateview.feature.consistency.CatalogKind
import com.jaydocoder.plateview.feature.consistency.CatalogSyncStatus
import com.jaydocoder.plateview.feature.consistency.DefaultCatalogConsistencyStateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val vehicleCacheRepository: VehicleCacheRepository,
    private val statisticsRepository: StatisticsRepository,
    private val queryEventSyncScheduler: QueryEventSyncScheduler,
    private val sessionProvider: AuthSessionProvider,
    private val consistencyStateProvider: CatalogConsistencyStateProvider = DefaultCatalogConsistencyStateProvider,
) : ViewModel() {
    private val vehicleId = savedStateHandle.toRoute<VehicleDetailDestination>().vehicleId
    private val _uiState = MutableStateFlow(VehicleDetailUiState())
    private var queryEventRecorded = false

    val uiState: StateFlow<VehicleDetailUiState> = _uiState.asStateFlow()

    init {
        observeFreshness()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val session = sessionProvider.session.first()
            if (session == null) {
                _uiState.update {
                    it.copy(content = VehicleDetailContent.Error(detailError(AppErrorKind.SessionExpired, "登录已失效，请重新登录")))
                }
                return@launch
            }

            val cached = runCatching {
                vehicleCacheRepository.getDetail(session.userId, vehicleId)
            }.getOrNull()
            if (cached != null) {
                recordQueryOnce(session, cached.vehicle)
                _uiState.update { it.copy(content = VehicleDetailContent.Data(cached.vehicle, isCached = true)) }
                val freshness = consistencyStateProvider.freshness.value[CatalogKind.VEHICLE]
                if (freshness?.isConfirmed() == true) return@launch
            }

            try {
                if (cached == null) _uiState.update { it.copy(content = VehicleDetailContent.Loading) }
                val vehicle = vehicleRepository.getVehicle(session.accessToken, vehicleId)
                recordQueryOnce(session, vehicle)
                _uiState.update { it.copy(content = VehicleDetailContent.Data(vehicle)) }
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                val error = AppErrorMapper.map("查看车辆详情", throwable)
                if (error.kind == AppErrorKind.SessionExpired) sessionProvider.logout()
                AppErrorTelemetry.report(error)
                if (cached == null) _uiState.update { it.copy(content = VehicleDetailContent.Error(error)) }
            }
        }
    }

    private fun observeFreshness() {
        viewModelScope.launch {
            consistencyStateProvider.freshness.collect { states ->
                val freshness = states[CatalogKind.VEHICLE] ?: return@collect
                if (freshness.status == CatalogSyncStatus.CONFIRMED && freshness.isConfirmed()) {
                    val session = sessionProvider.session.first() ?: return@collect
                    vehicleCacheRepository.getDetail(session.userId, vehicleId)?.let { cached ->
                        _uiState.update { it.copy(content = VehicleDetailContent.Data(cached.vehicle, isCached = true)) }
                    }
                }
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
    }
}

private fun detailError(kind: AppErrorKind, message: String) = AppError(
    operation = "查看车辆详情",
    kind = kind,
    requestId = java.util.UUID.randomUUID().toString(),
    message = message,
    retryable = false,
)
