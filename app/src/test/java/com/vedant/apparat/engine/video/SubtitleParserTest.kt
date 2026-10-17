package com.vedant.apparat.engine.video

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleParserTest {

    @Test
    fun parsesSrt() {
        val srt = "﻿1\r\n00:00:01,500 --> 00:00:03,000\r\n<i>Hello</i>\r\nworld\r\n\r\n2\r\n01:02:03,004 --> 01:02:04,000\r\n{\\an8}Top\r\n"
        val cues = SubtitleParser.parse(srt)
        assertEquals(2, cues.size)
        assertEquals(1_500_000L, cues[0].startUs)
        assertEquals(3_000_000L, cues[0].endUs)
        assertEquals("Hello\nworld", cues[0].text)
        assertEquals(((3600 + 120 + 3) * 1000L + 4) * 1000, cues[1].startUs)
        assertEquals("Top", cues[1].text)
    }

    @Test
    fun parsesVttWithShortTimestampsAndSettings() {
        val vtt = "WEBVTT\n\nintro\n00:05.250 --> 00:07.000 align:center\nHi there\n"
        val cues = SubtitleParser.parse(vtt)
        assertEquals(1, cues.size)
        assertEquals(5_250_000L, cues[0].startUs)
        assertEquals("Hi there", cues[0].text)
    }
}
