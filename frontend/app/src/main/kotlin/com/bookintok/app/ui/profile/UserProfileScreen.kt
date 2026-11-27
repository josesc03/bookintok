package com.bookintok.app.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bookintok.app.ui.safety.BlockDialog
import com.bookintok.app.ui.safety.ReportSheet
import com.bookintok.app.ui.safety.SafetyViewModel
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.PublicProfileDto
import com.bookintok.app.core.network.RatingDto
import com.bookintok.app.data.BookRepository
import com.bookintok.app.data.UserRepository
import com.bookintok.app.ui.common.LoadingBox
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.components.EmptyState
import com.bookintok.app.ui.navigation.AppNavigator
import com.bookintok.app.ui.navigation.UserProfileRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileUiState(
    val profile: PublicProfileDto? = null,
    val books: List<BookDto> = emptyList(),
    val ratings: List<RatingDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/** Another user's public profile: name, area, rating, their books and what others said. */
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val users: UserRepository,
    private val books: BookRepository,
) : ViewModel() {
    private val uid = savedStateHandle.toRoute<UserProfileRoute>().uid
    private val _state = MutableStateFlow(UserProfileUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                _state.update {
                    it.copy(profile = users.publicProfile(uid), books = books.booksOf(uid), ratings = users.ratings(uid))
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    nav: AppNavigator,
    viewModel: UserProfileViewModel = hiltViewModel(),
    safety: SafetyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val safetyState by safety.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(safetyState.message) {
        safetyState.message?.let {
            snackbar.showSnackbar(it)
            safety.dismissMessage()
        }
    }
    val profile = state.profile
    if (reporting && profile != null) {
        ReportSheet(
            nickname = profile.nickname,
            working = safetyState.working,
            onDismiss = { reporting = false },
            onSend = { reason, comment ->
                safety.report(profile.uid, reason, comment, null)
                reporting = false
            },
        )
    }
    if (blocking && profile != null) {
        BlockDialog(
            nickname = profile.nickname,
            onDismiss = { blocking = false },
            onConfirm = {
                safety.block(profile.uid, profile.nickname)
                blocking = false
            },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
                actions = {
                    if (profile != null) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Denunciar") },
                                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        reporting = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Bloquear a @${profile.nickname}", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menu = false
                                        blocking = true
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Phases(if (profile != null) 2 else if (state.loading) 0 else 1) { phase ->
            when (phase) {
                0 -> LoadingBox()
                1 -> EmptyState(
                    Icons.Default.ErrorOutline,
                    "No hemos podido abrir el perfil",
                    state.error ?: "Inténtalo de nuevo en un momento.",
                    actionLabel = "Reintentar",
                    onAction = viewModel::load,
                    modifier = Modifier.padding(padding),
                )
                else -> ProfileContent(
                    header = { ProfileHeader(profile!!.nickname, profile.displayName, profile.rating, profile.locationLabel) },
                    books = state.books,
                    ratings = state.ratings,
                    booksEmpty = "Todavía no ha publicado ningún libro.",
                    onBookClick = nav.openBook,
                    contentPadding = padding,
                )
            }
        }
    }
}
