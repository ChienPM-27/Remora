package com.remora.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.ui.gallery.CardDetailDialog
import com.remora.ui.theme.RemoraBg
import com.remora.ui.theme.RemoraBlue
import com.remora.ui.theme.RemoraBlueSoft
import com.remora.ui.theme.RemoraBorder
import com.remora.ui.theme.RemoraNavyDark
import com.remora.ui.theme.RemoraSubtleText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(
    cards: List<MemoryCard>,
    onRetryCard: (MemoryCard) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCard by remember { mutableStateOf<MemoryCard?>(null) }
    var selectedSubject by remember { mutableStateOf("tất cả") }

    // Dynamic list of subjects for filter pills
    val subjects = remember(cards) {
        val list = mutableListOf("tất cả")
        val extracted = cards.map { it.tags.subject.trim().lowercase() }
            .filter { it.isNotBlank() && it != "chưa phân loại" }
            .distinct()
        list.addAll(extracted)
        list
    }

    val filteredCards = remember(cards, selectedSubject) {
        if (selectedSubject == "tất cả") {
            cards
        } else {
            cards.filter { it.tags.subject.trim().lowercase() == selectedSubject }
        }
    }

    val todayDateStr = remember {
        val sdf = SimpleDateFormat("MMM d · 'hôm nay'", Locale.getDefault())
        sdf.format(Date()).lowercase()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RemoraBg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Header (Date + Title from 01-timeline.png)
        Text(
            text = todayDateStr,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = RemoraSubtleText
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "ký ức của bạn, được sắp xếp.",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = RemoraNavyDark
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Search Quick-Bar (Pill shaped, tap to jump to search)
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = Color.White,
            border = BorderStroke(1.dp, RemoraBorder),
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clickable(onClick = onNavigateToSearch)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Tìm kiếm",
                    tint = RemoraSubtleText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "hỏi bằng lời của bạn...",
                    fontSize = 14.sp,
                    color = RemoraSubtleText.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Filter Chips Row (Scrollable horizontally)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            subjects.forEach { subject ->
                val isSelected = subject == selectedSubject
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = if (isSelected) RemoraNavyDark else Color.White,
                    border = if (isSelected) null else BorderStroke(1.dp, RemoraBorder),
                    modifier = Modifier.clickable { selectedSubject = subject }
                ) {
                    Text(
                        text = subject,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White else RemoraNavyDark,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Timeline List with time column on left
        if (filteredCards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "chưa có thẻ nào được lưu.\nhãy chụp ảnh slide bài giảng để remora ghi nhớ!",
                    fontSize = 14.sp,
                    color = RemoraSubtleText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                items(filteredCards, key = { it.id }) { card ->
                    TimelineCardRow(
                        card = card,
                        onClick = { selectedCard = card },
                        onRetry = { onRetryCard(card) }
                    )
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
private fun TimelineCardRow(
    card: MemoryCard,
    onClick: () -> Unit,
    onRetry: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("h:mma", Locale.US) }
    val timeText = remember(card.createdAt) {
        timeFormatter.format(Date(card.createdAt)).lowercase()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top
    ) {
        // Left Column: Time label
        Box(
            modifier = Modifier
                .width(62.dp)
                .padding(top = 10.dp)
        ) {
            Text(
                text = timeText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = RemoraSubtleText
            )
        }

        // Right Column: Card container
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Card Thumbnail Preview
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RemoraBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    if (card.imageUri.isNotBlank()) {
                        AsyncImage(
                            model = card.imageUri,
                            contentDescription = card.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "#",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = RemoraBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Text Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (card.title.isNotBlank()) card.title.lowercase() else "thẻ học mới lưu",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = RemoraNavyDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (card.summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = card.summary.lowercase(),
                            fontSize = 12.sp,
                            color = RemoraSubtleText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // User Intent Badge (Blue from 01-timeline.png)
                        val userIntent = card.userIntent
                        if (!userIntent.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = RemoraBlueSoft
                            ) {
                                Text(
                                    text = userIntent.lowercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = RemoraBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Subject hashtag
                        if (card.tags.subject.isNotBlank()) {
                            Text(
                                text = "#${card.tags.subject.lowercase()}",
                                fontSize = 11.sp,
                                color = RemoraSubtleText
                            )
                        }

                        // Status indicators
                        when (card.status) {
                            ProcessingStatus.PENDING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = RemoraBlue
                                )
                            }
                            ProcessingStatus.FAILED -> {
                                Button(
                                    onClick = onRetry,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Thử lại",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("thử lại", fontSize = 10.sp, color = Color.White)
                                }
                            }
                            ProcessingStatus.DONE -> Unit
                        }
                    }
                }
            }
        }
    }
}
