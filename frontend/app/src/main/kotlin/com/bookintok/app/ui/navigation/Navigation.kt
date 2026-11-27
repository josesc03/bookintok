package com.bookintok.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bookintok.app.ui.books.BookDetailScreen
import com.bookintok.app.ui.books.BookEditorScreen
import com.bookintok.app.ui.books.ExploreScreen
import com.bookintok.app.ui.chat.ChatScreen
import com.bookintok.app.ui.components.LocalNavAnimatedScope
import com.bookintok.app.ui.components.LocalSharedTransitionScope
import com.bookintok.app.ui.exchanges.ExchangeListScreen
import com.bookintok.app.ui.location.LocationScreen
import com.bookintok.app.ui.profile.ProfileScreen
import com.bookintok.app.ui.profile.UserProfileScreen
import com.bookintok.app.ui.theme.BookintokColors
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// Type-safe routes

@Serializable
data object ExploreRoute

@Serializable
data object ExchangesRoute

@Serializable
data object ProfileRoute

@Serializable
data class BookDetailRoute(val id: Long)

/** [id] is null when publishing a new book. */
@Serializable
data class BookEditorRoute(val id: Long? = null)

/**
 * [created]: the user just asked for the book, so the owner is told about the new chat.
 * [existing]: the user asked again for a book they already had a chat about.
 */
@Serializable
data class ChatRoute(val exchangeId: Long, val created: Boolean = false, val existing: Boolean = false)

@Serializable
data object LocationRoute

/** Another user's public profile. */
@Serializable
data class UserProfileRoute(val uid: String)

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val selectedIcon: ImageVector, val icon: ImageVector)

private val tabs = listOf(
    Tab(ExploreRoute, ExploreRoute::class, "Explorar", Icons.Filled.Explore, Icons.Outlined.Explore),
    Tab(ExchangesRoute, ExchangesRoute::class, "Chats", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    Tab(ProfileRoute, ProfileRoute::class, "Perfil", Icons.Filled.Person, Icons.Outlined.Person),
)

private const val DURATION = 320

/**
 * App shell: the bottom navigation bar (only on the three main tabs) and the navigation graph.
 * Each screen draws its own top app bar. Pushed screens slide in from the right; tabs cross-fade.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookintokNavHost(inbox: InboxViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val unread by inbox.unreadTotal.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // Realtime inbox only while the app is visible; push notifications cover the rest
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { inbox.runInbox() }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val onTab = tabs.any { destination?.hasRoute(it.routeClass) == true }

    val nav = AppNavigator(
        openBook = { navController.navigate(BookDetailRoute(it)) },
        openChat = { navController.navigate(ChatRoute(it)) },
        openRequestedChat = { id, created -> navController.navigate(ChatRoute(id, created = created, existing = !created)) },
        openUser = { navController.navigate(UserProfileRoute(it)) },
        editBook = { navController.navigate(BookEditorRoute(it)) },
        newBook = { navController.navigate(BookEditorRoute()) },
        changeLocation = { navController.navigate(LocationRoute) },
        back = { navController.navigateUp() },
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                onTab,
                enter = slideInVertically(tween(DURATION)) { it },
                exit = slideOutVertically(tween(DURATION)) { it },
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    tabs.forEach { tab ->
                        val selected = destination?.hasRoute(tab.routeClass) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val count = if (tab.route == ExchangesRoute) unread else 0
                                BadgedBox(
                                    badge = {
                                        if (count > 0) {
                                            Badge(containerColor = BookintokColors.Pink, contentColor = Color.White) {
                                                Text(if (count > 99) "99+" else count.toString())
                                            }
                                        }
                                    },
                                ) {
                                    Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BookintokColors.Ink,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController,
                    startDestination = ExploreRoute,
                    modifier = Modifier.padding(padding),
                    enterTransition = { slideInHorizontally(tween(DURATION)) { it / 3 } + fadeIn(tween(DURATION)) },
                    exitTransition = { fadeOut(tween(DURATION / 2)) },
                    popEnterTransition = { fadeIn(tween(DURATION)) },
                    popExitTransition = { slideOutHorizontally(tween(DURATION)) { it / 3 } + fadeOut(tween(DURATION)) },
                ) {
                    tab<ExploreRoute> { ExploreScreen(nav) }
                    tab<ExchangesRoute> { ExchangeListScreen(nav) }
                    tab<ProfileRoute> { ProfileScreen(nav) }
                    screen<BookDetailRoute> { BookDetailScreen(nav) }
                    screen<BookEditorRoute> {
                        BookEditorScreen(
                            onClose = { navController.navigateUp() },
                            onSaved = { id ->
                                navController.popBackStack()
                                navController.navigate(BookDetailRoute(id)) { launchSingleTop = true }
                            },
                        )
                    }
                    screen<ChatRoute> { ChatScreen(nav) }
                    screen<UserProfileRoute> { UserProfileScreen(nav) }
                    screen<LocationRoute> {
                        LocationScreen(onDone = { navController.popBackStack() }, onBack = { navController.navigateUp() })
                    }
                }
            }
        }
    }
}

/** Navigation actions handed to screens, so they do not depend on the NavController. */
class AppNavigator(
    val openBook: (Long) -> Unit,
    val openChat: (Long) -> Unit,
    /** After asking for a book: [created] false means the user already had a chat about it. */
    val openRequestedChat: (Long, Boolean) -> Unit,
    val openUser: (String) -> Unit,
    val editBook: (Long) -> Unit,
    val newBook: () -> Unit,
    val changeLocation: () -> Unit,
    val back: () -> Unit,
)

/** Main tabs switch with a plain cross-fade. */
private inline fun <reified T : Any> NavGraphBuilder.tab(crossinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T>(
        enterTransition = { fadeIn(tween(DURATION)) },
        exitTransition = { fadeOut(tween(DURATION / 2)) },
        popEnterTransition = { fadeIn(tween(DURATION)) },
    ) { entry -> CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) } }
}

private inline fun <reified T : Any> NavGraphBuilder.screen(crossinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T> { entry -> CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) } }
}
