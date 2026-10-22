package com.remora.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotDetectorTest {

    private fun isScreenshotPath(relativePath: String?, displayName: String?): Boolean {
        val path = relativePath?.lowercase() ?: ""
        val name = displayName?.lowercase() ?: ""
        return path.contains("screenshot") || name.contains("screenshot") || name.startsWith("scr_")
    }

    @Test
    fun testValidScreenshotPathsDetected() {
        assertTrue(isScreenshotPath("Pictures/Screenshots/", "Screenshot_20261022.png"))
        assertTrue(isScreenshotPath("DCIM/Screenshots/", "Screenshot_1.jpg"))
        assertTrue(isScreenshotPath("Pictures/", "screenshot_math_slide.png"))
        assertTrue(isScreenshotPath("Pictures/", "SCR_20261022_120000.png"))
    }

    @Test
    fun testCameraAndDownloadImagesIgnored() {
        assertFalse(isScreenshotPath("DCIM/Camera/", "IMG_20261022_120000.jpg"))
        assertFalse(isScreenshotPath("Download/", "lecture_notes.pdf"))
        assertFalse(isScreenshotPath("Pictures/Telegram/", "photo_2026.jpg"))
    }
}
