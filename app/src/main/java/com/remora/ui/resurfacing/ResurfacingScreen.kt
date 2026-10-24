package com.remora.ui.resurfacing

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.ui.gallery.CardDetailDialog
import com.remora.ui.theme.RemoraBg
import com.remora.ui.theme.RemoraBlue
import com.remora.ui.theme.RemoraBlueSoft
import com.remora.ui.theme.RemoraBorder
import com.remora.ui.theme.RemoraNavyDark
import com.remora.ui.theme.RemoraSubtleText

data class ResurfacingDisplayGroup(
    val group: ResurfaceGroup,
    val title: String,
    val items: List<ResurfacedItem>
)

enum class ResurfacingEmptyReason {
    NO_CARDS,
    LATEST_PENDING,
    LATEST_FAILED,
    NO_CONFIDENT_LINKS
}

data class ResurfacingUiState(
    val headerCard: MemoryCard?,
    val headerTitle: String,
    val items: List<ResurfacedItem>,
    val emptyReason: ResurfacingEmptyReason?
)

/**
 * Pure function partitioning items into the 3 resurfacing groups:
 * 1. SIMILAR -> "tương đồng"
 * 2. COMPLEMENTARY -> "bổ trợ"
 * 3. ACTIONABLE -> "có giá trị sử dụng"
 *
 * Only groups with non-empty items are returned.
 * Items within each group are sorted by confidence descending (then by candidateCard.id).
 */
fun groupForDisplay(items: List<ResurfacedItem>): List<ResurfacingDisplayGroup> {
    val groupConfigs = listOf(
        ResurfaceGroup.SIMILAR to "tương đồng",
        ResurfaceGroup.COMPLEMENTARY to "bổ trợ",
        ResurfaceGroup.ACTIONABLE to "có giá trị sử dụng"
    )

    return groupConfigs.mapNotNull { (group, title) ->
        val matching = items.filter { it.group == group }
        if (matching.isNotEmpty()) {
            val sorted = matching.sortedWith(
                compareByDescending<ResurfacedItem> { it.confidence }
                    .thenBy { it.candidateCard.id }
            )
            ResurfacingDisplayGroup(
                group = group,
                title = title,
                items = sorted
            )
        } else {
            null
        }
    }
}

/**
 * Resolves the active UI state for the Resurfacing tab cleanly distinguishing:
 * - NO_CARDS: No cards stored in the database yet.
 * - LATEST_PENDING: The latest card is currently being analyzed by AI.
 * - NO_CONFIDENT_LINKS: Card completed, but no confident links found (> 0.80).
 * - Active results: Source card and its corresponding items.
 */
fun resolveResurfacingUiState(
    sourceCard: MemoryCard?,
    items: List<ResurfacedItem>,
    latestCard: MemoryCard?
): ResurfacingUiState {
    val effectiveSourceCard = sourceCard

    // 1. No cards in database at all
    if (latestCard == null && effectiveSourceCard == null) {
        return ResurfacingUiState(
            headerCard = null,
            headerTitle = "chưa có bài học mới",
            items = emptyList(),
            emptyReason = ResurfacingEmptyReason.NO_CARDS
        )
    }

    // 2. The latest card in database is currently being analyzed
    if (latestCard != null && latestCard.status == ProcessingStatus.PENDING) {
        return ResurfacingUiState(
            headerCard = latestCard,
            headerTitle = resolveCardTitle(latestCard, "Đang xử lý..."),
            items = emptyList(),
            emptyReason = ResurfacingEmptyReason.LATEST_PENDING
        )
    }

    // 3. A newer card exists in database compared to sourceCard, but generated no results
    val isLatestNewerThanSource = latestCard != null && effectiveSourceCard != null && (
        latestCard.createdAt > effectiveSourceCard.createdAt ||
        (latestCard.createdAt == effectiveSourceCard.createdAt && latestCard.id > effectiveSourceCard.id)
    )

    if (isLatestNewerThanSource) {
        val emptyReason = if (latestCard.status == ProcessingStatus.FAILED) {
            ResurfacingEmptyReason.LATEST_FAILED
        } else {
            ResurfacingEmptyReason.NO_CONFIDENT_LINKS
        }
        return ResurfacingUiState(
            headerCard = latestCard,
            headerTitle = resolveCardTitle(latestCard, "Bài học vừa lưu"),
            items = emptyList(),
            emptyReason = emptyReason
        )
    }

    // 4. Source card available
    if (effectiveSourceCard != null) {
        val title = resolveCardTitle(effectiveSourceCard, "Bài học vừa lưu")
        return if (items.isNotEmpty()) {
            ResurfacingUiState(
                headerCard = effectiveSourceCard,
                headerTitle = title,
                items = items,
                emptyReason = null
            )
        } else {
            val emptyReason = if (effectiveSourceCard.status == ProcessingStatus.FAILED) {
                ResurfacingEmptyReason.LATEST_FAILED
            } else {
                ResurfacingEmptyReason.NO_CONFIDENT_LINKS
            }
            ResurfacingUiState(
                headerCard = effectiveSourceCard,
                headerTitle = title,
                items = emptyList(),
                emptyReason = emptyReason
            )
        }
    }

    // 5. Only latestCard is available (DONE/FAILED, but no resurfacing links)
    val title = if (latestCard != null) {
        resolveCardTitle(latestCard, "Bài học vừa lưu")
    } else {
        "Bài học vừa lưu"
    }

    val emptyReason = if (latestCard?.status == ProcessingStatus.FAILED) {
        ResurfacingEmptyReason.LATEST_FAILED
    } else {
        ResurfacingEmptyReason.NO_CONFIDENT_LINKS
    }

    return ResurfacingUiState(
        headerCard = latestCard,
        headerTitle = title,
        items = emptyList(),
        emptyReason = emptyReason
    )
}

private fun resolveCardTitle(card: MemoryCard, defaultFallback: String): String {
    val intent = card.userIntent
    return when {
        card.title.isNotBlank() && card.title != "Đang xử lý..." -> card.title
        !intent.isNullOrBlank() -> intent
        else -> card.title.ifBlank { defaultFallback }
    }
}

@Composable
fun ResurfacingScreen(
    items: List<ResurfacedItem> = emptyList(),
    sourceCard: MemoryCard? = null,
    latestCard: MemoryCard? = null,
    modifier: Modifier = Modifier
) {
    var selectedCard by remember { mutableStateOf<MemoryCard?>(null) }

    val uiState = remember(items, sourceCard, latestCard) {
        resolveResurfacingUiState(
            sourceCard = sourceCard,
            items = items,
            latestCard = latestCard
        )
    }

    val displayGroups = remember(uiState.items) {
        groupForDisplay(uiState.items)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RemoraBg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Header (03-resurface.png)
        Text(
            text = "vừa lưu  ·  gợi nhớ",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = RemoraSubtleText
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = uiState.headerTitle,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = RemoraNavyDark
        )

        Spacer(modifier = Modifier.height(4.dp))

        val subtitleText = when (uiState.emptyReason) {
            ResurfacingEmptyReason.NO_CARDS -> "chưa có ký ức nào"
            ResurfacingEmptyReason.LATEST_PENDING -> "đang phân tích liên kết tri thức..."
            ResurfacingEmptyReason.LATEST_FAILED -> "không thể phân tích bài học"
            ResurfacingEmptyReason.NO_CONFIDENT_LINKS -> "0 ký ức cũ liên quan tới nó"
            null -> "${uiState.items.size} ký ức cũ liên quan tới nó"
        }

        Text(
            text = subtitleText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = RemoraBlue
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Empty state or Grouped Resurfaced Items List
        if (uiState.emptyReason != null) {
            ResurfacingEmptyState(
                reason = uiState.emptyReason,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                displayGroups.forEachIndexed { groupIndex, group ->
                    item(key = "header_${group.group.name}") {
                        ResurfacingGroupHeader(
                            title = group.title,
                            count = group.items.size,
                            modifier = if (groupIndex > 0) Modifier.padding(top = 16.dp) else Modifier
                        )
                    }

                    itemsIndexed(
                        items = group.items,
                        key = { _, item -> item.candidateCard.id }
                    ) { index, item ->
                        val globalIndex = displayGroups.take(groupIndex).sumOf { it.items.size } + index + 1
                        ResurfacingItemRow(
                            index = globalIndex,
                            item = item,
                            onClick = { selectedCard = item.candidateCard }
                        )

                        if (index < group.items.size - 1) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = RemoraBorder.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    selectedCard?.let { card ->
        CardDetailDialog(
            card = card,
            onDismiss = { selectedCard = null }
        )
    }
}

@Composable
private fun ResurfacingGroupHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = RemoraBlue,
            letterSpacing = 0.5.sp
        )
        Text(
            text = "$count liên kết",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = RemoraSubtleText
        )
    }
}

@Composable
private fun ResurfacingEmptyState(
    reason: ResurfacingEmptyReason,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            when (reason) {
                ResurfacingEmptyReason.NO_CARDS -> {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = RemoraSubtleText,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "chưa có thẻ nào",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RemoraNavyDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "khi bạn lưu ảnh bài học mới, remora sẽ tự động tìm kiếm các liên kết tri thức cũ tại đây!",
                        fontSize = 14.sp,
                        color = RemoraSubtleText,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }

                ResurfacingEmptyReason.LATEST_PENDING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = RemoraBlue,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AI đang phân tích...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RemoraNavyDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "remora đang đọc nội dung ảnh và đối chiếu với kho tri thức của bạn...",
                        fontSize = 14.sp,
                        color = RemoraSubtleText,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }

                ResurfacingEmptyReason.LATEST_FAILED -> {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "xử lý bài học chưa thành công",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RemoraNavyDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "bài học này gặp lỗi trong quá trình phân tích. bạn có thể bấm thử lại ở màn hình dòng thời gian.",
                        fontSize = 14.sp,
                        color = RemoraSubtleText,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }

                ResurfacingEmptyReason.NO_CONFIDENT_LINKS -> {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = RemoraSubtleText,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "chưa tìm thấy liên kết đủ chắc",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RemoraNavyDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "remora chỉ gợi ý những bài học có độ tin cậy trên 80% để đảm bảo thông tin thực sự hữu ích.",
                        fontSize = 14.sp,
                        color = RemoraSubtleText,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ResurfacingItemRow(
    index: Int,
    item: ResurfacedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val hasExpandableContent = item.newInfo.isNotBlank() || item.benefit.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Number on the left (1, 2, 3)
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .padding(top = 2.dp)
            ) {
                Text(
                    text = "$index",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = RemoraBlue
                )
            }

            // Thumbnail Card
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(RemoraBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                if (item.candidateCard.imageUri.isNotBlank()) {
                    AsyncImage(
                        model = item.candidateCard.imageUri,
                        contentDescription = item.candidateCard.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "*",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = RemoraBlue
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Info: Title, Reason, and Expand Toggle
            Column(modifier = Modifier.weight(1f)) {
                // Candidate card title (preserves casing)
                Text(
                    text = item.candidateCard.title.ifBlank { "Thẻ liên quan #${item.candidateCard.id}" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = RemoraNavyDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Reason / Connection (preserves casing)
                if (item.reason.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.reason,
                        fontSize = 12.sp,
                        color = RemoraSubtleText,
                        lineHeight = 16.sp
                    )
                }

                // Expand/collapse toggle for new_info and benefit
                if (hasExpandableContent) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isExpanded) "thu gọn" else "chi tiết liên kết",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = RemoraBlue
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (isExpanded) "thu gọn" else "xem thêm",
                            tint = RemoraBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Expanded content: new_info & benefit
        if (hasExpandableContent && isExpanded) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = RemoraBlueSoft.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 42.dp, top = 8.dp, bottom = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Consume clicks inside the details container so tapping text doesn't open the card detail sheet
                    }
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (item.newInfo.isNotBlank()) {
                        Text(
                            text = "thông tin mới",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RemoraBlue
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.newInfo,
                            fontSize = 12.sp,
                            color = RemoraNavyDark,
                            lineHeight = 16.sp
                        )
                    }

                    if (item.benefit.isNotBlank()) {
                        if (item.newInfo.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Text(
                            text = "lợi ích học tập",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RemoraBlue
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.benefit,
                            fontSize = 12.sp,
                            color = RemoraNavyDark,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
