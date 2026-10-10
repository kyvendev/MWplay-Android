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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.stremio.mobile.data.model.LiquidGlassTuning
import com.stremio.mobile.presentation.components.*
import com.stremio.mobile.presentation.navigation.AppView
import com.stremio.mobile.presentation.navigation.toAppView
import com.stremio.mobile.presentation.navigation.toSection
import com.stremio.mobile.presentation.state.MainSection
import com.stremio.mobile.presentation.viewmodel.MainViewModel
import com.stremio.mobile.update.UpdateInfo
import com.stremio.mobile.update.UpdateState
import java.io.File
import kotlin.math.roundToInt

@Composable
fun StremioMobileApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val streamsState by viewModel.streamsState.collectAsStateWithLifecycle()
    val isPlayerOpen by viewModel.isPlayerOpen.collectAsStateWithLifecycle()
    val isTv = rememberIsTelevision()
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
                    continueWatchingActionsEnabled = !state.isSearchOpen && state.selectedDetails == null &&
                        state.selectedAddonDetails == null && !streamsState.isOpen && !isPlayerOpen,
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
                TvModalSurface(onDismissRequest = viewModel::clearSearch) {
                    SearchResultsScreen(
                        query = state.searchQuery,
                        results = state.searchResults,
                        shelves = state.searchShelves,
                        onQueryChange = viewModel::search,
                        onOpenDetails = viewModel::openDetails,
                        onOpenDiscoverCatalog = { request, title ->
                            viewModel.clearSearch()
                            viewModel.openDiscoverCatalog(request, title)
                        },
                        onBack = viewModel::clearSearch,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            state.selectedDetails?.let { details ->
                BackHandler(onBack = viewModel::closeDetails)
                TvModalSurface(onDismissRequest = viewModel::closeDetails) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x99000000))
                            .then(if (isTv) Modifier else Modifier.clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { viewModel.closeDetails() })
                    ) {
                        DetailSheet(
                            details = details,
                            inLibrary = state.library.items.any { it.id == details.item.id && it.type == details.item.type },
                            onBack = viewModel::closeDetails,
                            onToggleLibrary = { viewModel.toggleLibrary(details.item) },
                            onOpenStreams = { viewModel.openStreams(details.item) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .then(if (isTv) Modifier else Modifier.clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {}) // Block touch propagation without a dead TV focus target.
                        )
                    }
                }
            }

            state.selectedAddonDetails?.let { details ->
                BackHandler(onBack = viewModel::closeAddonDetails)
                TvModalSurface(onDismissRequest = viewModel::closeAddonDetails) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x99000000))
                            .then(if (isTv) Modifier else Modifier.clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { viewModel.closeAddonDetails() })
                    ) {
                        AddonDetailsSheet(
                            details = details,
                            onBack = viewModel::closeAddonDetails,
                            onInstall = viewModel::installAddon,
                            onUninstall = viewModel::uninstallAddon,
                            onUpgrade = viewModel::upgradeAddon,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .then(if (isTv) Modifier else Modifier.clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {}) // Block touch propagation without a dead TV focus target.
                        )
                    }
                }
            }

            if (streamsState.isOpen) {
                val onStreamsBack = {
                    if (streamsState.isSeries && streamsState.selectedVideoId != null) {
                        viewModel.backToEpisodes()
                    } else {
                        viewModel.closeStreams()
                    }
                }
                BackHandler(onBack = onStreamsBack)
                TvModalSurface(onDismissRequest = onStreamsBack) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        StreamsSheet(
                            state = streamsState,
                            preferredQuality = state.preferredQuality,
                            onBack = onStreamsBack,
                            onSelect = viewModel::playStream,
                            onSelectEpisode = viewModel::selectEpisode,
                            onSelectSeason = viewModel::selectSeason,
                            onSelectProvider = viewModel::selectStreamProvider,
                            onSelectSortCriterion = viewModel::selectStreamSortCriterion,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Visual layer over the existing resolving state; the player continues it.
                        PlaybackLoadingOverlay(
                            info = remember(streamsState.forItem, streamsState.selectedVideoId, streamsState.selectedEpisodeLabel, streamsState.episodes) {
                                streamsState.toPlaybackLoadingInfo()
                            },
                            visible = streamsState.isResolving,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            if (state.showMobileDataWarning) {
                var disableWarning by remember { mutableStateOf(false) }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = viewModel::cancelMobileDataPlayback,
                    title = {
                        Text(
                            text = "Mobile Data Warning",
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
                                text = "You are currently connected to mobile data. Streaming video may consume a significant amount of data. Do you want to continue?",
                                color = MutedText,
                                fontSize = 14.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusTarget(focusedScale = 1f)
                                    .toggleable(
                                        value = disableWarning,
                                        role = Role.Checkbox,
                                        onValueChange = { disableWarning = it },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = disableWarning,
                                    onCheckedChange = null,
                                    colors = androidx.compose.material3.CheckboxDefaults.colors(
                                        checkedColor = AccentPurple,
                                        uncheckedColor = MutedText,
                                        checkmarkColor = Color.White,
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Don't show this warning again",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            modifier = Modifier.tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1f),
                            onClick = {
                                if (disableWarning) {
                                    viewModel.setMobileDataWarning(false)
                                }
                                viewModel.confirmMobileDataPlayback()
                            }
                        ) {
                            Text(text = "Stream Anyway", color = AccentPurple, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            modifier = Modifier.tvDialogInitialFocus().tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1f),
                            onClick = viewModel::cancelMobileDataPlayback
                        ) {
                            Text(text = "Cancel", color = MutedText)
                        }
                    },
                    containerColor = Color(0xFF2A2935),
                    shape = RoundedCornerShape(20.dp),
                )
            }

            if (state.server is StreamingServerState.Failed) {
                val context = LocalContext.current
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        (context as? Activity)?.finish()
                    },
                    title = {
                        Text(
                            text = "Streaming Server Error",
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
                            modifier = Modifier.tvDialogInitialFocus().tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1f),
                            onClick = viewModel::startServer
                        ) {
                            Text(text = "Retry", color = AccentPurple, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            modifier = Modifier.tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1f),
                            onClick = {
                                (context as? Activity)?.finish()
                            }
                        ) {
                            Text(text = "Exit", color = MutedText)
                        }
                    },
                    containerColor = Color(0xFF2A2935),
                    shape = RoundedCornerShape(20.dp),
                )
            }

            when (val update = state.updateState) {
                is UpdateState.Available -> {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = viewModel::dismissUpdateDialog,
                        title = {
                            Text(
                                text = "New Update Available",
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
                                    text = update.info.releaseNotes.ifBlank { "A new version of Stremio is available." },
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
                                modifier = Modifier.tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1f),
                                onClick = { viewModel.downloadAndInstallUpdate(update.info) }
                            ) {
                                Text(text = "Install", color = AccentPurple, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                modifier = Modifier.tvDialogInitialFocus().tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1f),
                                onClick = viewModel::dismissUpdateDialog
                            ) {
                                Text(text = "Not Now", color = MutedText)
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
                                text = "Downloading Update...",
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
                                text = "Ready to Install",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = if (update.needsUnknownSourcesPermission) {
                                    "Please allow installs from this source, then return and tap Install."
                                } else {
                                    "The update has been downloaded. Press Install to launch the system installer."
                                },
                                color = MutedText,
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                modifier = Modifier.tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1f),
                                onClick = { viewModel.installDownloadedUpdate(update.file) }
                            ) {
                                Text(text = "Install", color = AccentPurple, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                modifier = Modifier.tvDialogInitialFocus().tvFocusTarget(cornerRadius = 14.dp, focusedScale = 1f),
                                onClick = viewModel::dismissUpdateDialog
                            ) {
                                Text(text = "Later", color = MutedText)
                            }
                        },
                        containerColor = Color(0xFF2A2935),
                        shape = RoundedCornerShape(20.dp),
                    )
                }
                else -> Unit
            }

            if (isPlayerOpen) {
                val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
                var playerRemoteBack by remember { mutableStateOf<(() -> Unit)?>(null) }
                TvModalSurface(onDismissRequest = { playerRemoteBack?.invoke() ?: viewModel.closePlayer() }) {
                    PlayerScreen(
                        player = viewModel.getPlayer(),
                        activeUri = playbackState.activeUri,
                        title = playbackState.title ?: "Stream",
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
                        onRemoteBackHandlerChanged = { playerRemoteBack = it },
                    )
                }
            }

            if (state.showAnalyticsDisclosure) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { /* Block clicks outside */ },
                    title = {
                        Text(
                            text = "Privacy & Analytics Disclosure",
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
                                    text = "To help us improve stability and resolve errors, this open-source client collects anonymous crash reports (via Firebase Crashlytics) and usage metrics (via PostHog).",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = "All personally identifiable info (PII) is automatically scrubbed locally on your device, and session recordings mask all text fields and image content.",
                                    color = MutedText,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    text = "Stremio separate analytics notice:\nNote that the official Stremio backend services, addons, or streaming server core run independently and may collect separate telemetry from this app.",
                                    color = MutedText,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "You can change your consent preferences at any time in Android Settings.",
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
                                text = "Opt Out",
                                onClick = { viewModel.acknowledgeAnalyticsDisclosure(false) },
                                containerColor = Color(0xFFD32F2F),
                                modifier = Modifier.weight(1f).tvDialogInitialFocus()
                            )
                            ThemedButton(
                                text = "Accept",
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
        }
    }
}
}

/** A separate TV window keeps D-pad focus inside the visible screen and restores it on exit. */
@Composable
private fun TvModalSurface(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (rememberIsTelevision()) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize().focusGroup()) {
                content()
            }
        }
    } else {
        content()
    }
}

@Composable
private fun Modifier.tvDialogInitialFocus(): Modifier {
    if (!rememberIsTelevision()) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) {
        requester.requestFocus()
    }
    return focusRequester(requester)
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
    val isTv = rememberIsTelevision()
    val contentListState = rememberLazyListState()
    val contentFocusRequester = remember { FocusRequester() }
    val gridColumns = rememberPosterGridColumns()
    var restoreFocusAfterEmptyContinueShelf by remember { mutableStateOf(false) }

    LaunchedEffect(restoreFocusAfterEmptyContinueShelf, state.continueWatching.items.isEmpty(), continueWatchingActionsEnabled) {
        if (restoreFocusAfterEmptyContinueShelf && state.continueWatching.items.isEmpty()) {
            if (!continueWatchingActionsEnabled) return@LaunchedEffect
            restoreFocusAfterEmptyContinueShelf = false
            if (isTv && state.selectedSection == MainSection.Home) {
                contentListState.scrollToItem(0)
                withFrameNanos { }
                withFrameNanos { }
                contentFocusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(state.selectedSection, settingsSubScreen) {
        contentListState.scrollToItem(0)
    }

    LaunchedEffect(state.selectedSection) {
        if (state.selectedSection != MainSection.Settings) {
            settingsSubScreen = SettingsSubScreen.Main
        }
    }

    BackHandler(
        enabled = (state.selectedSection == MainSection.Settings && settingsSubScreen != SettingsSubScreen.Main) ||
                (state.selectedSection == MainSection.Discover && state.discoverCatalogRequest != null)
    ) {
        if (state.selectedSection == MainSection.Settings && settingsSubScreen != SettingsSubScreen.Main) {
            settingsSubScreen = SettingsSubScreen.Main
        } else if (state.selectedSection == MainSection.Discover && state.discoverCatalogRequest != null) {
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
    val isAddonsSettingsPage = state.selectedSection == MainSection.Settings &&
            settingsSubScreen == SettingsSubScreen.Addons
    CompositionLocalProvider(
        LocalGlobalUiTheme provides globalTheme,
        LocalGlassAlpha provides state.globalGlassAlpha,
        LocalGlobalBackdrop provides appBackdrop
    ) {
        TvNavigationScaffold(
            selectedView = state.selectedSection.toAppView(),
            backdrop = contentBackdrop ?: appBackdrop,
            onSelect = { onSelectSection(it.toSection()) },
            contentFocusRequester = contentFocusRequester,
            navigationEnabled = continueWatchingActionsEnabled,
        ) { contentModifier ->
            if (appBackdrop != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(appBackdrop)
                        .background(StremioBackgroundBrush)
                )
            }

            LazyColumn(
                state = contentListState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(contentModifier)
                    .then(if (contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier)
                    .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = if (isTv) 32.dp else BottomBarSpace + navBottom),
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
                                onItemClick = onOpenDetails,
                                onRemoveItem = { item ->
                                    if (state.continueWatching.items.size == 1) restoreFocusAfterEmptyContinueShelf = true
                                    onRemoveFromContinueWatching(item)
                                },
                                onEmptyAfterRemoval = { restoreFocusAfterEmptyContinueShelf = true },
                                onContinueWatching = onOpenStreams,
                                actionsEnabled = continueWatchingActionsEnabled,
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

                MainSection.Discover -> {
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
                                    contentDescription = "Back",
                                    onClick = { onCloseDiscoverCatalog() },
                                    modifier = Modifier
                                        .size(44.dp),
                                    containerColor = SurfaceHigh,
                                )
                            }
                            Text(
                                text = if (state.isDiscoverSeeAll) state.discoverCatalogTitle ?: "Descobrir" else "Descobrir",
                                color = Color.White,
                                fontSize = 28.sp,
                                lineHeight = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    item {
                        DiscoverFiltersRow(
                            discoverCatalogWithFilters = state.discoverCatalogWithFilters,
                            backdrop = appBackdrop,
                            onSelectRequest = onSelectDiscoverFilter
                        )
                    }
                    if (state.discoverCatalog.isLoading) {
                        item { LoadingRow() }
                    } else if (state.discoverCatalog.error != null) {
                        item { EmptyState(state.discoverCatalog.error) }
                    } else {
                        val items = state.discoverCatalog.items
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
                                    modifier = Modifier.padding(horizontal = ScreenGutter),
                                )
                            }
                            when {
                                state.addons.isLoading -> item { LoadingRow() }
                                state.addons.error != null -> item { EmptyState(state.addons.error) }
                                state.addons.items.isEmpty() -> item {
                                    EmptyState(
                                        if (state.addons.isBrowsingRemote) "No addons found in this catalog." else "No addons installed."
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
                                            modifier = Modifier.padding(horizontal = ScreenGutter),
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
    val backgroundColor = if (selected) AccentPurple else GlassSurface
    val textColor = if (selected) Color.White else MutedText
    Box(
        modifier = modifier
            .tvFocusTarget(cornerRadius = 99.dp, focusedScale = 1.04f)
            .clip(RoundedCornerShape(99.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
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

private fun typeLabel(type: String?): String {
    return when (val t = type?.lowercase()) {
        null -> "All Types"
        "movie" -> "Movies"
        "series" -> "Series"
        "anime" -> "Anime"
        "channel" -> "Channels"
        else -> t.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
    }
}

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
    var restoreAnchorFocus by remember { mutableStateOf(false) }
    val isTv = rememberIsTelevision()
    val anchorFocus = remember { FocusRequester() }
    val selectedOptionFocus = remember { FocusRequester() }
    val focusIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val theme = LocalGlobalUiTheme.current
    val useRealGlass = theme.style == "modern" && theme.glassEffectsMode == "full" && backdrop != null
    LaunchedEffect(expanded) {
        if (isTv && !expanded && restoreAnchorFocus) {
            kotlinx.coroutines.yield()
            anchorFocus.requestFocus()
            restoreAnchorFocus = false
        }
    }
    Box(modifier = modifier) {
        val backgroundColor = SurfaceHigh
        val textColor = Color.White
        Row(
            modifier = Modifier
                .focusRequester(anchorFocus)
                .tvFocusTarget(cornerRadius = 99.dp, focusedScale = 1.04f)
                .clip(RoundedCornerShape(99.dp))
                .background(backgroundColor)
                .clickable(enabled = options.isNotEmpty()) {
                    restoreAnchorFocus = true
                    expanded = true
                }
                .border(1.dp, HairlineBorder, RoundedCornerShape(99.dp))
                .padding(start = 18.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 15.sp,
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
                .width(260.dp)
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
                .width(260.dp)
                .background(if (theme.style == "modern") Color(0xEE141422) else SurfaceHigh)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = menuModifier
        ) {
            LaunchedEffect(Unit) {
                if (isTv && options.isNotEmpty()) {
                    selectedOptionFocus.requestFocus()
                }
            }
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (isSelected) Color.White else MutedText,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp
                        )
                    },
                    onClick = {
                        onSelectIndex(index)
                        expanded = false
                    },
                    modifier = Modifier
                        .then(if (index == focusIndex) Modifier.focusRequester(selectedOptionFocus) else Modifier)
                        .tvFocusTarget(cornerRadius = 8.dp, focusedScale = 1f)
                        .background(if (isSelected) AccentPurple.copy(alpha = 0.15f) else Color.Transparent)
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
            .focusRestorer()
    ) {
        // Types dropdown
        if (selectable.types.isNotEmpty()) {
            item {
                val options = selectable.types.map { typeLabel(it.type) }
                val selectedIndex = selectable.types.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "All Types"
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
                        com.stremio.core.models.LibraryWithFilters.Sort.LAST_WATCHED -> "Last Watched"
                        com.stremio.core.models.LibraryWithFilters.Sort.NAME -> "Name (A-Z)"
                        com.stremio.core.models.LibraryWithFilters.Sort.NAME_REVERSE -> "Name (Z-A)"
                        com.stremio.core.models.LibraryWithFilters.Sort.TIMES_WATCHED -> "Times Watched"
                        com.stremio.core.models.LibraryWithFilters.Sort.WATCHED -> "Watched"
                        com.stremio.core.models.LibraryWithFilters.Sort.NOT_WATCHED -> "Not Watched"
                        else -> selectableSort.sort.name ?: "Unknown"
                    }
                }
                val selectedIndex = selectable.sorts.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "Sort"
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

@Composable
private fun DiscoverFiltersRow(
    discoverCatalogWithFilters: com.stremio.core.models.CatalogWithFilters?,
    backdrop: LayerBackdrop?,
    onSelectRequest: (com.stremio.core.types.addon.ResourceRequest) -> Unit
) {
    if (discoverCatalogWithFilters == null) return

    val selectable = discoverCatalogWithFilters.selectable
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenGutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .focusRestorer()
    ) {
        // Types dropdown
        if (selectable.types.isNotEmpty()) {
            item {
                val options = selectable.types.map { typeLabel(it.type) }
                val selectedIndex = selectable.types.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "Type"
                DropdownFilter(
                    label = selectedLabel,
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelectIndex = { index -> onSelectRequest(selectable.types[index].request) },
                    backdrop = backdrop
                )
            }
        }

        // Catalogs dropdown
        if (selectable.catalogs.isNotEmpty()) {
            item {
                val options = selectable.catalogs.map { it.name }
                val selectedIndex = selectable.catalogs.indexOfFirst { it.selected }
                val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "Catalog"
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
        selectable.extra.forEach { selectableExtra ->
            if (selectableExtra.options.isNotEmpty()) {
                item {
                    val options = selectableExtra.options.map { it.value ?: "All ${selectableExtra.name}s" }
                    val selectedIndex = selectableExtra.options.indexOfFirst { it.selected }
                    val selectedLabel = if (selectedIndex >= 0) options[selectedIndex] else "All ${selectableExtra.name}s"
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

