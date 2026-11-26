package com.bookintok.app.ui.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.BookSearch
import com.bookintok.app.data.BookRepository
import com.bookintok.app.ui.common.label
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.components.Badge
import com.bookintok.app.ui.components.BookCover
import com.bookintok.app.ui.components.EmptyState
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

    fun clearQuery() {
        _state.update { it.copy(query = "") }
        search()
    }

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

private val DISTANCES = listOf(5, 10, 25, 50)

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
            ExtendedFloatingActionButton(
                onClick = onAddBook,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Publicar libro", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Libros cerca de ti", style = MaterialTheme.typography.headlineMedium)
                    SearchField(state.query, viewModel::onQuery, onSearch = viewModel::search, onClear = viewModel::clearQuery)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DISTANCES) { km ->
                            FilterChip(
                                selected = state.maxDistanceKm == km,
                                onClick = { viewModel.onDistance(km) },
                                label = { Text("A menos de $km km") },
                                shape = MaterialTheme.shapes.small,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }
            }
            when {
                state.error != null && state.books.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Default.WifiOff, "No hemos podido cargar los libros", state.error!!, actionLabel = "Reintentar", onAction = viewModel::search)
                }
                !state.loading && state.books.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        Icons.Default.SearchOff,
                        "No hay libros por aquí",
                        if (state.query.isNotBlank() || state.maxDistanceKm != null) "Prueba con otra búsqueda o amplía la distancia."
                        else "Sé el primero: publica un libro que ya hayas leído.",
                    )
                }
                else -> {
                    items(state.books, key = { it.id }) { book -> BookTile(book, onClick = { onBookClick(book.id) }) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            when {
                                state.loading -> CircularProgressIndicator(Modifier.size(28.dp))
                                state.hasMore -> com.bookintok.app.ui.components.GhostButton("Ver más libros", viewModel::loadMore)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Busca por título o autor") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda") }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            focusedBorderColor = MaterialTheme.colorScheme.secondary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Grid tile: cover on top, then title, author and where the book is. */
@Composable
fun BookTile(book: BookDto, onClick: () -> Unit, showOwner: Boolean = true) {
    Column(Modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            BookCover(book.title, book.imageUrl, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
            Badge(book.condition.label, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showOwner) {
            val place = listOfNotNull(book.owner.locationLabel, book.distanceKm?.let { "a $it km" }).joinToString(" · ")
            if (place.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(place, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
