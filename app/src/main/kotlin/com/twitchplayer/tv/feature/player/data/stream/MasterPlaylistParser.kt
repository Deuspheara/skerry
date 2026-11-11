package com.twitchplayer.tv.feature.player.data.stream

/**
 * Reads the variants of a Twitch master playlist (`#EXT-X-MEDIA` + `#EXT-X-STREAM-INF` pairs). Twitch names every
 * rendition through a VIDEO group: `#EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="720p60",NAME="720p60"` followed by
 * `#EXT-X-STREAM-INF:BANDWIDTH=...,RESOLUTION=1280x720,CODECS="...",VIDEO="720p60",FRAME-RATE=60.000` and the URL.
 */
object MasterPlaylistParser {
    private val attribute = Regex("""([A-Z0-9-]+)=("([^"]*)"|[^,]*)""")

    fun parse(playlist: String): List<StreamVariant> {
        val lines = playlist.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.firstOrNull() != "#EXTM3U") return emptyList()
        val names = HashMap<String, String>()
        val variants = ArrayList<StreamVariant>()
        var pending: Map<String, String>? = null
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-MEDIA:") -> attributes(line.substringAfter(':')).let { a ->
                    val group = a["GROUP-ID"]
                    if (a["TYPE"] == "VIDEO" && group != null) names[group] = a["NAME"] ?: group
                }
                line.startsWith("#EXT-X-STREAM-INF:") -> pending = attributes(line.substringAfter(':'))
                !line.startsWith("#") -> pending?.let { a ->
                    pending = null
                    val id = a["VIDEO"] ?: return@let
                    val (w, h) = a["RESOLUTION"]?.split('x')?.let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) } ?: (0 to 0)
                    variants += StreamVariant(
                        id = id,
                        name = names[id] ?: id,
                        bandwidth = a["BANDWIDTH"]?.toIntOrNull() ?: 0,
                        width = w,
                        height = h,
                        frameRate = a["FRAME-RATE"]?.toFloatOrNull() ?: 0f,
                        codecs = a["CODECS"].orEmpty(),
                        url = line,
                    )
                }
            }
        }
        // Best first (source is the highest bandwidth), audio only last; duplicates of one id keep the first.
        return variants.distinctBy { it.id }.sortedWith(compareBy<StreamVariant> { it.audioOnly }.thenByDescending { it.bandwidth })
    }

    private fun attributes(list: String): Map<String, String> =
        attribute.findAll(list).associate { it.groupValues[1] to (it.groups[3]?.value ?: it.groupValues[2]) }
}

/** Which rendition starts a channel: 720p60, else 1080p30, else the best one that is not above 1080p, else whatever exists. */
object QualityPolicy {
    const val AUTO = "auto"

    fun default(variants: List<StreamVariant>): StreamVariant? {
        val video = variants.filter { !it.audioOnly }
        fun match(height: Int, fps: IntRange) = video.firstOrNull { it.height == height && it.frameRate.toInt() in fps }
        return match(720, 50..120) ?: match(1080, 0..49) ?: video.filter { it.height <= 1080 }.maxByOrNull { it.bandwidth } ?: video.firstOrNull() ?: variants.firstOrNull()
    }

    /** The viewer's pick when this stream offers it, else the default. [AUTO] is the adaptive master playlist (returns null). */
    fun choose(variants: List<StreamVariant>, preferred: String?): StreamVariant? =
        if (preferred == AUTO) null else variants.firstOrNull { it.id == preferred } ?: default(variants)
}
