package com.stremio.mobile.cast

import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

data class CastMediaDecision(
    val url: String?,
    val contentType: String?,
    val rejection: String?,
) {
    val supported: Boolean get() = url != null
}

/** Checks whether a receiver can fetch the original URL without a phone-side proxy. */
object CastMediaPolicy {
    fun evaluate(rawUrl: String?, requiresHeaders: Boolean = false): CastMediaDecision {
        val url = rawUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: return reject("Selecione um link de vídeo para transmitir.")
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return reject("O link de vídeo não é válido para transmissão.")
        }
        if (!uri.scheme.equals("http", ignoreCase = true) &&
            !uri.scheme.equals("https", ignoreCase = true)
        ) {
            return reject("O Chromecast precisa de um link HTTP ou HTTPS acessível na rede.")
        }
        val authority = uri.rawAuthority
            ?: return reject("O link de vídeo não contém um servidor válido.")
        if (uri.rawUserInfo != null || '@' in authority) {
            return reject("Links com usuário ou senha não são compatíveis com o Chromecast.")
        }
        val host = uri.host?.removePrefix("[")?.removeSuffix("]")
            ?.lowercase(Locale.ROOT)?.trimEnd('.')
            ?.takeIf { it.isNotEmpty() }
            ?: return reject("O link de vídeo não contém um servidor válido.")
        val portSuffix = if (authority.startsWith("[")) {
            authority.substringAfter(']', "")
        } else {
            authority.substringAfter(':', "")
        }
        val hasExplicitPort = if (authority.startsWith("[")) {
            portSuffix.isNotEmpty()
        } else {
            ':' in authority
        }
        if (hasExplicitPort && uri.port !in 1..65535) {
            return reject("A porta do link de vídeo não é válida para transmissão.")
        }
        if (isPhoneLocalHost(host)) {
            return reject("Este vídeo usa um endereço local do celular e não pode ser aberto pelo Chromecast.")
        }
        if (requiresHeaders) {
            return reject(
                "O receptor padrão do Google Cast não consegue copiar os cabeçalhos ou cookies exigidos por este vídeo.",
            )
        }
        val contentType = when (uri.path?.substringAfterLast('.')?.lowercase(Locale.ROOT)) {
            "m3u8" -> "application/x-mpegURL"
            "mpd" -> "application/dash+xml"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "video/mp4"
        }
        // Keep signed paths, query parameters and fragments exactly as provided.
        return CastMediaDecision(url = url, contentType = contentType, rejection = null)
    }

    fun isRemoteHttpUrl(rawUrl: String?): Boolean = evaluate(rawUrl).supported

    private fun reject(reason: String) = CastMediaDecision(null, null, reason)

    private fun isPhoneLocalHost(host: String): Boolean {
        if (host == "localhost" || host.startsWith("localhost.") || host.endsWith(".localhost") ||
            host == "localdomain" || host.endsWith(".localdomain") ||
            host == "ip6-localhost" || host == "ip6-loopback"
        ) {
            return true
        }
        if (':' in host) {
            // A colon guarantees a literal IPv6 address, so this never performs DNS lookup.
            val address = try {
                InetAddress.getByName(host.substringBefore('%'))
            } catch (_: IllegalArgumentException) {
                return true
            } catch (_: java.net.UnknownHostException) {
                return true
            }
            return address.isLoopbackAddress || address.isAnyLocalAddress
        }
        // Receivers differ in how they interpret legacy numeric IPv4 notation. Check both
        // decimal leading-zero components and inet_aton-style octal/hex/shortened forms.
        return listOf(false, true).any { octal ->
            numericIpv4(host, octal)?.let { it == 0L || it ushr 24 == 127L } == true
        }
    }

    private fun numericIpv4(host: String, leadingZeroIsOctal: Boolean): Long? {
        val parts = host.split('.')
        if (parts.size !in 1..4 || parts.any { it.isEmpty() }) return null
        val values = parts.map { part ->
            val (digits, radix) = when {
                part.startsWith("0x") -> part.substring(2) to 16
                leadingZeroIsOctal && part.length > 1 && part.startsWith('0') -> part to 8
                else -> part to 10
            }
            if (digits.isEmpty() || digits.any { Character.digit(it, radix) < 0 }) return null
            digits.toLongOrNull(radix)?.takeIf { it in 0..0xffffffffL } ?: return null
        }
        if (values.dropLast(1).any { it > 255 }) return null
        val lastBits = 8 * (5 - values.size)
        if (values.last() >= 1L shl lastBits) return null
        val prefix = values.dropLast(1).fold(0L) { address, value -> (address shl 8) or value }
        return (prefix shl lastBits) or values.last()
    }
}
