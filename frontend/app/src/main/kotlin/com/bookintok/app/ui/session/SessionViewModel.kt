package com.bookintok.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    /** Signed up with email and password but has not clicked the link in the verification email. */
    data class NeedsEmailVerification(val email: String?) : SessionState

    /** Has a profile but has not told the app where they are yet. */
    data object NeedsLocation : SessionState

    /** Signed in to Firebase but without a Bookintok profile yet. */
    data object NeedsProfile : SessionState

    /** [offline] is true when the profile could not be checked: cached chats are still usable. */
    data class Ready(val uid: String, val offline: Boolean = false) : SessionState
}

/** Decides which part of the app to show: sign in, email verification, profile creation or the app. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
) : ViewModel() {

    /** Bumped to evaluate the session again (after verifying the email or creating the profile). */
    private val version = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SessionState> = combine(auth.currentUser, version) { user, _ -> user?.uid }
        .flatMapLatest { uid ->
            flow {
                // Read the user again: reloadUser() may have changed emailVerified since the last emission
                val user = auth.currentUserNow()
                if (uid == null || user == null) {
                    emit(SessionState.SignedOut)
                    return@flow
                }
                if (!user.emailVerified) {
                    emit(SessionState.NeedsEmailVerification(user.email))
                    return@flow
                }
                emit(SessionState.Loading)
                val state = try {
                    val profile = users.myProfile()
                    when {
                        profile == null -> SessionState.NeedsProfile
                        profile.latitude == null -> SessionState.NeedsLocation
                        else -> SessionState.Ready(uid)
                    }
                } catch (_: Exception) {
                    SessionState.Ready(uid, offline = true)
                }
                emit(state)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    fun refresh() {
        version.value++
    }
}
