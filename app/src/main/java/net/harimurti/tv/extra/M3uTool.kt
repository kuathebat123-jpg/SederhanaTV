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


    fun parse(
        content: String?
    ): Playlist {

        val result = Playlist()

        var chRaw = ChannelRaw()

        var chReset = true

        val lines =
            content
                ?.lines()
                ?: throw Exception(
                    "Empty Content"
                )


        lines.forEach { line ->

            val currentLine =
                line.trim()


            if (currentLine.isBlank()) {
                return@forEach
            }


            /*
             * ==========================================
             * VLC OPTIONS
             * ==========================================
             */

            if (
                currentLine.startsWith(
                    "#EXTVLCOPT",
                    ignoreCase = true
                )
            ) {

                if (
                    currentLine.contains(
                        "http-user-agent",
                        ignoreCase = true
                    )
                ) {

                    chRaw.userAgent =
                        currentLine.findPattern(
                            ".*http-user-agent=(.+?)$"
                        )
                }


                if (
                    currentLine.contains(
                        "http-referrer",
                        ignoreCase = true
                    )
                ) {

                    chRaw.referer =
                        currentLine.findPattern(
                            ".*http-referrer=(.+?)$"
                        )
                }


                chReset = false

                return@forEach
            }


            /*
             * ==========================================
             * GROUP
             * ==========================================
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

                chReset = false

                return@forEach
            }


            /*
             * ==========================================
             * KODIPROP
             * ==========================================
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
                 * DRM TYPE
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
                 * LICENSE KEY
                 */

                if (
                    lowerProp.contains(
                        "license_key"
                    )
                ) {

                    val licenseValue =
                        Regex(
                            "(?i)license_key=(.*)"
                        )
                            .find(prop)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.trim()
                            .orEmpty()


                    if (
                        licenseValue.isNotBlank()
                    ) {

                        val parts =
                            licenseValue.split("|")


                        chRaw.drmKey =
                            parts
                                .first()
                                .trim()


                        if (parts.size > 1) {

                            parseHeaderList(
                                parts
                                    .drop(1)
                                    .joinToString("|"),
                                chRaw.drmHeaders
                            )
                        }
                    }
                }


                /*
                 * MANIFEST TYPE
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


                chReset = false

                return@forEach
            }


            /*
             * ==========================================
             * EXTINF
             * ==========================================
             */

            if (
                currentLine.startsWith(
                    "#EXTINF",
                    ignoreCase = true
                )
            ) {

                if (
                    chReset &&
                    !chRaw.name.isNullOrBlank()
                ) {

                    chRaw =
                        ChannelRaw()
                }


                chRaw.name =
                    currentLine.findPattern(
                        ".*,(.+?)$"
                    )


                chRaw.group =
                    currentLine.findPattern(
                        ".*group-title=\"(.*?)\".*"
                    )
                        ?: chRaw.group


                chRaw.logoUrl =
                    currentLine.findPattern(
                        ".*tvg-logo=\"(.*?)\".*"
                    )


                if (
                    chRaw.name.isNullOrBlank()
                ) {

                    chRaw.name =
                        "NO NAME"
                }


                if (
                    chRaw.group.isNullOrBlank()
                ) {

                    chRaw.group =
                        "UNCATEGORIZED"
                }


                chReset = true

                return@forEach
            }


            /*
             * ==========================================
             * STREAM URL
             * ==========================================
             */

            if (
                currentLine.isStreamUrl()
            ) {

                chReset = true


                chRaw.streamUrl =
                    currentLine
                        .findPattern(
                            "(.+?)(\\|.*)?"
                        )
                        ?.trim()
                        ?: currentLine


                /*
                 * URL HEADERS
                 */

                chRaw.userAgent =
                    chRaw.userAgent
                        ?: currentLine.findPattern(
                            ".*\\|user-agent=(.+?)(\\|.*)?"
                        )


                chRaw.referer =
                    chRaw.referer
                        ?: currentLine.findPattern(
                            ".*\\|referer=(.+?)(\\|.*)?"
                        )


                /*
                 * DRM ID
                 */

                val drmId =
                    chRaw.drmKey
                        ?.toCRC32()


                /*
                 * REGISTER DRM
                 */

                val drmExists =
                    result.drmLicenses.any {
                        it.id == drmId
                    }


                if (
                    drmId != null &&
                    !drmExists &&
                    !chRaw.drmKey.isNullOrBlank()
                ) {

                    result.drmLicenses.add(
                        DrmLicense().apply {

                            id = drmId

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


                /*
                 * CHANNEL
                 */

                val channel =
                    Channel().apply {

                        name =
                            chRaw.name.normalize()

                        logoUrl =
                            chRaw.logoUrl

                        streamUrl =
                            chRaw.streamUrl

                        mimeType =
                            when (
                                chRaw.manifestType
                                    ?.lowercase()
                            ) {

                                "mpd",
                                "dash" ->
                                    MimeTypes.APPLICATION_MPD

                                "m3u8",
                                "hls" ->
                                    MimeTypes.APPLICATION_M3U8

                                else ->
                                    null
                            }

                        drmId =
                            drmId

                        userAgent =
                            chRaw.userAgent

                        referer =
                            chRaw.referer
                    }


                /*
                 * CATEGORY
                 */

                val categoryName =
                    chRaw.group.normalize()


                val category =
                    result.categories
                        .firstOrNull {
                            it.name ==
                                    categoryName
                        }


                if (category == null) {

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

                    val duplicate =
                        category.channels
                            ?.count {

                                it.name
                                    ?.substringBefore(
                                        " #"
                                    ) ==
                                        chRaw.name
                            }
                            ?: 0


                    if (duplicate > 0) {

                        channel.name =
                            "${chRaw.name} #$duplicate"
                    }


                    category.channels
                        ?.add(channel)
                }
            }
        }


        return result
    }
}
