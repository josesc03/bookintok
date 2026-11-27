package com.bookintok.app.ui.books

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.network.ApiException
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.BookStatus
import com.bookintok.app.core.network.ExchangeStatus
import com.bookintok.app.data.BookRepository
import com.bookintok.app.data.ExchangeRepository
import com.bookintok.app.ui.common.LoadingBox
import com.bookintok.app.ui.common.label
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.components.Avatar
import com.bookintok.app.ui.components.Badge
import com.bookintok.app.ui.components.BadgeStyle
import com.bookintok.app.ui.components.BookCover
import com.bookintok.app.ui.components.BottomActionBar
import com.bookintok.app.ui.components.EmptyState
import com.bookintok.app.ui.components.PrimaryButton
import com.bookintok.app.ui.components.apiImageUrl
import com.bookintok.app.ui.components.coverKey
import com.bookintok.app.ui.navigation.AppNavigator
import com.bookintok.app.ui.navigation.BookDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookDetailUiState(
    val book: BookDto? = null,
    val isMine: Boolean = false,
    val loading: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
    /** Set when an exchange was created (or already existed): the screen opens its chat. */
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

    /** Opens a new exchange, or the one the user already has open for this book. */
    fun requestExchange() = launchCatching {
        val exchangeId = try {
            exchanges.request(bookId).id
        } catch (e: ApiException) {
            if (e.status != 409) throw e
            exchanges.refreshAll()
            exchanges.observeAll().first()
                .firstOrNull { it.bookId == bookId && it.status == ExchangeStatus.PENDING && it.requester.uid == auth.currentUidNow() }
                ?.id ?: throw e
        }
        _state.update { it.copy(openChat = exchangeId) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(nav: AppNavigator, viewModel: BookDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.openChat) {
        state.openChat?.let {
            viewModel.chatOpened()
            nav.openChat(it)
        }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) nav.back() }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val book = state.book
    val editable = book != null && state.isMine && book.status != BookStatus.EXCHANGED

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
                actions = {
                    if (editable) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Editar") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        nav.editBook(book!!.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menu = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            if (book != null && !state.isMine && book.status != BookStatus.EXCHANGED) {
                BottomActionBar {
                    PrimaryButton(
                        "Lo quiero: abrir chat",
                        onClick = viewModel::requestExchange,
                        loading = state.working,
                        icon = Icons.AutoMirrored.Filled.Chat,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = when {
                state.loading && book == null -> 0
                book == null -> 1
                else -> 2
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "detail",
        ) { phase ->
            when (phase) {
                0 -> LoadingBox()
                1 -> EmptyState(
                    Icons.Default.ErrorOutline,
                    "No hemos podido abrir el libro",
                    state.error ?: "Puede que ya no exista.",
                    actionLabel = "Reintentar",
                    onAction = viewModel::load,
                )
                else -> BookDetailContent(book!!, state, padding.calculateBottomPadding(), onOwnerClick = {
                    if (!state.isMine) nav.openUser(book.owner.uid)
                })
            }
        }
    }

    if (confirmDelete && book != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar este libro?") },
            text = { Text("Se borrará \"${book.title}\" y sus solicitudes abiertas. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = MaterialTheme.colorScheme.onSurface) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetailContent(book: BookDto, state: BookDetailUiState, bottomPadding: androidx.compose.ui.unit.Dp, onOwnerClick: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding)) {
        CoverHero(book)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, style = MaterialTheme.typography.headlineMedium)
                Text(book.author, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (book.status == BookStatus.EXCHANGED) Badge("Intercambiado", style = BadgeStyle.Strong)
                Badge(book.condition.label, style = BadgeStyle.Brand)
                Badge(book.coverType.label)
                Badge(book.language)
                Badge(book.mainCategory)
                book.secondaryCategory?.let { Badge(it) }
            }
            book.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        OwnerRow(book, state.isMine, onOwnerClick)
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        if (book.status == BookStatus.EXCHANGED) {
            Text(
                if (state.isMine) "Ya intercambiaste este libro." else "Este libro ya se ha intercambiado y no está disponible.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

/** Full-width header: the cover over a blurred, faded copy of itself. */
@Composable
private fun CoverHero(book: BookDto) {
    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        if (book.imageUrl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AsyncImage(
                model = apiImageUrl(book.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(32.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.35f), MaterialTheme.colorScheme.background),
                ),
            ),
        )
        BookCover(
            book.title,
            book.imageUrl,
            Modifier.padding(top = 24.dp).width(180.dp).aspectRatio(3f / 4f).shadow(16.dp, MaterialTheme.shapes.medium),
            sharedKey = coverKey(book.id),
        )
    }
}

@Composable
private fun OwnerRow(book: BookDto, isMine: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !isMine, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(book.owner.nickname)
        Column(Modifier.weight(1f)) {
            Text(if (isMine) "Lo ofreces tú" else "@${book.owner.nickname}", style = MaterialTheme.typography.titleMedium)
            val place = listOfNotNull(book.owner.locationLabel, book.distanceKm?.takeIf { !isMine }?.let { "a unos $it km de ti" })
            if (place.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(place.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!isMine) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Ver perfil")
    }
}
