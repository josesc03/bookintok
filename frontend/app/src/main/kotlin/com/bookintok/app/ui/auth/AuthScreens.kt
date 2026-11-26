package com.bookintok.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.data.UserRepository
import com.bookintok.app.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSignUp: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun toggleMode() = _state.update { it.copy(isSignUp = !it.isSignUp, error = null) }

    /** On success the session flow notices the signed-in user and moves on. */
    fun submit() {
        val current = _state.value
        if (current.email.isBlank() || current.password.length < 6) {
            _state.update { it.copy(error = "Introduce tu email y una contraseña de al menos 6 caracteres") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                if (current.isSignUp) auth.signUp(current.email.trim(), current.password)
                else auth.signIn(current.email.trim(), current.password)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FormColumn {
        Text("BookinTok", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text("Intercambia libros con gente cerca de ti", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::submit, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            if (state.loading) CircularProgressIndicator(Modifier.padding(2.dp)) else Text(if (state.isSignUp) "Crear cuenta" else "Entrar")
        }
        TextButton(onClick = viewModel::toggleMode) {
            Text(if (state.isSignUp) "Ya tengo cuenta" else "¿No tienes cuenta? Regístrate")
        }
    }
}

data class ProfileSetupUiState(
    val nickname: String = "",
    val displayName: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileSetupUiState())
    val state = _state.asStateFlow()

    fun onNickname(value: String) = _state.update { it.copy(nickname = value, error = null) }

    fun onDisplayName(value: String) = _state.update { it.copy(displayName = value) }

    fun submit() {
        val nickname = _state.value.nickname.trim()
        if (!NICKNAME.matches(nickname)) {
            _state.update { it.copy(error = "Entre 3 y 30 caracteres: letras, números, '_' o '.'") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                users.register(nickname, _state.value.displayName)
                _state.update { it.copy(done = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private companion object {
        val NICKNAME = Regex("^[a-zA-Z0-9_.]{3,30}$")
    }
}

@Composable
fun ProfileSetupScreen(onCreated: () -> Unit, viewModel: ProfileSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onCreated() }
    FormColumn {
        Text("Crea tu perfil", style = MaterialTheme.typography.headlineMedium)
        Text("Así te verán los demás cuando intercambies libros.")
        OutlinedTextField(
            value = state.nickname,
            onValueChange = viewModel::onNickname,
            label = { Text("Nombre de usuario") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayName,
            label = { Text("Nombre (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::submit, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            Text("Continuar")
        }
    }
}

@Composable
private fun FormColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
