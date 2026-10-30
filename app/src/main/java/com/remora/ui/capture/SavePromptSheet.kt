package com.remora.ui.capture

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.remora.ui.theme.RemoraBg
import com.remora.ui.theme.RemoraBlue
import com.remora.ui.theme.RemoraBlueSoft
import com.remora.ui.theme.RemoraBorder
import com.remora.ui.theme.RemoraNavyDark
import com.remora.ui.theme.RemoraSubtleText

@Composable
fun SavePromptSheet(
    imageUri: Uri,
    onSave: (userIntent: String) -> Unit,
    onDismiss: () -> Unit,
    onToggleNotifications: () -> Unit,
    onPauseMonitoring: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    errorMessage: String? = null
) {
    var userIntentText by androidx.compose.runtime.saveable.rememberSaveable(imageUri.toString()) { mutableStateOf("") }
    val suggestionChips = listOf("ôn thi", "làm bài", "tham khảo")

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {} // Consume clicks inside sheet
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Preview Card for Screenshot
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(200.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RemoraBlueSoft.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Ảnh chụp màn hình vừa phát hiện",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                        )
                    }
                }

                // 2. Action Bottom Sheet (from 04-save.png)
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = "vì sao bạn lưu ảnh này?",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = RemoraNavyDark
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Ghi chú không bắt buộc. Chỉ khi bấm Lưu, ảnh và ghi chú được gửi qua máy chủ Remora tới Google Gemini để phân tích.",
                            fontSize = 13.sp,
                            color = RemoraSubtleText
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Custom Styled Underline Input Field
                        BasicTextField(
                            value = userIntentText,
                            onValueChange = { userIntentText = it.take(2000) },
                            enabled = !isSaving,
                            textStyle = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RemoraBlue
                            ),
                            cursorBrush = SolidColor(RemoraBlue),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (!isSaving) onSave(userIntentText)
                            }),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Column {
                                    Box(modifier = Modifier.padding(vertical = 4.dp)) {
                                        if (userIntentText.isEmpty()) {
                                            Text(
                                                text = "nhập ghi chú của bạn...",
                                                fontSize = 18.sp,
                                                color = RemoraSubtleText.copy(alpha = 0.6f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = RemoraBlue.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Suggestion Chips Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            suggestionChips.forEach { chip ->
                                Surface(
                                    shape = RoundedCornerShape(percent = 50),
                                    border = BorderStroke(1.dp, RemoraBorder),
                                    color = Color.Transparent,
                                    modifier = Modifier.clickable { userIntentText = chip }
                                ) {
                                    Text(
                                        text = chip,
                                        fontSize = 13.sp,
                                        color = RemoraNavyDark,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                        }
                        // Two Rounded-Pill Action Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isSaving,
                                shape = RoundedCornerShape(percent = 50),
                                border = BorderStroke(1.dp, RemoraBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RemoraNavyDark),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "bỏ qua",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }

                            Button(
                                onClick = { onSave(userIntentText) },
                                enabled = !isSaving,
                                shape = RoundedCornerShape(percent = 50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RemoraNavyDark,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = if (isSaving) "đang lưu…" else "lưu vào remora",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Subtle Footer Links
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "tắt thông báo",
                                fontSize = 12.sp,
                                color = RemoraSubtleText,
                                modifier = Modifier
                                    .clickable(onClick = onToggleNotifications)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                            Text(
                                text = " · ",
                                fontSize = 12.sp,
                                color = RemoraSubtleText
                            )
                            Text(
                                text = "tạm dừng theo dõi",
                                fontSize = 12.sp,
                                color = RemoraSubtleText,
                                modifier = Modifier
                                    .clickable(onClick = onPauseMonitoring)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
