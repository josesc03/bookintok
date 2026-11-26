package com.bookintok.app.ui.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.BookSearch
import com.bookintok.app.data.BookRepository
import com.bookintok.app.ui.common.MessageBox
import com.bookintok.app.ui.common.label
import com.bookintok.app.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookListUiState(
    val query: String = "",
    val maxDistanceKm: Int? = null,
    val books: List<BookDto> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BookListViewModel @Inject constructor(private val books: BookRepository) : ViewModel() {
    private val _state = MutableStateFlow(BookListUiState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        search()
    }

    fun onQuery(value: String) = _state.update { it.copy(query = value) }

    /** Selecting the active distance again removes the filter. */
    fun onDistance(km: Int) {
        _state.update { it.copy(maxDistanceKm = if (it.maxDistanceKm == km) null else km) }
        search()
    }

    fun search() = load(page = 0)

    fun loadMore() {
        val current = _state.value
        if (current.hasMore && !current.loading) load(current.page + 1)
    }

    private fun load(page: Int) {
        searchJob?.cancel() // a new search replaces the one in progress
        _state.update { it.copy(loading = true, error = null) }
        searchJob = viewModelScope.launch {
            val current = _state.value
            try {
                val result = books.search(BookSearch(query = current.query, maxDistanceKm = current.maxDistanceKm), page)
                _state.update {
                    it.copy(
                        books = if (page == 0) result.content else it.books + result.content,
                        page = result.page,
                        hasMore = result.hasMore,
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }
}

@Composable
fun BookListScreen(
    onBookClick: (Long) -> Unit,
    onAddBook: () -> Unit,
    viewModel: BookListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        // The outer Scaffold (top bar and navigation bar) already handles the system bars
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBook) { Icon(Icons.Default.Add, contentDescription = "Publicar libro") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQuery,
                placeholder = { Text("Título o autor") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 20, 50).forEach { km ->
                    FilterChip(
                        selected = state.maxDistanceKm == km,
                        onClick = { viewModel.onDistance(km) },
                        label = { Text("< $km km") },
                    )
                }
            }
            when {
                state.error != null && state.books.isEmpty() ->
                    MessageBox(state.error!!, actionLabel = "Reintentar", onAction = viewModel::search)
                !state.loading && state.books.isEmpty() ->
                    MessageBox("No hay libros que coincidan. ¡Prueba otra búsqueda o publica el tuyo!")
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.books, key = { it.id }) { book -> BookCard(book, onClick = { onBookClick(book.id) }) }
                    item {
                        when {
                            state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                            state.hasMore -> TextButton(onClick = viewModel::loadMore) { Text("Cargar más") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookCard(book: BookDto, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = book.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp, 80.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${book.condition.label} · ${book.language} · @${book.owner.nickname}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
