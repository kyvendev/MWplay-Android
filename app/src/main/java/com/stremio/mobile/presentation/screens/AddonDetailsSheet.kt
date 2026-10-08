package com.stremio.mobile.presentation.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.core.theme.SearchBackground
import com.stremio.mobile.data.model.AddonItem
import com.stremio.mobile.presentation.components.GlassPill
import com.stremio.mobile.presentation.components.ThemedButton
import com.stremio.mobile.presentation.components.rememberIsTelevision
import com.stremio.mobile.presentation.components.tvFocusTarget
import com.stremio.mobile.presentation.state.AddonDetailsUiState

@Composable
fun AddonDetailsSheet(
    details: AddonDetailsUiState,
    onBack: () -> Unit,
    onInstall: (AddonItem) -> Unit,
    onUninstall: (AddonItem) -> Unit,
    onUpgrade: (AddonItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val addon = details.addon
    val isTv = rememberIsTelevision()
    val backRequester = remember { FocusRequester() }
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.82f).coerceAtMost(720.dp)

    LaunchedEffect(isTv) {
        if (isTv) runCatching { backRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .focusGroup()
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(Color(0xF2141422))
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .focusRequester(backRequester)
                    .tvFocusTarget(cornerRadius = 999.dp, focusedScale = 1.1f)
                    .clip(CircleShape)
                    .background(SearchBackground)
                    .clickable(onClick = onBack)
                    .padding(8.dp),
            )
            Text(
                text = "Addon Details",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        when {
            addon == null && details.isLoading -> CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(28.dp))
            addon == null -> Text(
                text = details.error ?: "Unable to load this addon.",
                color = Color(0xFFFFC66D),
                fontSize = 13.sp,
            )
            else -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SearchBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (addon.logo != null) {
                            AsyncImage(
                                model = addon.logo,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Extension,
                                contentDescription = null,
                                tint = MutedText,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = addon.name,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            addon.version?.let {
                                Text(text = "v$it", color = MutedText, fontSize = 12.sp)
                            }
                            if (addon.official) GlassPill("Official")
                            if (addon.protected) GlassPill("Protected")
                        }
                    }
                }

                Text(
                    text = addon.description ?: "No description available.",
                    color = Color(0xFFE4E0EE),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )

                if (addon.types.isNotEmpty()) {
                    Text(
                        text = "Supports: " + addon.types.joinToString(", "),
                        color = MutedText,
                        fontSize = 12.sp,
                    )
                }

                if (addon.configurationRequired && !addon.installed) {
                    Text(
                        text = "This addon must be configured before it can be installed.",
                        color = Color(0xFFFFC66D),
                        fontSize = 12.sp,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (addon.configurable) {
                        ThemedButton(
                            text = "Configure",
                            onClick = {
                                val url = addon.transportUrl.replace("manifest.json", "configure")
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }.onFailure {
                                    Toast.makeText(context, "Nenhum navegador disponível para configurar o addon.", Toast.LENGTH_LONG).show()
                                }
                            },
                            containerColor = Color(0x26FFFFFF),
                        )
                    }
                    if (addon.installed) {
                        if (addon.upgradeable) {
                            ThemedButton(
                                text = "Upgrade",
                                onClick = { onUpgrade(addon) },
                                containerColor = AccentPurple,
                            )
                        }
                        ThemedButton(
                            text = if (addon.uninstallable) "Uninstall" else "Protected",
                            onClick = { onUninstall(addon) },
                            enabled = addon.uninstallable,
                            containerColor = Color(0xFFD32F2F),
                        )
                    } else {
                        ThemedButton(
                            text = "Install",
                            onClick = { onInstall(addon) },
                            enabled = addon.installable,
                            modifier = Modifier.weight(1f),
                            containerColor = AccentPurple,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

