package com.omniforge.app.engine.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import kotlin.math.ceil
import kotlin.math.max

data class SubtitleCue(val startUs: Long, val endUs: Long, val text: String)

/** Parses SRT and WebVTT. Styling tags (<i>, <b>, {\an8}…) are stripped. */
object SubtitleParser {
    private val timeLine = Regex("""((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})""")
    private val tags = Regex("""<[^>]+>|\{\\[^}]*\}""")

    fun parse(raw: String): List<SubtitleCue> {
        val lines = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val cues = mutableListOf<SubtitleCue>()
        var i = 0
        while (i < lines.size) {
            val match = timeLine.find(lines[i])
            if (match == null) { i++; continue }
            val start = parseTime(match.groupValues[1])
            val end = parseTime(match.groupValues[2])
            i++
            val text = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (text.isNotEmpty()) text.append('\n')
                text.append(lines[i].replace(tags, "").trim())
                i++
            }
            if (end > start && text.isNotBlank()) cues += SubtitleCue(start, end, text.toString())
        }
        return cues.sortedBy { it.startUs }
    }

    private fun parseTime(s: String): Long {
        val parts = s.replace(',', '.').split(':')
        val (h, m, sec) = when (parts.size) {
            3 -> Triple(parts[0].toLong(), parts[1].toLong(), parts[2])
            else -> Triple(0L, parts[0].toLong(), parts[1])
        }
        val secMs = (sec.toDouble() * 1000).toLong()
        return ((h * 3600 + m * 60) * 1000 + secMs) * 1000
    }
}

/**
 * Draws the active cue as white text on a translucent black box near the bottom of the frame.
 * [fontSizePt] is relative to a 720p-tall frame and scales with the real video height.
 */
@OptIn(UnstableApi::class)
class SubtitleOverlay(
    private val cues: List<SubtitleCue>,
    private val fontSizePt: Int
) : BitmapOverlay() {

    private var frameHeight = 720
    private var frameWidth = 1280
    private val empty: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var cachedCue: SubtitleCue? = null
    private var cachedBitmap: Bitmap? = null

    private val visible = OverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, -0.92f)
        .setOverlayFrameAnchor(0f, -1f)
        .build()
    private val hidden = OverlaySettings.Builder().setAlphaScale(0f).build()

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        frameWidth = videoSize.width
        frameHeight = videoSize.height
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        if (cueAt(presentationTimeUs) != null) visible else hidden

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val cue = cueAt(presentationTimeUs) ?: return empty
        if (cue !== cachedCue) {
            cachedBitmap?.recycle()
            cachedBitmap = render(cue.text)
            cachedCue = cue
        }
        return cachedBitmap!!
    }

    private fun cueAt(timeUs: Long): SubtitleCue? = cues.firstOrNull { timeUs >= it.startUs && timeUs < it.endUs }

    private fun render(text: String): Bitmap {
        // 20pt at 720p ≈ 40px; scale with the frame.
        val textPx = fontSizePt * 2f * frameHeight / 720f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textPx
            isFakeBoldText = true
        }
        val maxWidth = (frameWidth * 0.9f).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        var lineWidth = 0f
        for (l in 0 until layout.lineCount) lineWidth = max(lineWidth, layout.getLineWidth(l))
        val pad = textPx * 0.35f
        val w = ceil(lineWidth + pad * 2).toInt().coerceAtLeast(2)
        val h = ceil(layout.height + pad * 2).toInt().coerceAtLeast(2)

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 0, 0, 0) }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), pad, pad, bg)
        canvas.save()
        // The layout is maxWidth wide and centred; shift so its centre lands on the bitmap centre.
        canvas.translate(w / 2f - maxWidth / 2f, pad)
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }
}
