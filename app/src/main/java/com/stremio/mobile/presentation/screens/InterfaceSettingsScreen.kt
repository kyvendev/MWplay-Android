package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.layout.widthIn
import com.stremio.mobile.core.theme.ScreenGutter
import com.stremio.mobile.core.theme.SettingsMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AppFont
import com.stremio.mobile.core.theme.MutedText

@Composable
fun InterfaceSettingsScreen(
    settings: com.stremio.core.types.profile.Profile.Settings?, globalUiStyle: String, glassEffectsMode: String,
    globalGlassAlpha: Float, adaptiveGlassContrast: Boolean, glassHapticsEnabled: Boolean,
    hapticsIntensity: String, selectedFont: AppFont,
    onUpdateSettings: (com.stremio.core.types.profile.Profile.Settings) -> Unit,
    onSetGlobalUiStyle: (String) -> Unit, onSetGlassEffectsMode: (String) -> Unit,
    onSetGlobalGlassAlpha: (Float) -> Unit, onSetAdaptiveGlassContrastEnabled: (Boolean) -> Unit,
    onSetGlassHapticsEnabled: (Boolean) -> Unit, onSetHapticsIntensity: (String) -> Unit,
    onSetSelectedFont: (AppFont) -> Unit, onNavigateToLiquidGlassLab: () -> Unit, onBack: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = ScreenGutter).widthIn(max = SettingsMaxWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsHeader(title = "Configurações da interface", onBack = onBack)
        if (settings != null) {
            val languages = listOf("eng" to "Inglês", "spa" to "Espanhol", "fre" to "Francês", "ger" to "Alemão", "ita" to "Italiano", "por" to "Português", "rus" to "Russo", "zho" to "Chinês")
            val uiStyles = listOf("classic" to "Clássico", "modern" to "Moderno (Liquid Glass)")
            val glassEffects = listOf("balanced" to "Equilibrado", "full" to "Desfoque completo", "static" to "Desempenho")
            val intensities = listOf("Light" to "Leve", "Medium" to "Média", "Heavy" to "Forte")
            Text(text = "APARÊNCIA", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            SettingsDropdownRow(title = "Estilo global da interface", selectedValue = globalUiStyle, options = uiStyles, onSelect = onSetGlobalUiStyle)
            val fonts = AppFont.entries.map { it to it.displayName }
            SettingsDropdownRow(title = "Tipografia do aplicativo", selectedValue = selectedFont, options = fonts, onSelect = onSetSelectedFont, description = "Escolha a fonte padrão do aplicativo")
            SettingsDropdownRow(title = "Efeitos de vidro", selectedValue = glassEffectsMode, options = glassEffects, onSelect = onSetGlassEffectsMode, description = "Controla o desempenho e o desfoque do Liquid Glass")
            SettingsSliderRow(title = "Transparência do vidro", value = globalGlassAlpha, onValueChange = onSetGlobalGlassAlpha, valueRange = 0f..0.6f, description = "Ajuste a opacidade das superfícies Liquid Glass")
            SettingsToggleRow(title = "Contraste adaptativo", checked = adaptiveGlassContrast, onCheckedChange = onSetAdaptiveGlassContrastEnabled, description = "Melhora contraste, bordas e sombras sobre conteúdos claros ou movimentados")
            SettingsToggleRow(title = "Resposta tátil", checked = glassHapticsEnabled, onCheckedChange = onSetGlassHapticsEnabled, description = "Ativa vibrações sutis durante as interações")
            if (glassHapticsEnabled) SettingsDropdownRow(title = "Intensidade da vibração", selectedValue = hapticsIntensity, options = intensities, onSelect = onSetHapticsIntensity)
            SettingsClickRow(title = "Laboratório Liquid Glass", onClick = onNavigateToLiquidGlassLab, description = "Ajuste desfoque, refração, realces e valores personalizados")
            Text(text = "INTERFACE", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            SettingsDropdownRow(title = "Idioma da interface", selectedValue = settings.interfaceLanguage, options = languages, onSelect = { onUpdateSettings(settings.copy(interfaceLanguage = it)) }, description = "Escolha o idioma dos menus e catálogos")
            SettingsToggleRow(title = "Ocultar spoilers", checked = settings.hideSpoilers, onCheckedChange = { onUpdateSettings(settings.copy(hideSpoilers = it)) }, description = "Desfoca pôsteres de filmes e episódios ainda não assistidos")
            SettingsToggleRow(title = "Suporte a controle", checked = settings.gamepadSupport, onCheckedChange = { onUpdateSettings(settings.copy(gamepadSupport = it)) }, description = "Ativa navegação por controle remoto e gamepad")
        }
    }
}
