package com.omniforge.apparat.engine.video

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Typeface
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

data class SubtitleCue(val startUs: Long, val endUs: Long, val text: String)

/** Parses SRT and WebVTT. Styling tags (<i>, <b>, {\an8}…) are stripped. (Same as Android.) */
object SubtitleParser {
    private val timeLine = Regex("""((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})""")
    private val tags = Regex("""<[^>]+>|\{\\[^}]*\}""")

    fun parse(raw: String): List<SubtitleCue> {
        val lines = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n')
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
 * Renders cues the way the Android overlay does: bold white text, centred, on a translucent
 * rounded black box whose bottom sits 4% above the bottom of the frame. Each cue becomes a
 * transparent full-frame PNG that FFmpeg overlays for the cue's time span.
 */
internal class SubtitleRenderer(private val frameWidth: Int, private val frameHeight: Int, fontSizePt: Int) {
    private val textPx = fontSizePt * 2f * frameHeight / 720f
    private val typeface: Typeface = runCatching {
        FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD)
    }.getOrNull() ?: FontMgr.default.legacyMakeTypeface("", FontStyle.BOLD) ?: error("No font available for subtitles")
    private val font = Font(typeface, textPx)
    private val maxWidth = frameWidth * 0.9f

    private fun wrap(text: String): List<String> {
        val out = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            var line = ""
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && font.measureTextWidth(candidate) > maxWidth) {
                    out += line
                    line = word
                } else line = candidate
            }
            if (line.isNotEmpty()) out += line
        }
        return out.ifEmpty { listOf(text) }
    }

    fun renderPng(text: String, target: File) {
        Surface.makeRasterN32Premul(frameWidth, frameHeight).use { surface ->
            val canvas: Canvas = surface.canvas
            canvas.clear(Color.TRANSPARENT)
            val lines = wrap(text)
            val metrics = font.metrics
            val lineHeight = (metrics.descent - metrics.ascent + metrics.leading) * 1.0f
            val widths = lines.map { font.measureTextWidth(it) }
            val pad = textPx * 0.35f
            val w = ceil((widths.maxOrNull() ?: 0f) + pad * 2)
            val h = ceil(lineHeight * lines.size + pad * 2)
            val bottom = frameHeight * 0.96f
            val top = bottom - h
            val left = (frameWidth - w) / 2f
            val bg = Paint().apply { color = Color.makeARGB(170, 0, 0, 0); isAntiAlias = true }
            canvas.drawRRect(RRect.makeXYWH(left, top, w, h, pad), bg)
            val textPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
            lines.forEachIndexed { i, line ->
                val x = (frameWidth - widths[i]) / 2f
                val baseline = top + pad + i * lineHeight - metrics.ascent
                canvas.drawTextLine(TextLine.make(line, font), x, baseline, textPaint)
            }
            surface.makeImageSnapshot().use { image ->
                val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("Could not render subtitle")
                target.writeBytes(data.use { it.bytes })
            }
        }
    }

    fun renderBlank(target: File) {
        Surface.makeRasterN32Premul(max(2, frameWidth), max(2, frameHeight)).use { surface ->
            surface.canvas.clear(Color.TRANSPARENT)
            surface.makeImageSnapshot().use { image ->
                target.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes })
            }
        }
    }
}
