package com.stremio.mobile.cast

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.stremio.mobile.R
import com.stremio.mobile.player.PlayerTrackOption
import java.util.concurrent.Executors
import kotlin.math.roundToLong

// Module loading uses a background executor; all SDK UI calls and task listeners run on main.
private val castButtonExecutor = Executors.newSingleThreadExecutor()

@Composable
fun CastDialog(
    controller: CastPlaybackController,
    url: String?, localUri: String?, title: String, positionMs: Long, durationMs: Long,
    playing: Boolean, requiresHeaders: Boolean, subtitles: List<PlayerTrackOption>,
    onLoaded: () -> Unit, onDismiss: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    val decision = remember(url, requiresHeaders) { CastMediaPolicy.evaluate(url, requiresHeaders) }
    var externalError by remember { mutableStateOf<String?>(null) }
    var localNetworkAllowed by remember { mutableStateOf(hasLocalNetworkPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        localNetworkAllowed = it
        if (it) controller.retryInitialize()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) localNetworkAllowed = hasLocalNetworkPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transmitir com Chromecast") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Transmite o vídeo para a TV, continuando do ponto atual. Conecte o celular e o Chromecast à mesma rede Wi-Fi.")
                Text("A TV acessa o link diretamente. Cookies, cabeçalhos personalizados e alguns formatos não são compatíveis. Legendas externas: somente WebVTT remoto. Seleções de áudio e ajustes de estilo/atraso do celular não são transferidos. Para espelhar toda a tela, use a opção de transmissão do Android ou do Google Home.", style = MaterialTheme.typography.bodySmall)
                decision.rejection?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.initializing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text("Preparando Chromecast…")
                    }
                }
                if (!localNetworkAllowed) {
                    Text("Permita o acesso à rede local para encontrar e controlar seu Chromecast no Android 17.")
                    TextButton(onClick = { permissionLauncher.launch("android.permission.ACCESS_LOCAL_NETWORK") }) {
                        Text("Permitir encontrar TVs")
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }) { Text("Abrir permissões do aplicativo") }
                }
                if (state.ready && localNetworkAllowed) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CastRouteButton(controller, Modifier.size(48.dp))
                        Text(if (state.connected) "Conectado: ${state.deviceName ?: "TV"}" else "Escolher dispositivo")
                    }
                    if (!state.devicesAvailable && !state.connected) {
                        Text("Nenhum Chromecast encontrado. Verifique o Wi-Fi, ligue o dispositivo e confirme que a rede permite comunicação entre aparelhos.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.connecting) Text("Conectando à TV…")
                    if (state.connected && decision.supported) {
                        Button(
                            enabled = !state.loading,
                            onClick = {
                                controller.load(url, localUri, title, positionMs, durationMs, playing, requiresHeaders, subtitles) {
                                    onLoaded()
                                    onDismiss()
                                }
                            },
                        ) { Text(if (state.loading) "Carregando na TV…" else "Transmitir este vídeo") }
                    }
                    if (state.connected) TextButton(onClick = controller::stopCasting) { Text("Desconectar Chromecast") }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!state.ready && !state.initializing) {
                    TextButton(onClick = controller::retryInitialize) { Text("Tentar Chromecast novamente") }
                }
                if (decision.supported) {
                    TextButton(onClick = {
                        externalError = openExternalPlayer(context, decision.url!!, decision.contentType!!, title, positionMs)
                    }) { Text("Abrir em outro aplicativo") }
                }
                externalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

private fun hasLocalNetworkPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 37 ||
    ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") == PackageManager.PERMISSION_GRANTED

@Composable
private fun CastRouteButton(controller: CastPlaybackController, modifier: Modifier = Modifier) {
    var routeError by remember { mutableStateOf(false) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MediaRouteButton(ContextThemeWrapper(context, R.style.Theme_MwCast)).apply {
                contentDescription = "Escolher ou desconectar Chromecast"
                setAlwaysVisible(true)
                runCatching { CastButtonFactory.setUpMediaRouteButton(context, castButtonExecutor, this) }
                    .onFailure { routeError = true }
                    .getOrNull()?.addOnFailureListener { routeError = true }
            }
        },
    )
    if (routeError) Text("Seletor indisponível. Atualize o Google Play Services.", color = MaterialTheme.colorScheme.error)
}

/** The chooser is an optional hand-off; apps differ in their support for position/title extras. */
private fun openExternalPlayer(context: Context, url: String, type: String, title: String, positionMs: Long): String? {
    if (!CastMediaPolicy.isRemoteHttpUrl(url)) return "Este link não pode ser enviado para outro aplicativo."
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), type)
        .putExtra(Intent.EXTRA_TITLE, title).putExtra("position", positionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
    return try {
        context.startActivity(Intent.createChooser(intent, "Abrir vídeo com"))
        null
    } catch (_: ActivityNotFoundException) {
        "Nenhum aplicativo compatível instalado. Instale um player de vídeo ou um aplicativo de transmissão."
    } catch (_: SecurityException) {
        "O Android não permitiu abrir este link em outro aplicativo."
    }
}

@Composable
fun CastRemotePlayer(controller: CastPlaybackController, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val enabled = state.connected && !state.loading
    var scrubPosition by remember { mutableStateOf<Long?>(null) }
    var volume by remember(state.volume) { mutableFloatStateOf(state.volume) }
    var showSubtitles by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().background(Color(0xFF101216)).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
            Icon(Icons.Outlined.Cast, contentDescription = null)
            Text("Transmitindo em ${state.deviceName ?: "Chromecast"}", Modifier.weight(1f).padding(start = 8.dp))
            CastRouteButton(controller, Modifier.size(48.dp))
        }
        Text(state.title ?: "Vídeo", style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (state.suspended || state.connecting) Text("Reconectando… A TV pode continuar reproduzindo enquanto o Wi-Fi é restabelecido.")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.buffering || state.loading) CircularProgressIndicator(Modifier.size(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(enabled = enabled && state.durationMs > 0, onClick = { controller.seekTo(state.positionMs - 10000) }) {
                Icon(Icons.Outlined.FastRewind, "Voltar 10 segundos")
            }
            IconButton(enabled = enabled, onClick = controller::togglePlayback) {
                Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.playing) "Pausar na TV" else "Reproduzir na TV")
            }
            IconButton(enabled = enabled && state.durationMs > 0, onClick = { controller.seekTo(state.positionMs + 10000) }) {
                Icon(Icons.Outlined.FastForward, "Avançar 10 segundos")
            }
        }
        val duration = state.durationMs.coerceAtLeast(1)
        val position = scrubPosition ?: state.positionMs
        Slider(value = (position.toDouble() / duration).toFloat().coerceIn(0f, 1f), enabled = enabled && state.durationMs > 0,
            onValueChange = { scrubPosition = (it * duration).roundToLong() },
            onValueChangeFinished = { scrubPosition?.let(controller::seekTo); scrubPosition = null })
        Text("${formatCastTime(position)} / ${formatCastTime(state.durationMs)}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = enabled, onClick = controller::toggleMute) {
                Icon(if (state.muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, if (state.muted) "Ativar som da TV" else "Silenciar TV")
            }
            Slider(value = volume, enabled = enabled, onValueChange = { volume = it },
                onValueChangeFinished = { controller.setVolume(volume) }, modifier = Modifier.weight(1f))
        }
        if (state.subtitles.isNotEmpty()) TextButton(enabled = enabled, onClick = { showSubtitles = true }) { Text("Legendas na TV") }
        Button(onClick = controller::stopCasting) { Text("Voltar a reproduzir no celular") }
    }
    if (showSubtitles) AlertDialog(
        onDismissRequest = { showSubtitles = false }, title = { Text("Legendas na TV") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { controller.selectSubtitle(null); showSubtitles = false }) { Text("Desativar legendas") }
                state.subtitles.forEach { track ->
                    TextButton(onClick = { controller.selectSubtitle(track.id); showSubtitles = false }) {
                        Text(if (track.selected) "✓ ${track.label}" else track.label)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showSubtitles = false }) { Text("Fechar") } },
    )
}

@Composable
fun CastMiniController(controller: CastPlaybackController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    if ((!state.connected && !state.suspended) || state.mediaUrl == null) return
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color(0xFF252332), tonalElevation = 4.dp) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable {
                context.startActivity(Intent(context, CastExpandedControllerActivity::class.java))
            }.padding(10.dp)) {
                Text(state.title ?: "Vídeo na TV", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.deviceName ?: "Chromecast", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(enabled = state.connected && !state.loading, onClick = controller::togglePlayback) {
                Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.playing) "Pausar na TV" else "Reproduzir na TV")
            }
            IconButton(onClick = controller::stopCasting) { Icon(Icons.Outlined.Stop, "Parar transmissão") }
        }
    }
}

private fun formatCastTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
