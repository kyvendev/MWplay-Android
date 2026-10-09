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
import com.stremio.mobile.core.theme.MutedText

@Composable
fun AndroidSettingsScreen(
    isAutoStartOnBoot: Boolean, onSetAutoStartOnBoot: (Boolean) -> Unit,
    isServerInForeground: Boolean, onSetServerInForeground: (Boolean) -> Unit,
    isMobileDataWarning: Boolean, onSetMobileDataWarning: (Boolean) -> Unit,
    isKeepScreenOn: Boolean, onSetKeepScreenOn: (Boolean) -> Unit,
    isAnalyticsEnabled: Boolean, onSetAnalyticsEnabled: (Boolean) -> Unit,
    onShowAnalyticsDisclosure: () -> Unit, onBack: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = ScreenGutter).widthIn(max = SettingsMaxWidth), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsHeader(title = "Configurações do Android", onBack = onBack)
        Text(text = "SISTEMA E INTEGRAÇÃO", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        SettingsToggleRow(title = "Iniciar servidor com o aparelho", checked = isAutoStartOnBoot, onCheckedChange = onSetAutoStartOnBoot, description = "Inicia o servidor local automaticamente quando o aparelho ligar")
        SettingsToggleRow(title = "Serviço do servidor em primeiro plano", checked = isServerInForeground, onCheckedChange = onSetServerInForeground, description = "Mantém o servidor ativo com uma notificação para evitar que o Android o encerre")
        SettingsToggleRow(title = "Manter tela ligada", checked = isKeepScreenOn, onCheckedChange = onSetKeepScreenOn, description = "Impede que a tela desligue durante a reprodução")
        Text(text = "REDE E USO DE DADOS", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        SettingsToggleRow(title = "Aviso de dados móveis", checked = isMobileDataWarning, onCheckedChange = onSetMobileDataWarning, description = "Avisa antes de reproduzir usando a conexão de dados móveis")
        Text(text = "PRIVACIDADE E DIAGNÓSTICOS", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        SettingsToggleRow(title = "Compartilhar diagnósticos e análises", checked = isAnalyticsEnabled, onCheckedChange = onSetAnalyticsEnabled, description = "Ajuda a melhorar o aplicativo compartilhando relatórios anônimos de falhas e uso")
        SettingsClickRow(title = "Privacidade e análises", onClick = onShowAnalyticsDisclosure, description = "Veja como relatórios anônimos de falhas e estatísticas de uso são processados")
    }
}
