@file:OptIn(ExperimentalLayoutApi::class)

package com.remora.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.remora.core.model.entity.ContentType
import com.remora.search.MatchKind
import com.remora.search.SearchOutcome
import com.remora.search.SearchResultCard
import com.remora.search.Snippets
import com.remora.ui.common.RemoraPill
import com.remora.ui.common.label
import com.remora.ui.common.relativeTime
import com.remora.ui.theme.RemoraBg
import com.remora.ui.theme.RemoraBlue
import com.remora.ui.theme.RemoraBlueBadge
import com.remora.ui.theme.RemoraBlueSoft
import com.remora.ui.theme.RemoraBorder
import com.remora.ui.theme.RemoraNavyDark
import com.remora.ui.theme.RemoraSubtleText

private val SUGGESTIONS = listOf(
    "pttk",
    "slide phân phối chuẩn hôm bữa",
    "bài tập tuần trước",
    "đoạn code đệ quy"
)

@Composable
fun SearchScreen(
    query: String,
    uiState: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onOpenCard: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isSearching: Boolean = false,
    recentQueries: List<String> = emptyList(),
    indexedCount: Int = 0,
    onSubmit: () -> Unit = {}
) {
    var typeFilter by remember { mutableStateOf<ContentType?>(null) }

    val outcome: SearchOutcome? = when (uiState) {
        is SearchUiState.Success -> uiState.outcome
        is SearchUiState.Empty -> uiState.outcome
        else -> null
    }
    // A new result set invalidates the type filter chosen for the previous one.
    LaunchedEffect(outcome) { typeFilter = null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RemoraBg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "tìm kiếm",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = RemoraSubtleText
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "tìm lại thứ bạn đã lưu",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = RemoraNavyDark
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (indexedCount > 0) "$indexedCount thẻ sẵn sàng tìm theo ý nghĩa"
            else "chưa có thẻ sẵn sàng tìm theo ý nghĩa",
            fontSize = 13.sp,
            color = RemoraSubtleText
        )

        Spacer(modifier = Modifier.height(14.dp))

        SearchField(
            query = query,
            isSearching = isSearching,
            onQueryChanged = onQueryChanged,
            onSubmit = onSubmit
        )

        // How Remora understood the query: only real, derived values (no hardcoded chips).
        if (outcome != null && (outcome.timeLabel != null || outcome.expansions.isNotEmpty())) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                outcome.timeLabel?.let { label ->
                    RemoraPill(
                        text = label,
                        container = RemoraNavyDark,
                        contentColor = Color.White,
                        borderColor = null,
                        leadingIcon = Icons.Outlined.Schedule
                    )
                }
                outcome.expansions.forEach { (abbr, full) ->
                    RemoraPill(
                        text = "$abbr = $full",
                        container = RemoraBlueSoft,
                        contentColor = RemoraBlue,
                        borderColor = null,
                        leadingIcon = Icons.Outlined.AutoAwesome
                    )
                }
            }
        }

        if (outcome != null && !outcome.semanticAvailable) {
            Spacer(modifier = Modifier.height(12.dp))
            InfoBanner(
                icon = Icons.Outlined.CloudOff,
                text = "Không kết nối được AI, đang tìm theo từ khóa trên máy. Kết quả theo ý nghĩa sẽ có lại khi có mạng."
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (uiState) {
            is SearchUiState.Idle -> IdleContent(
                recent = recentQueries,
                onPick = { text ->
                    onQueryChanged(text)
                    onSubmit()
                }
            )

            is SearchUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RemoraBlue, strokeWidth = 2.dp)
            }

            is SearchUiState.Empty -> CenteredMessage(
                icon = Icons.Outlined.SearchOff,
                title = "chưa có thẻ nào khớp",
                body = when {
                    indexedCount == 0 -> "Bạn chưa lưu ảnh nào. Chụp màn hình bài học để Remora ghi nhớ."
                    uiState.outcome.timeLabel != null ->
                        "Không có thẻ nào trong khoảng \"${uiState.outcome.timeLabel}\". Thử bỏ mốc thời gian hoặc đổi cách diễn đạt."
                    else -> "Thử từ khóa ngắn hơn, từ viết tắt (pttk, xstk…) hoặc mô tả nội dung bằng lời của bạn."
                },
                actionLabel = "xóa tìm kiếm",
                onAction = { onQueryChanged("") },
                modifier = Modifier.weight(1f)
            )

            is SearchUiState.Error -> CenteredMessage(
                icon = Icons.Outlined.ErrorOutline,
                title = "có lỗi khi tìm kiếm",
                body = uiState.message,
                actionLabel = "thử lại",
                onAction = onSubmit,
                iconTint = Color(0xFFEF4444),
                modifier = Modifier.weight(1f)
            )

            is SearchUiState.Success -> {
                val results = uiState.outcome.results
                val types = results.map { it.card.contentType }.distinct()

                if (types.size > 1) {
                    val counts = results.groupingBy { it.card.contentType }.eachCount()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterPill("tất cả · ${results.size}", typeFilter == null) { typeFilter = null }
                        types.forEach { type ->
                            FilterPill("${type.label()} · ${counts[type]}", typeFilter == type) { typeFilter = type }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val shown = typeFilter?.let { f -> results.filter { it.card.contentType == f } } ?: results

                Text(
                    text = when {
                        uiState.outcome.results.all { it.matchKind == MatchKind.TIME } ->
                            "${shown.size} kết quả · theo thời gian"
                        uiState.outcome.semanticAvailable -> "${shown.size} kết quả · khớp từ khóa và ý nghĩa"
                        else -> "${shown.size} kết quả · khớp từ khóa"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = RemoraSubtleText
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shown, key = { it.card.id }) { item ->
                        ResultCard(
                            item = item,
                            highlightTerms = uiState.outcome.highlightTerms,
                            onClick = { onOpenCard(item.card.id) }
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun SearchField(
    query: String,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = Color.White,
        border = BorderStroke(1.dp, RemoraBorder),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = RemoraSubtleText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                textStyle = TextStyle(fontSize = 15.sp, color = RemoraNavyDark),
                cursorBrush = SolidColor(RemoraBlue),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSubmit()
                    focusManager.clearFocus()
                }),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "hỏi bằng lời của bạn…",
                                fontSize = 15.sp,
                                color = RemoraSubtleText.copy(alpha = 0.8f)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (isSearching) {
                CircularProgressIndicator(
                    color = RemoraBlue,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(18.dp)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Xóa",
                        tint = RemoraSubtleText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    RemoraPill(
        text = text,
        onClick = onClick,
        container = if (selected) RemoraNavyDark else Color.White,
        contentColor = if (selected) Color.White else RemoraNavyDark,
        borderColor = if (selected) null else RemoraBorder
    )
}

@Composable
private fun InfoBanner(icon: ImageVector, text: String) {
    val fg = Color(0xFF92400E)
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFEF3C7), modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, fontSize = 12.sp, lineHeight = 17.sp, color = fg)
        }
    }
}

@Composable
private fun IdleContent(recent: List<String>, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        if (recent.isNotEmpty()) {
            SectionLabel("gần đây")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recent.forEach { q ->
                    RemoraPill(text = q, onClick = { onPick(q) }, leadingIcon = Icons.Outlined.History)
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
        }

        SectionLabel("thử hỏi")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SUGGESTIONS.forEach { s ->
                RemoraPill(
                    text = s,
                    onClick = { onPick(s) },
                    container = RemoraBlueSoft,
                    contentColor = RemoraBlue,
                    borderColor = null
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HintRow(Icons.Outlined.TextFields, "từ viết tắt", "pttk, xstk, cnpm… Remora tự hiểu nghĩa đầy đủ")
                HintRow(Icons.Outlined.AutoAwesome, "theo ý nghĩa", "mô tả nội dung ảnh bằng lời của bạn")
                HintRow(Icons.Outlined.Schedule, "theo mốc thời gian", "hôm qua, hôm bữa, tuần trước, tháng trước")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = RemoraSubtleText,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun HintRow(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = RemoraBlueSoft, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = RemoraBlue, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RemoraNavyDark)
            Text(text = body, fontSize = 12.sp, color = RemoraSubtleText, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = RemoraSubtleText
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = RemoraNavyDark,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = RemoraSubtleText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onAction) {
            Text(text = actionLabel, color = RemoraBlue, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ResultCard(
    item: SearchResultCard,
    highlightTerms: List<String>,
    onClick: () -> Unit
) {
    val card = item.card

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
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
                    Text(text = "¶", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RemoraBlue)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title.ifBlank { "Chưa có tiêu đề" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = RemoraNavyDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${card.tags.subject} · ${card.contentType.label()} · ${relativeTime(card.createdAt)}",
                    fontSize = 11.sp,
                    color = RemoraSubtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (card.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = remember(card.summary, highlightTerms) { highlighted(card.summary, highlightTerms) },
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = RemoraNavyDark.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                item.snippet?.let { snippet ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = remember(snippet, highlightTerms) { highlighted("ảnh: $snippet", highlightTerms) },
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = RemoraSubtleText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val intent = card.userIntent
                    if (!intent.isNullOrBlank()) {
                        Text(
                            text = "\u201C$intent\u201D",
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            color = RemoraBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    RemoraPill(
                        text = matchLabel(item.matchKind),
                        container = RemoraBlueBadge,
                        contentColor = RemoraBlue,
                        borderColor = null,
                        fontSize = 10,
                        verticalPadding = 3
                    )
                }
            }
        }
    }
}

private fun matchLabel(kind: MatchKind): String = when (kind) {
    MatchKind.KEYWORD -> "khớp từ khóa"
    MatchKind.SEMANTIC -> "khớp ý nghĩa"
    MatchKind.BOTH -> "khớp từ khóa + ý nghĩa"
    MatchKind.TIME -> "theo thời gian"
}

private fun highlighted(text: String, terms: List<String>): AnnotatedString = buildAnnotatedString {
    append(text)
    Snippets.highlightRanges(text, terms).forEach { r ->
        addStyle(
            SpanStyle(background = RemoraBlueBadge, color = RemoraNavyDark, fontWeight = FontWeight.SemiBold),
            r.first,
            r.last + 1
        )
    }
}
