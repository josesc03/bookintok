package com.bookintok.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.core.auth.GoogleSignInCancelledException
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

    /** [requestToken] shows Google's account picker; it lives in the UI because it needs the Activity. */
    fun signInWithGoogle(requestToken: suspend () -> String) = work {
        try {
            auth.signInWithGoogle(requestToken())
        } catch (_: GoogleSignInCancelledException) {
            // The user closed the picker: nothing to report
        }
    }

    /** On success the session flow notices the signed-in user and moves on. */
    fun submit() {
        val current = _state.value
        if (current.email.isBlank() || current.password.length < 6) {
            _state.update { it.copy(error = "Introduce tu email y una contraseña de al menos 6 caracteres") }
            return
        }
        work {
            if (current.isSignUp) auth.signUp(current.email.trim(), current.password)
            else auth.signIn(current.email.trim(), current.password)
        }
    }

    private fun work(block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
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

data class VerifyEmailUiState(val working: Boolean = false, val message: String? = null, val verified: Boolean = false)

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(VerifyEmailUiState())
    val state = _state.asStateFlow()

    /** Called when the user says they clicked the link: asks Firebase whether it is verified now. */
    fun check() = work {
        val user = auth.reloadUser()
        if (user?.emailVerified == true) {
            _state.update { it.copy(verified = true) }
        } else {
            _state.update { it.copy(message = "Todavía no aparece como verificado. Abre el enlace del correo e inténtalo de nuevo.") }
        }
    }

    fun resend() = work {
        auth.sendVerificationEmail()
        _state.update { it.copy(message = "Te hemos enviado otro correo. Revisa también la carpeta de spam.") }
    }

    fun signOut() = work { users.signOut() }

    private fun work(block: suspend () -> Unit) {
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _state.update { it.copy(message = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }
}
