package com.bookintok.app.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.BookStatus
import com.bookintok.app.data.BookRepository
import com.bookintok.app.data.ExchangeRepository
import com.bookintok.app.ui.common.LoadingBox
import com.bookintok.app.ui.common.MessageBox
import com.bookintok.app.ui.common.label
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.navigation.BookDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookDetailUiState(
    val book: BookDto? = null,
    val isMine: Boolean = false,
    val loading: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
    /** Set when an exchange was created: the screen opens its chat. */
    val openChat: Long? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val books: BookRepository,
    private val exchanges: ExchangeRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val bookId = savedStateHandle.toRoute<BookDetailRoute>().id
    private val _state = MutableStateFlow(BookDetailUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() = launchCatching(initial = true) {
        val book = books.book(bookId)
        _state.update { it.copy(book = book, isMine = book.owner.uid == auth.currentUidNow()) }
    }

    fun requestExchange() = launchCatching {
        val exchange = exchanges.request(bookId)
        _state.update { it.copy(openChat = exchange.id) }
    }

    fun delete() = launchCatching {
        books.delete(bookId)
        _state.update { it.copy(deleted = true) }
    }

    fun chatOpened() = _state.update { it.copy(openChat = null) }

    private fun launchCatching(initial: Boolean = false, block: suspend () -> Unit) {
        _state.update { it.copy(loading = initial || it.loading, working = !initial, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(loading = false, working = false) }
            }
        }
    }
}

@Composable
fun BookDetailScreen(
    onOpenChat: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.openChat) {
        state.openChat?.let {
            viewModel.chatOpened()
            onOpenChat(it)
        }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    val book = state.book
    when {
        state.loading -> LoadingBox()
        book == null -> MessageBox(state.error ?: "No se ha encontrado el libro", actionLabel = "Reintentar", onAction = viewModel::load)
        else -> Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (book.imageUrl != null) {
                AsyncImage(
                    model = book.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
            }
            Text(book.title, style = MaterialTheme.typography.headlineSmall)
            Text(book.author, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(book.mainCategory, book.secondaryCategory, book.language, book.coverType.label, book.condition.label)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            book.description?.let { Text(it) }
            Text("Lo ofrece @${book.owner.nickname}", style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            when {
                book.status == BookStatus.EXCHANGED -> Text("Este libro ya se ha intercambiado")
                state.isMine -> {
                    OutlinedButton(onClick = { onEdit(book.id) }, modifier = Modifier.fillMaxWidth()) { Text("Editar") }
                    OutlinedButton(onClick = viewModel::delete, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                        Text("Eliminar")
                    }
                }
                else -> Button(onClick = viewModel::requestExchange, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                    Text("Lo quiero: abrir chat")
                }
            }
        }
    }
}
