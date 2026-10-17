package com.omniforge.apparat.core.util

import com.omniforge.apparat.feature.pdf.splitPartName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class OutputNamingTest {

    @Test
    fun `folder names can't escape Downloads or break on a PC`() {
        assertEquals("Work", OutputSaver.sanitizeFolderName("Work"))
        assertEquals("x", OutputSaver.sanitizeFolderName("../x"))
        assertEquals("a b", OutputSaver.sanitizeFolderName("a/b"))
        assertEquals("Scans 2026", OutputSaver.sanitizeFolderName("  Scans:  2026  "))
        assertEquals("Bills", OutputSaver.sanitizeFolderName("Bills..."))
        assertEquals(OutputSaver.DEFAULT_FOLDER, OutputSaver.sanitizeFolderName("   "))
        assertEquals(OutputSaver.DEFAULT_FOLDER, OutputSaver.sanitizeFolderName("\\/:*?\"<>|"))
        assertEquals(60, OutputSaver.sanitizeFolderName("x".repeat(100)).length)
    }

    @Test
    fun `output names follow the produced file, not the screen state`() {
        assertEquals("song_extracted.m4a", FileUtils.outputName("song.mp4", "audio", "_extracted", File("extracted_audio_1.m4a")))
        assertEquals("song_extracted.wav", FileUtils.outputName("song.mp4", "audio", "_extracted", File("extracted_audio_1.wav")))
        assertEquals("report_compressed.pdf", FileUtils.outputName("report.pdf", "document", "_compressed", File("compressed_9.pdf")))
        assertEquals("document_compressed.pdf", FileUtils.outputName(null, "document", "_compressed", File("compressed_9.pdf")))
        assertEquals("a.b_clean.png", FileUtils.outputName("a.b.heic", "photo", "_clean", File("clean_exif_1.png")))
    }

    @Test
    fun `mime types come from the file extension`() {
        assertEquals("application/pdf", FileUtils.mimeForFile(File("x.pdf")))
        assertEquals("audio/wav", FileUtils.mimeForFile(File("x.wav")))
        assertEquals("audio/mp4", FileUtils.mimeForFile(File("x.m4a")))
        assertEquals("image/jpeg", FileUtils.mimeForFile(File("x.JPG")))
    }

    @Test
    fun `split parts are named after their pages`() {
        assertEquals("bank_pages_1-3.pdf", splitPartName("bank.pdf", File("part_1_p1_p3_1700000000.pdf"), 0))
        assertEquals("bank_page_7.pdf", splitPartName("bank.pdf", File("part_2_p7_p7_1700000000.pdf"), 1))
        assertEquals("document_part_3.pdf", splitPartName(null, File("weird.pdf"), 2))
    }
}
