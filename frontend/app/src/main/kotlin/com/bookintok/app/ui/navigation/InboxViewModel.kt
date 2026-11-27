package com.bookintok.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookintok.app.core.auth.AuthRepository
import com.bookintok.app.data.InboxManager
import com.bookintok.app.push.DeviceRegistration
import kotlinx.coroutines.launch
import com.bookintok.app.data.UnreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-wide realtime inbox: keeps chats in sync while the app is visible and counts unread messages. */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val inbox: InboxManager,
    unread: UnreadRepository,
    auth: AuthRepository,
    registration: DeviceRegistration,
) : ViewModel() {

    init {
        // So notifications reach this device while the app is closed (best effort: retried on next start)
        viewModelScope.launch { runCatching { registration.register() } }
    }

    /** Messages not read yet across all chats, for the badge of the Chats tab. */
    val unreadTotal = unread.unreadCounts(auth.currentUidNow().orEmpty())
        .map { it.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Collected while the app is in the foreground (see BookintokNavHost). */
    suspend fun runInbox() = inbox.run()
}
