package com.remora.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotDetectorTest {

    private fun isScreenshotPath(relativePath: String?, displayName: String?): Boolean {
        return ScreenshotObserver.isScreenshot(
            path = relativePath.orEmpty(),
            name = displayName.orEmpty()
        )
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
        assertFalse(isScreenshotPath("Pictures/", "holiday_photo.jpg"))
    }
}
