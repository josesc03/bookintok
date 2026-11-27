package com.bookintok.app.ui.exchanges

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.database.MessageDao
import com.bookintok.app.core.network.ExchangeStatus
import com.bookintok.app.data.Exchange
import com.bookintok.app.data.ExchangeRepository
import com.bookintok.app.data.UnreadRepository
import com.bookintok.app.ui.common.InfoBanner
import com.bookintok.app.ui.components.BookCover
import com.bookintok.app.ui.components.EmptyState
import com.bookintok.app.ui.navigation.AppNavigator
import com.bookintok.app.ui.theme.BookintokColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ConversationItem(
    val exchange: Exchange,
    val counterpart: String,
    val lastMessage: String?,
    val lastActivity: Instant,
    /** Messages from the other participant not read yet. */
    val unread: Int = 0,
)

/** The tabs of the chat list. */
enum class ChatTab(val title: String, val empty: String) {
    OPEN("En curso", "Cuando pidas un libro o alguien pida uno tuyo, el chat aparecerá aquí."),
    COMPLETED("Completados", "Aquí verás los intercambios que hayáis confirmado los dos."),
    CANCELLED("Cancelados", "Los intercambios cancelados aparecerán aquí."),
    ARCHIVED("Archivados", "Desliza un chat hacia un lado para archivarlo."),
    ;

    fun matches(exchange: Exchange) = when (this) {
        ARCHIVED -> exchange.archived
        OPEN -> !exchange.archived && exchange.status == ExchangeStatus.PENDING
        COMPLETED -> !exchange.archived && exchange.status == ExchangeStatus.COMPLETED
        CANCELLED -> !exchange.archived && exchange.status == ExchangeStatus.CANCELLED
    }
}

data class ExchangeListUiState(
    val items: List<ConversationItem> = emptyList(),
    val refreshing: Boolean = false,
    /** True when the list comes from the cache because the server could not be reached. */
    val offline: Boolean = false,
    val error: String? = null,
) {
    fun itemsFor(tab: ChatTab) = items.filter { tab.matches(it.exchange) }
}

@HiltViewModel
class ExchangeListViewModel @Inject constructor(
    private val exchanges: ExchangeRepository,
    messages: MessageDao,
    unread: UnreadRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val offline = MutableStateFlow(false)
    private val uid = auth.currentUidNow().orEmpty()

    val state = combine(
        exchanges.observeAll(),
        messages.observeLastMessages(),
        unread.unreadCounts(uid),
        refreshing,
        offline,
    ) { list, last, unreadCounts, refreshing, offline ->
        val lastByExchange = last.associateBy { it.exchangeId }
        ExchangeListUiState(
            items = list.map {
                val lastMessage = lastByExchange[it.id]
                ConversationItem(
                    exchange = it,
                    counterpart = it.counterpartOf(uid).nickname,
                    lastMessage = lastMessage?.content,
                    lastActivity = maxOf(it.updatedAt, Instant.ofEpochMilli(lastMessage?.sentAt ?: 0)),
                    unread = unreadCounts[it.id] ?: 0,
                )
            }.sortedByDescending { it.lastActivity },
            refreshing = refreshing,
            offline = offline,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExchangeListUiState(refreshing = true))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            offline.value = try {
                exchanges.refreshAll()
                false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                true
            }
            refreshing.value = false
        }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { runCatching { exchanges.setArchived(id, archived) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeListScreen(nav: AppNavigator, viewModel: ExchangeListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = ChatTab.entries
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Chats", style = MaterialTheme.typography.headlineMedium) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                PrimaryScrollableTabRow(
                    selectedTabIndex = pager.currentPage,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            Modifier.tabIndicatorOffset(pager.currentPage, matchContentSize = true),
                            width = androidx.compose.ui.unit.Dp.Unspecified,
                            color = BookintokColors.Accent,
                        )
                    },
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)) },
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val count = state.itemsFor(tab).size
                        val unreadInTab = state.itemsFor(tab).sumOf { it.unread }
                        Tab(
                            selected = pager.currentPage == index,
                            onClick = { scope.launch { pager.animateScrollToPage(index) } },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        if (count > 0 && tab != ChatTab.ARCHIVED) "${tab.title} ($count)" else tab.title,
                                        fontWeight = if (pager.currentPage == index) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    if (unreadInTab > 0) UnreadDot()
                                }
                            },
                            selectedContentColor = MaterialTheme.colorScheme.onSurface,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AnimatedVisibility(state.offline, enter = expandVertically(), exit = shrinkVertically()) {
                InfoBanner("Sin conexión: mostrando tus conversaciones guardadas")
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
                HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { page ->
                    val tab = tabs[page]
                    val items = state.itemsFor(tab)
                    if (items.isEmpty() && !state.refreshing) {
                        EmptyState(Icons.AutoMirrored.Filled.Chat, "Nada por aquí", tab.empty)
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(items, key = { it.exchange.id }) { item ->
                                SwipeToArchive(
                                    archived = item.exchange.archived,
                                    onSwiped = { viewModel.setArchived(item.exchange.id, !item.exchange.archived) },
                                    modifier = Modifier.animateItem(),
                                ) {
                                    ConversationRow(item, onClick = { nav.openChat(item.exchange.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Swiping a chat sideways archives it (or restores it in the Archived tab). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToArchive(archived: Boolean, onSwiped: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onSwiped()
            false // the item leaves the tab through the list update, not by staying dismissed
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
            ) {
                Icon(if (archived) Icons.Default.Unarchive else Icons.Default.Archive, contentDescription = null)
                Text(if (archived) "  Desarchivar" else "  Archivar", style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { content() }
}

/** Small dot on a tab that holds unread chats. */
@Composable
private fun UnreadDot() {
    Box(Modifier.size(8.dp).clip(CircleShape).background(BookintokColors.Pink))
}

/** Number of unread messages of a chat, like most messaging apps. */
@Composable
private fun UnreadCountBadge(count: Int) {
    Box(
        Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).clip(CircleShape).background(BookintokColors.Green)
            .border(1.dp, BookintokColors.Accent, CircleShape).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = BookintokColors.Ink,
        )
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM")

private fun Instant.shortLabel(): String {
    val local = atZone(ZoneId.systemDefault())
    return if (local.toLocalDate() == LocalDate.now()) timeFormat.format(local) else dateFormat.format(local)
}

@Composable
private fun ConversationRow(item: ConversationItem, onClick: () -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(item.exchange.bookTitle, item.exchange.bookImageUrl, Modifier.width(48.dp).aspectRatio(3f / 4f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val hasUnread = item.unread > 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "@${item.counterpart}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (hasUnread) FontWeight.Bold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        item.lastActivity.shortLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (hasUnread) FontWeight.Bold else null,
                        color = if (hasUnread) BookintokColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(item.exchange.bookTitle, style = MaterialTheme.typography.labelMedium, color = BookintokColors.Accent, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        item.lastMessage ?: "Todavía no hay mensajes",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (hasUnread) FontWeight.SemiBold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasUnread) UnreadCountBadge(item.unread)
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 78.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    }
}

