package net.harimurti.tv.extension

import android.text.Html
import androidx.media3.common.C
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.util.UUID
import java.util.zip.CRC32

fun String?.isLinkUrl(): Boolean {
    if (this.isNullOrBlank()) return false

    return Regex(
        "^https?://(?:[\\w.-]+\\.[a-zA-Z]{2,63}|\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d+)?(?:/.*)?$"
    ).matches(this)
}

fun String?.isStreamUrl(): Boolean {
    if (this.isNullOrBlank()) return false

    return Regex(
        "^(?:https?|rtmp|rtsp)://(?:[\\w.-]+\\.[a-zA-Z]{2,63}|\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d+)?(?:/.*)?$"
    ).matches(this)
}

fun String?.isPathExist(): Boolean {
    return File(this.orEmpty()).exists()
}

fun String?.toFile(): File {
    return File(this.orEmpty())
}

fun String?.findPattern(pattern: String): String? {
    if (this == null) return null

    val result = Regex(
        pattern,
        RegexOption.IGNORE_CASE
    ).matchEntire(this)

    return result?.groups?.get(1)?.value
}

@Suppress("DEPRECATION")
fun String?.normalize(): String? {
    if (this == null) return null

    val decoded = Html.fromHtml(this).toString()

    return Regex(
        "([~@#\\$%&<>{}();_=])(?:\\1{2,})"
    ).replace(decoded, "").trim()
}

fun String.toRequest(): Request {
    return Request.Builder()
        .url(this)
        .build()
}

fun String.toRequestBuilder(): Request.Builder {
    return Request.Builder()
        .url(this)
}

fun String.decodeUrl(): String {
    return URLDecoder.decode(this, "UTF-8")
}

fun String.decodeHex(): ByteArray {
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

/**
 * Convert:
 *
 * kid:key
 *
 * atau:
 *
 * kid:key|kid2:key2
 *
 * menjadi ClearKey JSON Web Key Set.
 */
fun String.toClearKey(): ByteArray {

    val raw = trim()

    // Sudah berupa JWK JSON.
    if (raw.startsWith("{")) {
        return raw.toByteArray(Charsets.UTF_8)
    }

    fun normalizePart(value: String): String {

        val part = value.trim()

        return if (
            part.matches(Regex("^[0-9a-fA-F]+$")) &&
            part.length % 2 == 0
        ) {
            part.decodeHex().toBase64Url()
        } else {
            part
        }
    }

    val keys = raw
        .split("|", ";", ",")
        .mapNotNull { pair ->

            val separator = pair.indexOf(':')

            if (separator <= 0) {
                return@mapNotNull null
            }

            val kid = normalizePart(
                pair.substring(0, separator)
            )

            val key = normalizePart(
                pair.substring(separator + 1)
            )

            if (kid.isBlank() || key.isBlank()) {
                null
            } else {
                """
                {
                    "kty":"oct",
                    "k":"$key",
                    "kid":"$kid"
                }
                """.trimIndent()
            }
        }

    if (keys.isEmpty()) {
        throw IllegalArgumentException(
            "Invalid ClearKey value"
        )
    }

    return """
        {
            "keys":[${keys.joinToString(",")}],
            "type":"temporary"
        }
    """.trimIndent().toByteArray(Charsets.UTF_8)
}

fun String.toCRC32(): String {

    val bytes = toByteArray()

    return CRC32()
        .apply {
            update(bytes)
        }
        .value
        .toString()
}

fun String.toUUID(): UUID {

    val normalized = lowercase().trim()

    return when {

        normalized.contains("widevine") ||
                normalized == "com.widevine.alpha" -> {
            C.WIDEVINE_UUID
        }

        normalized.contains("clearkey") ||
                normalized == "org.w3.clearkey" -> {
            C.CLEARKEY_UUID
        }

        normalized.contains("playready") ||
                normalized == "com.microsoft.playready" -> {
            C.PLAYREADY_UUID
        }

        else -> {
            C.UUID_NIL
        }
    }
}
