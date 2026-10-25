package com.remora.capture

/**
 * Simple in-memory tracker for app foreground status.
 * Updated in MainActivity onStart/onStop without adding any external dependencies.
 */
object AppForegroundTracker {
    @Volatile
    var isForeground: Boolean = false
}
