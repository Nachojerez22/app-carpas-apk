package com.nachojerez.carpstrategy.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.ui.about.AboutScreen
import com.nachojerez.carpstrategy.ui.conditions.ConditionsScreen
import com.nachojerez.carpstrategy.ui.location.LocationScreen
import com.nachojerez.carpstrategy.ui.strategy.StrategyScreen
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

@Serializable data object LocationRoute
@Serializable data object ConditionsRoute
@Serializable data object StrategyRoute
@Serializable data object AboutRoute

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Location(LocationRoute, LocationRoute::class, R.string.nav_location, R.drawable.ic_nav_location),
    Conditions(ConditionsRoute, ConditionsRoute::class, R.string.nav_conditions, R.drawable.ic_nav_conditions),
    Strategy(StrategyRoute, StrategyRoute::class, R.string.nav_strategy, R.drawable.ic_nav_strategy),
    About(AboutRoute, AboutRoute::class, R.string.nav_about, R.drawable.ic_nav_about),
}

@Composable
fun CarpStrategyNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.hasRoute(destination.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LocationRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<LocationRoute> { LocationScreen() }
            composable<ConditionsRoute> { ConditionsScreen() }
            composable<StrategyRoute> { StrategyScreen() }
            composable<AboutRoute> { AboutScreen() }
        }
    }
}
