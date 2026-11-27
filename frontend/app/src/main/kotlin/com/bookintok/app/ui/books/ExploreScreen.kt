package com.bookintok.app.ui.books

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.BookSearch
import com.bookintok.app.core.network.BookStatus
import com.bookintok.app.data.BookRepository
import com.bookintok.app.ui.common.label
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.components.BadgeStyle
import com.bookintok.app.ui.components.BookCover
import com.bookintok.app.ui.components.EmptyState
import com.bookintok.app.ui.components.GhostButton
import com.bookintok.app.ui.components.bookintokFieldColors
import com.bookintok.app.ui.components.coverKey
import com.bookintok.app.ui.components.pressable
import com.bookintok.app.ui.navigation.AppNavigator
import com.bookintok.app.ui.theme.BookintokColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.bookintok.app.ui.components.Badge as BookBadge

data class BookListUiState(
    val search: BookSearch = BookSearch(maxDistanceKm = DistanceSteps.DEFAULT_KM),
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

    fun onQuery(value: String) = _state.update { it.copy(search = it.search.copy(query = value)) }

    fun clearQuery() {
        _state.update { it.copy(search = it.search.copy(query = "")) }
        search()
    }

    /** Replaces every filter at once (from the filter sheet) and searches again. */
    fun applyFilters(search: BookSearch) {
        _state.update { it.copy(search = search.copy(query = it.search.query)) }
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
            try {
                val result = books.search(_state.value.search, page)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(nav: AppNavigator, viewModel: BookListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val gridState = rememberLazyGridState()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Explorar", style = MaterialTheme.typography.headlineMedium) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = nav.newBook,
                expanded = fabExpanded,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Publicar libro", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                SearchBar(state.search, viewModel, onOpenFilters = { showFilters = true })
            }
            when {
                state.error != null && state.books.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                    EmptyState(Icons.Default.WifiOff, "No hemos podido cargar los libros", state.error!!, actionLabel = "Reintentar", onAction = viewModel::search)
                }
                !state.loading && state.books.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyState(
                        Icons.Default.SearchOff,
                        "No hay libros por aquí",
                        "Prueba a ampliar la distancia o a quitar algún filtro.",
                        actionLabel = "Ver filtros",
                        onAction = { showFilters = true },
                    )
                }
                else -> {
                    items(state.books, key = { it.id }) { book ->
                        BookTile(book, onClick = { nav.openBook(book.id) }, modifier = Modifier.animateItem())
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            when {
                                state.loading -> CircularProgressIndicator(Modifier.size(28.dp), color = BookintokColors.Accent)
                                state.hasMore -> GhostButton("Ver más libros", viewModel::loadMore)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            current = state.search,
            onApply = {
                showFilters = false
                viewModel.applyFilters(it)
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun SearchBar(search: BookSearch, viewModel: BookListViewModel, onOpenFilters: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search.query.orEmpty(),
                onValueChange = viewModel::onQuery,
                placeholder = { Text("Título o autor") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (!search.query.isNullOrEmpty()) {
                        IconButton(onClick = viewModel::clearQuery) { Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                colors = bookintokFieldColors(),
                modifier = Modifier.weight(1f),
            )
            BadgedBox(badge = { if (search.activeFilters > 0) Badge { Text("${search.activeFilters}") } }) {
                FilledTonalIconButton(
                    onClick = onOpenFilters,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Icon(Icons.Default.Tune, contentDescription = "Filtros") }
            }
        }
        ActiveFilters(search, viewModel, onOpenFilters)
    }
}

/** The filters in use as removable chips; the distance chip opens the sheet instead. */
@Composable
private fun ActiveFilters(search: BookSearch, viewModel: BookListViewModel, onOpenFilters: () -> Unit) {
    val chips = buildList<Pair<String, BookSearch?>> {
        add("Hasta ${search.maxDistanceKm ?: DistanceSteps.DEFAULT_KM} km" to null)
        search.category?.let { add(it to search.copy(category = null)) }
        search.languages.forEach { add(it to search.copy(languages = search.languages - it)) }
        search.conditions.forEach { add(it.label to search.copy(conditions = search.conditions - it)) }
        search.coverTypes.forEach { add(it.label to search.copy(coverTypes = search.coverTypes - it)) }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips, key = { it.first }) { (text, without) ->
            InputChip(
                selected = without != null,
                onClick = { if (without == null) onOpenFilters() else viewModel.applyFilters(without) },
                label = { Text(text) },
                leadingIcon = if (without == null) ({ Icon(Icons.Default.Place, contentDescription = null, Modifier.size(16.dp)) }) else null,
                trailingIcon = if (without != null) ({ Icon(Icons.Default.Close, contentDescription = "Quitar $text", Modifier.size(16.dp)) }) else null,
                shape = MaterialTheme.shapes.small,
                colors = InputChipDefaults.inputChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = BookintokColors.Ink,
                    selectedTrailingIconColor = BookintokColors.Ink,
                ),
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** Grid tile: cover with its badges, title, author and where the book is. */
@Composable
fun BookTile(book: BookDto, onClick: () -> Unit, modifier: Modifier = Modifier, showPlace: Boolean = true) {
    Column(modifier.pressable(onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            BookCover(book.title, book.imageUrl, Modifier.fillMaxWidth().aspectRatio(3f / 4f), sharedKey = coverKey(book.id))
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (book.status == BookStatus.EXCHANGED) BookBadge("Intercambiado", style = BadgeStyle.Strong)
                BookBadge(book.condition.label)
            }
        }
        Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showPlace) {
            val place = listOfNotNull(book.owner.locationLabel, book.distanceKm?.let { "a $it km" }).joinToString(" · ")
            AnimatedVisibility(place.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(place, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
