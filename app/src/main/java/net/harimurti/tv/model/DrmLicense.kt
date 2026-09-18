package net.harimurti.tv.model

import com.google.gson.annotations.SerializedName

class DrmLicense {

    @SerializedName("drm_id")
    var id: String? = null

    @SerializedName("drm_type")
    var type: String = ""

    @SerializedName("drm_key")
    var key: String = ""

    /**
     * HTTP headers yang dikirim ketika meminta license.
     *
     * Contoh:
     * Authorization = Bearer xxxxx
     * X-Custom-Header = value
     */
    var headers: HashMap<String, String> = HashMap()
}
