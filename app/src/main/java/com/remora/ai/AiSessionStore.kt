package com.remora.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AiSession(val endpoint: String, val token: String? = null, val expiresAt: Long = 0) {
    fun isValid(now: Long = System.currentTimeMillis()) = !token.isNullOrBlank() && expiresAt > now
}

interface SessionStore {
    val state: StateFlow<AiSession>
    fun save(session: AiSession)
}

class MemorySessionStore(initial: AiSession) : SessionStore {
    override val state = MutableStateFlow(initial)
    override fun save(session: AiSession) { state.value = session }
}

/** Stores only short-lived authorization in private, backup-disabled app storage. Never a password. */
class AiSessionStore(context: Context, defaultEndpoint: String) : SessionStore {
    private val prefs = context.getSharedPreferences("ai_session", Context.MODE_PRIVATE)
    override val state = MutableStateFlow(AiSession(
        prefs.getString("endpoint", defaultEndpoint) ?: defaultEndpoint,
        prefs.getString("token", null), prefs.getLong("expires_at", 0)
    ))
    override fun save(session: AiSession) {
        prefs.edit().putString("endpoint", session.endpoint).putString("token", session.token)
            .putLong("expires_at", session.expiresAt).apply()
        state.value = session
    }
}