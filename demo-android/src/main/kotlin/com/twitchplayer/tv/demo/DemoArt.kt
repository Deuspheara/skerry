package com.twitchplayer.tv.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options

/** Draws the prototype's abstract artwork (palette only) for `demoart://x/<seed>/<style>` so demo screens need no network. */
internal class DemoArtFetcher(private val data: Uri, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val parts = data.path.orEmpty().trim('/').split('/')
        val seed = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val style = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val size = options.size
        val w = (size.width as? coil3.size.Dimension.Pixels)?.px ?: 400
        val h = (size.height as? coil3.size.Dimension.Pixels)?.px ?: 225
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), w.toFloat(), h.toFloat(), seed, style)
        return ImageFetchResult(bitmap.asImage(), isSampled = false, dataSource = DataSource.MEMORY)
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? = if (data.scheme == "demoart") DemoArtFetcher(data, options) else null
    }

    private fun draw(canvas: Canvas, w: Float, h: Float, seed: Int, style: Int) {
        var a = seed * 7919 + 13
        fun rnd(): Float {
            a += 0x6D2B79F5
            var t = a
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
        }
        fun f(lo: Float, hi: Float) = lo + (hi - lo) * rnd()
        val fills = listOf(0xE6 to 0xFF9146FF.toInt(), 0x8C to 0xFF9146FF.toInt(), 0xFF to 0xFF303032.toInt(), 0x38 to 0xFFADADB8.toInt(), 0x1A to 0xFFEFEFF1.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(alphaColor: Pair<Int, Int>) {
            paint.style = Paint.Style.FILL
            paint.color = alphaColor.second
            paint.alpha = alphaColor.first
        }
        paint.color = 0xFF18181B.toInt()
        canvas.drawRect(0f, 0f, w, h, paint)
        when (style) {
            0 -> {
                val cols = 10
                val cw = w / cols
                val rows = Math.ceil((h / cw).toDouble()).toInt()
                val pad = cw * .12f
                for (i in 0 until cols) {
                    for (j in 0 until rows) {
                        if (rnd() < .42f) {
                            fill(fills[(rnd() * fills.size).toInt()])
                            canvas.drawRect(i * cw + pad, j * cw + pad, (i + 1) * cw - pad, (j + 1) * cw - pad, paint)
                        }
                    }
                }
            }

            1 -> {
                val cx = f(.3f, .7f) * w
                val cy = f(.3f, .7f) * h
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w * .012f
                for (i in 6 downTo 1) {
                    paint.color = if (i % 2 == 1) 0xFF303032.toInt() else 0xFFADADB8.toInt()
                    paint.alpha = if (i % 2 == 1) 255 else 90
                    canvas.drawCircle(cx, cy, i * maxOf(w, h) * .11f, paint)
                }
                paint.style = Paint.Style.FILL
                paint.color = 0xFF9146FF.toInt()
                paint.alpha = 255
                canvas.drawCircle(cx, cy, minOf(w, h) * .12f, paint)
            }

            2 -> repeat(6) {
                fill(fills[(rnd() * fills.size).toInt()])
                val x = f(0f, .8f) * w
                val y = f(0f, .7f) * h
                canvas.drawRect(x, y, x + f(.15f, .45f) * w, y + f(.2f, .6f) * h, paint)
            }

            else -> {
                paint.style = Paint.Style.FILL
                paint.color = 0xFF9146FF.toInt()
                paint.alpha = 255
                canvas.drawCircle(f(.55f, .85f) * w, f(.2f, .4f) * h, minOf(w, h) * .14f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = h * .02f
                for (i in 0 until 5) {
                    val y = h * (.35f + i * .13f)
                    val amp = f(.04f, .12f) * h
                    paint.color = if (i % 2 == 1) 0xFFADADB8.toInt() else 0xFF303032.toInt()
                    paint.alpha = if (i % 2 == 1) 90 else 255
                    val path = Path().apply {
                        moveTo(0f, y)
                        cubicTo(w * .25f, y - amp, w * .5f, y + amp, w * .75f, y - amp * .5f)
                        lineTo(w, y)
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
    }
}
