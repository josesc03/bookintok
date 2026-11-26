package com.bookintok.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookintok.app.ui.auth.LoginScreen
import com.bookintok.app.ui.auth.ProfileSetupScreen
import com.bookintok.app.ui.common.LoadingBox
import com.bookintok.app.ui.navigation.BookintokNavHost
import com.bookintok.app.ui.session.SessionState
import com.bookintok.app.ui.session.SessionViewModel
import com.bookintok.app.ui.theme.BookintokTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val session: SessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookintokTheme {
                Surface {
                    val state by session.state.collectAsStateWithLifecycle()
                    when (state) {
                        SessionState.Loading -> LoadingBox()
                        SessionState.SignedOut -> LoginScreen()
                        SessionState.NeedsProfile -> ProfileSetupScreen(onCreated = session::profileCreated)
                        is SessionState.Ready -> BookintokNavHost()
                    }
                }
            }
        }
    }
}
