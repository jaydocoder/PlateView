package com.jaydocoder.plateview.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jaydocoder.plateview.feature.search.SearchRoute
import com.jaydocoder.plateview.feature.vehicle.VehicleDetailRoute

@Composable
fun AuthenticatedNavigation(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val navigateToVehicle = remember(navController) {
        { vehicleId: Long -> navController.navigate(VehicleDetailDestination(vehicleId)) }
    }
    NavHost(
        navController = navController,
        startDestination = SearchDestination,
    ) {
        composable<SearchDestination> {
            SearchRoute(
                onNavigateToVehicle = navigateToVehicle,
                onLogout = onLogout,
            )
        }
        composable<VehicleDetailDestination> {
            VehicleDetailRoute(onNavigateUp = navController::navigateUp)
        }
    }
}
