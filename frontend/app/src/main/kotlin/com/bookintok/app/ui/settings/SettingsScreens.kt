package com.bookintok.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bookintok.app.BuildConfig
import com.bookintok.app.core.legal.LegalDocument
import com.bookintok.app.core.legal.LegalTexts
import com.bookintok.app.core.network.BlockedUserDto
import com.bookintok.app.data.AccountRepository
import com.bookintok.app.data.NotificationPreferences
import com.bookintok.app.data.SafetyRepository
import com.bookintok.app.data.UserRepository
import com.bookintok.app.ui.common.LoadingBox
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.components.EmptyState
import com.bookintok.app.ui.components.GhostButton
import com.bookintok.app.ui.components.Wordmark
import com.bookintok.app.ui.navigation.AppNavigator
import com.bookintok.app.ui.theme.BookintokColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(val deleting: Boolean = false, val error: String? = null)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val users: UserRepository,
    private val account: AccountRepository,
    val notifications: NotificationPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    fun signOut() {
        viewModelScope.launch { users.signOut() }
    }

    /** On success the session ends and the app goes back to the welcome screen. */
    fun deleteAccount() {
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, error = null) }
            try {
                account.deleteAccount()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(deleting = false, error = e.toUserMessage()) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        content = content,
    )
}

@Composable
fun SettingsScreen(nav: AppNavigator, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages by viewModel.notifications.messages.collectAsStateWithLifecycle()
    val exchanges by viewModel.notifications.exchanges.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    SettingsScaffold("Ajustes", nav.back) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Section("Cuenta")
            Row(Icons.Default.EditLocationAlt, "Cambiar ubicación", "GPS, provincia o un punto en el mapa", onClick = nav.changeLocation)
            Row(Icons.Default.Block, "Usuarios bloqueados", "No ves sus libros ni pueden escribirte", onClick = nav.openBlocked)
            Row(Icons.AutoMirrored.Filled.Logout, "Cerrar sesión", onClick = viewModel::signOut)

            Section("Notificaciones")
            SwitchRow(Icons.AutoMirrored.Filled.Chat, "Mensajes nuevos", messages, viewModel.notifications::setMessages)
            SwitchRow(Icons.Default.SwapHoriz, "Novedades de tus intercambios", exchanges, viewModel.notifications::setExchanges)
            Row(
                Icons.Default.NotificationsActive,
                "Ajustes del sistema",
                "Sonido, vibración y permiso de notificaciones",
                trailing = Icons.AutoMirrored.Filled.OpenInNew,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
            )

            Section("Legal")
            Row(Icons.Default.Policy, "Política de privacidad", onClick = { nav.openLegal(LegalTexts.PRIVACY) })
            Row(Icons.Default.Description, "Términos de uso", onClick = { nav.openLegal(LegalTexts.TERMS) })
            Row(Icons.Default.Code, "Licencias de código abierto", onClick = nav.openLicenses)

            Section("Acerca de")
            Row(Icons.Default.Info, "Versión", BuildConfig.VERSION_NAME)
            Row(
                Icons.Default.Mail,
                "Contacto",
                LegalTexts.email,
                onClick = {
                    val mail = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${LegalTexts.email}"))
                        .putExtra(Intent.EXTRA_SUBJECT, "Bookintok")
                    runCatching { context.startActivity(mail) } // no mail app: nothing to open
                },
            )

            Section("Zona peligrosa")
            Row(
                Icons.Default.DeleteForever,
                "Eliminar cuenta",
                "Borra tu perfil, libros, fotos y chats",
                danger = true,
                onClick = { confirmDelete = true },
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete || state.deleting) {
        AlertDialog(
            onDismissRequest = { if (!state.deleting) confirmDelete = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Eliminar tu cuenta?") },
            text = {
                Text(
                    "Se borrarán para siempre tu perfil, tus libros y fotos, tus intercambios con sus chats " +
                        "(también para la otra persona) y tus valoraciones. No se puede deshacer.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::deleteAccount, enabled = !state.deleting) {
                    Text(if (state.deleting) "Eliminando…" else "Eliminar cuenta", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !state.deleting) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurface)
                }
            },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = BookintokColors.Accent,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Row(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: ImageVector? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title, color = color) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        trailingContent = trailing?.let { { Icon(it, contentDescription = null) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = BookintokColors.Accent, checkedThumbColor = androidx.compose.ui.graphics.Color.White),
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

// Blocked users

data class BlockedUiState(val users: List<BlockedUserDto> = emptyList(), val loading: Boolean = true, val error: String? = null)

@HiltViewModel
class BlockedUsersViewModel @Inject constructor(private val safety: SafetyRepository) : ViewModel() {
    private val _state = MutableStateFlow(BlockedUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                _state.update { it.copy(users = safety.blocked()) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(error = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun unblock(uid: String) {
        viewModelScope.launch {
            try {
                safety.unblock(uid)
                _state.update { state -> state.copy(users = state.users.filterNot { it.uid == uid }) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }
}

@Composable
fun BlockedUsersScreen(nav: AppNavigator, viewModel: BlockedUsersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScaffold("Usuarios bloqueados", nav.back) { padding ->
        when {
            state.loading -> LoadingBox()
            state.users.isEmpty() -> EmptyState(
                Icons.Default.Block,
                "No has bloqueado a nadie",
                state.error ?: "Puedes bloquear a alguien desde su perfil o desde el chat.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(contentPadding = padding) {
                items(state.users, key = { it.uid }) { user ->
                    ListItem(
                        headlineContent = { Text("@${user.nickname}") },
                        trailingContent = { GhostButton("Desbloquear", { viewModel.unblock(user.uid) }) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.animateItem(),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
            }
        }
    }
}

// Legal documents

@Composable
fun LegalScreen(nav: AppNavigator, document: LegalDocument) {
    SettingsScaffold(document.title, nav.back) { padding ->
        LegalDocumentContent(document, Modifier.padding(padding))
    }
}

/** Also used from the sign-up screen, outside the navigation graph. */
@Composable
fun LegalDocumentContent(document: LegalDocument, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Última actualización: ${document.updated}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(document.intro, style = MaterialTheme.typography.bodyLarge)
        document.sections.forEach { section ->
            Text(section.heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            section.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// Open source licences

private data class Library(val name: String, val license: String)

private val libraries = listOf(
    Library("Kotlin, Kotlin Coroutines y Serialization", "Apache License 2.0"),
    Library("Jetpack Compose, Material 3, Navigation, Room y Lifecycle (AndroidX)", "Apache License 2.0"),
    Library("Hilt / Dagger", "Apache License 2.0"),
    Library("Ktor", "Apache License 2.0"),
    Library("OkHttp", "Apache License 2.0"),
    Library("Coil", "Apache License 2.0"),
    Library("Firebase (Auth y Cloud Messaging)", "Apache License 2.0"),
    Library("Google Play services", "Términos de Google APIs"),
    Library("osmdroid", "Apache License 2.0"),
    Library("Datos del mapa © colaboradores de OpenStreetMap", "Open Database License (ODbL)"),
    Library("Space Grotesk (tipografía)", "SIL Open Font License 1.1"),
    Library("Inter (tipografía)", "SIL Open Font License 1.1"),
)

@Composable
fun LicensesScreen(nav: AppNavigator) {
    val context = LocalContext.current
    var shownFont by remember { mutableStateOf<String?>(null) }
    SettingsScaffold("Licencias", nav.back) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Wordmark()
                    Text(
                        "Bookintok se construye con estos proyectos de código abierto. Gracias a sus autores.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            items(libraries) { library ->
                val fontFile = when {
                    library.name.startsWith("Space Grotesk") -> "OFL-SpaceGrotesk.txt"
                    library.name.startsWith("Inter") -> "OFL-Inter.txt"
                    else -> null
                }
                ListItem(
                    headlineContent = { Text(library.name) },
                    supportingContent = { Text(library.license) },
                    trailingContent = fontFile?.let { { Text("Ver", color = BookintokColors.Accent) } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = if (fontFile != null) Modifier.clickable { shownFont = fontFile } else Modifier,
                )
            }
        }
    }
    shownFont?.let { file ->
        val text = remember(file) { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }
        AlertDialog(
            onDismissRequest = { shownFont = null },
            confirmButton = { TextButton(onClick = { shownFont = null }) { Text("Cerrar", color = MaterialTheme.colorScheme.onSurface) } },
            text = { Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState())) },
        )
    }
}
