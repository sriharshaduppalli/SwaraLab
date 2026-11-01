package com.swaralab.app.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swaralab.app.graph.SwaraLabGraph
import com.swaralab.app.ui.HomeRoute
import com.swaralab.feature.library.LibraryRoute
import com.swaralab.feature.publish.PublishRoute
import com.swaralab.feature.studio.StudioRoute

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val STUDIO = "studio/{sessionId}"
    const val PUBLISH = "publish/{sessionId}"

    fun studio(id: String) = "studio/$id"
    fun publish(id: String) = "publish/$id"
}

@Composable
fun SwaraLabNav(graph: SwaraLabGraph) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showTabs = route == Routes.HOME || route == Routes.LIBRARY

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showTabs) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = route == Routes.HOME,
                        onClick = {
                            nav.navigate(Routes.HOME) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text("●") },
                        label = { Text("Create") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.LIBRARY,
                        onClick = {
                            nav.navigate(Routes.LIBRARY) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text("♪") },
                        label = { Text("Library") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeRoute(
                    createSession = graph.createSession,
                    onOpen = { id -> nav.navigate(Routes.studio(id)) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryRoute(
                    listSessions = graph.listSessions,
                    onOpen = { id -> nav.navigate(Routes.studio(id)) },
                )
            }
            composable(
                route = Routes.STUDIO,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { entry ->
                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                StudioRoute(
                    sessionId = sessionId,
                    loadSession = graph.loadSession,
                    updateGear = graph.updateGear,
                    audio = graph.audio,
                    onBack = { nav.popBackStack() },
                    onPublish = { nav.navigate(Routes.publish(sessionId)) },
                )
            }
            composable(
                route = Routes.PUBLISH,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { entry ->
                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                PublishRoute(
                    sessionId = sessionId,
                    loadSession = graph.loadSession,
                    updatePublish = graph.updatePublish,
                    render = graph.render,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
