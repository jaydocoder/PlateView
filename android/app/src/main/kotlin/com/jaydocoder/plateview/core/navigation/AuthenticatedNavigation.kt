package com.jaydocoder.plateview.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jaydocoder.plateview.feature.admin.AdminWorkspaceRoute
import com.jaydocoder.plateview.feature.search.SearchRoute
import com.jaydocoder.plateview.feature.schedule.ScheduleScreen
import com.jaydocoder.plateview.feature.schedule.SchedulePlannerRoute
import com.jaydocoder.plateview.feature.statistics.StatisticsRoute
import com.jaydocoder.plateview.feature.profile.ProfileRoute
import com.jaydocoder.plateview.feature.vehicle.VehicleDetailRoute
import com.jaydocoder.plateview.PlateViewDimensions
import com.jaydocoder.plateview.component.glass.GlassNavigationBar
import com.jaydocoder.plateview.component.glass.GlassNavigationItem

@Composable
fun AuthenticatedNavigation(
    username: String,
    role: String,
    scheduleEnabled: Boolean,
    onLogout: () -> Unit,
    onOpenUpdate: (() -> Unit)? = null,
    onCheckForUpdate: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navigateToHome: () -> Unit = remember(navController) {
        {
            navController.navigate(SearchDestination) {
                popUpTo<SearchDestination> { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    val isAdministrator = role == "ADMIN"
    val isPrimaryAdministrator = isAdministrator && username == "admin"
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val topLevelDestinations = buildList {
        add(SearchDestination)
        if (scheduleEnabled) add(ScheduleDestination)
        add(StatisticsDestination)
        add(ProfileDestination)
    }
    val showBottomNavigation = topLevelDestinations.any { destination?.hasRoute(it::class) == true }
    val shouldReturnToHome = destination?.hasRoute<ScheduleDestination>() == true ||
        destination?.hasRoute<StatisticsDestination>() == true ||
        destination?.hasRoute<ProfileDestination>() == true
    BackHandler(enabled = shouldReturnToHome) {
        navigateToHome()
    }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            if (showBottomNavigation) {
                GlassNavigationBar(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlateViewDimensions.pageHorizontal, vertical = PlateViewDimensions.compactSpacing)
                        .navigationBarsPadding(),
                ) {
                    BottomNavigationItem("首页", Icons.Outlined.Home, destination?.hasRoute<SearchDestination>() == true, navigateToHome)
                    if (scheduleEnabled) BottomNavigationItem("排班", Icons.Outlined.CalendarMonth, destination?.hasRoute<ScheduleDestination>() == true) { navController.navigate(ScheduleDestination) { launchSingleTop = true } }
                    BottomNavigationItem("统计", Icons.Outlined.QueryStats, destination?.hasRoute<StatisticsDestination>() == true) { navController.navigate(StatisticsDestination) { launchSingleTop = true } }
                    BottomNavigationItem("我的", Icons.Outlined.Person, destination?.hasRoute<ProfileDestination>() == true) { navController.navigate(ProfileDestination) { launchSingleTop = true } }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = SearchDestination,
            modifier = androidx.compose.ui.Modifier.padding(padding),
        ) {
            composable<SearchDestination> {
                SearchRoute(onNavigateToVehicle = navigateToVehicle, onNavigateToProfile = { navController.navigate(ProfileDestination) })
            }
            if (scheduleEnabled) composable<ScheduleDestination> { ScheduleScreen() }
            composable<StatisticsDestination> { StatisticsRoute(onNavigateToVehicle = navigateToVehicle) }
            composable<ProfileDestination> {
                ProfileRoute(
                    onNavigateUp = navigateToHome,
                    onOpenAdmin = navigateToAdmin,
                    onCheckForUpdate = onCheckForUpdate,
                    onLogout = onLogout,
                )
            }
            composable<VehicleDetailDestination> {
                VehicleDetailRoute(onNavigateUp = navController::navigateUp, onOpenUpdate = onOpenUpdate)
            }
            if (isAdministrator) {
                composable<AdminWorkspaceDestination> {
                    AdminWorkspaceRoute(
                        onNavigateUp = navController::navigateUp,
                        onOpenUpdate = onOpenUpdate,
                        onOpenSchedulePlanner = if (isPrimaryAdministrator) {
                            { navController.navigate(SchedulePlannerDestination) }
                        } else {
                            {}
                        },
                    )
                }
            }
            if (isPrimaryAdministrator) {
                composable<SchedulePlannerDestination> { SchedulePlannerRoute(onNavigateUp = navController::navigateUp) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomNavigationItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) = GlassNavigationItem(label, icon, selected, onClick)
