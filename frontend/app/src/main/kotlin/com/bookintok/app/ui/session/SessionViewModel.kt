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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    /** Signed in to Firebase but without a BookinTok profile yet. */
    data object NeedsProfile : SessionState

    /** [offline] is true when the profile could not be checked: cached chats are still usable. */
    data class Ready(val uid: String, val offline: Boolean = false) : SessionState
}

/** Decides which part of the app to show: sign in, profile creation or the app itself. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    auth: AuthRepository,
    private val users: UserRepository,
) : ViewModel() {

    /** Bumped after the profile is created, to check it again. */
    private val profileVersion = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SessionState> = combine(auth.currentUid, profileVersion) { uid, _ -> uid }
        .flatMapLatest { uid ->
            flow {
                if (uid == null) {
                    emit(SessionState.SignedOut)
                    return@flow
                }
                emit(SessionState.Loading)
                val state = try {
                    if (users.myProfile() == null) SessionState.NeedsProfile else SessionState.Ready(uid)
                } catch (_: Exception) {
                    SessionState.Ready(uid, offline = true)
                }
                emit(state)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    fun profileCreated() {
        profileVersion.value++
    }
}
