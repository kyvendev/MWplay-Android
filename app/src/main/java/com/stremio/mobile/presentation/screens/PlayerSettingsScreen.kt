package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.player.LanguageCatalog
import com.stremio.mobile.player.PlayerEngine

@Composable
fun PlayerSettingsScreen(settings: com.stremio.core.types.profile.Profile.Settings?, playerUiStyle: String, onSetPlayerUiStyle: (String) -> Unit, onUpdateSettings: (com.stremio.core.types.profile.Profile.Settings) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader(title = "Configurações do player", onBack = onBack)
        if (settings != null) {
            val context = LocalContext.current
            val languages = remember(context) { LanguageCatalog.options(context) }
            val subtitleLanguages = remember(languages) { listOf<Pair<String?, String>>(null to "Nenhuma") + languages.map { (code, label) -> code to label } }
            val subtitleSizes = LanguageCatalog.subtitleSizes.map { it to "$it%" }
            val seekDurations = listOf(5000L to "5 segundos", 10000L to "10 segundos", 15000L to "15 segundos", 30000L to "30 segundos", 60000L to "1 minuto")
            val frameRates = listOf(
                com.stremio.core.types.profile.Profile.FrameRateMatchingStrategy.DISABLED to "Desativado",
                com.stremio.core.types.profile.Profile.FrameRateMatchingStrategy.FRAME_RATE_ONLY to "Somente taxa de quadros",
                com.stremio.core.types.profile.Profile.FrameRateMatchingStrategy.FRAME_RATE_AND_RESOLUTION to "Taxa de quadros e resolução"
            )
            val colors = listOf("#FFFFFF" to "Branco", "#FFFF00" to "Amarelo", "#00FFFF" to "Ciano", "#FF00FF" to "Magenta", "#00FF00" to "Verde", "#FF0000" to "Vermelho", "#000000" to "Preto")
            val nextVideoDurations = listOf(0L to "Desativado", 5000L to "5 segundos", 10000L to "10 segundos", 15000L to "15 segundos", 30000L to "30 segundos")
            val playerEngines = listOf(
                PlayerEngine.EXO.profileValue to "ExoPlayer",
                PlayerEngine.VLC.profileValue to "VLC",
                PlayerEngine.MPV.profileValue to "MPV"
            )
            val playerUiStyles = listOf("global" to "Seguir tema global", "classic" to "Clássico", "modern" to "Moderno (Liquid Glass)")
            SettingsDropdownRow(title = "Player interno", selectedValue = PlayerEngine.fromProfileValue(settings.playerType).profileValue, options = playerEngines, onSelect = { onUpdateSettings(settings.copy(playerType = it)) }, description = "A alteração vale a partir da próxima reprodução.")
            SettingsDropdownRow(title = "Estilo do player", selectedValue = playerUiStyle, options = playerUiStyles, onSelect = onSetPlayerUiStyle, description = "Escolha o visual usado especificamente no player de vídeo")
            Text(text = "LEGENDAS", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            SettingsToggleRow(title = "Selecionar legendas automaticamente", checked = settings.subtitlesAutoSelect, onCheckedChange = { onUpdateSettings(settings.copy(subtitlesAutoSelect = it)) }, description = "Seleciona automaticamente uma legenda durante a reprodução")
            SettingsDropdownRow(title = "Idioma padrão da legenda", selectedValue = settings.subtitlesLanguage, options = subtitleLanguages, onSelect = { onUpdateSettings(settings.copy(subtitlesLanguage = it)) })
            SettingsDropdownRow(title = "Tamanho da legenda", selectedValue = settings.subtitlesSize, options = subtitleSizes, onSelect = { onUpdateSettings(settings.copy(subtitlesSize = it)) })
            SettingsDropdownRow(title = "Cor do texto da legenda", selectedValue = settings.subtitlesTextColor, options = colors, onSelect = { onUpdateSettings(settings.copy(subtitlesTextColor = it)) })
            SettingsDropdownRow(title = "Cor de fundo da legenda", selectedValue = settings.subtitlesBackgroundColor, options = colors, onSelect = { onUpdateSettings(settings.copy(subtitlesBackgroundColor = it)) })
            SettingsDropdownRow(title = "Cor do contorno da legenda", selectedValue = settings.subtitlesOutlineColor, options = colors, onSelect = { onUpdateSettings(settings.copy(subtitlesOutlineColor = it)) })
            SettingsToggleRow(title = "Estilo de legendas ASS", checked = settings.assSubtitlesStyling, onCheckedChange = { onUpdateSettings(settings.copy(assSubtitlesStyling = it)) }, description = "Usa o estilo fornecido pela legenda ASS quando disponível")
            Text(text = "ÁUDIO", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            SettingsDropdownRow(title = "Idioma padrão do áudio", selectedValue = settings.audioLanguage ?: "eng", options = languages, onSelect = { onUpdateSettings(settings.copy(audioLanguage = it)) })
            SettingsToggleRow(title = "Passagem direta de áudio", checked = settings.audioPassthrough, onCheckedChange = { onUpdateSettings(settings.copy(audioPassthrough = it)) }, description = "Envia áudio compatível, como AC3/DTS, diretamente ao receptor")
            SettingsToggleRow(title = "Som surround", checked = settings.surroundSound, onCheckedChange = { onUpdateSettings(settings.copy(surroundSound = it)) }, description = "Ativa saída multicanal para sistemas de áudio 5.1/7.1")
            Text(text = "CONTROLES DE REPRODUÇÃO", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            SettingsToggleRow(title = "Reproduzir em segundo plano", checked = settings.playInBackground, onCheckedChange = { onUpdateSettings(settings.copy(playInBackground = it)) }, description = "Continua reproduzindo áudio quando o aplicativo for minimizado")
            SettingsToggleRow(title = "Decodificação por hardware", checked = settings.hardwareDecoding, onCheckedChange = { onUpdateSettings(settings.copy(hardwareDecoding = it)) }, description = "Usa aceleração de hardware para decodificar vídeo no ExoPlayer")
            SettingsDropdownRow(title = "Ajuste da taxa de quadros", selectedValue = settings.frameRateMatchingStrategy, options = frameRates, onSelect = { onUpdateSettings(settings.copy(frameRateMatchingStrategy = it)) }, description = "Ajusta a taxa de atualização da TV à taxa de quadros do vídeo")
            SettingsDropdownRow(title = "Tempo de avanço/retrocesso", selectedValue = settings.seekTimeDuration, options = seekDurations, onSelect = { onUpdateSettings(settings.copy(seekTimeDuration = it)) }, description = "Tempo usado pelos botões de avançar e voltar")
            SettingsToggleRow(title = "Reproduzir próximo episódio automaticamente", checked = settings.bingeWatching, onCheckedChange = { onUpdateSettings(settings.copy(bingeWatching = it)) })
            SettingsDropdownRow(title = "Aviso do próximo vídeo", selectedValue = settings.nextVideoNotificationDuration, options = nextVideoDurations, onSelect = { onUpdateSettings(settings.copy(nextVideoNotificationDuration = it)) }, description = "Duração do aviso para reproduzir o próximo episódio")
        }
    }
}

