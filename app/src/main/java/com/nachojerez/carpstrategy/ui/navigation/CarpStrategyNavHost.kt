package com.nachojerez.carpstrategy.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.ui.conditions.ConditionsScreen
import com.nachojerez.carpstrategy.ui.diary.DiaryScreen
import com.nachojerez.carpstrategy.ui.manual.DataScreen
import com.nachojerez.carpstrategy.ui.place.PlaceScreen
import com.nachojerez.carpstrategy.ui.strategy.StrategyScreen
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.today.TodayScreen
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

@Serializable data object TodayRoute
@Serializable data object StrategyRoute

/** [newRecord]: abrir directamente el formulario de un registro nuevo (desde «Añadir medición»). */
@Serializable data class DataRoute(val newRecord: Boolean = false)
@Serializable data object DiaryRoute
@Serializable data object PlaceRoute

/** Datos en bruto por fuente (la antigua pantalla Condiciones). */
@Serializable data object RawDataRoute

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Today(TodayRoute, TodayRoute::class, R.string.nav_today, R.drawable.ic_nav_today),
    Strategy(StrategyRoute, StrategyRoute::class, R.string.nav_strategy, R.drawable.ic_nav_strategy),
    Data(DataRoute(), DataRoute::class, R.string.nav_data, R.drawable.ic_nav_data),
    Diary(DiaryRoute, DiaryRoute::class, R.string.nav_diary, R.drawable.ic_nav_diary),
    Place(PlaceRoute, PlaceRoute::class, R.string.nav_place, R.drawable.ic_nav_location),
}

private fun NavHostController.navigateToTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun CarpStrategyNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val apple = CarpTheme.style.translucentNavBar
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.background,
        bottomBar = {
            NavigationBar(
                // Apple: barra translúcida, icono activo en primary y sin píldora.
                containerColor = if (apple) cs.surfaceContainer.copy(alpha = 0.92f) else cs.surfaceContainer,
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(destination.route) },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                        colors = if (apple) {
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = cs.primary,
                                selectedTextColor = cs.primary,
                                indicatorColor = Color.Transparent,
                            )
                        } else {
                            NavigationBarItemDefaults.colors()
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayRoute> {
                TodayScreen(
                    onOpenRawData = { navController.navigate(RawDataRoute) },
                    onAddMeasurement = {
                        // Sin restoreState: se abre el formulario aunque la pestaña Datos tenga estado guardado.
                        navController.navigate(DataRoute(newRecord = true)) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenPlace = { navController.navigateToTab(PlaceRoute) },
                )
            }
            composable<StrategyRoute> { StrategyScreen() }
            composable<DataRoute> { entry ->
                DataScreen(startWithNewRecord = entry.toRoute<DataRoute>().newRecord)
            }
            composable<DiaryRoute> { DiaryScreen() }
            composable<PlaceRoute> { PlaceScreen() }
            composable<RawDataRoute> {
                Column {
                    TextButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.action_back)) }
                    ConditionsScreen(onOpenManualData = { navController.navigateToTab(DataRoute()) })
                }
            }
        }
    }
}
