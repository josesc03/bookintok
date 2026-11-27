package com.bookintok.app

import android.content.Intent
import android.os.Bundle
import com.bookintok.app.push.ChatNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookintok.app.ui.auth.LoginScreen
import com.bookintok.app.ui.auth.ProfileSetupScreen
import com.bookintok.app.ui.auth.VerifyEmailScreen
import com.bookintok.app.ui.components.LogoLoading
import com.bookintok.app.ui.location.LocationScreen
import com.bookintok.app.ui.navigation.BookintokNavHost
import com.bookintok.app.ui.session.SessionState
import com.bookintok.app.ui.session.SessionViewModel
import com.bookintok.app.ui.theme.BookintokTheme
import com.bookintok.app.ui.welcome.WelcomeScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val session: SessionViewModel by viewModels()

    /** Chat to open because the user tapped a notification. */
    private val chatFromNotification = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readChatFrom(intent)
        enableEdgeToEdge()
        setContent {
            BookintokTheme {
                Surface {
                    val state by session.state.collectAsStateWithLifecycle()
                    // The welcome slider opens the app for signed-out users
                    var welcomed by rememberSaveable { mutableStateOf(false) }
                    val screen = if (state == SessionState.SignedOut && !welcomed) WELCOME else state
                    AnimatedContent(screen, contentKey = { it::class }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "session") { current ->
                        when (current) {
                            WELCOME -> WelcomeScreen(onEnter = { welcomed = true })
                            SessionState.Loading -> LogoLoading()
                            SessionState.SignedOut -> LoginScreen()
                            is SessionState.NeedsEmailVerification -> VerifyEmailScreen(email = current.email, onVerified = session::refresh)
                            SessionState.NeedsProfile -> ProfileSetupScreen(onCreated = session::refresh)
                            SessionState.NeedsLocation -> LocationScreen(onDone = session::refresh)
                            is SessionState.Ready -> {
                                val pendingChat by chatFromNotification.collectAsStateWithLifecycle()
                                BookintokNavHost(pendingChat = pendingChat, onPendingChatOpened = { chatFromNotification.value = null })
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readChatFrom(intent)
    }

    private fun readChatFrom(intent: Intent?) {
        val id = intent?.getLongExtra(ChatNotifications.EXTRA_EXCHANGE_ID, -1L) ?: -1L
        if (id > 0) chatFromNotification.value = id
    }

    private companion object {
        val WELCOME = Any()
    }
}
