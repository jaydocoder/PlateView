package com.jaydocoder.plateview.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jaydocoder.plateview.feature.admin.AdminWorkspaceRoute
import com.jaydocoder.plateview.feature.search.SearchRoute
import com.jaydocoder.plateview.feature.vehicle.VehicleDetailRoute

@Composable
fun AuthenticatedNavigation(
    role: String,
    onLogout: () -> Unit,
    onOpenUpdate: (() -> Unit)? = null,
) {
    val navController = rememberNavController()
    val isAdministrator = role == "ADMIN"
    val navigateToVehicle = remember(navController) {
        { vehicleId: Long -> navController.navigate(VehicleDetailDestination(vehicleId)) }
    }
    val navigateToAdmin = remember(navController, isAdministrator) {
        if (isAdministrator) {
            { navController.navigate(AdminWorkspaceDestination) }
        } else {
            null
        }
    }
    NavHost(
        navController = navController,
        startDestination = SearchDestination,
    ) {
        composable<SearchDestination> {
            SearchRoute(
                onNavigateToVehicle = navigateToVehicle,
                onNavigateToAdmin = navigateToAdmin,
                onLogout = onLogout,
                onOpenUpdate = onOpenUpdate,
            )
        }
        composable<VehicleDetailDestination> {
            VehicleDetailRoute(
                onNavigateUp = navController::navigateUp,
                onOpenUpdate = onOpenUpdate,
            )
        }
        if (isAdministrator) {
            composable<AdminWorkspaceDestination> {
                AdminWorkspaceRoute(
                    onNavigateUp = navController::navigateUp,
                    onOpenUpdate = onOpenUpdate,
                )
            }
        }
    }
}
