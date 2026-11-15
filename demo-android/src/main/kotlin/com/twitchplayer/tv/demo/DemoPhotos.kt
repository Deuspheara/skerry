package com.twitchplayer.tv.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * Photographs for the demo data. The fictional channels and categories ask for `demoart://x/<seed>/<style>`; the seeds in
 * [DemoData] map to one of the bundled public-domain / CC0 photos (assets/demo_photos, credits in CREDITS.md), cropped to
 * the requested size. A seed with no photo falls back to the abstract artwork drawn in [DemoArtFetcher].
 */
internal object DemoPhotos {
    private val keyBySeed: Map<Int, String> = mapOf(
        // categories
        11 to "valmoor", 12 to "racers", 13 to "kestrel", 14 to "discussion", 15 to "speedrun", 16 to "musique", 17 to "arcana",
        18 to "simulation", 19 to "cuisine", 20 to "echecs", 21 to "art", 22 to "sport", 23 to "concerts", 24 to "voyages",
        25 to "peinture", 26 to "artisanat",
        // channels (avatar seed; the stream thumbnail is seed + 500)
        101 to "valmoor", 102 to "echecs", 103 to "cuisine", 104 to "musique", 105 to "kestrel", 106 to "art", 107 to "speedrun",
        108 to "arcana", 109 to "tournesol", 110 to "racers", 111 to "simulation", 112 to "brume", 113 to "racers", 114 to "echecs",
        115 to "discussion", 116 to "peinture", 117 to "concerts",
    )

    /**
     * [seed] is `base + 1000 * variant`: the base picks the photo (a thumbnail adds [THUMB_OFFSET]), the variant picks a different
     * crop of it, so a channel's replays and clips look like different moments of the same stream.
     */
    fun render(context: Context, seed: Int, width: Int, height: Int): Bitmap? {
        val variant = seed / VARIANT_STEP
        var base = seed % VARIANT_STEP
        if (base >= THUMB_OFFSET) base -= THUMB_OFFSET
        val key = keyBySeed[base] ?: return null
        val source = runCatching {
            context.assets.open("demo_photos/$key.jpg").use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Avatars (channel seeds, square) zoom in on the photo; replays and clips zoom a little more per variant.
        val avatar = base in CHANNEL_SEEDS && variant == 0 && seed < THUMB_OFFSET
        val zoom = if (avatar) 1.6f else 1f + 0.12f * (variant % 5)
        val scale = maxOf(width / source.width.toFloat(), height / source.height.toFloat()) * zoom
        val cropW = (width / scale).toInt().coerceIn(1, source.width)
        val cropH = (height / scale).toInt().coerceIn(1, source.height)
        val focus = FOCI[(base + variant) % FOCI.size]
        val left = ((source.width - cropW) * focus).toInt().coerceIn(0, source.width - cropW)
        val top = ((source.height - cropH) * (1f - focus)).toInt().coerceIn(0, source.height - cropH)
        Canvas(out).drawBitmap(source, Rect(left, top, left + cropW, top + cropH), Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return out
    }

    private const val VARIANT_STEP = 1000
    private val CHANNEL_SEEDS = 101..117
    private const val THUMB_OFFSET = 500
    private val FOCI = floatArrayOf(.5f, .2f, .8f, .35f, .65f)
}
