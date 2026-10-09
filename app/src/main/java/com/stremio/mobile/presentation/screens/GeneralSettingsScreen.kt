package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.layout.widthIn
import com.stremio.mobile.core.theme.ScreenGutter
import com.stremio.mobile.core.theme.SettingsMaxWidth
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.presentation.components.ThemedButton
import com.stremio.mobile.presentation.components.ThemedCard

@Composable
fun GeneralSettingsScreen(
    isAuthenticated: Boolean,
    isTraktAuthenticated: Boolean,
    onAuthenticateTrakt: (Context) -> Unit,
    onLogoutTrakt: () -> Unit,
    onInstallTraktAddon: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = ScreenGutter).widthIn(max = SettingsMaxWidth), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader(title = "Configurações gerais", onBack = onBack)
        Text(text = "INTEGRAÇÕES", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        ThemedCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Integração com Trakt", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = if (isTraktAuthenticated) "Autenticado" else "Não autenticado", color = if (isTraktAuthenticated) Color(0xFF4CAF50) else MutedText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Text(text = "Sincronize o que você assiste com o histórico e a lista do seu perfil no Trakt.", color = MutedText, fontSize = 12.sp, lineHeight = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isTraktAuthenticated) {
                        ThemedButton(text = "Sair do Trakt", onClick = onLogoutTrakt, containerColor = Color(0xFFD32F2F), modifier = Modifier.weight(1f))
                        ThemedButton(text = "Instalar integração", onClick = onInstallTraktAddon, containerColor = AccentPurple, modifier = Modifier.weight(1f))
                    } else {
                        ThemedButton(text = "Autenticar no Trakt", onClick = { onAuthenticateTrakt(context) }, enabled = isAuthenticated, containerColor = AccentPurple, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
