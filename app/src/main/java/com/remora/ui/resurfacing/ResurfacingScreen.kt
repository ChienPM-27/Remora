package com.remora.ui.resurfacing

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.ui.gallery.CardDetailDialog
import com.remora.ui.theme.RemoraBg
import com.remora.ui.theme.RemoraBlue
import com.remora.ui.theme.RemoraBlueSoft
import com.remora.ui.theme.RemoraBorder
import com.remora.ui.theme.RemoraNavyDark
import com.remora.ui.theme.RemoraSubtleText

@Composable
fun ResurfacingScreen(
    items: List<ResurfacedItem>,
    latestCard: MemoryCard? = null,
    modifier: Modifier = Modifier
) {
    var selectedCard by remember { mutableStateOf<MemoryCard?>(null) }

    val primaryTitle = remember(latestCard, items) {
        when {
            latestCard != null && latestCard.title.isNotBlank() -> latestCard.title.lowercase()
            items.isNotEmpty() -> items.first().candidateCard.title.ifBlank { "gợi nhớ" }.lowercase()
            else -> "chưa có bài học mới"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RemoraBg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Header (from 03-resurface.png)
        Text(
            text = "vừa lưu  ·  gợi nhớ",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = RemoraSubtleText
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = primaryTitle,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = RemoraNavyDark
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${items.size} ký ức cũ liên quan tới nó",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = RemoraBlue
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Resurfaced Items List (1, 2, 3...)
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "chưa có gợi nhớ nào.\nkhi bạn lưu ảnh mới, remora sẽ tự động tìm liên kết tri thức cũ tại đây!",
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
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(items) { index, item ->
                    ResurfacingNumberedRow(
                        index = index + 1,
                        item = item,
                        onClick = { selectedCard = item.candidateCard }
                    )

                    if (index < items.size - 1) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = RemoraBorder.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
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
private fun ResurfacingNumberedRow(
    index: Int,
    item: ResurfacedItem,
    onClick: () -> Unit
) {
    val groupLabel = when (item.group) {
        ResurfaceGroup.SIMILAR -> "tương đồng"
        ResurfaceGroup.COMPLEMENTARY -> "bổ trợ"
        ResurfaceGroup.ACTIONABLE -> "có giá trị sử dụng"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

        // Knowledge linkage info
        Column(modifier = Modifier.weight(1f)) {
            // Group label (tương đồng, bổ trợ, có giá trị sử dụng)
            Text(
                text = groupLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = RemoraBlue
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Candidate card title
            Text(
                text = item.candidateCard.title.ifBlank { "thẻ liên quan #${item.candidateCard.id}" }.lowercase(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = RemoraNavyDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Reason / Connection
            if (item.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.reason.lowercase(),
                    fontSize = 12.sp,
                    color = RemoraSubtleText,
                    lineHeight = 16.sp
                )
            }

            // Benefit highlight (blue background pill or text)
            if (item.benefit.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = RemoraBlueSoft
                ) {
                    Text(
                        text = item.benefit.lowercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RemoraBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
