package com.remora.ui.resurfacing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.ui.common.formatFullDate
import com.remora.ui.theme.RemoraBg
import com.remora.ui.theme.RemoraBlue
import com.remora.ui.theme.RemoraBlueSoft
import com.remora.ui.theme.RemoraBorder
import com.remora.ui.theme.RemoraNavyDark
import com.remora.ui.theme.RemoraSubtleText

@Composable
fun ResurfacingScreen(
    batches: List<ResurfacingBatch>,
    onOpenCard: (Long) -> Unit,
    onRetryBatch: (Long) -> Unit,
    isRetrying: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RemoraBg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Gợi nhớ",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = RemoraNavyDark
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (batches.isEmpty()) "Kết nối những kiến thức bạn đã lưu"
                else "${batches.sumOf { it.items.size }} liên kết từ ${batches.size} ảnh đã phân tích",
            fontSize = 14.sp,
            color = RemoraSubtleText
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (batches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chưa có ảnh đã phân tích.\nLưu một ảnh học tập để tìm lại những kiến thức liên quan.",
                    fontSize = 14.sp,
                    color = RemoraSubtleText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(batches, key = { it.sourceCard.id }) { batch ->
                    ResurfacingSourceBatch(batch, onOpenCard, onRetryBatch, isRetrying)
                }
            }
        }
    }

}

@Composable
private fun ResurfacingSourceBatch(
    batch: ResurfacingBatch,
    onOpenCard: (Long) -> Unit,
    onRetryBatch: (Long) -> Unit,
    isRetrying: Boolean
) {
    val source = batch.sourceCard
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, RemoraBorder)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Ảnh nguồn", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = RemoraBlue)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClickLabel = "Mở ảnh nguồn", onClick = { onOpenCard(source.id) })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardThumbnail(source)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(source.title.ifBlank { "Ảnh đã lưu #${source.id}" }, fontSize = 17.sp,
                        fontWeight = FontWeight.Bold, color = RemoraNavyDark)
                    Text(formatFullDate(source.createdAt), fontSize = 12.sp, color = RemoraSubtleText)
                }
            }
            source.userIntent?.takeIf { it.isNotBlank() }?.let { note ->
                Text("Vì sao bạn lưu: $note", fontSize = 13.sp, color = RemoraSubtleText)
                Spacer(Modifier.height(8.dp))
            }

            when (batch.status) {
                ProcessingStatus.PENDING -> Text("Đang tìm liên kết. Remora sẽ tiếp tục khi có kết nối AI.",
                    fontSize = 14.sp, color = RemoraSubtleText, lineHeight = 20.sp)
                ProcessingStatus.FAILED -> {
                    Text("Chưa tạo được gợi nhớ. Kiểm tra kết nối AI trong Cài đặt rồi thử lại.",
                        fontSize = 14.sp, color = RemoraSubtleText, lineHeight = 20.sp)
                    OutlinedButton(onClick = { onRetryBatch(source.id) }, enabled = !isRetrying) {
                        Text("Thử lại gợi nhớ")
                    }
                }
                ProcessingStatus.DONE -> {
                    if (batch.items.isEmpty()) {
                        Text("Đã tìm kiếm, chưa có liên kết hữu ích.", fontSize = 14.sp, color = RemoraSubtleText)
                    } else {
                        ResurfaceGroup.entries.forEach { group ->
                            val groupItems = batch.items.filter { it.group == group }
                            if (groupItems.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(groupLabel(group), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RemoraBlue)
                                Spacer(Modifier.height(8.dp))
                                groupItems.forEachIndexed { index, item ->
                                    ResurfacingNumberedRow(index + 1, item, onClick = { onOpenCard(item.candidateCard.id) })
                                    if (index < groupItems.lastIndex) {
                                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = RemoraBorder)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun groupLabel(group: ResurfaceGroup) = when (group) {
    ResurfaceGroup.SIMILAR -> "Tương đồng"
    ResurfaceGroup.COMPLEMENTARY -> "Bổ trợ"
    ResurfaceGroup.ACTIONABLE -> "Có thể áp dụng"
}

@Composable
private fun CardThumbnail(card: MemoryCard) {
    Box(Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)).background(RemoraBlueSoft),
        contentAlignment = Alignment.Center) {
        if (card.imageUri.isNotBlank()) {
            AsyncImage(model = card.imageUri, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        } else {
            Text("*", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = RemoraBlue)
        }
    }
}

@Composable
private fun ResurfacingNumberedRow(
    index: Int,
    item: ResurfacedItem,
    onClick: () -> Unit
) {
    val relationLabel = when (item.relation) {
        ResurfaceRelation.DUPLICATE -> "Trùng nội dung"
        ResurfaceRelation.SUPPLEMENT -> "Bổ sung kiến thức"
        ResurfaceRelation.EXPLANATION -> "Giải thích"
        ResurfaceRelation.CONTRADICTION -> "Đối chiếu điểm khác nhau"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Mở ảnh liên quan", onClick = onClick)
            .padding(vertical = 4.dp),
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

        CardThumbnail(item.candidateCard)

        Spacer(modifier = Modifier.width(14.dp))

        // Knowledge linkage info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = relationLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = RemoraBlue
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Candidate card title
            Text(
                text = item.candidateCard.title.ifBlank { "Ảnh liên quan #${item.candidateCard.id}" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = RemoraNavyDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Reason / Connection
            if (item.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Vì sao liên quan: ${item.reason}",
                    fontSize = 12.sp,
                    color = RemoraSubtleText,
                    lineHeight = 16.sp
                )
            }

            if (item.benefit.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = RemoraBlueSoft
                ) {
                    Text(
                        text = "Lợi ích học tập: ${item.benefit}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RemoraBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
