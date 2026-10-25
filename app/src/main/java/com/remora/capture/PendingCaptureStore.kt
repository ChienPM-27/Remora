package com.remora.capture

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.TimeUnit

@Serializable
data class PendingCapture(
    val uriString: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Pure functions for managing the pending capture queue.
 * Separated for deterministic JVM unit testing without Android runtime dependencies.
 */
object PendingCaptureQueue {
    const val MAX_ITEMS = 5
    val TTL_MS = TimeUnit.HOURS.toMillis(24) // 24 hours

    /**
     * Adds an item to the list in an idempotent manner.
     * - If [uriString] already exists:
     *     - If [prioritize] is true, moves it to the front and updates its timestamp to [now].
     *     - Otherwise, leaves the list unchanged.
     * - If [uriString] is new: prepends it to the list.
     * Prunes expired items (older than [ttlMs]) and ensures at most [maxItems] are retained (dropping oldest).
     */
    fun add(
        current: List<PendingCapture>,
        uriString: String,
        now: Long = System.currentTimeMillis(),
        prioritize: Boolean = false,
        maxItems: Int = MAX_ITEMS,
        ttlMs: Long = TTL_MS
    ): List<PendingCapture> {
        val nonExpired = pruneExpired(current, now, ttlMs)
        val existingIndex = nonExpired.indexOfFirst { it.uriString == uriString }

        val listWithItem = when {
            existingIndex != -1 -> {
                if (prioritize) {
                    val existing = nonExpired[existingIndex]
                    val mutable = nonExpired.toMutableList()
                    mutable.removeAt(existingIndex)
                    mutable.add(0, existing.copy(timestamp = now))
                    mutable
                } else {
                    nonExpired
                }
            }
            else -> {
                listOf(PendingCapture(uriString, now)) + nonExpired
            }
        }

        return if (listWithItem.size > maxItems) {
            listWithItem.take(maxItems)
        } else {
            listWithItem
        }
    }

    /**
     * Removes the pending capture with the given [uriString].
     */
    fun resolve(
        current: List<PendingCapture>,
        uriString: String
    ): List<PendingCapture> {
        return current.filterNot { it.uriString == uriString }
    }

    /**
     * Filters out items older than [ttlMs] milliseconds relative to [now].
     */
    fun pruneExpired(
        current: List<PendingCapture>,
        now: Long = System.currentTimeMillis(),
        ttlMs: Long = TTL_MS
    ): List<PendingCapture> {
        return current.filter { (now - it.timestamp) in 0..ttlMs }
    }

    /**
     * Finds the next pending URI to prompt the user for,
     * skipping any URI that already has a card in the database according to [hasCardInDb].
     */
    fun nextPromptUri(
        current: List<PendingCapture>,
        hasCardInDb: (String) -> Boolean
    ): String? {
        return current.firstOrNull { !hasCardInDb(it.uriString) }?.uriString
    }
}

/**
 * SharedPreferences-backed store for pending captures.
 * Exposes a [StateFlow] so UI can reactively display the SavePromptSheet.
 */
class PendingCaptureStore(
    private val prefs: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    private val _pending = MutableStateFlow<List<PendingCapture>>(emptyList())
    val pending: StateFlow<List<PendingCapture>> = _pending.asStateFlow()

    init {
        loadFromPrefs()
    }

    @Synchronized
    fun markPending(
        uri: Uri,
        now: Long = System.currentTimeMillis(),
        prioritize: Boolean = false
    ) {
        markPending(uri.toString(), now, prioritize)
    }

    @Synchronized
    fun markPending(
        uriString: String,
        now: Long = System.currentTimeMillis(),
        prioritize: Boolean = false
    ) {
        val updated = PendingCaptureQueue.add(_pending.value, uriString, now, prioritize)
        _pending.value = updated
        saveToPrefs(updated)
    }

    @Synchronized
    fun resolve(uri: Uri) {
        resolve(uri.toString())
    }

    @Synchronized
    fun resolve(uriString: String) {
        val updated = PendingCaptureQueue.resolve(_pending.value, uriString)
        _pending.value = updated
        saveToPrefs(updated)
    }

    @Synchronized
    fun prune(now: Long = System.currentTimeMillis()) {
        val updated = PendingCaptureQueue.pruneExpired(_pending.value, now)
        _pending.value = updated
        saveToPrefs(updated)
    }

    fun getPendingList(): List<PendingCapture> = _pending.value

    @Synchronized
    fun clearAll() {
        _pending.value = emptyList()
        prefs.edit().remove(KEY_PENDING_LIST).apply()
    }

    private fun saveToPrefs(list: List<PendingCapture>) {
        val encoded = try {
            json.encodeToString(list)
        } catch (e: Exception) {
            Timber.e(e, "Failed to serialize pending captures")
            "[]"
        }
        prefs.edit().putString(KEY_PENDING_LIST, encoded).apply()
    }

    private fun loadFromPrefs() {
        val raw = prefs.getString(KEY_PENDING_LIST, null)
        if (!raw.isNullOrBlank()) {
            val list = try {
                json.decodeFromString<List<PendingCapture>>(raw)
            } catch (e: Exception) {
                Timber.e(e, "Failed to deserialize pending captures")
                emptyList()
            }
            _pending.value = PendingCaptureQueue.pruneExpired(list, System.currentTimeMillis())
        }
    }

    companion object {
        private const val PREFS_NAME = "remora_pending_captures"
        private const val KEY_PENDING_LIST = "pending_list_json"

        @Volatile
        private var instance: PendingCaptureStore? = null

        fun getInstance(context: Context): PendingCaptureStore {
            return instance ?: synchronized(this) {
                instance ?: PendingCaptureStore(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }
}
