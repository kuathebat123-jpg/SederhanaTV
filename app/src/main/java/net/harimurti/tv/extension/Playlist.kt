package net.harimurti.tv.extension

import androidx.media3.common.MimeTypes
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import net.harimurti.tv.extra.M3uTool
import net.harimurti.tv.model.Category
import net.harimurti.tv.model.Channel
import net.harimurti.tv.model.DrmLicense
import net.harimurti.tv.model.Playlist

fun Playlist?.sortCategories() {
    this?.categories?.sortBy { category ->
        category.name?.lowercase()
    }
}

fun Playlist?.sortChannels() {
    if (this == null) return

    for (catId in this.categories.indices) {
        this.categories[catId].channels?.sortBy { channel ->
            channel.name?.lowercase()
        }
    }
}

fun Playlist?.trimChannelWithEmptyStreamUrl() {
    if (this == null) return

    for (catId in this.categories.indices) {
        this.categories[catId].channels?.removeAll { channel ->
            channel.streamUrl.isNullOrBlank()
        }
    }
}

fun Playlist?.mergeWith(
    playlist: Playlist?
) {
    if (this == null || playlist == null) return

    this.categories.addAll(
        playlist.categories
    )

    this.drmLicenses.addAll(
        playlist.drmLicenses
    )
}

fun Playlist?.insertFavorite(
    channels: ArrayList<Channel>
) {
    if (this == null) return

    if (
        this.categories.isNotEmpty() &&
        this.categories[0].isFavorite()
    ) {
        this.categories[0].channels = channels
    } else {
        this.categories.addFavorite(channels)
    }
}

fun Playlist?.removeFavorite() {
    if (this == null) return

    if (
        this.categories.isNotEmpty() &&
        this.categories[0].isFavorite()
    ) {
        this.categories.removeAt(0)
    }
}


/*
 * ============================================================
 * StarVision-TV JSON parser
 * ============================================================
 *
 * Membaca:
 *
 * {
 *   "_info": {...},
 *   "cat_logos": {...},
 *   "channels": [
 *      {
 *        "id": 1,
 *        "name": "TVRI",
 *        "cat": "NASIONAL",
 *        "logo": "...",
 *        "url": "...",
 *        "type": "hls",
 *        "drm": false,
 *        "ua": "..."
 *      }
 *   ]
 * }
 *
 * DRM:
 *
 * "drm": true,
 * "type": "dash",
 * "drmType": "ClearKey",
 * "licUrl": "KID:KEY"
 *
 * ============================================================
 */

private fun parseStarVisionJson(
    content: String
): Playlist? {

    val root = try {
        JsonParser.parseString(content)
    } catch (e: Exception) {
        return null
    }

    if (!root.isJsonObject) {
        return null
    }

    val rootObject =
        root.asJsonObject

    val channelsElement =
        rootObject.get("channels")

    if (
        channelsElement == null ||
        !channelsElement.isJsonArray
    ) {
        return null
    }

    val playlist =
        Playlist()

    /*
     * Menyimpan kategori berdasarkan nama.
     *
     * LinkedHashMap dipakai supaya urutan kategori
     * mengikuti urutan pertama kali muncul di JSON.
     */
    val categoryMap =
        LinkedHashMap<String, Category>()

    /*
     * ========================================================
     * LOOP CHANNEL
     * ========================================================
     */

    channelsElement.asJsonArray.forEach { element ->

        if (!element.isJsonObject) {
            return@forEach
        }

        val item =
            element.asJsonObject


        /*
         * ----------------------------------------------------
         * NAME
         * ----------------------------------------------------
         */

        val name =
            item.get("name")
                ?.takeIf {
                    !it.isJsonNull
                }
                ?.asString
                ?.trim()
                ?.ifEmpty {
                    null
                }
                ?: "Unknown Channel"


        /*
         * ----------------------------------------------------
         * CATEGORY
         * ----------------------------------------------------
         */

        val categoryName =
            item.get("cat")
                ?.takeIf {
                    !it.isJsonNull
                }
                ?.asString
                ?.trim()
                ?.ifEmpty {
                    null
                }
                ?: "LAINNYA"


        /*
         * ----------------------------------------------------
         * LOGO
         * ----------------------------------------------------
         */

        val logo =
            item.get("logo")
                ?.takeIf {
                    !it.isJsonNull
                }
                ?.asString
                ?.trim()
                ?.ifEmpty {
                    null
                }


        /*
         * ----------------------------------------------------
         * STREAM URL
         * ----------------------------------------------------
         */

        val streamUrl =
            item.get("url")
                ?.takeIf {
                    !it.isJsonNull
                }
                ?.asString
                ?.trim()
                ?.ifEmpty {
                    null
                }

        /*
         * Channel tanpa URL tidak berguna untuk player.
         */
        if (streamUrl.isNullOrBlank()) {
            return@forEach
        }


        /*
         * ----------------------------------------------------
         * TYPE
         * ----------------------------------------------------
         */

        val type =
            item.get("type")
                ?.takeIf {
                    !it.isJsonNull
                }
                ?.asString
                ?.trim()
                ?.lowercase()


        /*
         * Tentukan MIME type.
         */
        val mimeType =
            when (type) {

                "hls",
                "m3u8" ->
                    MimeTypes.APPLICATION_M3U8

                "dash",
                "mpd" ->
                    MimeTypes.APPLICATION_MPD

                else -> {

                    when {

                        streamUrl.contains(
                            ".m3u8",
                            ignoreCase = true
                        ) ->
                            MimeTypes.APPLICATION_M3U8

                        streamUrl.contains(
                            ".mpd",
                            ignoreCase = true
                        ) ->
                            MimeTypes.APPLICATION_MPD

                        else ->
                            null
                    }
                }
            }


        /*
         * ----------------------------------------------------
         * USER AGENT
         * ----------------------------------------------------
         */

        val userAgent =
            item.get("ua")
                ?.takeIf {
                    !it.isJsonNull
                }
                ?.asString
                ?.trim()
                ?.let { ua ->

                    if (
                        ua.startsWith(
                            "http-user-agent=",
                            ignoreCase = true
                        )
                    ) {
                        ua.substringAfter(
                            "="
                        ).trim()
                    } else {
                        ua
                    }
                }


        /*
         * ----------------------------------------------------
         * REFERER
         * ----------------------------------------------------
         */

        val referer =
            item.get("referer")
                ?.takeIf {
                    !it.isJsonNull
                }
                ?.asString
                ?.trim()
                ?.ifEmpty {
                    null
                }


        /*
         * ----------------------------------------------------
         * DRM
         * ----------------------------------------------------
         */

        val drmEnabled =
            item.get("drm")
                ?.takeIf {
                    !it.isJsonNull
                }
                ?.asBoolean
                ?: false

        val drmType =
            item.get("drmType")
                ?.takeIf {
                    !it.isJsonNull
                }
                ?.asString
                ?.trim()
                .orEmpty()

        val licenseKey =
            item.get("licUrl")
                ?.takeIf {
                    !it.isJsonNull
                }
                ?.asString
                ?.trim()
                .orEmpty()


        /*
         * ID DRM.
         *
         * Kita menggunakan CRC32 dari license key supaya
         * setiap license memiliki ID yang konsisten.
         */
        val drmId: String? =
            if (
                drmEnabled &&
                licenseKey.isNotBlank()
            ) {
                licenseKey.toCRC32()
            } else {
                null
            }


        /*
         * ----------------------------------------------------
         * DRM LICENSE
         * ----------------------------------------------------
         */

        if (
            drmEnabled &&
            drmId != null &&
            licenseKey.isNotBlank()
        ) {

            val alreadyExists =
                playlist.drmLicenses.any {
                    it.id == drmId
                }

            if (!alreadyExists) {

                val drmLicense =
                    DrmLicense()

                drmLicense.id =
                    drmId

                drmLicense.type =
                    drmType

                drmLicense.key =
                    licenseKey

                /*
                 * User-Agent dan Referer dimasukkan sebagai
                 * header license jika tersedia.
                 */
                if (
                    !userAgent.isNullOrBlank()
                ) {
                    drmLicense.headers[
                        "User-Agent"
                    ] = userAgent
                }

                if (
                    !referer.isNullOrBlank()
                ) {
                    drmLicense.headers[
                        "Referer"
                    ] = referer
                }

                playlist.drmLicenses.add(
                    drmLicense
                )
            }
        }


        /*
         * ----------------------------------------------------
         * CHANNEL
         * ----------------------------------------------------
         */

        val channel =
            Channel()

        channel.name =
            name.normalize()

        channel.logoUrl =
            logo

        channel.streamUrl =
            streamUrl

        channel.mimeType =
            mimeType

        channel.drmId =
            drmId

        channel.userAgent =
            userAgent

        channel.referer =
            referer


        /*
         * ----------------------------------------------------
         * CATEGORY
         * ----------------------------------------------------
         */

        val normalizedCategory =
            categoryName.normalize()

        val category =
            categoryMap.getOrPut(
                normalizedCategory
            ) {

                Category().apply {

                    name =
                        normalizedCategory

                    channels =
                        ArrayList()
                }
            }


        /*
         * Pastikan list channel tersedia.
         */
        if (
            category.channels == null
        ) {
            category.channels =
                ArrayList()
        }


        /*
         * Tambahkan channel.
         *
         * Kita tidak mengubah nama channel apabila duplikat.
         * Dengan demikian nama di channels.json tetap sama.
         */
        category.channels?.add(
            channel
        )
    }


    /*
     * ========================================================
     * MASUKKAN CATEGORY KE PLAYLIST
     * ========================================================
     */

    playlist.categories.addAll(
        categoryMap.values
    )


    /*
     * Jangan mengembalikan playlist kosong.
     */
    if (
        playlist.categories.isEmpty()
    ) {
        return null
    }

    return playlist
}


/*
 * ============================================================
 * MAIN PARSER
 * ============================================================
 */

fun String?.toPlaylist(): Playlist? {

    if (this.isNullOrBlank()) {
        return null
    }


    /*
     * --------------------------------------------------------
     * 1. STARVISION-TV JSON
     * --------------------------------------------------------
     */

    try {

        val starVisionPlaylist =
            parseStarVisionJson(this)

        if (
            starVisionPlaylist != null &&
            !starVisionPlaylist.isCategoriesEmpty()
        ) {
            return starVisionPlaylist
        }

    } catch (e: Exception) {

        e.printStackTrace()
    }


    /*
     * --------------------------------------------------------
     * 2. FORMAT PLAYLIST JSON LAMA
     * --------------------------------------------------------
     */

    try {

        val playlist =
            Gson().fromJson(
                this,
                Playlist::class.java
            )

        if (
            playlist != null &&
            !playlist.isCategoriesEmpty()
        ) {
            return playlist
        }

    } catch (e: JsonParseException) {

        e.printStackTrace()

    } catch (e: Exception) {

        e.printStackTrace()
    }


    /*
     * --------------------------------------------------------
     * 3. M3U
     * --------------------------------------------------------
     */

    try {

        val m3uPlaylist =
            M3uTool().parse(this)

        if (
            m3uPlaylist != null &&
            !m3uPlaylist.isCategoriesEmpty()
        ) {
            return m3uPlaylist
        }

    } catch (e: Exception) {

        e.printStackTrace()
    }


    return null
}


/*
 * ============================================================
 * EMPTY CHECK
 * ============================================================
 */

fun Playlist?.isCategoriesEmpty(): Boolean {

    if (this == null) {
        return true
    }

    if (
        this.categories.isEmpty()
    ) {
        return true
    }

    return this.categories.all { category ->

        category.channels.isNullOrEmpty()
    }
}
