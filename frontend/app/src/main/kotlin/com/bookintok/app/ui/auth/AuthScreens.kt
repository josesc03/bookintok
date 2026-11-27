package com.bookintok.app.ui.auth

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bookintok.app.core.legal.LegalDocument
import com.bookintok.app.core.legal.LegalTexts
import com.bookintok.app.ui.settings.LegalDocumentContent
import com.bookintok.app.ui.theme.BookintokColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookintok.app.BuildConfig
import com.bookintok.app.core.auth.requestGoogleIdToken
import com.bookintok.app.ui.components.FormField
import com.bookintok.app.ui.components.GhostButton
import com.bookintok.app.ui.components.PrimaryButton
import com.bookintok.app.ui.components.SecondaryButton

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    FormColumn {
        Logo()
        Text(
            if (state.isSignUp) "Crea tu cuenta" else "Hola de nuevo",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            if (state.isSignUp) "Empieza a intercambiar libros con gente cerca de ti." else "Entra para ver los libros que tienes cerca.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryButton(
            "Continuar con Google",
            onClick = { viewModel.signInWithGoogle { requestGoogleIdToken(context, BuildConfig.GOOGLE_WEB_CLIENT_ID) } },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f))
            Text("o con tu email", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f))
        }
        FormField("Email", state.email, viewModel::onEmail, placeholder = "tu@email.com", keyboardType = KeyboardType.Email)
        FormField(
            "Contraseña",
            state.password,
            viewModel::onPassword,
            placeholder = "Al menos 6 caracteres",
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        PrimaryButton(
            if (state.isSignUp) "Crear cuenta" else "Entrar",
            onClick = viewModel::submit,
            loading = state.loading,
            modifier = Modifier.fillMaxWidth(),
        )
        GhostButton(
            if (state.isSignUp) "Ya tengo cuenta" else "¿No tienes cuenta? Regístrate",
            onClick = viewModel::toggleMode,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        LegalConsent()
    }
}

/** "By continuing you accept…", with both documents one tap away (this screen is outside the navigation graph). */
@Composable
private fun LegalConsent() {
    var shown by remember { mutableStateOf<LegalDocument?>(null) }
    val link = SpanStyle(color = BookintokColors.Accent, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
    val text = buildAnnotatedString {
        append("Al continuar aceptas los ")
        withLink(LinkAnnotation.Clickable("terms", TextLinkStyles(link)) { shown = LegalTexts.terms }) { append("Términos de uso") }
        append(" y la ")
        withLink(LinkAnnotation.Clickable("privacy", TextLinkStyles(link)) { shown = LegalTexts.privacy }) { append("Política de privacidad") }
        append(".")
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    shown?.let { document ->
        Dialog(onDismissRequest = { shown = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { shown = null }) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
                        Text(document.title, style = MaterialTheme.typography.titleLarge)
                    }
                    LegalDocumentContent(document)
                }
            }
        }
    }
}

@Composable
fun VerifyEmailScreen(email: String?, onVerified: () -> Unit, viewModel: VerifyEmailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.verified) { if (state.verified) onVerified() }
    FormColumn {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.MarkEmailUnread, contentDescription = null, modifier = Modifier.size(36.dp)) }
        Text("Revisa tu correo", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Te hemos enviado un enlace a ${email ?: "tu correo"}. Ábrelo para activar tu cuenta y vuelve aquí.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.message?.let { Notice(it) }
        PrimaryButton("Ya lo he verificado", onClick = viewModel::check, loading = state.working, modifier = Modifier.fillMaxWidth())
        SecondaryButton("Reenviar el correo", onClick = viewModel::resend, enabled = !state.working, modifier = Modifier.fillMaxWidth())
        GhostButton("Usar otra cuenta", onClick = viewModel::signOut, enabled = !state.working, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun ProfileSetupScreen(onCreated: () -> Unit, viewModel: ProfileSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onCreated() }
    FormColumn {
        Text("Crea tu perfil", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Así te verán los demás cuando intercambies libros.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FormField(
            "Nombre de usuario",
            state.nickname,
            viewModel::onNickname,
            placeholder = "por ejemplo, ana_lee",
            error = state.error,
        )
        FormField("Nombre (opcional)", state.displayName, viewModel::onDisplayName, placeholder = "Cómo quieres que te llamen")
        PrimaryButton("Continuar", onClick = viewModel::submit, loading = state.loading, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp)) }
        Text("Bookintok", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Notice(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Start)
    }
}

/** Left-aligned form that stays above the keyboard and scrolls when it does not fit. */
@Composable
private fun FormColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}
