package com.jaydocoder.plateview.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SearchDestination

@Serializable
data object ScheduleDestination

@Serializable
data object StatisticsDestination

@Serializable
data object ProfileDestination

@Serializable
data class VehicleDetailDestination(
    val vehicleId: Long,
)

@Serializable
data object AdminWorkspaceDestination

@Serializable
data object SchedulePlannerDestination
