package com.bookintok.app.ui.profile

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.network.BookDto
import com.bookintok.app.core.network.MyProfileDto
import com.bookintok.app.core.network.RatingDto
import com.bookintok.app.data.BookRepository
import com.bookintok.app.data.UserRepository
import com.bookintok.app.ui.common.LoadingBox
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.components.EmptyState
import com.bookintok.app.ui.components.GhostButton
import com.bookintok.app.ui.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: MyProfileDto? = null,
    val books: List<BookDto> = emptyList(),
    val ratings: List<RatingDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UserRepository,
    private val booksRepository: BookRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    /** Called every time the screen is shown, so changes made elsewhere (location, books) appear. */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val profile = users.myProfile() ?: return@launch
                _state.update {
                    it.copy(profile = profile, books = booksRepository.booksOf(profile.uid), ratings = users.ratings(profile.uid))
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { users.signOut() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(nav: AppNavigator, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Perfil", style = MaterialTheme.typography.headlineMedium) },
                actions = { IconButton(onClick = nav.openSettings) { Icon(Icons.Default.Settings, contentDescription = "Ajustes") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val profile = state.profile
        Phases(if (profile != null) 2 else if (state.loading) 0 else 1) { phase ->
            when (phase) {
                0 -> LoadingBox()
                1 -> EmptyState(
                    Icons.Default.ErrorOutline,
                    "No hemos podido cargar tu perfil",
                    state.error ?: "Inténtalo de nuevo en un momento.",
                    actionLabel = "Reintentar",
                    onAction = viewModel::load,
                    modifier = Modifier.padding(padding),
                )
                else -> ProfileContent(
                    header = {
                        ProfileHeader(
                            nickname = profile!!.nickname,
                            displayName = profile.displayName,
                            rating = profile.rating,
                            locationLabel = profile.locationLabel,
                            trailing = { GhostButton("Cambiar", nav.changeLocation) },
                        )
                    },
                    books = state.books,
                    ratings = state.ratings,
                    booksEmpty = "Publica tu primer libro desde Explorar.",
                    onBookClick = nav.openBook,
                    contentPadding = padding,
                )
            }
        }
    }
}
