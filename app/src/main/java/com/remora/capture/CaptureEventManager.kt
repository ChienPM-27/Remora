package com.remora.capture

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CaptureEventManager {
    private val _screenshotEvents = MutableSharedFlow<Uri>(extraBufferCapacity = 5)
    val screenshotEvents: SharedFlow<Uri> = _screenshotEvents.asSharedFlow()

    fun emitScreenshot(uri: Uri) {
        _screenshotEvents.tryEmit(uri)
    }
}
