package com.bookintok.app.ui.exchanges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.database.MessageDao
import com.bookintok.app.data.Exchange
import com.bookintok.app.data.ExchangeRepository
import com.bookintok.app.ui.common.InfoBanner
import com.bookintok.app.ui.common.MessageBox
import com.bookintok.app.ui.common.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationItem(val exchange: Exchange, val counterpart: String, val lastMessage: String?)

data class ExchangeListUiState(
    val items: List<ConversationItem> = emptyList(),
    val refreshing: Boolean = false,
    /** True when the list comes from the cache because the server could not be reached. */
    val offline: Boolean = false,
)

@HiltViewModel
class ExchangeListViewModel @Inject constructor(
    private val exchanges: ExchangeRepository,
    messages: MessageDao,
    private val auth: AuthRepository,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val offline = MutableStateFlow(false)

    val state = combine(exchanges.observeAll(), messages.observeLastMessages(), refreshing, offline) { list, last, refreshing, offline ->
        val uid = auth.currentUidNow().orEmpty()
        val lastByExchange = last.associateBy { it.exchangeId }
        ExchangeListUiState(
            items = list.map { ConversationItem(it, it.counterpartOf(uid).nickname, lastByExchange[it.id]?.content) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeListScreen(onOpenChat: (Long) -> Unit, viewModel: ExchangeListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        if (state.offline) InfoBanner("Sin conexión: mostrando tus conversaciones guardadas")
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
            if (state.items.isEmpty() && !state.refreshing) {
                MessageBox("Aún no tienes intercambios. Busca un libro y pulsa «Lo quiero».")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.exchange.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.exchange.bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(item.lastMessage ?: "@${item.counterpart}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = { Text(item.exchange.status.label) },
                            modifier = Modifier.clickable { onOpenChat(item.exchange.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
