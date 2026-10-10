package com.stremio.mobile.server

fun formatServerErrorMessage(message: String): String {
    return if (message.contains("failed to bind", ignoreCase = true) || message.contains("11470")) {
        "A porta 11470 já está em uso. Feche outros apps de streaming ou o Stremio em segundo plano e tente novamente."
    } else {
        message
    }
}
