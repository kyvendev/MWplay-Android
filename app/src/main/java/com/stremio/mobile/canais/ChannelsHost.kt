package com.stremio.mobile.canais

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stremio.mobile.BuildConfig
import com.stremio.mobile.core.theme.AccentPurple

import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.SurfaceHigh
import com.stremio.mobile.presentation.state.MainSection
import com.stremio.mobile.presentation.state.MainUiState
import com.stremio.mobile.presentation.viewmodel.MainViewModel
import com.stremio.mobile.update.UpdateState

/** MW Play Canais once signed in: the channel grid, or its short settings page. */
@Composable
fun ChannelsHost(viewModel: MainViewModel, state: MainUiState, onCloseApp: (() -> Unit)? = null) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val favorites by viewModel.favoriteChannels.collectAsStateWithLifecycle()
    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        ChannelsSettings(
            email = state.account.email,
            updateState = state.updateState,
            onCheckUpdates = { viewModel.checkForUpdates(manual = true) },
            onLogout = viewModel::logout,
            onCloseApp = onCloseApp,
            onBack = { settingsOpen = false },
        )
    } else {
        ChannelsScreen(
            filters = state.discoverCatalogWithFilters,
            catalog = state.discoverCatalog,
            favorites = favorites,
            onLoadChannels = { viewModel.selectSection(MainSection.Tv) },
            onSelectRequest = viewModel::selectDiscoverFilter,
            onPlay = viewModel::playChannel,
            onToggleFavorite = viewModel::toggleFavoriteChannel,
            onOpenSettings = { settingsOpen = true },
            onCloseApp = onCloseApp,
        )
    }
}

@Composable
private fun ChannelsSettings(
    email: String?,
    updateState: UpdateState,
    onCheckUpdates: () -> Unit,
    onLogout: () -> Unit,
    onCloseApp: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val isTv = isTvDevice()
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(isTv) { if (isTv) runCatching { backFocus.requestFocus() } }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 28.dp else 12.dp)
            .widthIn(max = 720.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .focusRequester(backFocus)
                    .channelFocus(cornerRadius = 999.dp, focusedScale = 1.08f)
                    .clip(CircleShape)
                    .background(SurfaceHigh)
                    .clickable(role = Role.Button, onClickLabel = "Voltar", onClick = onBack),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Text("Ajustes", color = Color.White, fontSize = if (isTv) 28.sp else 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                Column {
                    Text(email ?: "Conta MW Play", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("A mesma conta do MW Play", color = MutedText, fontSize = 13.sp)
                }
            }
            SettingsAction("Sair da conta", danger = true, onClick = onLogout)
        }
        SettingsCard {
            Text("Atualizações", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                when (updateState) {
                    UpdateState.Checking -> "Verificando…"
                    UpdateState.UpToDate -> "Você está na versão mais recente."
                    is UpdateState.Available -> "Versão ${updateState.info.versionName} disponível."
                    is UpdateState.Error -> updateState.message
                    else -> "Versão instalada: ${BuildConfig.VERSION_NAME}"
                },
                color = MutedText,
                fontSize = 13.sp,
            )
            SettingsAction("Verificar atualizações", onClick = onCheckUpdates)
        }
        onCloseApp?.let { close ->
            SettingsCard {
                Text("Fechar o app", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Encerra o MW Play Canais por completo.", color = MutedText, fontSize = 13.sp)
                SettingsAction("Fechar app", onClick = close)
            }
        }
        Text("MW Play Canais ${BuildConfig.VERSION_NAME}", color = MutedText, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceHigh)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun SettingsAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier
            .channelFocus(cornerRadius = 999.dp, focusedScale = 1.03f)
            .clip(RoundedCornerShape(999.dp))
            .background(if (danger) Color(0x33FF4D4F) else AccentPurple)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

