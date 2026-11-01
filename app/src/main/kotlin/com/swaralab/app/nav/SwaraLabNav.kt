package com.swaralab.app.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.swaralab.feature.account.AccountRoute
import com.swaralab.feature.library.LibraryRoute
import com.swaralab.feature.publish.PublishRoute
import com.swaralab.feature.studio.StudioRoute
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.SwaraMark
import com.swaralab.ui.SwaraMarkIcon

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val ACCOUNT = "account"
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
    val showTabs = route == Routes.HOME || route == Routes.LIBRARY || route == Routes.ACCOUNT

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showTabs) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SwaraColors.Teal,
                        selectedTextColor = SwaraColors.Teal,
                        indicatorColor = SwaraColors.TealWash,
                        unselectedIconColor = SwaraColors.Muted,
                        unselectedTextColor = SwaraColors.Muted,
                    )
                    NavigationBarItem(
                        selected = route == Routes.HOME,
                        onClick = {
                            nav.navigate(Routes.HOME) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { SwaraMarkIcon(SwaraMark.SING, route == Routes.HOME) },
                        label = { Text("Sing") },
                        colors = colors,
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
                        icon = { SwaraMarkIcon(SwaraMark.SONGS, route == Routes.LIBRARY) },
                        label = { Text("Songs") },
                        colors = colors,
                    )
                    NavigationBarItem(
                        selected = route == Routes.ACCOUNT,
                        onClick = {
                            nav.navigate(Routes.ACCOUNT) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { SwaraMarkIcon(SwaraMark.PROFILE, route == Routes.ACCOUNT) },
                        label = { Text("Profile") },
                        colors = colors,
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
                    deleteSession = graph.deleteSession,
                    onOpen = { id -> nav.navigate(Routes.studio(id)) },
                )
            }
            composable(Routes.ACCOUNT) {
                AccountRoute(
                    registerAccount = graph.registerAccount,
                    signIn = graph.signIn,
                    signOut = graph.signOut,
                    currentAccount = graph.currentAccount,
                )
            }
            composable(
                route = Routes.STUDIO,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { entry ->
                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                StudioRoute(
                    sessionId = sessionId,
                    projectsDir = graph.projectsDir,
                    loadSession = graph.loadSession,
                    renameSession = graph.renameSession,
                    updateGear = graph.updateGear,
                    updateVocalPreset = graph.updateVocalPreset,
                    updateLyrics = graph.updateLyrics,
                    addVoicePart = graph.addVoicePart,
                    commitRecordedTake = graph.commitRecordedTake,
                    deleteRecordedClip = graph.deleteRecordedClip,
                    importBackingTrack = graph.importBackingTrack,
                    updatePublish = graph.updatePublish,
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
                    projectsDir = graph.projectsDir,
                    loadSession = graph.loadSession,
                    updatePublish = graph.updatePublish,
                    updateTrackMix = graph.updateTrackMix,
                    updateVocalChain = graph.updateVocalChain,
                    updateVocalSyncOffset = graph.updateVocalSyncOffset,
                    render = graph.render,
                    audio = graph.audio,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
