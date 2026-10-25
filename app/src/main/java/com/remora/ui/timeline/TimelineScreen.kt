package com.remora.ui.timeline

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.ui.common.RemoraPill
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
    onImportImage: (Uri, String?) -> Unit,
    onBulkImport: (List<Uri>) -> Unit = {},
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCard by remember { mutableStateOf<MemoryCard?>(null) }
    var selectedSubject by remember { mutableStateOf("tất cả") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // One entry point: picking a single image keeps the intent-note dialog,
    // picking several goes straight to the bulk-import confirm flow.
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        when {
            uris.isEmpty() -> Unit
            uris.size == 1 -> pendingImportUri = uris.first()
            else -> onBulkImport(uris)
        }
    }

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

        // 1. Header with Title & Import Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todayDateStr,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = RemoraSubtleText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ký ức của bạn, được sắp xếp.",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = RemoraNavyDark
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            RemoraPill(
                text = "thêm ảnh",
                leadingIcon = Icons.Outlined.AddPhotoAlternate,
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                container = RemoraNavyDark,
                contentColor = Color.White,
                borderColor = null,
                fontSize = 13,
                verticalPadding = 8
            )
        }

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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = RemoraSubtleText,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "chưa có thẻ nào được lưu.\nhãy chọn ảnh bài giảng từ thư viện để remora ghi nhớ!",
                        fontSize = 14.sp,
                        color = RemoraSubtleText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(containerColor = RemoraNavyDark)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("chọn ảnh từ thư viện", fontSize = 14.sp, color = Color.White)
                    }
                }
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

    pendingImportUri?.let { uri ->
        ImportCardDialog(
            imageUri = uri,
            onDismiss = { pendingImportUri = null },
            onConfirm = { intentText ->
                onImportImage(uri, intentText)
                pendingImportUri = null
            }
        )
    }
}

@Composable
private fun ImportCardDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var intentText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Thêm thẻ bài học mới",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RemoraNavyDark
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RemoraBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Ảnh đã chọn",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Mục đích lưu của bạn (không bắt buộc):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RemoraSubtleText
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = intentText,
                    onValueChange = { intentText = it },
                    placeholder = {
                        Text(
                            text = "VD: bài tập tuần 3, công thức thi giữa kỳ...",
                            fontSize = 13.sp,
                            color = RemoraSubtleText.copy(alpha = 0.7f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3,
                    textStyle = TextStyle(fontSize = 14.sp, color = RemoraNavyDark)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(intentText.trim().ifBlank { null }) },
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = RemoraNavyDark)
            ) {
                Text("Lưu vào Remora", fontSize = 13.sp, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", fontSize = 13.sp, color = RemoraSubtleText)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left Column: Time label + circle indicator
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(64.dp)
        ) {
            Text(
                text = timeText,
                fontSize = 12.sp,
                color = RemoraSubtleText,
                fontWeight = FontWeight.Medium
            )
        }

        // Right Column: Timeline Card Content
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, RemoraBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Top Meta Row: Subject tag + Processing Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val subject = card.tags.subject
                    if (subject.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = RemoraBlueSoft
                        ) {
                            Text(
                                text = subject,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RemoraBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Status Badge
                    when (card.status) {
                        ProcessingStatus.PENDING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = RemoraBlue
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Đang xử lý...",
                                    fontSize = 11.sp,
                                    color = RemoraBlue
                                )
                            }
                        }
                        ProcessingStatus.FAILED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Lỗi xử lý",
                                    fontSize = 11.sp,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Thử lại",
                                    tint = RemoraBlue,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable(onClick = onRetry)
                                )
                            }
                        }
                        ProcessingStatus.DONE -> {
                            // Completed state
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Card Title
                Text(
                    text = card.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = RemoraNavyDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // User Intent Highlight
                if (!card.userIntent.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "\"${card.userIntent}\"",
                        fontSize = 13.sp,
                        color = RemoraBlue,
                        fontWeight = FontWeight.Medium
                    )
                }

                // AI Summary
                if (card.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = card.summary,
                        fontSize = 12.sp,
                        color = RemoraNavyDark.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )
                }

                // Thumbnail preview if available
                if (card.imageUri.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AsyncImage(
                        model = card.imageUri,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }
    }
}
