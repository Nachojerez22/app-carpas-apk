package com.nachojerez.carpstrategy.ui.navigation

import com.nachojerez.carpstrategy.ui.components.BackButton
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
import androidx.compose.runtime.LaunchedEffect
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
import com.nachojerez.carpstrategy.ui.assistant.AssistantScreen
import com.nachojerez.carpstrategy.ui.conditions.ConditionsScreen
import com.nachojerez.carpstrategy.ui.diary.DiaryScreen
import com.nachojerez.carpstrategy.ui.guided.ActiveGuidedBar
import com.nachojerez.carpstrategy.ui.guided.ActiveGuidedViewModel
import com.nachojerez.carpstrategy.ui.guided.GearScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.ui.guided.GuidedScreen
import com.nachojerez.carpstrategy.ui.guided.SpotsMapScreen
import com.nachojerez.carpstrategy.ui.guided.SpotsScreen
import com.nachojerez.carpstrategy.ui.lessons.LessonsScreen
import com.nachojerez.carpstrategy.ui.manual.DataScreen
import com.nachojerez.carpstrategy.ui.place.PlaceScreen
import com.nachojerez.carpstrategy.ui.reservoir.ReservoirSheetScreen
import com.nachojerez.carpstrategy.ui.reservoir.ReservoirsScreen
import com.nachojerez.carpstrategy.ui.strategy.PreparedOutingScreen
import com.nachojerez.carpstrategy.ui.strategy.StrategyScreen
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.today.TodayScreen
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

@Serializable data object TodayRoute
@Serializable data object StrategyRoute

/** [newRecord]: abrir directamente el formulario de un registro nuevo (desde «Añadir medición»). */
@Serializable data class DataRoute(val newRecord: Boolean = false)

/** [openSessionId]: abrir la ficha de esa sesión (al terminar una sesión guiada). */
@Serializable data class DiaryRoute(val openSessionId: Long = 0L)
@Serializable data object PlaceRoute

/** Ajustes de la app (fase 7.23), desde Preparar. */
@Serializable data object SettingsRoute

/** Datos en bruto por fuente (la antigua pantalla Condiciones). */
@Serializable data object RawDataRoute

/** Sesión guiada (fase 7) y el equipo del usuario. */
@Serializable data object GuidedRoute
@Serializable data object GearRoute

/** Mis puestos (fase 7.3). */
@Serializable data object SpotsRoute

/** Asistente de IA (fase 7.4). */
@Serializable data object AssistantRoute

/** Lo aprendido (fase 7.5). */
@Serializable data object LessonsRoute

/** La última preparación de la próxima salida (fase 7.19). */
@Serializable data object PreparedOutingRoute

/** Mapa de puestos (fase 7.9). */
@Serializable data object SpotsMapRoute

/** Elegir embalse y ficha del activo (fase 7.6). */
@Serializable data object ReservoirsRoute

@Serializable data object ReservoirSheetRoute

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    // Revisión de UI (7.28): en el orden de una salida (mirar → preparar → pescar → apuntar) y Datos al final.
    Today(TodayRoute, TodayRoute::class, R.string.nav_today, R.drawable.ic_nav_today),
    Place(PlaceRoute, PlaceRoute::class, R.string.nav_place, R.drawable.ic_nav_prepare),
    Strategy(StrategyRoute, StrategyRoute::class, R.string.nav_strategy, R.drawable.ic_nav_strategy),
    Diary(DiaryRoute(), DiaryRoute::class, R.string.nav_diary, R.drawable.ic_nav_diary),
    Data(DataRoute(), DataRoute::class, R.string.nav_data, R.drawable.ic_nav_data),
}

private fun NavHostController.navigateToTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** [openGuided]: se ha tocado la notificación de la sesión guiada; [onGuidedOpened] lo consume. */
@Composable
fun CarpStrategyNavHost(openGuided: Boolean = false, onGuidedOpened: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val apple = CarpTheme.style.translucentNavBar
    val cs = MaterialTheme.colorScheme
    val activeGuided by hiltViewModel<ActiveGuidedViewModel>().active.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = cs.background,
        bottomBar = {
            Column {
                // Con una sesión guiada abierta, volver a ella desde cualquier pestaña (no en la propia guiada).
                val guidedOpen = currentDestination?.hierarchy?.any { it.hasRoute(GuidedRoute::class) } == true
                activeGuided?.takeIf { !guidedOpen }?.let { active ->
                    ActiveGuidedBar(active, onOpen = { navController.navigate(GuidedRoute) { launchSingleTop = true } })
                }
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
                    onOpenStrategy = { navController.navigateToTab(StrategyRoute) },
                )
            }
            composable<StrategyRoute> {
                StrategyScreen(
                    onStartGuided = { navController.navigate(GuidedRoute) { launchSingleTop = true } },
                    onOpenSpots = { navController.navigate(SpotsRoute) },
                    onOpenAssistant = { navController.navigate(AssistantRoute) },
                    onOpenLessons = { navController.navigate(LessonsRoute) },
                    onOpenReservoir = { navController.navigate(ReservoirSheetRoute) },
                    onOpenReservoirs = { navController.navigate(ReservoirsRoute) },
                    onOpenGear = { navController.navigate(GearRoute) },
                    onOpenPrepared = { navController.navigate(PreparedOutingRoute) },
                    onOpenPrepare = { navController.navigateToTab(PlaceRoute) },
                )
            }
            composable<PreparedOutingRoute> {
                PreparedOutingScreen(
                    onBack = { navController.popBackStack() },
                    onStartGuided = { navController.navigate(GuidedRoute) { launchSingleTop = true } },
                )
            }
            composable<DataRoute> { entry ->
                DataScreen(startWithNewRecord = entry.toRoute<DataRoute>().newRecord)
            }
            composable<DiaryRoute> { entry ->
                DiaryScreen(
                    openSessionId = entry.toRoute<DiaryRoute>().openSessionId,
                    onOpenGuided = { navController.navigate(GuidedRoute) { launchSingleTop = true } },
                )
            }
            composable<GuidedRoute> {
                GuidedScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGear = { navController.navigate(GearRoute) },
                    onOpenSpots = { navController.navigate(SpotsRoute) },
                    onFinished = { id ->
                        navController.navigate(DiaryRoute(openSessionId = id)) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<GearRoute> { GearScreen(onBack = { navController.popBackStack() }) }
            composable<SpotsRoute> { SpotsScreen(onBack = { navController.popBackStack() }, onOpenMap = { navController.navigate(SpotsMapRoute) }) }
            composable<SpotsMapRoute> { SpotsMapScreen(onBack = { navController.popBackStack() }) }
            composable<AssistantRoute> { AssistantScreen(onBack = { navController.popBackStack() }) }
            composable<LessonsRoute> { LessonsScreen(onBack = { navController.popBackStack() }) }
            composable<ReservoirsRoute> {
                ReservoirsScreen(onBack = { navController.popBackStack() }, onOpenSheet = { navController.navigate(ReservoirSheetRoute) })
            }
            composable<ReservoirSheetRoute> { ReservoirSheetScreen(onBack = { navController.popBackStack() }) }
            composable<SettingsRoute> {
                PlaceScreen(
                    settings = true,
                    onBack = { navController.popBackStack() },
                    onOpenAssistant = { navController.navigate(AssistantRoute) },
                )
            }
            composable<PlaceRoute> {
                PlaceScreen(
                    onOpenGear = { navController.navigate(GearRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onOpenPrepared = { navController.navigate(PreparedOutingRoute) },
                    onOpenSpots = { navController.navigate(SpotsRoute) },
                    onOpenAssistant = { navController.navigate(AssistantRoute) },
                    onOpenLessons = { navController.navigate(LessonsRoute) },
                    onOpenReservoirs = { navController.navigate(ReservoirsRoute) },
                    onOpenReservoirSheet = { navController.navigate(ReservoirSheetRoute) },
                )
            }
            composable<RawDataRoute> {
                Column {
                    BackButton({ navController.popBackStack() })
                    ConditionsScreen(onOpenManualData = { navController.navigateToTab(DataRoute()) })
                }
            }
        }
        // Tras crear el grafo: abrir la sesión guiada si se tocó su notificación.
        LaunchedEffect(openGuided) {
            if (openGuided) {
                navController.navigate(GuidedRoute) { launchSingleTop = true }
                onGuidedOpened()
            }
        }
    }
}
