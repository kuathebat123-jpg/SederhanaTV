package net.harimurti.tv.model

class ChannelRaw {

    var name: String? = null
    var group: String? = null
    var logoUrl: String? = null
    var streamUrl: String? = null

    var manifestType: String? = null

    var drmType: String? = null
    var drmKey: String? = null

    var drmHeaders: HashMap<String, String> =
        HashMap()

    var userAgent: String? = null
    var referer: String? = null
}
