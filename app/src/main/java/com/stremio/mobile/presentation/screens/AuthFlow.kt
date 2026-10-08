package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.presentation.components.StremioMark
import com.stremio.mobile.presentation.components.ThemedButton
import com.stremio.mobile.presentation.components.rememberIsTelevision
import com.stremio.mobile.presentation.components.tvTextInput

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
    LoginScreen(
        isLoading = isLoading,
        error = error,
        onClearError = onClearError,
        onSubmit = onLogin,
    )
}

@Composable
private fun LoginScreen(
    isLoading: Boolean,
    error: String?,
    onClearError: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val autofillManager = LocalAutofillManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val isTv = rememberIsTelevision()
    val emailRequester = remember { FocusRequester() }
    val passwordRequester = remember { FocusRequester() }
    val submit = {
        if (!isLoading && email.isNotBlank() && password.isNotBlank()) {
            autofillManager?.commit()
            keyboard?.hide()
            onSubmit(email.trim(), password)
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) runCatching { emailRequester.requestFocus() }
    }

    AuthFormScaffold(
        title = "Log in",
        subtitle = "Entre com o e-mail e a senha fornecidos para acessar o MW Play.",
        isLoading = isLoading,
        error = error,
    ) {
        AuthTextField(
            value = email,
            onValueChange = { email = it; onClearError() },
            label = "E-mail",
            modifier = Modifier.focusRequester(emailRequester).semantics { contentType = ContentType.EmailAddress },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordRequester.requestFocus() }),
        )
        AuthTextField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = "Senha",
            isPassword = true,
            modifier = Modifier.focusRequester(passwordRequester).semantics { contentType = ContentType.Password },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        AuthButton(
            text = if (isLoading) "Entrando..." else "Log in",
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            onClick = submit,
        )
    }
}

@Composable
private fun AuthFormScaffold(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    error: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isTv = rememberIsTelevision()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 26.dp),
        contentPadding = PaddingValues(top = if (isTv) 24.dp else 64.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StremioMark(modifier = Modifier.size(58.dp))
                Text("MW Play", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(if (isTv) 20.dp else 54.dp))
            Text(title, color = Color.White, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(subtitle, color = MutedText, fontSize = 16.sp, lineHeight = 22.sp)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                content()
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp),
                        color = AccentPurple,
                    )
                }
                error?.let {
                    Text(it, color = Color(0xFFFFC66D), fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().tvTextInput(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = AccentPurple,
            unfocusedBorderColor = Color(0xFF555160),
            focusedLabelColor = MutedText,
            unfocusedLabelColor = MutedText,
            cursorColor = AccentPurple,
        ),
    )
}

@Composable
fun AuthButton(
    text: String,
    containerColor: Color = AccentPurple,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ThemedButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor,
    )
}

