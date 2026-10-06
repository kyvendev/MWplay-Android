package com.stremio.mobile.presentation.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.stremio.mobile.auth.FacebookLoginBridge
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.StremioBackground
import com.stremio.mobile.presentation.components.StremioMark
import com.stremio.mobile.presentation.components.ThemedButton
import com.stremio.mobile.presentation.components.ThemedTextButton

enum class AuthScreen { Intro, Login, Signup }

@Composable
fun AuthFlow(
    isLoading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onFacebookLogin: (String) -> Unit,
    onFacebookLoginError: (String) -> Unit,
    onSignup: (String, String, Boolean) -> Unit,
    onClearError: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(AuthScreen.Intro) }
    when (screen) {
        AuthScreen.Intro -> IntroScreen(isLoading, error, { onClearError(); screen = AuthScreen.Login }, onFacebookLogin, onFacebookLoginError, onClearError) { onClearError(); screen = AuthScreen.Signup }
        AuthScreen.Login -> LoginScreen(isLoading, error, { onClearError(); screen = AuthScreen.Intro }, onLogin) { onClearError(); screen = AuthScreen.Signup }
        AuthScreen.Signup -> SignupScreen(isLoading, error, { onClearError(); screen = AuthScreen.Intro }, onSignup) { onClearError(); screen = AuthScreen.Login }
    }
}

@Composable
private fun IntroScreen(
    isLoading: Boolean,
    error: String?,
    onLoginClicked: () -> Unit,
    onFacebookLogin: (String) -> Unit,
    onFacebookLoginError: (String) -> Unit,
    onClearError: () -> Unit,
    onSignupClicked: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(onFacebookLogin, onFacebookLoginError) {
        val callback = object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                result.accessToken.token.takeIf { it.isNotBlank() }?.let(onFacebookLogin)
                    ?: onFacebookLoginError("O Facebook não retornou um token de acesso.")
            }
            override fun onCancel() = onFacebookLoginError("Login com Facebook cancelado.")
            override fun onError(error: FacebookException) = onFacebookLoginError(error.localizedMessage ?: "Não foi possível entrar com Facebook.")
        }
        LoginManager.getInstance().registerCallback(FacebookLoginBridge.callbackManager, callback)
        onDispose { LoginManager.getInstance().unregisterCallback(FacebookLoginBridge.callbackManager) }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF4E2D9C),
                    0.20f to Color(0xFF151735),
                    0.48f to StremioBackground,
                    1.0f to StremioBackground,
                ),
            ),
        ).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 31.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StremioMark(modifier = Modifier.size(64.dp))
                Text("MW Play", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(56.dp))
            Text("Seu mundo em streaming.", color = Color.White, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
            Text("Filmes, séries e muito mais em um só lugar.", color = Color(0xFFD7D7EA), fontSize = 18.sp, lineHeight = 24.sp)
            Spacer(modifier = Modifier.height(28.dp))
            AuthButton("Criar conta", enabled = !isLoading, onClick = onSignupClicked)
            AuthButton(
                text = if (isLoading) "Conectando..." else "Continuar com Facebook",
                containerColor = Color(0xFF126FFF),
                enabled = !isLoading,
            ) {
                onClearError()
                val activity = context.findActivity()
                if (activity == null) onFacebookLoginError("O login com Facebook precisa de uma tela ativa.")
                else LoginManager.getInstance().logInWithReadPermissions(activity, listOf("email"))
            }
            if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp), color = Color.White)
            error?.let { Text(it, color = Color(0xFFFFC66D), fontSize = 14.sp, lineHeight = 19.sp) }
            Spacer(modifier = Modifier.height(18.dp))
            Text("Já tem uma conta?", modifier = Modifier.align(Alignment.CenterHorizontally), color = MutedText, fontSize = 17.sp)
            ThemedTextButton("Entrar", onLoginClicked, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun LoginScreen(isLoading: Boolean, error: String?, onBack: () -> Unit, onSubmit: (String, String) -> Unit, onSignup: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val autofillManager = LocalAutofillManager.current
    AuthFormScaffold("Entrar", "Use sua conta para sincronizar biblioteca, preferências e continuar assistindo.", isLoading, error, onBack) {
        AuthTextField(email, { email = it }, "E-mail", modifier = Modifier.semantics { contentType = ContentType.EmailAddress })
        AuthTextField(password, { password = it }, "Senha", true, Modifier.semantics { contentType = ContentType.Password })
        AuthButton(if (isLoading) "Entrando..." else "Entrar", enabled = !isLoading) { autofillManager?.commit(); onSubmit(email, password) }
        TextLink("Não tem uma conta? Criar conta", onSignup)
    }
}

@Composable
private fun SignupScreen(isLoading: Boolean, error: String?, onBack: () -> Unit, onSubmit: (String, String, Boolean) -> Unit, onLogin: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordConfirm by rememberSaveable { mutableStateOf("") }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    var acceptedPrivacy by rememberSaveable { mutableStateOf(false) }
    var marketing by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val autofillManager = LocalAutofillManager.current
    AuthFormScaffold("Criar conta", "Crie sua conta para manter biblioteca e preferências sincronizadas entre seus dispositivos.", isLoading, localError ?: error, onBack) {
        AuthTextField(email, { email = it }, "E-mail", modifier = Modifier.semantics { contentType = ContentType.EmailAddress })
        AuthTextField(password, { password = it }, "Senha", true, Modifier.semantics { contentType = ContentType.NewPassword })
        AuthTextField(passwordConfirm, { passwordConfirm = it }, "Confirmar senha", true, Modifier.semantics { contentType = ContentType.NewPassword })
        AuthCheckRow(acceptedTerms, "Aceito os Termos de Serviço") { acceptedTerms = it }
        AuthCheckRow(acceptedPrivacy, "Aceito a Política de Privacidade") { acceptedPrivacy = it }
        AuthCheckRow(marketing, "Receber novidades do produto por e-mail") { marketing = it }
        AuthButton(if (isLoading) "Criando conta..." else "Criar conta", enabled = !isLoading) {
            localError = when {
                password != passwordConfirm -> "As senhas não coincidem"
                !acceptedTerms -> "Você precisa aceitar os Termos de Serviço"
                !acceptedPrivacy -> "Você precisa aceitar a Política de Privacidade"
                else -> null
            }
            if (localError == null) { autofillManager?.commit(); onSubmit(email, password, marketing) }
        }
        TextLink("Já tem uma conta? Entrar", onLogin)
    }
}

@Composable
private fun AuthFormScaffold(title: String, subtitle: String, isLoading: Boolean, error: String?, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 26.dp),
        contentPadding = PaddingValues(top = 30.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextLink("Voltar", onBack)
            Spacer(modifier = Modifier.height(42.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StremioMark(modifier = Modifier.size(46.dp))
                Text("MW Play", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(30.dp))
            Text(title, color = Color.White, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(subtitle, color = MutedText, fontSize = 16.sp, lineHeight = 22.sp)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                content()
                if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp), color = AccentPurple)
                error?.let { Text(it, color = Color(0xFFFFC66D), fontSize = 14.sp, lineHeight = 19.sp) }
            }
        }
    }
}

@Composable
private fun AuthTextField(value: String, onValueChange: (String) -> Unit, label: String, isPassword: Boolean = false, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = AccentPurple, unfocusedBorderColor = Color(0xFF555160),
            focusedLabelColor = MutedText, unfocusedLabelColor = MutedText, cursorColor = AccentPurple,
        ),
    )
}

@Composable
private fun AuthCheckRow(checked: Boolean, text: String, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = AccentPurple, uncheckedColor = MutedText, checkmarkColor = Color.White))
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun AuthButton(text: String, containerColor: Color = AccentPurple, enabled: Boolean = true, onClick: () -> Unit) {
    ThemedButton(text = text, onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), containerColor = containerColor)
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    ThemedTextButton(text = text, onClick = onClick)
}
