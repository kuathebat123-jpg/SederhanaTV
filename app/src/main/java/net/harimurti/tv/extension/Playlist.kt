package net.harimurti.tv.extension

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import net.harimurti.tv.extra.M3uTool
import net.harimurti.tv.model.Category
import net.harimurti.tv.model.Channel
import net.harimurti.tv.model.DrmLicense
import net.harimurti.tv.model.Playlist
import androidx.media3.common.MimeTypes

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
    if (playlist == null) return

    playlist.categories.let {
        this?.categories?.addAll(it)
    }

    playlist.drmLicenses.let {
        this?.drmLicenses?.addAll(it)
    }
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
 * StarVision-TV JSON
 * ============================================================
 *
 * Format yang dibaca:
 *
 * {
 *   "_info": {...},
 *   "cat_logos": {...},
 *   "channels": [
 *      {
 *        "id": 1,
 *        "name": "Nama Channel",
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
 * Untuk DRM:
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

    val root =
        JsonParser.parseString(content)

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

    val categoryMap =
        LinkedHashMap<String, Category>()

    channelsElement.asJsonArray.forEach { element ->

        if (
            !element.isJsonObject
        ) {
            return@forEach
        }

        val item =
            element.asJsonObject

        /*
         * ----------------------------------------------------
         * BASIC DATA
         * ----------------------------------------------------
         */

        val name =
            item.get("name")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()
                ?.ifBlank {
                    "NO NAME"
                }
                ?: "NO NAME"

        val categoryName =
            item.get("cat")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()
                ?.ifBlank {
                    "UNCATEGORIZED"
                }
                ?: "UNCATEGORIZED"

        val logo =
            item.get("logo")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()

        val streamUrl =
            item.get("url")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()

        /*
         * Channel tanpa URL tidak dimasukkan.
         */
        if (streamUrl.isNullOrBlank()) {
            return@forEach
        }

        /*
         * ----------------------------------------------------
         * STREAM TYPE
         * ----------------------------------------------------
         */

        val type =
            item.get("type")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()
                ?.lowercase()

        val mimeType =
            when (type) {

                "dash",
                "mpd" ->
                    MimeTypes.APPLICATION_MPD

                "hls",
                "m3u8" ->
                    MimeTypes.APPLICATION_M3U8

                else -> {

                    when {

                        streamUrl
                            .contains(
                                ".mpd",
                                ignoreCase = true
                            ) ->
                            MimeTypes.APPLICATION_MPD

                        streamUrl
                            .contains(
                                ".m3u8",
                                ignoreCase = true
                            ) ->
                            MimeTypes.APPLICATION_M3U8

                        else ->
                            null
                    }
                }
            }

        /*
         * ----------------------------------------------------
         * HTTP USER AGENT
         * ----------------------------------------------------
         */

        val userAgent =
            item.get("ua")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()
                ?.let { ua ->

                    when {

                        ua.startsWith(
                            "http-user-agent=",
                            ignoreCase = true
                        ) ->
                            ua.substringAfter(
                                "="
                            ).trim()

                        else ->
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
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()

        /*
         * ----------------------------------------------------
         * DRM
         * ----------------------------------------------------
         */

        val drmEnabled =
            item.get("drm")
                ?.takeIf { !it.isJsonNull }
                ?.asBoolean
                ?: false

        val drmType =
            item.get("drmType")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()
                .orEmpty()

        val licenseKey =
            item.get("licUrl")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()
                .orEmpty()

        var drmId: String? = null

        /*
         * Register DRM license jika channel memang DRM.
         */
        if (
            drmEnabled &&
            licenseKey.isNotBlank()
        ) {

            drmId =
                licenseKey.toCRC32()

            val exists =
                playlist.drmLicenses.any {
                    it.id == drmId
                }

            if (!exists) {

                playlist.drmLicenses.add(
                    DrmLicense().apply {

                        id =
                            drmId

                        type =
                            drmType

                        key =
                            licenseKey

                        headers =
                            HashMap()
                    }
                )
            }
        }

        /*
         * ----------------------------------------------------
         * CHANNEL MODEL
         * ----------------------------------------------------
         */

        val channel =
            Channel().apply {

                this.name =
                    name.normalize()

                this.logoUrl =
                    logo

                this.streamUrl =
                    streamUrl

                this.mimeType =
                    mimeType

                this.drmId =
                    drmId

                this.userAgent =
                    userAgent

                this.referer =
                    referer
            }

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
         * Hindari nama channel duplikat dalam kategori.
         */
        val existingCount =
            category.channels
                ?.count { existing ->

                    existing.name
                        ?.substringBefore(
                            " #"
                        ) ==
                            channel.name
                }
                ?: 0

        if (existingCount > 0) {

            channel.name =
                "${channel.name} #$existingCount"
        }

        category.channels?.add(
            channel
        )
    }

    /*
     * Masukkan kategori ke Playlist
     * dengan urutan sesuai channels.json.
     */
    playlist.categories.addAll(
        categoryMap.values
    )

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
     * 1. Coba format StarVision-TV terlebih dahulu.
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
     * 2. Coba format Playlist JSON lama.
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
     * 3. Kalau bukan JSON, coba M3U.
     */
    try {

        return M3uTool().parse(this)

    } catch (e: Exception) {

        e.printStackTrace()
    }


    /*
     * 4. Tidak bisa diparse.
     */
    return null
}


fun Playlist?.isCategoriesEmpty(): Boolean {

    if (
        this?.categories?.isEmpty() == true
    ) {
        return true
    }

    return (
        this?.categories?.size == 1
    ) &&
        (
            this.categories[0]
                .channels
                ?.isEmpty() == true
        )
}
