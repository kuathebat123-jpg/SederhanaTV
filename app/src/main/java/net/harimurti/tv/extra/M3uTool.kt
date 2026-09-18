package net.harimurti.tv.extra

import androidx.media3.common.MimeTypes

import net.harimurti.tv.extension.findPattern
import net.harimurti.tv.extension.isStreamUrl
import net.harimurti.tv.extension.normalize
import net.harimurti.tv.extension.toCRC32

import net.harimurti.tv.model.Category
import net.harimurti.tv.model.Channel
import net.harimurti.tv.model.ChannelRaw
import net.harimurti.tv.model.DrmLicense
import net.harimurti.tv.model.Playlist

class M3uTool {

    /*
     * ============================================================
     * PARSE HEADER LIST
     * ============================================================
     *
     * Mendukung format:
     *
     * key=value
     * key=value|key2=value2
     * key=value&key2=value2
     */
    private fun parseHeaderList(
        raw: String,
        target: HashMap<String, String>
    ) {

        raw
            .split("|", "&")
            .forEach { item ->

                val separator =
                    item.indexOf('=')

                if (separator <= 0) {
                    return@forEach
                }

                val name =
                    item
                        .substring(
                            0,
                            separator
                        )
                        .trim()

                val value =
                    item
                        .substring(
                            separator + 1
                        )
                        .trim()

                if (
                    name.isNotEmpty() &&
                    value.isNotEmpty()
                ) {

                    target[name] = value
                }
            }
    }


    /*
     * ============================================================
     * EXTVLCOPT VALUE
     * ============================================================
     *
     * Mengambil nilai setelah "=".
     *
     * Contoh:
     *
     * #EXTVLCOPT:http-user-agent=Mozilla/5.0
     *
     * hasil:
     *
     * Mozilla/5.0
     */
    private fun getVlcOptionValue(
        line: String
    ): String? {

        val separator =
            line.indexOf('=')

        if (separator < 0) {
            return null
        }

        return line
            .substring(
                separator + 1
            )
            .trim()
            .takeIf {
                it.isNotEmpty()
            }
    }


    /*
     * ============================================================
     * KODIPROP VALUE
     * ============================================================
     */
    private fun getKodiPropertyValue(
        line: String
    ): String? {

        val separator =
            line.indexOf('=')

        if (separator < 0) {
            return null
        }

        return line
            .substring(
                separator + 1
            )
            .trim()
            .takeIf {
                it.isNotEmpty()
            }
    }


    /*
     * ============================================================
     * MAIN PARSER
     * ============================================================
     */
    fun parse(
        content: String?
    ): Playlist {

        val result =
            Playlist()

        var chRaw =
            ChannelRaw()

        var chReset =
            true

        val lines =
            content
                ?.lines()
                ?: throw Exception(
                    "Empty Content"
                )


        /*
         * ========================================================
         * LOOP M3U
         * ========================================================
         */
        lines.forEach { line ->

            val currentLine =
                line.trim()


            if (currentLine.isBlank()) {
                return@forEach
            }


            /*
             * ====================================================
             * EXTVLCOPT
             * ====================================================
             *
             * Contoh:
             *
             * #EXTVLCOPT:http-referrer=https://visionplus.id
             *
             * #EXTVLCOPT:http-user-agent=Mozilla/5.0
             */
            if (
                currentLine.startsWith(
                    "#EXTVLCOPT",
                    ignoreCase = true
                )
            ) {

                val lowerLine =
                    currentLine.lowercase()


                /*
                 * HTTP USER AGENT
                 */
                if (
                    lowerLine.contains(
                        "http-user-agent="
                    )
                ) {

                    chRaw.userAgent =
                        getVlcOptionValue(
                            currentLine
                        )
                }


                /*
                 * HTTP REFERRER
                 */
                if (
                    lowerLine.contains(
                        "http-referrer="
                    )
                ) {

                    chRaw.referer =
                        getVlcOptionValue(
                            currentLine
                        )
                }


                /*
                 * Beberapa playlist menggunakan
                 * "http-referer" tanpa dua r.
                 */
                if (
                    lowerLine.contains(
                        "http-referer="
                    )
                ) {

                    if (
                        chRaw.referer.isNullOrBlank()
                    ) {

                        chRaw.referer =
                            getVlcOptionValue(
                                currentLine
                            )
                    }
                }


                chReset =
                    false

                return@forEach
            }


            /*
             * ====================================================
             * EXTGRP
             * ====================================================
             */
            if (
                currentLine.startsWith(
                    "#EXTGRP",
                    ignoreCase = true
                )
            ) {

                chRaw.group =
                    currentLine.findPattern(
                        ".*:(.+?)$"
                    )

                chReset =
                    false

                return@forEach
            }


            /*
             * ====================================================
             * KODIPROP
             * ====================================================
             *
             * Contoh:
             *
             * #KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
             *
             * #KODIPROP:inputstream.adaptive.license_key=KID:KEY
             */
            if (
                currentLine.startsWith(
                    "#KODIPROP",
                    ignoreCase = true
                )
            ) {

                val prop =
                    currentLine.substringAfter(
                        ":",
                        ""
                    )

                val lowerProp =
                    prop.lowercase()


                /*
                 * ------------------------------------------------
                 * DRM TYPE
                 * ------------------------------------------------
                 */
                if (
                    lowerProp.contains(
                        "license_type"
                    )
                ) {

                    chRaw.drmType =
                        Regex(
                            "(?i)license_type=(.*)"
                        )
                            .find(prop)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.trim()
                }


                /*
                 * ------------------------------------------------
                 * DRM LICENSE KEY
                 * ------------------------------------------------
                 */
                if (
                    lowerProp.contains(
                        "license_key"
                    )
                ) {

                    val licenseValue =
                        getKodiPropertyValue(
                            currentLine
                        )
                            .orEmpty()


                    if (
                        licenseValue.isNotBlank()
                    ) {

                        /*
                         * Format umum:
                         *
                         * KID:KEY
                         *
                         * Bisa juga:
                         *
                         * KID:KEY|header=value
                         */
                        val parts =
                            licenseValue.split(
                                "|"
                            )


                        chRaw.drmKey =
                            parts
                                .firstOrNull()
                                ?.trim()
                                ?.takeIf {
                                    it.isNotEmpty()
                                }


                        /*
                         * Header tambahan jika ada.
                         */
                        if (
                            parts.size > 1
                        ) {

                            parseHeaderList(
                                parts
                                    .drop(1)
                                    .joinToString(
                                        "|"
                                    ),
                                chRaw.drmHeaders
                            )
                        }
                    }
                }


                /*
                 * ------------------------------------------------
                 * MANIFEST TYPE
                 * ------------------------------------------------
                 */
                if (
                    lowerProp.contains(
                        "manifest_type"
                    )
                ) {

                    chRaw.manifestType =
                        Regex(
                            "(?i)manifest_type=(.*)"
                        )
                            .find(prop)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.trim()
                }


                chReset =
                    false

                return@forEach
            }


            /*
             * ====================================================
             * EXTINF
             * ====================================================
             */
            if (
                currentLine.startsWith(
                    "#EXTINF",
                    ignoreCase = true
                )
            ) {

                /*
                 * Jika sebelumnya sudah ada channel,
                 * buat ChannelRaw baru.
                 */
                if (
                    chReset &&
                    !chRaw.name.isNullOrBlank()
                ) {

                    chRaw =
                        ChannelRaw()
                }


                /*
                 * CHANNEL NAME
                 */
                chRaw.name =
                    currentLine.findPattern(
                        ".*,(.+?)$"
                    )
                        ?.trim()


                /*
                 * GROUP TITLE
                 */
                val group =
                    currentLine.findPattern(
                        ".*group-title=\"(.*?)\".*"
                    )

                if (
                    !group.isNullOrBlank()
                ) {

                    chRaw.group =
                        group
                }


                /*
                 * TVG LOGO
                 */
                chRaw.logoUrl =
                    currentLine.findPattern(
                        ".*tvg-logo=\"(.*?)\".*"
                    )


                /*
                 * DEFAULT NAME
                 */
                if (
                    chRaw.name.isNullOrBlank()
                ) {

                    chRaw.name =
                        "NO NAME"
                }


                /*
                 * DEFAULT GROUP
                 */
                if (
                    chRaw.group.isNullOrBlank()
                ) {

                    chRaw.group =
                        "UNCATEGORIZED"
                }


                chReset =
                    true

                return@forEach
            }


            /*
             * ====================================================
             * STREAM URL
             * ====================================================
             */
            if (
                currentLine.isStreamUrl()
            ) {

                /*
                 * Channel selesai dibaca.
                 */
                chReset =
                    true


                /*
                 * =================================================
                 * STREAM URL
                 * =================================================
                 *
                 * Mendukung:
                 *
                 * https://example.com/index.mpd
                 *
                 * maupun:
                 *
                 * https://example.com/index.mpd|user-agent=...|referer=...
                 */
                chRaw.streamUrl =
                    currentLine
                        .findPattern(
                            "(.+?)(\\|.*)?$"
                        )
                        ?.trim()
                        ?: currentLine


                /*
                 * =================================================
                 * URL USER AGENT
                 * =================================================
                 */
                if (
                    chRaw.userAgent.isNullOrBlank()
                ) {

                    chRaw.userAgent =
                        currentLine.findPattern(
                            ".*\\|user-agent=(.+?)(\\|.*)?$"
                        )
                }


                /*
                 * =================================================
                 * URL REFERER
                 * =================================================
                 */
                if (
                    chRaw.referer.isNullOrBlank()
                ) {

                    chRaw.referer =
                        currentLine.findPattern(
                            ".*\\|referer=(.+?)(\\|.*)?$"
                        )
                }


                /*
                 * =================================================
                 * MIME TYPE
                 * =================================================
                 */
                val streamUrl =
                    chRaw.streamUrl
                        .orEmpty()


                val mimeType =
                    when {

                        chRaw.manifestType
                            ?.equals(
                                "mpd",
                                ignoreCase = true
                            ) == true -> {

                            MimeTypes.APPLICATION_MPD
                        }

                        chRaw.manifestType
                            ?.equals(
                                "dash",
                                ignoreCase = true
                            ) == true -> {

                            MimeTypes.APPLICATION_MPD
                        }

                        chRaw.manifestType
                            ?.equals(
                                "m3u8",
                                ignoreCase = true
                            ) == true -> {

                            MimeTypes.APPLICATION_M3U8
                        }

                        chRaw.manifestType
                            ?.equals(
                                "hls",
                                ignoreCase = true
                            ) == true -> {

                            MimeTypes.APPLICATION_M3U8
                        }

                        streamUrl.contains(
                            ".mpd",
                            ignoreCase = true
                        ) -> {

                            MimeTypes.APPLICATION_MPD
                        }

                        streamUrl.contains(
                            ".m3u8",
                            ignoreCase = true
                        ) -> {

                            MimeTypes.APPLICATION_M3U8
                        }

                        else -> {
                            null
                        }
                    }


                /*
                 * =================================================
                 * DRM ID
                 * =================================================
                 */
                val drmId =
                    chRaw.drmKey
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.toCRC32()


                /*
                 * =================================================
                 * REGISTER DRM LICENSE
                 * =================================================
                 */
                if (
                    drmId != null &&
                    !chRaw.drmKey.isNullOrBlank()
                ) {

                    val drmExists =
                        result.drmLicenses.any {
                            it.id == drmId
                        }


                    if (
                        !drmExists
                    ) {

                        result.drmLicenses.add(
                            DrmLicense().apply {

                                id =
                                    drmId

                                key =
                                    chRaw.drmKey
                                        .orEmpty()

                                type =
                                    chRaw.drmType
                                        .orEmpty()

                                headers =
                                    HashMap(
                                        chRaw.drmHeaders
                                    )
                            }
                        )
                    }
                }


                /*
                 * =================================================
                 * CHANNEL
                 * =================================================
                 */
                val channel =
                    Channel().apply {

                        name =
                            chRaw.name
                                .orEmpty()
                                .normalize()


                        logoUrl =
                            chRaw.logoUrl


                        streamUrl =
                            chRaw.streamUrl


                        mimeType =
                            mimeType


                        this.drmId =
                            drmId


                        userAgent =
                            chRaw.userAgent
                                ?.trim()
                                ?.takeIf {
                                    it.isNotBlank()
                                }


                        referer =
                            chRaw.referer
                                ?.trim()
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                    }


                /*
                 * =================================================
                 * CATEGORY
                 * =================================================
                 */
                val categoryName =
                    chRaw.group
                        .orEmpty()
                        .normalize()


                val category =
                    result.categories
                        .firstOrNull {
                            it.name ==
                                categoryName
                        }


                if (
                    category == null
                ) {

                    result.categories.add(
                        Category().apply {

                            name =
                                categoryName

                            channels =
                                arrayListOf(
                                    channel
                                )
                        }
                    )

                } else {

                    /*
                     * Hindari nama channel yang sama
                     * dalam kategori yang sama.
                     */
                    val duplicate =
                        category.channels
                            ?.count {

                                it.name
                                    ?.substringBefore(
                                        " #"
                                    ) ==
                                    channel.name
                            }
                            ?: 0


                    if (
                        duplicate > 0
                    ) {

                        channel.name =
                            "${channel.name} #$duplicate"
                    }


                    category.channels
                        ?.add(
                            channel
                        )
                }
            }
        }


        return result
    }
}
