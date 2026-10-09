package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.layout.widthIn
import com.stremio.mobile.core.theme.ScreenGutter
import com.stremio.mobile.core.theme.SettingsMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.GlassSurface
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.presentation.components.ThemedButton
import com.stremio.mobile.presentation.components.ThemedCard
import com.stremio.mobile.server.StreamingServerState
import com.stremio.mobile.server.formatServerErrorMessage

@Composable
fun StreamingSettingsScreen(
    serverState: StreamingServerState,
    isNativeServerAvailable: Boolean = true,
    serverSettings: com.stremio.core.models.StreamingServer.Settings?,
    isSeedingEnabled: Boolean,
    minSeedsThreshold: Int,
    minDownloadSpeedBps: Long,
    preferredQuality: String,
    isAutoSwitchOnDeadStream: Boolean,
    onUpdateServerSettings: (com.stremio.core.models.StreamingServer.Settings) -> Unit,
    onSetSeedingEnabled: (Boolean) -> Unit,
    onSetMinSeedsThreshold: (Int) -> Unit,
    onSetMinDownloadSpeedBps: (Long) -> Unit,
    onSetPreferredQuality: (String) -> Unit,
    onSetAutoSwitchOnDeadStream: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = ScreenGutter).widthIn(max = SettingsMaxWidth),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsHeader(title = "Servidor de streaming", onBack = onBack)

        // Status Card
        ThemedCard(
            modifier = Modifier
                .fillMaxWidth(),
            cornerRadius = 20.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Status do servidor",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val dotColor = if (!isNativeServerAvailable) Color(0xFFF44336) else when (serverState) {
                        is StreamingServerState.Ready -> Color(0xFF4CAF50)
                        is StreamingServerState.Starting -> Color(0xFFFFC107)
                        is StreamingServerState.Stopped -> Color(0xFF9E9E9E)
                        is StreamingServerState.Failed -> Color(0xFFF44336)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )

                    val statusText = if (!isNativeServerAvailable) "Indisponível nesta versão" else when (serverState) {
                        is StreamingServerState.Ready -> "Em execução"
                        is StreamingServerState.Starting -> "Iniciando…"
                        is StreamingServerState.Stopped -> "Parado"
                        is StreamingServerState.Failed -> "Erro: ${formatServerErrorMessage(serverState.message)}"
                    }
                    Text(
                        text = "Status: $statusText",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }

                if (!isNativeServerAvailable) {
                    Text(
                        text = "O componente nativo do servidor não foi incluído neste APK. Links diretos (HTTP, HLS, DASH) continuam funcionando; torrents exigem o servidor.",
                        color = MutedText,
                        fontSize = 12.sp,
                    )
                } else if (serverState is StreamingServerState.Ready) {
                    Text(
                        text = "URL: ${serverState.baseUrl}",
                        color = MutedText,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (serverSettings != null) {
            Text(
                text = "OPÇÕES DO SERVIDOR",
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            val cacheSizes = listOf(
                0.0 to "Desativado",
                2147483648.0 to "2 GB",
                5368709120.0 to "5 GB",
                10737418240.0 to "10 GB",
                21474836480.0 to "20 GB"
            )

            val torrentProfiles = listOf(
                200L to "Padrão (equilibrado)",
                80L to "Leve (poucas conexões)",
                500L to "Rápido (muitas conexões)",
                800L to "Ultra rápido (sem limite)"
            )

            val transcodeProfiles = listOf(
                "disabled" to "Sem transcodificação",
                "default" to "Padrão (equilibrado)",
                "fast" to "Rápido (menor qualidade)",
                "slow" to "Alta qualidade (mais CPU)"
            )

            SettingsDropdownRow(
                title = "Tamanho do cache do servidor",
                selectedValue = serverSettings.cacheSize ?: 10737418240.0,
                options = cacheSizes,
                onSelect = { onUpdateServerSettings(serverSettings.copy(cacheSize = it)) },
                description = "Espaço em disco reservado para o buffer dos torrents"
            )

            SettingsDropdownRow(
                title = "Limite de conexões de torrent",
                selectedValue = serverSettings.btMaxConnections,
                options = torrentProfiles,
                onSelect = { onUpdateServerSettings(serverSettings.copy(btMaxConnections = it)) },
                description = "Número máximo de peers conectados ao mesmo tempo"
            )

            SettingsDropdownRow(
                title = "Perfil de transcodificação",
                selectedValue = serverSettings.transcodeProfile ?: "default",
                options = transcodeProfiles,
                onSelect = { onUpdateServerSettings(serverSettings.copy(transcodeProfile = it)) },
                description = "Ajusta o uso de CPU ao transcodificar o vídeo"
            )

            SettingsToggleRow(
                title = "Usar proxy nos streams de vídeo",
                checked = serverSettings.proxyStreamsEnabled,
                onCheckedChange = { onUpdateServerSettings(serverSettings.copy(proxyStreamsEnabled = it)) },
                description = "Faz o tráfego do player passar pelo proxy do servidor"
            )

            SettingsToggleRow(
                title = "Seeding ativado",
                checked = isSeedingEnabled,
                onCheckedChange = onSetSeedingEnabled,
                description = "Continua compartilhando torrents em segundo plano após o download"
            )
        }

        Text(
            text = "SAÚDE DO STREAM",
            color = MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        val minSeedsOptions = listOf(0 to "Qualquer", 1 to "1", 2 to "2", 3 to "3", 5 to "5")
        val minSpeedOptions = listOf(
            0L to "Desativado",
            25_000L to "25 KB/s",
            50_000L to "50 KB/s",
            100_000L to "100 KB/s",
            200_000L to "200 KB/s",
        )
        val qualityOptions = listOf(
            "Any" to "Qualquer",
            "2160p" to "4K",
            "1080p" to "1080p",
            "720p" to "720p",
            "480p" to "480p",
        )

        SettingsDropdownRow(
            title = "Mínimo de seeds",
            selectedValue = minSeedsThreshold,
            options = minSeedsOptions,
            onSelect = onSetMinSeedsThreshold,
            description = "Considera o stream morto quando tiver menos peers que isso"
        )

        SettingsDropdownRow(
            title = "Velocidade mínima de download",
            selectedValue = minDownloadSpeedBps,
            options = minSpeedOptions,
            onSelect = onSetMinDownloadSpeedBps,
            description = "Considera o stream lento abaixo dessa velocidade"
        )

        SettingsDropdownRow(
            title = "Qualidade de vídeo preferida",
            selectedValue = preferredQuality,
            options = qualityOptions,
            onSelect = onSetPreferredQuality,
            description = "Usada para desempatar ao ordenar fontes ou escolher alternativas"
        )

        SettingsToggleRow(
            title = "Trocar automaticamente stream morto",
            checked = isAutoSwitchOnDeadStream,
            onCheckedChange = onSetAutoSwitchOnDeadStream,
            description = "Reproduz a próxima melhor fonte sem perguntar"
        )
    }
}
