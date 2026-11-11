package com.twitchplayer.tv.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fictional emote and badge artwork drawn in code (no network, no brand art) so chat can be exercised end to end offline:
 * stills as bitmaps, animated ones as real GIFs run through the same animated decoder as the providers' WebP.
 *
 * URLs: `demoemote://x/<name>/<still|anim>` for third-party style emotes, and
 * `https://static-cdn.jtvnw.net/emoticons/v2/demo-<name>/<static|animated>/dark/2.0` for the Twitch-native path.
 */
internal object DemoEmoteArt {
    /** name -> (aspect, animated) */
    val specs = mapOf(
        "heart" to (1f to false), "smile" to (1f to false), "gg" to (1.7f to false), "star" to (1f to false), "badge-sub" to (1f to false),
        "badge-mod" to (1f to false), "badge-vip" to (1f to false), "badge-bc" to (1f to false),
        "bounce" to (1f to true), "clap" to (1f to true), "wave" to (2f to true), "spin" to (1f to true), "rain" to (1f to true), "sparkle" to (1f to false),
    )

    fun url(name: String, animated: Boolean) = "demoemote://x/$name/${if (animated) "anim" else "still"}"
    fun twitchUrl(name: String, animated: Boolean) = "https://static-cdn.jtvnw.net/emoticons/v2/demo-$name/${if (animated) "animated" else "static"}/dark/2.0"

    private const val BASE = 64
    private const val FRAMES = 12
    private const val FRAME_DELAY_CS = 6 // 60 ms

    /** Frame [t] (0..1 of the loop) of the artwork, [BASE] px high. */
    fun draw(name: String, t: Float): Bitmap {
        val aspect = specs[name]?.first ?: 1f
        val w = (BASE * aspect).toInt()
        val bitmap = Bitmap.createBitmap(w, BASE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = BASE.toFloat()
        val a = 2 * PI.toFloat() * t
        fun fill(color: Int) {
            p.style = Paint.Style.FILL
            p.color = color
        }
        fun stroke(color: Int, width: Float) {
            p.style = Paint.Style.STROKE
            p.color = color
            p.strokeWidth = width
        }
        when (name) {
            "heart" -> {
                fill(0xFFFF5C8A.toInt())
                c.drawPath(heart(s * .5f, s * .52f, s * .42f), p)
            }

            "smile" -> {
                fill(0xFFFFD84D.toInt())
                c.drawCircle(s / 2, s / 2, s * .42f, p)
                fill(0xFF3A2A00.toInt())
                c.drawCircle(s * .38f, s * .42f, s * .05f, p)
                c.drawCircle(s * .62f, s * .42f, s * .05f, p)
                stroke(0xFF3A2A00.toInt(), s * .06f)
                c.drawArc(RectF(s * .3f, s * .35f, s * .7f, s * .72f), 20f, 140f, false, p)
            }

            "gg" -> {
                fill(0xFF9146FF.toInt())
                c.drawRoundRect(RectF(2f, s * .15f, w - 2f, s * .85f), s * .2f, s * .2f, p)
                p.color = Color.WHITE
                p.textSize = s * .5f
                p.isFakeBoldText = true
                p.textAlign = Paint.Align.CENTER
                c.drawText("GG", w / 2f, s * .66f, p)
            }

            "star" -> {
                fill(0xFFFFC83D.toInt())
                c.drawPath(star(s / 2, s / 2, s * .44f, s * .19f, 0f), p)
            }

            "badge-sub" -> {
                fill(0xFFB28CFF.toInt())
                c.drawPath(star(s / 2, s / 2, s * .46f, s * .2f, 0f), p)
            }

            "badge-mod" -> {
                fill(0xFF2EB872.toInt())
                c.drawRoundRect(RectF(s * .12f, s * .12f, s * .88f, s * .88f), s * .14f, s * .14f, p)
                stroke(Color.WHITE, s * .09f)
                c.drawLine(s * .3f, s * .7f, s * .7f, s * .3f, p)
            }

            "badge-vip" -> {
                fill(0xFFFF69B4.toInt())
                c.drawPath(
                    Path().apply {
                        moveTo(s * .5f, s * .88f)
                        lineTo(s * .1f, s * .38f)
                        lineTo(s * .25f, s * .12f)
                        lineTo(s * .75f, s * .12f)
                        lineTo(s * .9f, s * .38f)
                        close()
                    },
                    p,
                )
            }

            "badge-bc" -> {
                fill(0xFFE91916.toInt())
                c.drawRoundRect(RectF(s * .12f, s * .12f, s * .88f, s * .88f), s * .2f, s * .2f, p)
                fill(Color.WHITE)
                c.drawCircle(s / 2, s / 2, s * .2f, p)
            }

            "bounce" -> { // a ball bouncing on a shadow
                val y = s * .75f - kotlin.math.abs(sin(a / 2)) * s * .5f
                fill(0x55FFFFFF)
                c.drawOval(RectF(s * .25f, s * .84f, s * .75f, s * .94f), p)
                fill(0xFF4FC3C3.toInt())
                c.drawCircle(s / 2, y, s * .17f, p)
            }

            "clap" -> { // two palms meeting
                val gap = (1f + cos(a * 2)) / 2f * s * .16f
                fill(0xFFFFB347.toInt())
                c.drawRoundRect(RectF(s * .2f - gap, s * .25f, s * .46f - gap, s * .78f), s * .1f, s * .1f, p)
                fill(0xFFFF8F3D.toInt())
                c.drawRoundRect(RectF(s * .54f + gap, s * .25f, s * .8f + gap, s * .78f), s * .1f, s * .1f, p)
            }

            "wave" -> { // wide: a travelling wave of dots
                for (i in 0 until 9) {
                    val x = w * (i + .5f) / 9f
                    fill(if (i % 2 == 0) 0xFF9146FF.toInt() else 0xFFBF94FF.toInt())
                    c.drawCircle(x, s / 2 + sin(a + i * .7f) * s * .25f, s * .08f, p)
                }
            }

            "spin" -> {
                fill(0xFFFF4D4D.toInt())
                c.drawPath(star(s / 2, s / 2, s * .44f, s * .18f, a), p)
            }

            "rain" -> { // zero-width overlay: falling drops across the whole slot
                stroke(0xFF5C7CFF.toInt(), s * .05f)
                p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 6) {
                    val x = s * (.12f + i * .15f)
                    val y = ((t + i * .17f) % 1f) * s * 1.2f - s * .1f
                    c.drawLine(x, y, x - s * .03f, y + s * .14f, p)
                }
            }

            "sparkle" -> {
                fill(0xFFFFF176.toInt())
                c.drawPath(star(s * .8f, s * .2f, s * .16f, s * .05f, 0f), p)
                c.drawPath(star(s * .22f, s * .25f, s * .1f, s * .03f, 0f), p)
            }
        }
        return bitmap
    }

    private fun heart(cx: Float, cy: Float, r: Float) = Path().apply {
        moveTo(cx, cy + r * .9f)
        cubicTo(cx - r * 1.4f, cy - r * .1f, cx - r * .7f, cy - r * 1.1f, cx, cy - r * .4f)
        cubicTo(cx + r * .7f, cy - r * 1.1f, cx + r * 1.4f, cy - r * .1f, cx, cy + r * .9f)
        close()
    }

    private fun star(cx: Float, cy: Float, outer: Float, inner: Float, rotation: Float) = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val ang = rotation + PI.toFloat() / 5f * i - PI.toFloat() / 2f
            val x = cx + r * cos(ang)
            val y = cy + r * sin(ang)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    fun gif(name: String): ByteArray {
        val aspect = specs[name]?.first ?: 1f
        return encodeGif(List(FRAMES) { draw(name, it / FRAMES.toFloat()) }, (BASE * aspect).toInt(), BASE, FRAME_DELAY_CS)
    }
}

/** Resolves the two demo URL shapes; drawn on demand, nothing is stored. */
internal class DemoEmoteFetcher(private val name: String, private val animated: Boolean, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        if (animated) {
            return SourceFetchResult(ImageSource(Buffer().write(DemoEmoteArt.gif(name)), FileSystem.SYSTEM), "image/gif", DataSource.MEMORY)
        }
        return ImageFetchResult(DemoEmoteArt.draw(name, 0f).asImage(), isSampled = false, dataSource = DataSource.MEMORY)
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val parts = data.path.orEmpty().trim('/').split('/')
            return when {
                data.scheme == "demoemote" && parts.size >= 2 -> DemoEmoteFetcher(parts[0], parts[1] == "anim", options)

                data.authority == "static-cdn.jtvnw.net" && parts.getOrNull(2)?.startsWith("demo-") == true ->
                    DemoEmoteFetcher(parts[2].removePrefix("demo-"), parts.getOrNull(3) == "animated", options)

                else -> null
            }
        }
    }
}

/** Minimal GIF89a writer: 16-colour palette (index 0 transparent), looping, one LZW stream per frame. Test art only. */
internal fun encodeGif(frames: List<Bitmap>, width: Int, height: Int, delayCs: Int): ByteArray {
    // Palette: transparent + the demo art's colours (nearest match) is plenty for 64 px emotes.
    val palette = intArrayOf(
        0x000000, 0xFFFFFF, 0x9146FF, 0xBF94FF, 0xFF5C8A, 0xFFD84D, 0xFFC83D, 0x4FC3C3,
        0xFFB347, 0xFF8F3D, 0xFF4D4D, 0x5C7CFF, 0xFFF176, 0x3A2A00, 0x888888, 0x2EB872,
    )
    fun nearest(argb: Int): Int {
        if (Color.alpha(argb) < 100) return 0
        var best = 1
        var bestD = Int.MAX_VALUE
        for (i in 1 until palette.size) {
            val dr = Color.red(argb) - (palette[i] shr 16 and 255)
            val dg = Color.green(argb) - (palette[i] shr 8 and 255)
            val db = Color.blue(argb) - (palette[i] and 255)
            val d = dr * dr + dg * dg + db * db
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }
    val out = ByteArrayOutputStream()
    fun short(v: Int) {
        out.write(v and 255)
        out.write(v shr 8 and 255)
    }
    out.write("GIF89a".toByteArray())
    short(width)
    short(height)
    out.write(0xF3)
    out.write(0)
    out.write(0)
    palette.forEach {
        out.write(it shr 16 and 255)
        out.write(it shr 8 and 255)
        out.write(it and 255)
    }
    out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
    out.write("NETSCAPE2.0".toByteArray())
    out.write(byteArrayOf(3, 1, 0, 0, 0))
    val px = IntArray(width * height)
    for (frame in frames) {
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0x09))
        short(delayCs)
        out.write(0)
        out.write(0)
        out.write(0x2C)
        short(0)
        short(0)
        short(width)
        short(height)
        out.write(0)
        frame.getPixels(px, 0, width, 0, 0, width, height)
        out.write(4) // LZW minimum code size
        val data = lzw(ByteArray(px.size) { nearest(px[it]).toByte() }, 4)
        var i = 0
        while (i < data.size) {
            val n = minOf(255, data.size - i)
            out.write(n)
            out.write(data, i, n)
            i += n
        }
        out.write(0)
    }
    out.write(0x3B)
    return out.toByteArray()
}

private fun lzw(pixels: ByteArray, minCode: Int): ByteArray {
    val clear = 1 shl minCode
    val eoi = clear + 1
    val out = ByteArrayOutputStream()
    var cur = 0
    var bits = 0
    var size = minCode + 1
    var next = eoi + 1
    var maxCode = (1 shl size) - 1
    val dict = HashMap<Int, Int>()
    fun write(code: Int) {
        cur = cur or (code shl bits)
        bits += size
        while (bits >= 8) {
            out.write(cur and 255)
            cur = cur ushr 8
            bits -= 8
        }
    }
    fun emit(code: Int) {
        write(code)
        if (next > maxCode && size < 12) {
            size++
            maxCode = (1 shl size) - 1
        }
    }
    write(clear)
    var prefix = pixels[0].toInt() and 255
    for (i in 1 until pixels.size) {
        val b = pixels[i].toInt() and 255
        val key = (prefix shl 8) or b
        val found = dict[key]
        if (found != null) {
            prefix = found
            continue
        }
        emit(prefix)
        if (next < 4096) {
            dict[key] = next++
        } else {
            write(clear)
            dict.clear()
            size = minCode + 1
            next = eoi + 1
            maxCode = (1 shl size) - 1
        }
        prefix = b
    }
    emit(prefix)
    write(eoi)
    if (bits > 0) out.write(cur and 255)
    return out.toByteArray()
}
