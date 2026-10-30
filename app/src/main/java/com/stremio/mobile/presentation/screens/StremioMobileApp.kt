package com.stremio.mobile.presentation.screens

import android.content.Context
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import com.stremio.mobile.server.formatServerErrorMessage
import com.stremio.mobile.server.StreamingServerState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import com.stremio.mobile.data.model.SportsChannelPolicy
import com.stremio.mobile.data.model.TvCategories
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.stremio.mobile.presentation.components.LocalGlobalUiTheme
import com.stremio.mobile.presentation.components.GlobalUiTheme
import com.stremio.mobile.presentation.components.LocalGlassAlpha
import com.stremio.mobile.core.theme.*
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.ContentLabels
import com.stremio.mobile.data.model.TvCatalogPolicy
import com.stremio.mobile.data.model.LiquidGlassTuning
import com.stremio.mobile.presentation.components.*
import com.stremio.mobile.presentation.navigation.AppView
import com.stremio.mobile.presentation.navigation.toAppView
import com.stremio.mobile.presentation.navigation.toSection
import com.stremio.mobile.presentation.state.MainSection
import com.stremio.mobile.presentation.viewmodel.MainViewModel
import com.stremio.mobile.update.UpdateInfo
import com.stremio.mobile.update.UpdateState
import com.stremio.mobile.MainApplication
import com.stremio.mobile.cast.CastMiniController
import java.io.File
import kotlin.math.roundToInt

@Composable
fun StremioMobileApp(viewModel: MainViewModel, showIntro: Boolean = false) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val streamsState by viewModel.streamsState.collectAsStateWithLifecycle()
    val isPlayerOpen by viewModel.isPlayerOpen.collectAsStateWithLifecycle()
    // Saveable so rotation / process restore never replays the opening.
    var introVisible by rememberSaveable { mutableStateOf(showIntro) }
    val sessionRestoring by viewModel.sessionRestoring.collectAsStateWithLifecycle()
    val rootGlobalTheme = remember(
        state.globalUiStyle,
        state.glassEffectsMode,
        state.globalGlassAlpha,
        state.glassHapticsEnabled,
        state.hapticsIntensity,
        state.liquidGlassTuning,
        state.adaptiveGlassContrast,
    ) {
        GlobalUiTheme(
            style = state.globalUiStyle,
            glassEffectsMode = state.glassEffectsMode,
            glassAlpha = state.globalGlassAlpha,
            hapticsEnabled = state.glassHapticsEnabled,
            hapticsIntensity = state.hapticsIntensity,
            liquidGlassTuning = state.liquidGlassTuning,
            adaptiveGlassContrast = state.adaptiveGlassContrast,
        )
    }

    StremioMobileTheme(appFont = state.selectedFont) {
        CompositionLocalProvider(
            LocalGlobalUiTheme provides rootGlobalTheme,
            LocalGlassAlpha provides state.globalGlassAlpha,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StremioBackgroundBrush),
            ) {
            if (state.account.isAuthenticated) {
                BoardScreen(
                    state = state,
                    onRefresh = viewModel::refreshCatalogs,
                    onOpenSearch = viewModel::openSearch,
                    onSelectSection = viewModel::selectSection,
                    onOpenDetails = viewModel::openDetails,
                    onRemoveFromContinueWatching = viewModel::removeFromContinueWatching,
                    continueWatchingActionsEnabled = !streamsState.isOpen && !streamsState.isResolving && !isPlayerOpen,
                    onCloseDetails = viewModel::closeDetails,
                    onToggleLibrary = viewModel::toggleLibrary,
                    onOpenStreams = viewModel::openStreams,
                    onStartServer = viewModel::startServer,
                    onStopServer = viewModel::stopServer,
                    onCheckServer = viewModel::checkServerWorking,
                    onLogout = viewModel::logout,
                    onCloseDiscoverCatalog = viewModel::closeDiscoverCatalog,
                    onOpenDiscoverCatalog = viewModel::openDiscoverCatalog,
                    onShelfVisible = viewModel::onShelfVisible,
                    onSelectLibraryFilter = viewModel::selectLibraryFilter,
                    onSelectDiscoverFilter = viewModel::selectDiscoverFilter,
                    onUpdateProfileSettings = viewModel::updateProfileSettings,
                    introSkipSettings = viewModel.introSkipSettings.collectAsStateWithLifecycle().value,
                    onUpdateIntroSkipSettings = viewModel::updateIntroSkipSettings,
                    onUpdateServerSettings = viewModel::updateStreamingServerSettings,
                    onAuthenticateTrakt = viewModel::authenticateTrakt,
                    onLogoutTrakt = viewModel::logoutTrakt,
                    onInstallTraktAddon = viewModel::installTraktAddon,
                    onSetAutoStartOnBoot = viewModel::setAutoStartOnBoot,
                    onSetServerInForeground = viewModel::setServerInForeground,
                    onSetMobileDataWarning = viewModel::setMobileDataWarning,
                    onSetKeepScreenOn = viewModel::setKeepScreenOn,
                    onSetSeedingEnabled = viewModel::setSeedingEnabled,
                    onSelectAddonsFilter = viewModel::selectAddonsFilter,
                    onOpenAddonDetails = viewModel::openAddonDetails,
                    onInstallAddon = viewModel::installAddon,
                    onUninstallAddon = viewModel::uninstallAddon,
                    onInstallAddonByUrl = viewModel::installAddonByUrl,
                    onSetMinSeedsThreshold = viewModel::setMinSeedsThreshold,
                    onSetMinDownloadSpeedBps = viewModel::setMinDownloadSpeedBps,
                    onSetPreferredQuality = viewModel::setPreferredQuality,
                    onSetGlobalUiStyle = viewModel::setGlobalUiStyle,
                    onSetPlayerUiStyle = viewModel::setPlayerUiStyle,
                    onSetGlassEffectsMode = viewModel::setGlassEffectsMode,
                    onSetAutoSwitchOnDeadStream = viewModel::setAutoSwitchOnDeadStream,
                    isSourceFailoverEnabled = viewModel.sourceFailoverConfig.collectAsStateWithLifecycle().value.enabled,
                    onSetSourceFailoverEnabled = viewModel::setSourceFailoverEnabled,
                    onSetGlobalGlassAlpha = viewModel::setGlobalGlassAlpha,
                    onSetAdaptiveGlassContrastEnabled = viewModel::setAdaptiveGlassContrastEnabled,
                    onSetGlassHapticsEnabled = viewModel::setGlassHapticsEnabled,
                    onSetHapticsIntensity = viewModel::setHapticsIntensity,
                    onSetLiquidGlassTuning = viewModel::setLiquidGlassTuning,
                    onResetLiquidGlassTuning = viewModel::resetLiquidGlassTuning,
                    onSetSelectedFont = viewModel::setSelectedFont,
                    onSetAnalyticsEnabled = viewModel::setAnalyticsEnabled,
                    onShowAnalyticsDisclosure = viewModel::showAnalyticsDisclosure,
                    onSetAutoUpdateEnabled = viewModel::setAutoUpdateEnabled,
                    onCheckForUpdates = { viewModel.checkForUpdates(manual = true) },
                    onDownloadAndInstallUpdate = viewModel::downloadAndInstallUpdate,
                    onInstallDownloadedUpdate = viewModel::installDownloadedUpdate,
                    onIgnoreUpdate = viewModel::ignoreUpdate,
                )
            } else {
                AuthFlow(
                    isLoading = state.account.isLoading,
                    error = state.account.error,
                    onLogin = viewModel::login,
                    onFacebookLogin = viewModel::loginWithFacebook,
                    onFacebookLoginError = viewModel::setAccountError,
                    onSignup = viewModel::signup,
                    onClearError = viewModel::clearAccountError,
                )
            }

            if (state.account.isAuthenticated && state.isSearchOpen) {
                val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
                SearchResultsScreen(
                    query = state.searchQuery,
                    results = state.searchResults,
                    shelves = state.searchShelves,
                    history = searchHistory,
                    onQueryChange = viewModel::search,
                    onSubmitSearch = { viewModel.rememberSearch() },
                    onRemoveHistory = viewModel::removeSearchFromHistory,
                    onOpenDetails = { item -> viewModel.rememberSearch(); viewModel.openDetails(item) },
                    onOpenDiscoverCatalog = { request, title -> viewModel.rememberSearch(); viewModel.clearSearch(); viewModel.openDiscoverCatalog(request, title) },
                    onBack = viewModel::clearSearch,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.selectedDetails?.let { details ->
                BackHandler(onBack = viewModel::closeDetails)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { viewModel.closeDetails() }
                ) {
                    DetailSheet(
                        details = details,
                        inLibrary = state.library.items.any { it.id == details.item.id && it.type == details.item.type },
                        onBack = viewModel::closeDetails,
                        onToggleLibrary = { viewModel.toggleLibrary(details.item) },
                        onOpenStreams = { viewModel.openStreams(details.item) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {} // Block click propagation
                    )
                }
            }

            state.selectedAddonDetails?.let { details ->
                BackHandler(onBack = viewModel::closeAddonDetails)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { viewModel.closeAddonDetails() }
                ) {
                    AddonDetailsSheet(
                        details = details,
                        onBack = viewModel::closeAddonDetails,
                        onInstall = viewModel::installAddon,
                        onUninstall = viewModel::uninstallAddon,
                        onUpgrade = viewModel::upgradeAddon,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {} // Block click propagation
                    )
                }
            }

            if (streamsState.isOpen) {
                BackHandler {
                    if (streamsState.isSeries && streamsState.selectedVideoId != null) {
                        viewModel.backToEpisodes()
                    } else {
                        viewModel.closeStreams()
                    }
                }
                StreamsSheet(
                    state = streamsState,
                    preferredQuality = state.preferredQuality,
                    onBack = {
                        if (streamsState.isSeries && streamsState.selectedVideoId != null) {
                            viewModel.backToEpisodes()
                        } else {
                            viewModel.closeStreams()
                        }
                    },
                    onSelect = viewModel::playStream,
                    onSelectEpisode = viewModel::selectEpisode,
                    onSelectSeason = viewModel::selectSeason,
                    onSelectProvider = viewModel::selectStreamProvider,
                    onSelectSortCriterion = viewModel::selectStreamSortCriterion,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Visual layer over the existing resolving state. It sits below the player, which
            // continues it until the first frame plays (same window, so there is no flash).
            PlaybackLoadingOverlay(
                info = remember(streamsState.forItem, streamsState.selectedVideoId, streamsState.selectedEpisodeLabel, streamsState.episodes) {
                    streamsState.toPlaybackLoadingInfo()
                },
                visible = streamsState.isResolving && !isPlayerOpen,
                modifier = Modifier.fillMaxSize(),
            )

            if (state.showMobileDataWarning) {
                var disableWarning by remember { mutableStateOf(false) }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = viewModel::cancelMobileDataPlayback,
                    title = {
                        Text(
                            text = "Aviso de dados móveis",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Você está usando dados móveis. Assistir vídeos pode consumir muitos dados. Deseja continuar?",
                                color = MutedText,
                                fontSize = 14.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { disableWarning = !disableWarning },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = disableWarning,
                                    onCheckedChange = { disableWarning = it },
                                    colors = androidx.compose.material3.CheckboxDefaults.colors(
                                        checkedColor = AccentPurple,
                                        uncheckedColor = MutedText,
                                        checkmarkColor = Color.White,
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Não mostrar este aviso novamente",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (disableWarning) {
                                    viewModel.setMobileDataWarning(false)
                                }
                                viewModel.confirmMobileDataPlayback()
                            }
                        ) {
                            Text(text = "Assistir mesmo assim", color = AccentPurple, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = viewModel::cancelMobileDataPlayback
                        ) {
                            Text(text = "Cancelar", color = MutedText)
                        }
                    },
                    containerColor = Color(0xFF2A2935),
                    shape = RoundedCornerShape(20.dp),
                )
            }

            if (!introVisible && state.server is StreamingServerState.Failed) {
                val context = LocalContext.current
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        (context as? Activity)?.finish()
                    },
                    title = {
                        Text(
                            text = "Erro no servidor de streaming",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = formatServerErrorMessage((state.server as StreamingServerState.Failed).message),
                            color = MutedText,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = viewModel::startServer
                        ) {
                            Text(text = "Tentar novamente", color = AccentPurple, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                (context as? Activity)?.finish()
                            }
                        ) {
                            Text(text = "Sair", color = MutedText)
                        }
                    },
                    containerColor = Color(0xFF2A2935),
                    shape = RoundedCornerShape(20.dp),
                )
            }

            when (val update = state.updateState.takeUnless { introVisible }) {
                is UpdateState.Available -> {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = viewModel::dismissUpdateDialog,
                        title = {
                            Text(
                                text = "Nova atualização disponível",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Version ${update.info.versionName} is ready to install.",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = update.info.releaseNotes.ifBlank { "Uma nova versão do MW Play está disponível." },
                                    color = MutedText,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.downloadAndInstallUpdate(update.info) }
                            ) {
                                Text(text = "Instalar", color = AccentPurple, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = viewModel::dismissUpdateDialog
                            ) {
                                Text(text = "Agora não", color = MutedText)
                            }
                        },
                        containerColor = Color(0xFF2A2935),
                        shape = RoundedCornerShape(20.dp),
                    )
                }
                is UpdateState.Downloading -> {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = {},
                        title = {
                            Text(
                                text = "Baixando atualização…",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFF1E1D26)),
                                ) {
                                    val progress = update.progress?.coerceIn(0f, 1f) ?: 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(AccentPurple),
                                    )
                                }
                                Text(
                                    text = if (update.progress != null) {
                                        "${(update.progress * 100f).roundToInt()}%"
                                    } else {
                                        "${update.bytesRead / (1024L * 1024L)} MB downloaded"
                                    },
                                    color = MutedText,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        confirmButton = {},
                        containerColor = Color(0xFF2A2935),
                        shape = RoundedCornerShape(20.dp),
                    )
                }
                is UpdateState.ReadyToInstall -> {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = viewModel::dismissUpdateDialog,
                        title = {
                            Text(
                                text = "Pronto para instalar",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = if (update.needsUnknownSourcesPermission) {
                                    "Permita instalações desta fonte, volte e toque em Instalar."
                                } else {
                                    "A atualização foi baixada. Toque em Instalar para abrir o instalador do sistema."
                                },
                                color = MutedText,
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.installDownloadedUpdate(update.file) }
                            ) {
                                Text(text = "Instalar", color = AccentPurple, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = viewModel::dismissUpdateDialog
                            ) {
                                Text(text = "Depois", color = MutedText)
                            }
                        },
                        containerColor = Color(0xFF2A2935),
                        shape = RoundedCornerShape(20.dp),
                    )
                }
                else -> Unit
            }

            if (!isPlayerOpen) {
                val application = LocalContext.current.applicationContext as MainApplication
                CastMiniController(application.castController, Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 90.dp))
            }

            if (isPlayerOpen) {
                val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
                PlayerScreen(
                    player = viewModel.getPlayer(),
                    activeUri = playbackState.activeUri,
                    activeSelectionId = playbackState.activeSelectionId,
                    castUri = playbackState.castUri,
                    castRequiresHeaders = playbackState.castRequiresHeaders,
                    title = playbackState.title ?: "MW Play",
                    onAttachView = viewModel::attachPlayerView,
                    onDetachView = viewModel::detachPlayerView,
                    onBack = viewModel::closePlayer,
                    getSubtitlePrefs = viewModel::getSubtitlePrefs,
                    onSubtitlePrefsChanged = viewModel::updateSubtitlePrefs,
                    profileSettings = state.profileSettings,
                    getPlayerStreamState = viewModel::getPlayerStreamState,
                    onAudioTrackSelected = viewModel::rememberAudioTrack,
                    onSubtitleTrackSelected = viewModel::rememberSubtitleTrack,
                    onSubtitlesDisabled = viewModel::rememberSubtitlesDisabled,
                    onSubtitleStyleChanged = viewModel::rememberSubtitleStyle,
                    onLocalSubtitlePicked = viewModel::importLocalSubtitle,
                    nextVideo = state.nextVideo,
                    showNextVideoPopup = state.showNextVideoPopup,
                    onDismissNextVideoPopup = viewModel::dismissNextVideoPopup,
                    onPlayNext = { state.nextVideo?.let(viewModel::playNextVideo) },
                    onTick = viewModel::onPlayerTick,
                    onSeekReported = viewModel::onPlayerSeek,
                    onPausedChanged = viewModel::onPlayerPausedChanged,
                    onEnded = viewModel::onPlaybackEnded,
                    showNoSeedsBanner = state.showNoSeedsBanner,
                    noSeedsReason = state.noSeedsReason,
                    onPlayNextBestStream = { viewModel.playNextBestStream() },
                    globalUiStyle = if (state.playerUiStyle == "global") state.globalUiStyle else state.playerUiStyle,
                    glassEffectsMode = state.glassEffectsMode,
                    globalGlassAlpha = state.globalGlassAlpha,
                    adaptiveGlassContrast = state.adaptiveGlassContrast,
                    glassHapticsEnabled = state.glassHapticsEnabled,
                    hapticsIntensity = state.hapticsIntensity,
                    liquidGlassTuning = state.liquidGlassTuning,
                    loadingInfo = remember(streamsState.forItem, streamsState.selectedVideoId, streamsState.selectedEpisodeLabel, streamsState.episodes) {
                        streamsState.toPlaybackLoadingInfo()
                    },
                    playbackIntro = viewModel.playbackIntro.collectAsStateWithLifecycle().value,
                    introSkipSettings = viewModel.introSkipSettings.collectAsStateWithLifecycle().value,
                    sourceFailover = viewModel.sourceFailover.collectAsStateWithLifecycle().value,
                    onRetrySources = viewModel::retrySourceFailover,
                    onChooseAnotherSource = viewModel::chooseAnotherSource,
                    onCastingChanged = viewModel::onCastingChanged,
                )
            }

            if (!introVisible && state.showAnalyticsDisclosure) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { /* Block clicks outside */ },
                    title = {
                        Text(
                            text = "Privacidade e análise de uso",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Para melhorar a estabilidade e corrigir erros, este cliente de código aberto coleta relatórios de falha anônimos (via Firebase Crashlytics) e métricas de uso (via PostHog).",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = "Informações pessoais identificáveis são removidas automaticamente no próprio aparelho, e as gravações de sessão ocultam todos os campos de texto e imagens.",
                                    color = MutedText,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    text = "Aviso sobre análises do Stremio:\nOs serviços oficiais do Stremio, os addons e o servidor de streaming funcionam de forma independente e podem coletar telemetria própria, separada deste app.",
                                    color = MutedText,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Você pode mudar suas preferências de consentimento a qualquer momento nos ajustes do Android.",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                    },
                    confirmButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemedButton(
                                text = "Recusar",
                                onClick = { viewModel.acknowledgeAnalyticsDisclosure(false) },
                                containerColor = Color(0xFFD32F2F),
                                modifier = Modifier.weight(1f)
                            )
                            ThemedButton(
                                text = "Aceitar",
                                onClick = { viewModel.acknowledgeAnalyticsDisclosure(true) },
                                containerColor = AccentPurple,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    },
                    containerColor = Color(0xFF171A20),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Opening animation on top of everything; the app keeps starting underneath.
            if (introVisible) {
                OrbitalIntro(
                    ready = !sessionRestoring,
                    onFinished = { introVisible = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
}

@Composable
private fun BoardScreen(
    state: com.stremio.mobile.presentation.state.MainUiState,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onSelectSection: (MainSection) -> Unit,
    onOpenDetails: (CatalogItem) -> Unit,
    onRemoveFromContinueWatching: (CatalogItem) -> Unit,
    continueWatchingActionsEnabled: Boolean,
    onCloseDetails: () -> Unit,
    onToggleLibrary: (CatalogItem) -> Unit,
    onOpenStreams: (CatalogItem) -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onCheckServer: () -> Unit,
    onLogout: () -> Unit,
    onCloseDiscoverCatalog: () -> Unit,
    onOpenDiscoverCatalog: (com.stremio.core.types.addon.ResourceRequest, String) -> Unit,
    onShelfVisible: (Int) -> Unit,
    onSelectLibraryFilter: (com.stremio.core.models.LibraryWithFilters.LibraryRequest) -> Unit,
    onSelectDiscoverFilter: (com.stremio.core.types.addon.ResourceRequest) -> Unit,
    onUpdateProfileSettings: (com.stremio.core.types.profile.Profile.Settings) -> Unit,
    introSkipSettings: com.stremio.mobile.player.intro.IntroSkipSettings,
    onUpdateIntroSkipSettings: (com.stremio.mobile.player.intro.IntroSkipSettings) -> Unit,
    onUpdateServerSettings: (com.stremio.core.models.StreamingServer.Settings) -> Unit,
    onAuthenticateTrakt: (Context) -> Unit,
    onLogoutTrakt: () -> Unit,
    onInstallTraktAddon: () -> Unit,
    onSetAutoStartOnBoot: (Boolean) -> Unit,
    onSetServerInForeground: (Boolean) -> Unit,
    onSetMobileDataWarning: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetSeedingEnabled: (Boolean) -> Unit,
    onSelectAddonsFilter: (com.stremio.core.types.addon.ResourceRequest) -> Unit,
    onOpenAddonDetails: (String) -> Unit,
    onInstallAddon: (com.stremio.mobile.data.model.AddonItem) -> Unit,
    onUninstallAddon: (com.stremio.mobile.data.model.AddonItem) -> Unit,
    onInstallAddonByUrl: (String) -> Boolean,
    onSetMinSeedsThreshold: (Int) -> Unit,
    onSetMinDownloadSpeedBps: (Long) -> Unit,
    onSetPreferredQuality: (String) -> Unit,
    onSetGlobalUiStyle: (String) -> Unit,
    onSetPlayerUiStyle: (String) -> Unit,
    onSetGlassEffectsMode: (String) -> Unit,
    onSetAutoSwitchOnDeadStream: (Boolean) -> Unit,
    isSourceFailoverEnabled: Boolean,
    onSetSourceFailoverEnabled: (Boolean) -> Unit,
    onSetGlobalGlassAlpha: (Float) -> Unit,
    onSetAdaptiveGlassContrastEnabled: (Boolean) -> Unit,
    onSetGlassHapticsEnabled: (Boolean) -> Unit,
    onSetHapticsIntensity: (String) -> Unit,
    onSetLiquidGlassTuning: (LiquidGlassTuning) -> Unit,
    onResetLiquidGlassTuning: () -> Unit,
    onSetSelectedFont: (AppFont) -> Unit,
    onSetAnalyticsEnabled: (Boolean) -> Unit,
    onShowAnalyticsDisclosure: () -> Unit,
    onSetAutoUpdateEnabled: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadAndInstallUpdate: (UpdateInfo) -> Unit,
    onInstallDownloadedUpdate: (File) -> Unit,
    onIgnoreUpdate: (String) -> Unit,
) {
    var settingsSubScreen by rememberSaveable { mutableStateOf(SettingsSubScreen.Main) }
    // Esportes on a TV source without a sports category (channels filtered by metadata).
    var tvSportsByMetadata by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.selectedSection) {
        if (state.selectedSection != MainSection.Settings) {
            settingsSubScreen = SettingsSubScreen.Main
        }
    }

    BackHandler(
        enabled = (state.selectedSection == MainSection.Settings && settingsSubScreen != SettingsSubScreen.Main) ||
                (state.selectedSection == MainSection.Discover && state.discoverCatalogRequest != null) ||
                state.selectedSection == MainSection.Tv
    ) {
        if (state.selectedSection == MainSection.Settings && settingsSubScreen != SettingsSubScreen.Main) {
            settingsSubScreen = SettingsSubScreen.Main
        } else if (state.selectedSection == MainSection.Tv ||
            (state.selectedSection == MainSection.Discover && state.discoverCatalogRequest != null)
        ) {
            // Back from Discover or TV returns to Início, as before.
            onCloseDiscoverCatalog()
        }
    }

    val globalTheme = remember(
        state.globalUiStyle,
        state.glassEffectsMode,
        state.globalGlassAlpha,
        state.glassHapticsEnabled,
        state.hapticsIntensity,
        state.liquidGlassTuning,
        state.adaptiveGlassContrast,
    ) {
        GlobalUiTheme(
            style = state.globalUiStyle,
            glassEffectsMode = state.glassEffectsMode,
            glassAlpha = state.globalGlassAlpha,
            hapticsEnabled = state.glassHapticsEnabled,
            hapticsIntensity = state.hapticsIntensity,
            liquidGlassTuning = state.liquidGlassTuning,
            adaptiveGlassContrast = state.adaptiveGlassContrast,
        )
    }

    val realGlassEnabled = state.globalUiStyle == "modern" && state.glassEffectsMode != "static"
    val appBackdrop = if (realGlassEnabled) {
        rememberLayerBackdrop {
            drawContent()
        }
    } else {
        null
    }
    val contentBackdrop = if (realGlassEnabled) {
        rememberLayerBackdrop {
            drawContent()
        }
    } else {
        null
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gridColumns = rememberPosterGridColumns()
    val isAddonsSettingsPage = state.selectedSection == MainSection.Settings &&
            settingsSubScreen == SettingsSubScreen.Addons
    CompositionLocalProvider(
        LocalGlobalUiTheme provides globalTheme,
        LocalGlassAlpha provides state.globalGlassAlpha,
        LocalGlobalBackdrop provides appBackdrop
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (appBackdrop != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(appBackdrop)
                        .background(StremioBackgroundBrush)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier)
                    .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = BottomBarSpace + navBottom),
            verticalArrangement = Arrangement.spacedBy(if (isAddonsSettingsPage) 10.dp else 27.dp),
        ) {
            item {
                BoardHeader(
                    onOpenSearch = onOpenSearch,
                )
            }
            when (state.selectedSection) {
                MainSection.Home -> {
                    val featuredItems = state.boardShelves.firstOrNull()?.items?.take(5) ?: emptyList()
                    if (featuredItems.isNotEmpty()) {
                        item(contentType = "hero") {
                            FeaturedHeroPager(
                                items = featuredItems,
                                onClick = { item -> onOpenDetails(item) },
                            )
                        }
                    }
                    if (state.continueWatching.items.isNotEmpty() || state.continueWatching.isLoading) {
                        item(key = "continue-watching", contentType = "shelf") {
                            ContinueWatchingShelf(
                                shelf = state.continueWatching,
                                onContinueWatching = onOpenStreams,
                                onOpenDetails = onOpenDetails,
                                onRemove = onRemoveFromContinueWatching,
                                actionsEnabled = continueWatchingActionsEnabled && !state.isSearchOpen && state.selectedDetails == null &&
                                    state.selectedAddonDetails == null,
                            )
                        }
                    }
                    state.boardShelves.forEachIndexed { index, shelf ->
                        item(key = shelf.seeAllRequest?.toString() ?: shelf.title, contentType = "shelf") {
                            val shelfKey = shelf.seeAllRequest ?: shelf.title
                            LaunchedEffect(shelfKey) {
                                onShelfVisible(index)
                            }
                            PosterShelf(
                                shelf = shelf,
                                mode = if (shelf.type == "series") ShelfMode.Series else ShelfMode.Movie,
                                onItemClick = onOpenDetails,
                                onSeeAllClick = {
                                    shelf.seeAllRequest?.let { req ->
                                        onOpenDiscoverCatalog(req, shelf.title)
                                    }
                                }
                            )
                        }
                    }
                }

                MainSection.Discover, MainSection.Tv -> {
                    val isTvTab = state.selectedSection == MainSection.Tv
                    val sectionTitle = if (isTvTab) "TV" else "Descobrir"
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .padding(horizontal = ScreenGutter)
                                .fillMaxWidth()
                        ) {
                            if (state.isDiscoverSeeAll) {
                                ThemedIconButton(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Voltar",
                                    onClick = { onCloseDiscoverCatalog() },
                                    modifier = Modifier
                                        .size(TouchTarget),
                                    containerColor = SurfaceHigh,
                                )
                            }
                            Text(
                                text = if (state.isDiscoverSeeAll) ContentLabels.catalog(state.discoverCatalogTitle ?: sectionTitle) else sectionTitle,
                                color = Color.White,
                                fontSize = 26.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    item {
                        DiscoverFiltersRow(
                            // The TV tab has no type filter; Discover lists every type except TV.
                            discoverCatalogWithFilters = state.discoverCatalogWithFilters?.takeIf { filters ->
                                val selectedTv = TvCatalogPolicy.isTv(filters.selected?.request?.path?.type)
                                selectedTv == isTvTab
                            },
                            showTypes = !isTvTab,
                            // The TV tab shows the categories as the row below.
                            showExtras = !isTvTab,
                            backdrop = appBackdrop,
                            onSelectRequest = onSelectDiscoverFilter
                        )
                    }
                    val tvFilters = state.discoverCatalogWithFilters?.takeIf { isTvTab && TvCatalogPolicy.isTv(it.selected?.request?.path?.type) }
                    val tvCategories = tvFilters?.let { tvCategoryRow(it, tvSportsByMetadata) }
                    if (tvCategories != null && tvCategories.chips.isNotEmpty()) {
                        item {
                            TvCategoryChipsRow(
                                row = tvCategories,
                                onSelect = { chip ->
                                    tvSportsByMetadata = chip.kind == TvCategories.Kind.SPORTS && tvCategories.sportsFromMetadata
                                    onSelectDiscoverFilter(chip.request)
                                },
                            )
                        }
                    }
                    if (state.discoverCatalog.isLoading) {
                        item { LoadingRow() }
                    } else if (state.discoverCatalog.error != null) {
                        item { EmptyState(state.discoverCatalog.error) }
                    } else {
                        val items = if (tvCategories?.sportsOpen == true) {
                            SportsChannelPolicy.sportsChannels(
                                state.discoverCatalog.items,
                                id = { it.id },
                                genres = { it.genres },
                                listedUnderSports = tvCategories.listedUnderSports,
                            )
                        } else {
                            state.discoverCatalog.items
                        }
                        if (tvCategories?.sportsOpen == true && items.isEmpty()) {
                            item { EmptyState("Nenhum canal de esportes encontrado neste catálogo.") }
                        }
                        val chunks = items.chunked(gridColumns)
                        chunks.forEach { rowItems ->
                            item(contentType = "poster-row") {
                                PosterGridRow(rowItems, gridColumns, onOpenDetails)
                            }
                        }
                    }
                }

                MainSection.Library -> {
                    item { SectionTitle("Biblioteca") }
                    item {
                        LibraryFiltersRow(
                            libraryWithFilters = state.libraryWithFilters,
                            backdrop = appBackdrop,
                            onSelectRequest = onSelectLibraryFilter
                        )
                    }
                    val items = state.library.items
                    if (items.isEmpty()) {
                        item { EmptyState("Adicione filmes e séries pela tela de detalhes para montar sua biblioteca.") }
                    } else {
                        val chunks = items.chunked(gridColumns)
                        chunks.forEach { rowItems ->
                            item(contentType = "poster-row") {
                                PosterGridRow(rowItems, gridColumns, onOpenDetails)
                            }
                        }
                    }
                }

                MainSection.Settings -> {
                    when (settingsSubScreen) {
                        SettingsSubScreen.Main -> {
                            item {
                                SettingsPanel(
                                    email = state.account.email,
                                    onLogout = onLogout,
                                    onNavigateTo = { settingsSubScreen = it },
                                )
                            }
                        }
                        SettingsSubScreen.Addons -> {
                            item {
                                AddonsHeaderAndControls(
                                    state = state.addons,
                                    backdrop = appBackdrop,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main },
                                    onSelectFilter = onSelectAddonsFilter,
                                    onInstallByUrl = onInstallAddonByUrl,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            when {
                                state.addons.isLoading -> item { LoadingRow() }
                                state.addons.error != null -> item { EmptyState(state.addons.error) }
                                state.addons.items.isEmpty() -> item {
                                    EmptyState(
                                        if (state.addons.isBrowsingRemote) "Nenhum addon encontrado neste catálogo." else "Nenhum addon instalado."
                                    )
                                }
                                else -> {
                                    items(
                                        items = state.addons.items,
                                        key = { addon -> addon.transportUrl },
                                        contentType = { "addon-row" },
                                    ) { addon ->
                                        AddonRow(
                                            addon = addon,
                                            onClick = { onOpenAddonDetails(addon.transportUrl) },
                                            onInstall = { onInstallAddon(addon) },
                                            onUninstall = { onUninstallAddon(addon) },
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        )
                                    }
                                }
                            }
                        }
                        SettingsSubScreen.General -> {
                            item {
                                GeneralSettingsScreen(
                                    isAuthenticated = state.account.isAuthenticated,
                                    isTraktAuthenticated = state.isTraktAuthenticated,
                                    onAuthenticateTrakt = onAuthenticateTrakt,
                                    onLogoutTrakt = onLogoutTrakt,
                                    onInstallTraktAddon = onInstallTraktAddon,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                        SettingsSubScreen.Interface -> {
                            item {
                                InterfaceSettingsScreen(
                                    settings = state.profileSettings,
                                    globalUiStyle = state.globalUiStyle,
                                    glassEffectsMode = state.glassEffectsMode,
                                    globalGlassAlpha = state.globalGlassAlpha,
                                    adaptiveGlassContrast = state.adaptiveGlassContrast,
                                    glassHapticsEnabled = state.glassHapticsEnabled,
                                    hapticsIntensity = state.hapticsIntensity,
                                    selectedFont = state.selectedFont,
                                    onUpdateSettings = onUpdateProfileSettings,
                                    onSetGlobalUiStyle = onSetGlobalUiStyle,
                                    onSetGlassEffectsMode = onSetGlassEffectsMode,
                                    onSetGlobalGlassAlpha = onSetGlobalGlassAlpha,
                                    onSetAdaptiveGlassContrastEnabled = onSetAdaptiveGlassContrastEnabled,
                                    onSetGlassHapticsEnabled = onSetGlassHapticsEnabled,
                                    onSetHapticsIntensity = onSetHapticsIntensity,
                                    onSetSelectedFont = onSetSelectedFont,
                                    onNavigateToLiquidGlassLab = { settingsSubScreen = SettingsSubScreen.LiquidGlassLab },
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                        SettingsSubScreen.Player -> {
                            item {
                                PlayerSettingsScreen(
                                    settings = state.profileSettings,
                                    playerUiStyle = state.playerUiStyle,
                                    onSetPlayerUiStyle = onSetPlayerUiStyle,
                                    onUpdateSettings = onUpdateProfileSettings,
                                    introSkipSettings = introSkipSettings,
                                    onUpdateIntroSkipSettings = onUpdateIntroSkipSettings,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                        SettingsSubScreen.Streaming -> {
                            item {
                                StreamingSettingsScreen(
                                    serverState = state.server,
                                    isNativeServerAvailable = state.isNativeServerAvailable,
                                    serverSettings = state.serverSettings,
                                    isSeedingEnabled = state.isSeedingEnabled,
                                    minSeedsThreshold = state.minSeedsThreshold,
                                    minDownloadSpeedBps = state.minDownloadSpeedBps,
                                    preferredQuality = state.preferredQuality,
                                    isAutoSwitchOnDeadStream = state.isAutoSwitchOnDeadStream,
                                    onUpdateServerSettings = onUpdateServerSettings,
                                    onSetSeedingEnabled = onSetSeedingEnabled,
                                    onSetMinSeedsThreshold = onSetMinSeedsThreshold,
                                    onSetMinDownloadSpeedBps = onSetMinDownloadSpeedBps,
                                    onSetPreferredQuality = onSetPreferredQuality,
                                    onSetAutoSwitchOnDeadStream = onSetAutoSwitchOnDeadStream,
                                    isSourceFailoverEnabled = isSourceFailoverEnabled,
                                    onSetSourceFailoverEnabled = onSetSourceFailoverEnabled,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                        SettingsSubScreen.Android -> {
                            item {
                                AndroidSettingsScreen(
                                    isAutoStartOnBoot = state.isAutoStartOnBoot,
                                    onSetAutoStartOnBoot = onSetAutoStartOnBoot,
                                    isServerInForeground = state.isServerInForeground,
                                    onSetServerInForeground = onSetServerInForeground,
                                    isMobileDataWarning = state.isMobileDataWarning,
                                    onSetMobileDataWarning = onSetMobileDataWarning,
                                    isKeepScreenOn = state.isKeepScreenOn,
                                    onSetKeepScreenOn = onSetKeepScreenOn,
                                    isAnalyticsEnabled = state.isAnalyticsEnabled,
                                    onSetAnalyticsEnabled = onSetAnalyticsEnabled,
                                    onShowAnalyticsDisclosure = onShowAnalyticsDisclosure,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                        SettingsSubScreen.LiquidGlassLab -> {
                            item {
                                LiquidGlassDevScreen(
                                    globalUiStyle = state.globalUiStyle,
                                    glassEffectsMode = state.glassEffectsMode,
                                    globalGlassAlpha = state.globalGlassAlpha,
                                    tuning = state.liquidGlassTuning,
                                    onSetGlobalUiStyle = onSetGlobalUiStyle,
                                    onSetGlassEffectsMode = onSetGlassEffectsMode,
                                    onSetGlobalGlassAlpha = onSetGlobalGlassAlpha,
                                    onSetLiquidGlassTuning = onSetLiquidGlassTuning,
                                    onResetLiquidGlassTuning = onResetLiquidGlassTuning,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Interface }
                                )
                            }
                        }
                        SettingsSubScreen.Info -> {
                            item {
                                InfoSettingsScreen(
                                    serverVersion = state.serverVersion,
                                    serverConfigPath = state.serverConfigPath,
                                    serverCachePath = state.serverCachePath,
                                    updateState = state.updateState,
                                    isAutoUpdateEnabled = state.isAutoUpdateEnabled,
                                    onSetAutoUpdateEnabled = onSetAutoUpdateEnabled,
                                    onCheckForUpdates = onCheckForUpdates,
                                    onDownloadAndInstallUpdate = onDownloadAndInstallUpdate,
                                    onInstallDownloadedUpdate = onInstallDownloadedUpdate,
                                    onIgnoreUpdate = onIgnoreUpdate,
                                    onBack = { settingsSubScreen = SettingsSubScreen.Main }
                                )
                            }
                        }
                    }
                }
            }
        }

        StremioBottomBar(
            selectedView = state.selectedSection.toAppView(),
            backdrop = contentBackdrop ?: appBackdrop,
            onSelect = { onSelectSection(it.toSection()) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) AccentPurple else SurfaceHigh
    val textColor = if (selected) Color.White else MutedText
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .border(1.dp, HairlineBorder, RoundedCornerShape(99.dp))
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun typeLabel(type: String?): String = ContentLabels.type(type)

@Composable
fun DropdownFilter(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val theme = LocalGlobalUiTheme.current
    val useRealGlass = theme.style == "modern" && theme.glassEffectsMode == "full" && backdrop != null
    Box(modifier = modifier) {
        val backgroundColor = SurfaceHigh
        val textColor = Color.White
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(backgroundColor)
                .clickable { expanded = true }
                .border(1.dp, HairlineBorder, RoundedCornerShape(99.dp))
            .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        val menuModifier = if (useRealGlass) {
            Modifier
                .width(220.dp)
                .drawBackdropSafe(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(12.dp) },
                    effects = {
                        vibrancy()
                        blur(16.dp.toPx())
                        lens(
                            refractionHeight = 8.dp.toPx(),
                            refractionAmount = 16.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Ambient.copy(alpha = 0.5f) },
                    shadow = {
                        Shadow(
                            radius = 16.dp,
                            offset = DpOffset(0.dp, 4.dp),
                            color = Color.Black.copy(alpha = 0.3f)
                        )
                    },
                    onDrawSurface = {
                        drawRoundRect(Color(0x85131220))
                    }
                )
        } else {
            Modifier
                .width(220.dp)
                .background(if (theme.style == "modern") Color(0xEE141422) else SurfaceHigh)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = menuModifier
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (isSelected) Color.White else MutedText,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        onSelectIndex(index)
                        expanded = false
                    },
                    modifier = Modifier.background(if (isSelected) AccentPurple.copy(alpha = 0.15f) else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun LibraryFiltersRow(
    libraryWithFilters: com.stremio.core.models.LibraryWithFilters?,
    backdrop: LayerBackdrop?,
    onSelectRequest: (com.stremio.core.models.LibraryWithFilters.LibraryRequest) -> Unit
) {
    if (libraryWithFilters == null) return

    val selectable = libraryWithFilters.selectable
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenGutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Types dropdown
        if (selectable.types.isNotEmpty()) {
            item {
                val options = selectable.types.map { typeLabel(it.type) }
                val selectedIndex = selectable.types.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "Todos os tipos"
                DropdownFilter(
                    label = selectedLabel,
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { index -> onSelectRequest(selectable.types[index].request) },
                    backdrop = backdrop
                )
            }
        }

        // Sorts dropdown
        if (selectable.sorts.isNotEmpty()) {
            item {
                val options = selectable.sorts.map { selectableSort ->
                    when (selectableSort.sort) {
                        com.stremio.core.models.LibraryWithFilters.Sort.LAST_WATCHED -> "Últimos assistidos"
                        com.stremio.core.models.LibraryWithFilters.Sort.NAME -> "Nome (A–Z)"
                        com.stremio.core.models.LibraryWithFilters.Sort.NAME_REVERSE -> "Nome (Z–A)"
                        com.stremio.core.models.LibraryWithFilters.Sort.TIMES_WATCHED -> "Mais assistidos"
                        com.stremio.core.models.LibraryWithFilters.Sort.WATCHED -> "Assistidos"
                        com.stremio.core.models.LibraryWithFilters.Sort.NOT_WATCHED -> "Não assistidos"
                        else -> selectableSort.sort.name ?: "Outro"
                    }
                }
                val selectedIndex = selectable.sorts.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "Ordenar"
                DropdownFilter(
                    label = selectedLabel,
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { index -> onSelectRequest(selectable.sorts[index].request) },
                    backdrop = backdrop
                )
            }
        }
    }
}

/** The TV tab's category row, from the selected TV catalog's genre filter. */
private fun tvCategoryRow(
    filters: com.stremio.core.models.CatalogWithFilters,
    sportsByMetadata: Boolean,
): TvCategories.Row<com.stremio.core.types.addon.ResourceRequest> {
    val genre = filters.selectable.extra.firstOrNull { it.name.equals("genre", ignoreCase = true) }
    val options = genre?.options.orEmpty().map { TvCategories.Option(it.value, it.selected, it.request) }
    return TvCategories.build(
        genres = options,
        sportsByMetadata = sportsByMetadata,
        label = ContentLabels::genre,
        allRequest = filters.selectable.catalogs.firstOrNull { it.selected }?.request,
        catalogs = filters.selectable.catalogs.map { TvCategories.Option(it.name, it.selected, it.request) },
    )
}

/** "Todos os canais", "Esportes" and the source's other categories, as tappable pills. */
@Composable
internal fun TvCategoryChipsRow(
    row: TvCategories.Row<com.stremio.core.types.addon.ResourceRequest>,
    onSelect: (TvCategories.Chip<com.stremio.core.types.addon.ResourceRequest>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        listOf(row.chips, if (row.sportsOpen) row.sportsSubcategories else emptyList())
            .filter { it.isNotEmpty() }
            .forEach { chips ->
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ScreenGutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(chips, key = { it.label }) { chip ->
                        val shape = RoundedCornerShape(99.dp)
                        Text(
                            text = chip.label,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .heightIn(min = TouchTarget)
                                .clip(shape)
                                .background(if (chip.selected) AccentPurple else SurfaceHigh)
                                .border(1.dp, if (chip.selected) AccentPurple else HairlineBorder, shape)
                                .selectable(selected = chip.selected, role = Role.Tab, onClick = { onSelect(chip) })
                                .wrapContentHeight(Alignment.CenterVertically)
                                .padding(horizontal = 18.dp),
                        )
                    }
                }
            }
    }
}

@Composable
private fun DiscoverFiltersRow(
    discoverCatalogWithFilters: com.stremio.core.models.CatalogWithFilters?,
    backdrop: LayerBackdrop?,
    onSelectRequest: (com.stremio.core.types.addon.ResourceRequest) -> Unit,
    showTypes: Boolean = true,
    showExtras: Boolean = true,
) {
    if (discoverCatalogWithFilters == null) return

    val selectable = discoverCatalogWithFilters.selectable
    // TV catalogs have their own tab.
    val types = selectable.types.filterNot { TvCatalogPolicy.isTv(it.type) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenGutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Types dropdown
        if (showTypes && types.isNotEmpty()) {
            item {
                val options = types.map { typeLabel(it.type) }
                val selectedIndex = types.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "Tipo"
                DropdownFilter(
                    label = selectedLabel,
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { index -> onSelectRequest(types[index].request) },
                    backdrop = backdrop
                )
            }
        }

        // Catalogs dropdown
        if (selectable.catalogs.isNotEmpty()) {
            item {
                val options = selectable.catalogs.map { ContentLabels.catalog(it.name) }
                val selectedIndex = selectable.catalogs.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "Catálogo"
                DropdownFilter(
                    label = selectedLabel,
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { index -> onSelectRequest(selectable.catalogs[index].request) },
                    backdrop = backdrop
                )
            }
        }

        // Extra Options dropdowns (usually Genre)
        if (showExtras) selectable.extra.forEach { selectableExtra ->
            if (selectableExtra.options.isNotEmpty()) {
                item {
                    val options = selectableExtra.options.map { ContentLabels.extraOption(selectableExtra.name, it.value) }
                    val selectedIndex = selectableExtra.options.indexOfFirst { it.selected }
                    val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else ContentLabels.extraOption(selectableExtra.name, null)
                    DropdownFilter(
                        label = selectedLabel,
                        options = options,
                        selectedIndex = selectedIndex,
                        onSelectIndex = { index -> onSelectRequest(selectableExtra.options[index].request) },
                        backdrop = backdrop
                    )
                }
            }
        }
    }
}

