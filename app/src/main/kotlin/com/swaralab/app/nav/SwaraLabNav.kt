package com.swaralab.app.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swaralab.app.graph.SwaraLabGraph
import com.swaralab.app.ui.ExploreRoute
import com.swaralab.app.ui.HomeRoute
import com.swaralab.app.ui.ImportRoute
import com.swaralab.app.ui.ListenRoute
import com.swaralab.app.ui.OnboardingRoute
import com.swaralab.app.ui.importStudioRoute
import com.swaralab.domain.port.AnalyticsEvent
import com.swaralab.feature.account.AccountRoute
import com.swaralab.feature.account.StorageRoute
import com.swaralab.feature.library.LibraryRoute
import com.swaralab.feature.publish.PublishRoute
import com.swaralab.feature.studio.StudioRoute
import com.swaralab.ui.AppCopySet
import com.swaralab.ui.AppLanguage
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.SwaraLabTheme
import com.swaralab.ui.SwaraMark
import com.swaralab.ui.SwaraMarkIcon
import com.swaralab.ui.SwaraThemeId

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Start : Screen("explore")
    data object Songs : Screen("library")
    data object Singer : Screen("account")
    data object Import : Screen("import")
    data object Studio : Screen("studio/{sessionId}?sing={sing}") {
        fun create(id: String, sing: String = "") = "studio/$id?sing=$sing"
    }
    data object Mix : Screen("publish/{sessionId}") {
        fun create(id: String) = "publish/$id"
    }
    data object Listen : Screen("listen/{sessionId}") {
        fun create(id: String) = "listen/$id"
    }
    data object Storage : Screen("storage")
}

object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val LIBRARY = "library"
    const val ACCOUNT = "account"
    const val IMPORT = "import"
    const val STUDIO = "studio/{sessionId}?sing={sing}"
    const val PUBLISH = "publish/{sessionId}"
    const val LISTEN = "listen/{sessionId}"
    const val STORAGE = "storage"

    fun studio(id: String, sing: String = "") = Screen.Studio.create(id, sing)
    fun publish(id: String) = Screen.Mix.create(id)
    fun listen(id: String) = Screen.Listen.create(id)
}

@Composable
fun SwaraLabNav(graph: SwaraLabGraph) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showTabs = route == Routes.HOME ||
        route == Routes.EXPLORE ||
        route == Routes.LIBRARY ||
        route == Routes.ACCOUNT
    var appLanguage by remember {
        mutableStateOf(AppLanguage.fromTag(graph.appSettings.uiLanguage()))
    }
    var themeId by remember {
        mutableStateOf(SwaraThemeId.fromTag(graph.appSettings.themeId()))
    }
    val copy = AppCopySet.forLanguage(appLanguage)
    var showOnboarding by remember {
        mutableStateOf(!graph.appSettings.firstOpenSeen())
    }
    LaunchedEffect(route, showOnboarding) {
        val screen = when {
            showOnboarding -> "onboarding"
            route == Routes.HOME -> "home"
            route == Routes.EXPLORE -> "start"
            route == Routes.LIBRARY -> "songs"
            route == Routes.ACCOUNT -> "singer"
            route == Screen.Import.route -> "import"
            route?.startsWith("studio/") == true || route == Routes.STUDIO -> "studio"
            route?.startsWith("listen/") == true || route == Routes.LISTEN -> "listen"
            route?.startsWith("publish/") == true || route == Routes.PUBLISH -> "mix"
            route == Routes.STORAGE || route == Screen.Storage.route -> "storage"
            else -> route ?: "unknown"
        }
        graph.analytics.log(AnalyticsEvent.SCREEN_VIEW, mapOf("screen_name" to screen))
    }

    SwaraLabTheme(themeId) {
    CompositionLocalProvider(LocalAppCopy provides copy) {
    if (showOnboarding) {
        OnboardingRoute(
            onFinished = {
                graph.appSettings.markFirstOpenSeen()
                graph.analytics.log(com.swaralab.domain.port.AnalyticsEvent.ONBOARDING_COMPLETED)
                showOnboarding = false
            },
        )
        return@CompositionLocalProvider
    }
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (showTabs) {
                Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SwaraColors.Line),
                )
                NavigationBar(
                    containerColor = SwaraColors.Card.copy(alpha = 0.92f),
                    tonalElevation = 3.dp,
                    windowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ) {
                    val tealNav = NavigationBarItemDefaults.colors(
                        selectedIconColor = SwaraColors.Teal,
                        selectedTextColor = SwaraColors.Teal,
                        indicatorColor = SwaraColors.TealWash,
                        unselectedIconColor = SwaraColors.Muted,
                        unselectedTextColor = SwaraColors.Muted,
                    )
                    val goldNav = NavigationBarItemDefaults.colors(
                        selectedIconColor = SwaraColors.Gold,
                        selectedTextColor = SwaraColors.Gold,
                        indicatorColor = SwaraColors.GoldWash,
                        unselectedIconColor = SwaraColors.Muted,
                        unselectedTextColor = SwaraColors.Muted,
                    )
                    val roseNav = NavigationBarItemDefaults.colors(
                        selectedIconColor = SwaraColors.Rose,
                        selectedTextColor = SwaraColors.Rose,
                        indicatorColor = SwaraColors.RoseWash,
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
                        icon = {
                            SwaraMarkIcon(
                                SwaraMark.BOOTH,
                                route == Routes.HOME,
                                selectedColor = SwaraColors.Teal,
                            )
                        },
                        label = { Text(copy.today) },
                        colors = tealNav,
                    )
                    NavigationBarItem(
                        selected = route == Routes.EXPLORE,
                        onClick = {
                            nav.navigate(Routes.EXPLORE) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            SwaraMarkIcon(
                                SwaraMark.EXPLORE,
                                route == Routes.EXPLORE,
                                selectedColor = SwaraColors.Teal,
                            )
                        },
                        label = { Text(copy.start) },
                        colors = tealNav,
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
                        icon = {
                            SwaraMarkIcon(
                                SwaraMark.SONGS,
                                route == Routes.LIBRARY,
                                selectedColor = SwaraColors.Gold,
                            )
                        },
                        label = { Text(copy.songs) },
                        colors = goldNav,
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
                        icon = {
                            SwaraMarkIcon(
                                SwaraMark.PROFILE,
                                route == Routes.ACCOUNT,
                                selectedColor = SwaraColors.Rose,
                            )
                        },
                        label = { Text(copy.singer) },
                        colors = roseNav,
                    )
                }
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
                    startSession = graph.startSession,
                    listSessions = graph.listSessions,
                    appSettings = graph.appSettings,
                    onOpen = { id -> nav.navigate(Routes.studio(id)) },
                    onImport = { nav.navigate(Screen.Import.route) },
                    onListen = { id -> nav.navigate(Routes.listen(id)) },
                    analytics = graph.analytics,
                )
            }
            composable(Routes.EXPLORE) {
                ExploreRoute(
                    startSession = graph.startSession,
                    onOpen = { id, sing -> nav.navigate(Routes.studio(id, sing)) },
                    onImport = { nav.navigate(Screen.Import.route) },
                )
            }
            composable(
                route = Screen.Import.route,
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { slideOutHorizontally { it } },
            ) {
                ImportRoute(
                    projectsDir = graph.projectsDir,
                    startSession = graph.startSession,
                    importBackingTrack = graph.importBackingTrack,
                    renameSession = graph.renameSession,
                    takeCatalog = graph.takeCatalog,
                    onTrackReady = { sessionId ->
                        nav.navigate(importStudioRoute(sessionId)) {
                            popUpTo(Screen.Import.route) { inclusive = true }
                        }
                    },
                    onBack = { nav.popBackStack() },
                    analytics = graph.analytics,
                )
            }
            composable(Routes.LIBRARY) {
                LibraryRoute(
                    listSessions = graph.listSessions,
                    deleteSession = graph.deleteSession,
                    onOpen = { id -> nav.navigate(Routes.studio(id)) },
                    onListen = { id -> nav.navigate(Routes.listen(id)) },
                )
            }
            composable(Routes.ACCOUNT) {
                AccountRoute(
                    registerAccount = graph.registerAccount,
                    signIn = graph.signIn,
                    signOut = graph.signOut,
                    currentAccount = graph.currentAccount,
                    listSessions = graph.listSessions,
                    deleteSession = graph.deleteSession,
                    invitePack = graph.invitePack,
                    projectsDir = graph.projectsDir,
                    onOpen = { id -> nav.navigate(Routes.studio(id)) },
                    onListen = { id -> nav.navigate(Routes.listen(id)) },
                    onStorage = { nav.navigate(Screen.Storage.route) },
                    onReplayOnboarding = { showOnboarding = true },
                    takeCatalog = graph.takeCatalog,
                    boothProfile = graph.appSettings,
                    appLanguage = appLanguage,
                    onAppLanguage = { language ->
                        graph.appSettings.setUiLanguage(language.tag)
                        appLanguage = language
                    },
                    themeId = themeId,
                    onTheme = { theme ->
                        graph.appSettings.setThemeId(theme.tag)
                        themeId = theme
                    },
                )
            }
            composable(
                route = Routes.STUDIO,
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { slideOutHorizontally { it } },
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType },
                    navArgument("sing") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                val initialSing = entry.arguments?.getString("sing").orEmpty()
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
                    saveTake = graph.saveTake,
                    deleteRecordedClip = graph.deleteRecordedClip,
                    importBackingTrack = graph.importBackingTrack,
                    updatePublish = graph.updatePublish,
                    updateMusical = graph.updateMusical,
                    trimRecordedClip = graph.trimRecordedClip,
                    splitRecordedClip = graph.splitRecordedClip,
                    updateRecordedClip = graph.updateRecordedClip,
                    updateTrackChain = graph.updateTrackChain,
                    updateTrackMix = graph.updateTrackMix,
                    preferRecordedClip = graph.preferRecordedClip,
                    updatePractice = graph.updatePractice,
                    restoreSession = graph.restoreSession,
                    cleanUnusedTakes = graph.cleanUnusedTakes,
                    sessionAutoSave = graph.sessionAutoSave,
                    audio = graph.audio,
                    render = graph.render,
                    takeCatalog = graph.takeCatalog,
                    takeConversion = graph.takeConversion,
                    analytics = graph.analytics,
                    onBack = { nav.popBackStack() },
                    onPublish = { nav.navigate(Routes.publish(sessionId)) },
                    initialSing = initialSing,
                )
            }
            composable(
                route = Routes.LISTEN,
                enterTransition = { fadeIn() + scaleIn(initialScale = 0.96f) },
                exitTransition = { fadeOut() },
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { entry ->
                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                ListenRoute(
                    sessionId = sessionId,
                    projectsDir = graph.projectsDir,
                    loadSession = graph.loadSession,
                    takeCatalog = graph.takeCatalog,
                    playback = graph.playback,
                    analytics = graph.analytics,
                    onBack = { nav.popBackStack() },
                    onMix = { nav.navigate(Routes.publish(sessionId)) },
                )
            }
            composable(
                route = Routes.PUBLISH,
                enterTransition = { fadeIn() + scaleIn(initialScale = 0.96f) },
                exitTransition = { fadeOut() },
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
                    invitePack = graph.invitePack,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.STORAGE) {
                StorageRoute(
                    listSessions = graph.listSessions,
                    deleteSession = graph.deleteSession,
                    projectsDir = graph.projectsDir,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
    }
    }
}
