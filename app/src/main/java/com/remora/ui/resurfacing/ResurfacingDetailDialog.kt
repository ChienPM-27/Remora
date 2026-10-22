package com.remora.ui.resurfacing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.resurface.ResurfacedItem

@Composable
fun ResurfacingDetailDialog(
    items: List<ResurfacedItem>,
    onDismiss: () -> Unit
) {
    val similarGroup = items.filter { it.group == ResurfaceGroup.SIMILAR }
    val complementaryGroup = items.filter { it.group == ResurfaceGroup.COMPLEMENTARY }
    val actionableGroup = items.filter { it.group == ResurfaceGroup.ACTIONABLE }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Gợi nhớ Tri thức (Memory Resurfacing)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (similarGroup.isNotEmpty()) {
                    GroupSection(title = "1. Nhóm Tương đồng (SIMILAR)", items = similarGroup)
                }
                if (complementaryGroup.isNotEmpty()) {
                    GroupSection(title = "2. Nhóm Bổ trợ (COMPLEMENTARY)", items = complementaryGroup)
                }
                if (actionableGroup.isNotEmpty()) {
                    GroupSection(title = "3. Nhóm Có giá trị thực hành (ACTIONABLE)", items = actionableGroup)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đã hiểu")
            }
        }
    )
}

@Composable
private fun GroupSection(
    title: String,
    items: List<ResurfacedItem>
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        items.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.candidateCard.title.ifBlank { "Thẻ liên quan #${item.candidateCard.id}" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text(item.relation.name) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lý do liên kết: ${item.reason}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (item.benefit.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Lợi ích: ${item.benefit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
