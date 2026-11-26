package com.bookintok.app.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.BookStatus
import com.bookintok.app.data.BookRepository
import com.bookintok.app.data.ExchangeRepository
import com.bookintok.app.ui.common.LoadingBox
import com.bookintok.app.ui.common.label
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.components.Avatar
import com.bookintok.app.ui.components.Badge
import com.bookintok.app.ui.components.BkCard
import com.bookintok.app.ui.components.BookCover
import com.bookintok.app.ui.components.EmptyState
import com.bookintok.app.ui.components.PrimaryButton
import com.bookintok.app.ui.components.SecondaryButton
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

    /** Silent reload when coming back to the screen (for example after editing the book or its photo). */
    fun refresh() {
        if (_state.value.book == null) return
        viewModelScope.launch {
            runCatching { books.book(bookId) }.onSuccess { book -> _state.update { it.copy(book = book) } }
        }
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
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val book = state.book
    when {
        state.loading -> LoadingBox()
        book == null -> EmptyState(
            Icons.Default.ErrorOutline,
            "No hemos podido abrir el libro",
            state.error ?: "Puede que ya no exista.",
            actionLabel = "Reintentar",
            onAction = viewModel::load,
        )
        else -> BookDetail(book, state, onEdit = { onEdit(book.id) }, onRequest = viewModel::requestExchange, onDelete = viewModel::delete)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetail(book: BookDto, state: BookDetailUiState, onEdit: () -> Unit, onRequest: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BookCover(book.title, book.imageUrl, Modifier.widthIn(max = 240.dp).fillMaxWidth(0.62f).aspectRatio(3f / 4f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, style = MaterialTheme.typography.headlineMedium)
            Text(book.author, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(book.condition.label, emphasized = true)
            Badge(book.coverType.label)
            Badge(book.language)
            Badge(book.mainCategory)
            book.secondaryCategory?.let { Badge(it) }
        }
        book.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        BkCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(book.owner.nickname)
                Column(Modifier.weight(1f)) {
                    Text(if (state.isMine) "Lo ofreces tú" else "Lo ofrece @${book.owner.nickname}", style = MaterialTheme.typography.titleMedium)
                    val place = listOfNotNull(
                        book.owner.locationLabel,
                        book.distanceKm?.takeIf { !state.isMine }?.let { "a unos $it km de ti" },
                    )
                    if (place.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(place.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        when {
            book.status == BookStatus.EXCHANGED -> Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                Text("Este libro ya se ha intercambiado.", modifier = Modifier.padding(16.dp).fillMaxWidth())
            }
            state.isMine -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Editar", onClick = onEdit, icon = Icons.Default.Edit, modifier = Modifier.weight(1f))
                SecondaryButton(
                    "Eliminar",
                    onClick = { confirmDelete = true },
                    icon = Icons.Default.Delete,
                    enabled = !state.working,
                    modifier = Modifier.weight(1f),
                )
            }
            else -> PrimaryButton(
                "Lo quiero: abrir chat",
                onClick = onRequest,
                loading = state.working,
                icon = Icons.AutoMirrored.Filled.Chat,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.size(8.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar este libro?") },
            text = { Text("Se borrará \"${book.title}\" y sus solicitudes abiertas. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
