package com.stremio.mobile.presentation.screens

import com.stremio.mobile.core.theme.SettingsMaxWidth
import com.stremio.mobile.core.theme.ScreenGutter
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.BuildConfig
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.GlassSurface
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.presentation.components.ThemedButton
import com.stremio.mobile.presentation.components.ThemedCard
import com.stremio.mobile.presentation.components.ThemedTextButton
import com.stremio.mobile.presentation.components.ThemedToggle
import com.stremio.mobile.update.UpdateInfo
import com.stremio.mobile.update.UpdateState
import java.io.File
import kotlin.math.roundToInt

@Composable
fun InfoSettingsScreen(serverVersion: String, serverConfigPath: String, serverCachePath: String, updateState: UpdateState, isAutoUpdateEnabled: Boolean, onSetAutoUpdateEnabled: (Boolean) -> Unit, onCheckForUpdates: () -> Unit, onDownloadAndInstallUpdate: (UpdateInfo) -> Unit, onInstallDownloadedUpdate: (File) -> Unit, onIgnoreUpdate: (String) -> Unit, onBack: () -> Unit) {
    var dismissedDialogTag by rememberSaveable { mutableStateOf<String?>(null) }
    val available = updateState as? UpdateState.Available
    if (available != null && dismissedDialogTag != available.info.tagName) {
        UpdateAvailableDialog(info = available.info, onUpdate = { dismissedDialogTag = available.info.tagName; onDownloadAndInstallUpdate(available.info) }, onLater = { dismissedDialogTag = available.info.tagName }, onSkip = { dismissedDialogTag = available.info.tagName; onIgnoreUpdate(available.info.tagName) })
    }
    Column(modifier = Modifier.padding(horizontal = ScreenGutter).widthIn(max = SettingsMaxWidth), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader(title = "Sobre o MW Play", onBack = onBack)
        SectionLabel("DIAGNÓSTICOS DO APLICATIVO")
        ThemedCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ServerDetailRow(label = "Versão do MW Play", value = BuildConfig.VERSION_NAME)
                ServerDetailRow(label = "Versão do servidor de streaming", value = serverVersion)
                ServerDetailRow(label = "Caminho da configuração", value = serverConfigPath)
                ServerDetailRow(label = "Diretório de cache", value = serverCachePath)
            }
        }
        SectionLabel("ATUALIZAÇÕES")
        ThemedCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Verificar atualizações automaticamente", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Verifica novas versões no GitHub ao iniciar, no máximo uma vez por dia.", color = MutedText, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    ThemedToggle(checked = isAutoUpdateEnabled, onCheckedChange = onSetAutoUpdateEnabled)
                }
                UpdateStatusContent(updateState, onCheckForUpdates, onInstallDownloadedUpdate)
            }
        }
    }
}

@Composable
private fun UpdateStatusContent(updateState: UpdateState, onCheckForUpdates: () -> Unit, onInstallDownloadedUpdate: (File) -> Unit) {
    val checking = updateState is UpdateState.Checking
    val downloading = updateState is UpdateState.Downloading
    val status = when (updateState) {
        UpdateState.Idle -> "Nenhuma verificação foi feita ainda."
        UpdateState.Checking -> "Verificando atualizações..."
        UpdateState.UpToDate -> "Você está usando a versão mais recente."
        is UpdateState.Available -> "A versão ${updateState.info.versionName} está disponível."
        is UpdateState.Downloading -> "Baixando atualização..."
        is UpdateState.ReadyToInstall -> if (updateState.needsUnknownSourcesPermission) "Permita instalações desta fonte, volte ao app e toque em Instalar." else "Atualização baixada. Se o instalador não abriu, toque em Instalar."
        is UpdateState.Error -> updateState.message
    }
    val color = when (updateState) { is UpdateState.Error -> Color(0xFFFF8A80); is UpdateState.Available, is UpdateState.ReadyToInstall -> AccentPurple; else -> MutedText }
    Text(text = status, color = color, fontSize = 13.sp, lineHeight = 17.sp)
    if (updateState is UpdateState.Downloading) DownloadProgress(updateState)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemedButton(text = "Verificar atualizações", onClick = onCheckForUpdates, enabled = !checking && !downloading, modifier = Modifier.weight(1f))
        if (updateState is UpdateState.ReadyToInstall) ThemedButton(text = "Instalar", onClick = { onInstallDownloadedUpdate(updateState.file) }, enabled = !downloading, modifier = Modifier.weight(1f), containerColor = Color(0xFF2E7D32))
    }
}

@Composable
private fun DownloadProgress(state: UpdateState.Downloading) {
    val progress = state.progress?.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(GlassSurface)) {
            Box(modifier = Modifier.fillMaxWidth(progress ?: 0f).height(8.dp).clip(RoundedCornerShape(999.dp)).background(AccentPurple))
        }
        Text(text = if (progress != null) "${(progress * 100f).roundToInt()}%" else "${state.bytesRead / (1024L * 1024L)} MB baixados", color = MutedText, fontSize = 12.sp)
    }
}

@Composable
private fun UpdateAvailableDialog(info: UpdateInfo, onUpdate: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(onDismissRequest = onLater, title = { Text(text = "Atualização ${info.versionName} disponível", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = info.apkName, color = MutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = info.releaseNotes.ifBlank { "Uma nova versão do MW Play está pronta para instalar." }, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
    }, confirmButton = { ThemedButton(text = "Atualizar", onClick = onUpdate) }, dismissButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemedTextButton(text = "Ignorar versão", onClick = onSkip); ThemedTextButton(text = "Depois", onClick = onLater) } }, containerColor = GlassSurface, shape = RoundedCornerShape(20.dp))
}

@Composable private fun SectionLabel(text: String) { Text(text = text, color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
@Composable private fun ServerDetailRow(label: String, value: String) { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(text = label, color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Medium); Text(text = value, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp) } }
