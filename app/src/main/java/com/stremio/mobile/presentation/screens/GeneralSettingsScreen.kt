package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.ScreenGutter
import com.stremio.mobile.core.theme.SettingsMaxWidth

/** The most used preferences in one place; they are the same settings shown in their own screens. */
@Composable
fun GeneralSettingsScreen(
    settings: com.stremio.core.types.profile.Profile.Settings?,
    onUpdateSettings: (com.stremio.core.types.profile.Profile.Settings) -> Unit,
    isKeepScreenOn: Boolean,
    onSetKeepScreenOn: (Boolean) -> Unit,
    isAutoUpdateEnabled: Boolean,
    onSetAutoUpdateEnabled: (Boolean) -> Unit,
    isMobileDataWarning: Boolean = false,
    /** Null where there is no mobile data (TV). */
    onSetMobileDataWarning: ((Boolean) -> Unit)? = null,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = ScreenGutter).widthIn(max = SettingsMaxWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsHeader(title = "Configurações gerais", onBack = onBack)
        if (settings != null) {
            GeneralSectionLabel("PREFERÊNCIAS")
            SettingsDropdownRow(title = "Idioma da interface", selectedValue = settings.interfaceLanguage, options = InterfaceLanguageOptions, onSelect = { onUpdateSettings(settings.copy(interfaceLanguage = it)) }, description = "Escolha o idioma dos menus e catálogos")
            SettingsToggleRow(title = "Ocultar spoilers", checked = settings.hideSpoilers, onCheckedChange = { onUpdateSettings(settings.copy(hideSpoilers = it)) }, description = "Desfoca pôsteres de filmes e episódios ainda não assistidos")
        }
        GeneralSectionLabel("REPRODUÇÃO")
        SettingsToggleRow(title = "Manter tela ligada", checked = isKeepScreenOn, onCheckedChange = onSetKeepScreenOn, description = "Impede que a tela desligue durante a reprodução")
        onSetMobileDataWarning?.let { onSet ->
            SettingsToggleRow(title = "Aviso de dados móveis", checked = isMobileDataWarning, onCheckedChange = onSet, description = "Avisa antes de reproduzir usando a conexão de dados móveis")
        }
        GeneralSectionLabel("ATUALIZAÇÕES")
        SettingsToggleRow(title = "Verificar atualizações automaticamente", checked = isAutoUpdateEnabled, onCheckedChange = onSetAutoUpdateEnabled, description = "Verifica novas versões ao iniciar, no máximo uma vez por dia")
    }
}

@Composable
private fun GeneralSectionLabel(text: String) {
    Text(text = text, color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}
