package com.stremio.mobile.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.stremio.mobile.presentation.state.MainSection

// "Ajustes" instead of "Configurações": the longer word did not fit the tabs. Live TV moved to
// the separate MW Play Canais app, so there is no TV tab.
enum class AppView(val label: String, val icon: ImageVector) {
    Home("Início", Icons.Outlined.Home),
    Discover("Descobrir", Icons.Outlined.Explore),
    Library("Biblioteca", Icons.Outlined.VideoLibrary),
    Settings("Ajustes", Icons.Outlined.Settings),
}

fun AppView.toSection(): MainSection = when (this) {
    AppView.Home -> MainSection.Home
    AppView.Discover -> MainSection.Discover
    AppView.Library -> MainSection.Library
    AppView.Settings -> MainSection.Settings
}

fun MainSection.toAppView(): AppView = when (this) {
    MainSection.Home -> AppView.Home
    MainSection.Discover -> AppView.Discover
    MainSection.Tv -> AppView.Home
    MainSection.Library -> AppView.Library
    MainSection.Settings -> AppView.Settings
}
