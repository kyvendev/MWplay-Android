package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.presentation.components.*

enum class SettingsSubScreen { Main, Addons, General, Interface, Player, Streaming, Android, LiquidGlassLab, Info }

@Composable
fun SettingsPanel(email: String?, onLogout: () -> Unit, onNavigateTo: (SettingsSubScreen) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Configurações")
        ThemedCard(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.AccountCircle, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Column {
                        Text(email ?: "Conta MW Play", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (email != null) "Conectado" else "Modo visitante", color = MutedText, fontSize = 13.sp)
                    }
                }
                AuthButton("Sair da conta", Color(0x15FFFFFF), onLogout)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsMenuRow(Icons.Outlined.AccountCircle, "Geral", "Conta, integrações e preferências gerais") { onNavigateTo(SettingsSubScreen.General) }
            SettingsMenuRow(Icons.Outlined.Language, "Interface", "Idioma e aparência do aplicativo") { onNavigateTo(SettingsSubScreen.Interface) }
            SettingsMenuRow(Icons.Outlined.PlayCircle, "Player", "Legendas, decodificação e reprodução") { onNavigateTo(SettingsSubScreen.Player) }
            SettingsMenuRow(Icons.Outlined.Cloud, "Streaming", "Conexão, cache e desempenho") { onNavigateTo(SettingsSubScreen.Streaming) }
            SettingsMenuRow(Icons.Outlined.Android, "Android", "Inicialização e comportamento em segundo plano") { onNavigateTo(SettingsSubScreen.Android) }
            SettingsMenuRow(Icons.Outlined.Info, "Sobre o MW Play", "Versão, diagnósticos e informações do aplicativo") { onNavigateTo(SettingsSubScreen.Info) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun AuthButton(text:String, containerColor:Color, onClick:()->Unit) = ThemedButton(text,onClick,Modifier.fillMaxWidth().tvFocusTarget(cornerRadius=999.dp,focusedScale=1.03f),containerColor=containerColor)

@Composable
private fun SettingsMenuRow(icon:ImageVector,title:String,description:String,onClick:()->Unit) {
    val haptic=rememberGlobalHapticFeedback()
    ThemedCard(Modifier.fillMaxWidth(),cornerRadius=16.dp) {
        Row(Modifier.fillMaxWidth().tvFocusTarget(cornerRadius=16.dp,focusedScale=1.025f).clickable{haptic();onClick()}.padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.weight(1f)) {
                Icon(icon,null,tint=Color.White,modifier=Modifier.size(24.dp)); Column(verticalArrangement=Arrangement.spacedBy(2.dp)){Text(title,color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold);Text(description,color=MutedText,fontSize=12.sp)}
            }
            Icon(Icons.Outlined.ChevronRight,null,tint=Color.White,modifier=Modifier.size(24.dp))
        }
    }
}

@Composable
fun SettingsHeader(title:String,onBack:()->Unit) {
    val haptic=rememberGlobalHapticFeedback()
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        Box(Modifier.tvFocusTarget(cornerRadius=999.dp,focusedScale=1.10f).clickable{haptic();onBack()}.padding(8.dp),contentAlignment=Alignment.Center){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Voltar",tint=Color.White,modifier=Modifier.size(24.dp))}
        Text(title,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold)
    }
}

@Composable
fun SettingsToggleRow(title:String,checked:Boolean,onCheckedChange:(Boolean)->Unit,description:String?=null) {
    val haptic=rememberGlobalHapticFeedback()
    ThemedCard(Modifier.fillMaxWidth(),cornerRadius=16.dp) {
        Row(Modifier.fillMaxWidth().tvFocusTarget(cornerRadius=16.dp,focusedScale=1.02f).clickable{haptic();onCheckedChange(!checked)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){Text(title,color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold);description?.let{Text(it,color=MutedText,fontSize=12.sp)}}
            // The whole row is the TV action. Prevent the visual switch from becoming a second D-pad stop.
            ThemedToggle(checked,onCheckedChange,modifier=Modifier.focusProperties{canFocus=false})
        }
    }
}

@Composable
fun <T> SettingsDropdownRow(title:String,selectedValue:T,options:List<Pair<T,String>>,onSelect:(T)->Unit,description:String?=null) {
    var expanded by remember{mutableStateOf(false)}; val selectedLabel=options.find{it.first==selectedValue}?.second?:selectedValue.toString(); val haptic=rememberGlobalHapticFeedback()
    ThemedCard(Modifier.fillMaxWidth(),cornerRadius=16.dp) {
        Row(Modifier.fillMaxWidth().tvFocusTarget(cornerRadius=16.dp,focusedScale=1.02f).clickable{haptic();expanded=true}.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){Text(title,color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold);description?.let{Text(it,color=MutedText,fontSize=12.sp)}}
            Box { Text(selectedLabel,color=AccentPurple,fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp)); ThemedDropdownMenu(expanded,{expanded=false}) { options.forEach{(value,label)->DropdownMenuItem(text={Text(label,color=Color.White)},onClick={haptic();onSelect(value);expanded=false},modifier=Modifier.tvFocusTarget(cornerRadius=10.dp,focusedScale=1.02f))} } }
        }
    }
}

@Composable
fun SettingsSliderRow(title:String,value:Float,onValueChange:(Float)->Unit,valueRange:ClosedFloatingPointRange<Float> = 0f..1f,displayValue:String="${(value*100).toInt()}%",description:String?=null) {
    ThemedCard(Modifier.fillMaxWidth(),cornerRadius=16.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) { Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){Text(title,color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold);description?.let{Text(it,color=MutedText,fontSize=12.sp)}};Text(displayValue,color=AccentPurple,fontSize=15.sp,fontWeight=FontWeight.Bold) }
            ThemedSlider(value,onValueChange,modifier=Modifier.fillMaxWidth().tvFocusTarget(cornerRadius=12.dp,focusedScale=1.01f),valueRange=valueRange)
        }
    }
}

@Composable
fun SettingsClickRow(title:String,onClick:()->Unit,description:String?=null) {
    val haptic=rememberGlobalHapticFeedback()
    ThemedCard(Modifier.fillMaxWidth(),cornerRadius=16.dp) { Row(Modifier.fillMaxWidth().tvFocusTarget(cornerRadius=16.dp,focusedScale=1.02f).clickable{haptic();onClick()}.padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) { Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){Text(title,color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold);description?.let{Text(it,color=MutedText,fontSize=12.sp)}};Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,tint=Color.White,modifier=Modifier.size(20.dp)) } }
}
