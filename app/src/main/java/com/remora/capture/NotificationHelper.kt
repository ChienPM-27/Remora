package com.remora.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.remora.MainActivity

import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.resurface.ResurfacedItem
import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale
import timber.log.Timber

data class ResurfacingAlertItem(
    val group: ResurfaceGroup,
    val candidateTitle: String,
    val reason: String,
    val confidence: Float = 0f
)

data class ResurfacingNotificationContent(
    val title: String,
    val lines: List<String>,
    val bigText: String
)

fun ResurfacedItem.toAlertItem(): ResurfacingAlertItem = ResurfacingAlertItem(
    group = group,
    candidateTitle = candidateCard.title,
    reason = reason,
    confidence = confidence
)

object NotificationHelper {

    const val CHANNEL_ID = "remora_capture_channel"
    const val RESURFACING_CHANNEL_ID = "remora_resurfacing_channel"
    const val STATUS_CHANNEL_ID = "remora_status_channel"
    const val NOTIFICATION_ID = 1001
    const val RESURFACING_NOTIFICATION_ID = 2001
    const val RESURFACING_REQUEST_CODE = 200

    const val ACTION_SAVE = "com.remora.action.SAVE"
    const val ACTION_SKIP = "com.remora.action.SKIP"
    const val ACTION_MUTE_1H = "com.remora.action.MUTE_1H"
    const val ACTION_PAUSE = "com.remora.action.PAUSE"

    const val EXTRA_IMAGE_URI = "extra_image_uri"
    const val EXTRA_USER_INTENT = "extra_user_intent"
    const val EXTRA_OPEN_SAVE_SHEET = "extra_open_save_sheet"
    const val EXTRA_OPEN_RESURFACING = "extra_open_resurfacing"
    const val EXTRA_NEW_CARD_ID = "extra_new_card_id"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Remora Screenshot Actions"
            val descriptionText = "Interactive notification for saving lecture screenshots"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            val resurfaceChannel = NotificationChannel(
                RESURFACING_CHANNEL_ID,
                "Remora Resurfacing Insights",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Proactive alerts when new card links to previous study knowledge"
            }
            notificationManager.createNotificationChannel(resurfaceChannel)

            val statusChannel = NotificationChannel(
                STATUS_CHANNEL_ID,
                "Remora Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent confirmations and processing status"
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(statusChannel)
        }
    }

    fun showCapturePrompt(context: Context, imageUri: Uri) {
        createNotificationChannel(context)

        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Tap on notification body -> Open Remora with Save layout directly
        val openSaveIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_IMAGE_URI, imageUri.toString())
            putExtra(EXTRA_OPEN_SAVE_SHEET, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(context, 100, openSaveIntent, piFlags)

        // Inline RemoteInput for user_intent
        val remoteInput = RemoteInput.Builder(EXTRA_USER_INTENT)
            .setLabel("Nhập ghi chú (VD: Ôn thi giữa kỳ)...")
            .build()

        val saveIntent = Intent(context, CaptureActionReceiver::class.java).apply {
            action = ACTION_SAVE
            putExtra(EXTRA_IMAGE_URI, imageUri.toString())
        }
        val savePendingIntent = PendingIntent.getBroadcast(context, 1, saveIntent, piFlags)

        val saveAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save,
            "Lưu",
            savePendingIntent
        ).addRemoteInput(remoteInput).build()

        val skipIntent = Intent(context, CaptureActionReceiver::class.java).apply {
            action = ACTION_SKIP
            putExtra(EXTRA_IMAGE_URI, imageUri.toString())
        }
        val skipPendingIntent = PendingIntent.getBroadcast(context, 2, skipIntent, piFlags)

        val mute1hIntent = Intent(context, CaptureActionReceiver::class.java).apply {
            action = ACTION_MUTE_1H
            putExtra(EXTRA_IMAGE_URI, imageUri.toString())
        }
        val mute1hPendingIntent = PendingIntent.getBroadcast(context, 3, mute1hIntent, piFlags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Phát hiện ảnh chụp bài giảng")
            .setContentText("Chạm để mở bảng lưu Remora hoặc nhập ghi chú")
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(saveAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Bỏ qua", skipPendingIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Tắt 1 giờ", mute1hPendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Truncates text safely up to [maxLength] characters without splitting
     * Vietnamese combined diacritics or UTF-16 surrogate pairs (e.g. emojis).
     * Normalizes output to NFC to guarantee canonical composition of Vietnamese characters.
     * Appends [ellipsis] if truncated.
     */
    fun truncateSafely(text: String, maxLength: Int = 80, ellipsis: String = "…"): String {
        if (text.length <= maxLength) return text
        if (maxLength <= 0) return ""
        if (maxLength <= ellipsis.length) {
            return ellipsis.take(maxLength)
        }

        val target = maxLength - ellipsis.length

        val iterator = BreakIterator.getCharacterInstance(Locale.forLanguageTag("vi"))
        iterator.setText(text)

        var end = iterator.preceding(target + 1)
        if (end == BreakIterator.DONE || end <= 0) {
            end = 0
        }

        // Never split surrogate pairs (e.g. emoji or astral unicode)
        if (end in 1 until text.length) {
            if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) {
                end--
            }
        }

        val rawTrimmed = text.substring(0, end).trimEnd()
        val trimmed = Normalizer.normalize(rawTrimmed, Normalizer.Form.NFC)
        return if (trimmed.isEmpty()) ellipsis.take(maxLength) else "$trimmed$ellipsis"
    }

    /**
     * Pure function selecting up to [maxLines] items for notification display.
     * Prioritizes the highest-confidence item from each distinct [ResurfaceGroup] first.
     * If fewer groups are present, fills the remaining slots with the highest-confidence
     * remaining candidates.
     */
    fun selectTopAlertItems(
        items: List<ResurfacingAlertItem>,
        maxLines: Int = 3
    ): List<ResurfacingAlertItem> {
        if (items.isEmpty() || maxLines <= 0) return emptyList()

        val itemsByGroup = items
            .groupBy { it.group }
            .mapValues { (_, list) -> list.sortedByDescending { it.confidence } }

        val topPerGroup = itemsByGroup.values
            .mapNotNull { it.firstOrNull() }
            .sortedByDescending { it.confidence }

        val selected = mutableListOf<ResurfacingAlertItem>()
        val remaining = items.toMutableList()

        for (topItem in topPerGroup) {
            if (selected.size < maxLines) {
                selected.add(topItem)
                remaining.remove(topItem)
            }
        }

        remaining.sortByDescending { it.confidence }
        while (selected.size < maxLines && remaining.isNotEmpty()) {
            selected.add(remaining.removeAt(0))
        }

        return selected.sortedByDescending { it.confidence }
    }

    /**
     * Pure function building display lines and BigTextStyle text for resurfacing notifications:
     * - First line is the source card title.
     * - Body contains at most [maxLines] lines formatted as "nhóm · tiêu đề — lý do (cắt ~80 ký tự)".
     * Replaces any internal newlines in titles and reason with spaces to guarantee single-line body layout.
     */
    fun buildResurfacingNotificationContent(
        sourceCardTitle: String,
        items: List<ResurfacingAlertItem>,
        maxLines: Int = 3
    ): ResurfacingNotificationContent {
        val sanitizedSourceTitle = sourceCardTitle.replace(Regex("[\\r\\n]+"), " ").trim()
        val cleanSourceTitle = truncateSafely(sanitizedSourceTitle, 80).ifBlank { "Bài học vừa lưu" }
        val selectedItems = selectTopAlertItems(items, maxLines)

        val lines = selectedItems.map { item ->
            val groupLabel = when (item.group) {
                ResurfaceGroup.SIMILAR -> "tương đồng"
                ResurfaceGroup.COMPLEMENTARY -> "bổ trợ"
                ResurfaceGroup.ACTIONABLE -> "có giá trị sử dụng"
            }
            val sanitizedCandidateTitle = item.candidateTitle.replace(Regex("[\\r\\n]+"), " ").trim()
            val candidateTitle = truncateSafely(sanitizedCandidateTitle, 80).ifBlank { "Thẻ liên quan" }
            val sanitizedReason = item.reason.replace(Regex("[\\r\\n]+"), " ").trim()
            val trimmedReason = truncateSafely(sanitizedReason, 80)
            if (trimmedReason.isNotBlank()) {
                "$groupLabel · $candidateTitle — $trimmedReason"
            } else {
                "$groupLabel · $candidateTitle"
            }
        }

        val bigText = if (lines.isEmpty()) {
            cleanSourceTitle
        } else {
            "$cleanSourceTitle\n" + lines.joinToString("\n")
        }

        return ResurfacingNotificationContent(
            title = cleanSourceTitle,
            lines = lines,
            bigText = bigText
        )
    }

    fun showResurfacingAlert(
        context: Context,
        sourceCardTitle: String,
        items: List<ResurfacingAlertItem>,
        newCardId: Long = -1L
    ) {
        if (items.isEmpty()) return

        createNotificationChannel(context)

        val content = buildResurfacingNotificationContent(sourceCardTitle, items)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val openResurfaceIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_OPEN_RESURFACING, true)
            if (newCardId > 0) {
                putExtra(EXTRA_NEW_CARD_ID, newCardId)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            RESURFACING_REQUEST_CODE,
            openResurfaceIntent,
            flags
        )

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle("Remora: Gợi nhớ kiến thức")
            .bigText(content.bigText)

        val collapsedText = content.lines.firstOrNull() ?: content.title

        val notification = NotificationCompat.Builder(context, RESURFACING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Remora: Gợi nhớ kiến thức")
            .setContentText(collapsedText)
            .setStyle(bigTextStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(RESURFACING_NOTIFICATION_ID, notification)

        // Privacy: Never log card title, candidate title, or reasons. Only log card ID and candidate count.
        Timber.i("Resurfacing alert notification posted for card #$newCardId with ${items.size} candidate links")
    }

    @JvmName("showResurfacingAlertForResurfacedItems")
    fun showResurfacingAlert(
        context: Context,
        sourceCardTitle: String,
        resurfacedItems: List<ResurfacedItem>,
        newCardId: Long = -1L
    ) {
        showResurfacingAlert(
            context = context,
            sourceCardTitle = sourceCardTitle,
            items = resurfacedItems.map { it.toAlertItem() },
            newCardId = newCardId
        )
    }

    fun showSaveSuccess(context: Context) {
        createNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Đã lưu vào Remora")
            .setContentText("AI đang phân tích bài học...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(1500L)
            .setAutoCancel(true)
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun dismissNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}

