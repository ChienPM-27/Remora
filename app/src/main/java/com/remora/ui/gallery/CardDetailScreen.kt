@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.remora.ui.gallery

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.ui.common.RemoraPill
import com.remora.ui.common.formatFullDate
import com.remora.ui.common.icon
import com.remora.ui.common.label
import com.remora.ui.theme.RemoraBg
import com.remora.ui.theme.RemoraBlue
import com.remora.ui.theme.RemoraBlueSoft
import com.remora.ui.theme.RemoraNavyDark
import com.remora.ui.theme.RemoraSubtleText
import kotlinx.coroutines.launch

private const val OCR_COLLAPSED_LINES = 6
private const val OCR_TOGGLE_MIN_CHARS = 240

/** Shared detail sheet; the caller supplies the current card from the library flow. */
@Composable
fun CardDetailDialog(
    card: MemoryCard,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    actionInProgress: Boolean = false,
    actionError: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable(card.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RemoraBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        CardDetailContent(
            card = card,
            onClose = {
                scope.launch { sheetState.hide(); onDismiss() }
            },
            onRetry = onRetry,
            onRequestDelete = { confirmDelete = true },
            actionInProgress = actionInProgress,
            actionError = actionError
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xóa thẻ này?") },
            text = { Text("Thẻ, các liên kết gợi nhớ và bản sao ảnh trong Remora sẽ bị xóa. Thao tác không thể hoàn tác. Ảnh gốc trong thư viện điện thoại được giữ lại.") },
            confirmButton = {
                TextButton(enabled = !actionInProgress, onClick = { confirmDelete = false; onDelete() }) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Giữ lại") } }
        )
    }
}

@Composable
private fun CardDetailContent(
    card: MemoryCard,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onRequestDelete: () -> Unit,
    actionInProgress: Boolean,
    actionError: String?
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var imageExpanded by remember(card.id) { mutableStateOf(false) }
    var ocrExpanded by remember(card.id) { mutableStateOf(false) }

    fun copy(label: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "Đã sao chép $label", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        // 1. Original screenshot (tap to enlarge) — the old dialog never showed the image at all.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(RemoraBlueSoft)
                .animateContentSize()
                .clickable { imageExpanded = !imageExpanded }
        ) {
            if (card.imageUri.isNotBlank()) {
                AsyncImage(
                    model = card.imageUri,
                    contentDescription = card.title,
                    contentScale = if (imageExpanded) ContentScale.Fit else ContentScale.Crop,
                    modifier = if (imageExpanded) {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    }
                )
                if (!imageExpanded) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Outlined.ZoomOutMap, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("chạm để phóng to", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Không có ảnh gốc", fontSize = 13.sp, color = RemoraSubtleText)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Meta: subject · type · saved at
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RemoraPill(
                text = card.tags.subject,
                container = RemoraBlueSoft,
                contentColor = RemoraBlue,
                borderColor = null,
                fontWeight = FontWeight.SemiBold
            )
            RemoraPill(text = card.contentType.label(), leadingIcon = card.contentType.icon())
            RemoraPill(text = formatFullDate(card.createdAt), leadingIcon = Icons.Outlined.Schedule)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Title
        Text(
            text = card.title.ifBlank { "Chưa có tiêu đề" },
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            color = RemoraNavyDark
        )

        // 4. Topics as compact tags (the old AssistChips rendered as large button-like blocks)
        if (card.tags.topics.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                card.tags.topics.forEach { topic ->
                    RemoraPill(
                        text = "#$topic",
                        container = Color.White,
                        contentColor = RemoraSubtleText,
                        fontSize = 11,
                        verticalPadding = 4
                    )
                }
            }
        }

        // Processing state, when the AI part is not finished
        if (card.status != ProcessingStatus.DONE) {
            Spacer(modifier = Modifier.height(16.dp))
            StatusNotice(card)
            if (card.status == ProcessingStatus.FAILED) {
                Button(onClick = onRetry, enabled = !actionInProgress, modifier = Modifier.fillMaxWidth()) {
                    Text("Thử lại")
                }
            }
        }
        if (actionInProgress || actionError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = actionError ?: "Đang cập nhật thẻ…",
                color = if (actionError != null) MaterialTheme.colorScheme.error else RemoraSubtleText,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        // 5. Why the user saved it
        if (!card.userIntent.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(18.dp))
            IntentBlock(card.userIntent.orEmpty())
        }

        // 6. AI summary
        if (card.summary.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = RemoraBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("tóm tắt AI", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RemoraSubtleText)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(card.summary, fontSize = 15.sp, lineHeight = 23.sp, color = RemoraNavyDark)
            }
        }

        // 7. OCR text, collapsed by default (it was an unbounded wall of text in the old dialog)
        if (card.ocrText.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "văn bản trong ảnh",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RemoraSubtleText,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { copy("văn bản", card.ocrText) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = RemoraBlue, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("sao chép", fontSize = 12.sp, color = RemoraBlue)
                    }
                }
                Text(
                    text = card.ocrText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.Monospace,
                    color = RemoraNavyDark.copy(alpha = 0.8f),
                    maxLines = if (ocrExpanded) Int.MAX_VALUE else OCR_COLLAPSED_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.animateContentSize()
                )
                if (card.ocrText.length > OCR_TOGGLE_MIN_CHARS) {
                    TextButton(onClick = { ocrExpanded = !ocrExpanded }) {
                        Text(if (ocrExpanded) "thu gọn" else "xem toàn bộ", fontSize = 12.sp, color = RemoraBlue)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 8. Actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { copy("tóm tắt", card.summary) },
                enabled = card.summary.isNotBlank(),
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("sao chép tóm tắt", fontSize = 13.sp, color = RemoraNavyDark)
            }
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = RemoraNavyDark),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("đóng", fontSize = 14.sp, color = Color.White)
            }
        }
        OutlinedButton(
            onClick = onRequestDelete,
            enabled = !actionInProgress,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Xóa thẻ", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun IntentBlock(intent: String) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(percent = 50))
                .background(RemoraBlue)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("vì sao bạn lưu ảnh này", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RemoraSubtleText)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = intent,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                color = RemoraBlue
            )
        }
    }
}

@Composable
private fun StatusNotice(card: MemoryCard) {
    val failed = card.status == ProcessingStatus.FAILED
    val bg = if (failed) Color(0xFFFEE2E2) else RemoraBlueSoft
    val fg = if (failed) Color(0xFF991B1B) else RemoraBlue
    Surface(shape = RoundedCornerShape(14.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Icon(
                imageVector = if (failed) Icons.Outlined.ErrorOutline else Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (failed) when (card.errorCode) {
                    "SIGN_IN_REQUIRED" -> "Hãy đăng nhập AI trong Cài đặt rồi bấm Thử lại."
                    "UNREADABLE_IMAGE" -> "Không đọc được bản sao ảnh. Bạn vẫn có thể xem nội dung đã lưu hoặc xóa thẻ."
                    else -> "Chưa xử lý được ảnh. Bấm Thử lại để tiếp tục; nội dung đã lưu được giữ nguyên."
                } else "AI đang đọc và tóm tắt ảnh này…",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = fg
            )
        }
    }
}
