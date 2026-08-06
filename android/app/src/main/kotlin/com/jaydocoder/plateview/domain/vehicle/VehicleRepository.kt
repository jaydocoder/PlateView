package com.jaydocoder.plateview.domain.vehicle

interface VehicleRepository {
    suspend fun search(accessToken: String, keyword: String): List<VehicleCandidate>

    suspend fun getVehicle(accessToken: String, vehicleId: Long): VehicleDetail
}
