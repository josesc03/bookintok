package com.bookintok.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bookintok.app.ui.books.BookDetailScreen
import com.bookintok.app.ui.books.BookEditorScreen
import com.bookintok.app.ui.books.BookListScreen
import com.bookintok.app.ui.chat.ChatScreen
import com.bookintok.app.ui.exchanges.ExchangeListScreen
import com.bookintok.app.ui.profile.ProfileScreen
import kotlinx.serialization.Serializable

// Type-safe routes

@Serializable
data object BooksRoute

@Serializable
data object ExchangesRoute

@Serializable
data object ProfileRoute

@Serializable
data class BookDetailRoute(val id: Long)

/** [id] is null when publishing a new book. */
@Serializable
data class BookEditorRoute(val id: Long? = null)

@Serializable
data class ChatRoute(val exchangeId: Long)

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(BooksRoute, "Libros", Icons.Default.MenuBook),
    Tab(ExchangesRoute, "Chats", Icons.AutoMirrored.Filled.Chat),
    Tab(ProfileRoute, "Perfil", Icons.Default.Person),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookintokNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val onTab = tabs.any { tab -> destination?.hasRoute(tab.route::class) == true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BookinTok") },
                navigationIcon = {
                    if (!onTab && navController.previousBackStackEntry != null) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (onTab) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hasRoute(tab.route::class) == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = BooksRoute, modifier = Modifier.padding(padding)) {
            composable<BooksRoute> {
                BookListScreen(
                    onBookClick = { navController.navigate(BookDetailRoute(it)) },
                    onAddBook = { navController.navigate(BookEditorRoute()) },
                )
            }
            composable<ExchangesRoute> {
                ExchangeListScreen(onOpenChat = { navController.navigate(ChatRoute(it)) })
            }
            composable<ProfileRoute> {
                ProfileScreen(onBookClick = { navController.navigate(BookDetailRoute(it)) })
            }
            composable<BookDetailRoute> {
                BookDetailScreen(
                    onOpenChat = { navController.navigate(ChatRoute(it)) },
                    onEdit = { navController.navigate(BookEditorRoute(it)) },
                    onDeleted = { navController.popBackStack() },
                )
            }
            composable<BookEditorRoute> {
                BookEditorScreen(onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate(BookDetailRoute(id)) { launchSingleTop = true }
                })
            }
            composable<ChatRoute> { ChatScreen() }
        }
    }
}
