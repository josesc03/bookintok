package com.bookintok.app.ui.safety

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.network.ReportReason
import com.bookintok.app.data.SafetyRepository
import com.bookintok.app.ui.common.toUserMessage
import com.bookintok.app.ui.components.FormField
import com.bookintok.app.ui.components.PrimaryButton
import com.bookintok.app.ui.theme.BookintokColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyUiState(val working: Boolean = false, val message: String? = null, val blocked: Boolean = false)

/** Report and block, shared by the chat and the public profile. */
@HiltViewModel
class SafetyViewModel @Inject constructor(private val safety: SafetyRepository) : ViewModel() {
    private val _state = MutableStateFlow(SafetyUiState())
    val state = _state.asStateFlow()

    fun report(uid: String, reason: ReportReason, comment: String?, exchangeId: Long?) = run("Gracias, revisaremos tu denuncia.") {
        safety.report(uid, reason, comment, exchangeId)
    }

    fun block(uid: String, nickname: String) = run("Has bloqueado a @$nickname.") {
        safety.block(uid)
        _state.update { it.copy(blocked = true) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun run(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            try {
                block()
                _state.update { it.copy(message = success) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(message = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(nickname: String, working: Boolean, onDismiss: () -> Unit, onSend: (ReportReason, String?) -> Unit) {
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var comment by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Denunciar a @$nickname", style = MaterialTheme.typography.titleLarge)
            Text(
                "La denuncia es anónima para @$nickname. La revisaremos y tomaremos medidas si incumple las normas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReportReason.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { reason = option }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = reason == option,
                        onClick = { reason = option },
                        colors = RadioButtonDefaults.colors(selectedColor = BookintokColors.Accent),
                    )
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            FormField("Detalles (opcional)", comment, { comment = it.take(1000) }, placeholder = "Cuéntanos qué ha pasado")
            PrimaryButton(
                "Enviar denuncia",
                onClick = { reason?.let { onSend(it, comment) } },
                enabled = reason != null,
                loading = working,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun BlockDialog(nickname: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("¿Bloquear a @$nickname?") },
        text = {
            Text(
                "Dejaréis de ver los libros del otro, se cancelarán vuestros intercambios abiertos y no podréis " +
                    "empezar ni retomar ninguno. Puedes desbloquearle desde Ajustes.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Bloquear", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = MaterialTheme.colorScheme.onSurface) } },
    )
}
