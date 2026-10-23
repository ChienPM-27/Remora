package com.remora.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remora.ui.theme.RemoraBg
import com.remora.ui.theme.RemoraBlue
import com.remora.ui.theme.RemoraBlueSoft
import com.remora.ui.theme.RemoraBorder
import com.remora.ui.theme.RemoraNavyDark
import com.remora.ui.theme.RemoraSubtleText

@Composable
fun SettingsScreen(
    onResetDemoData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var autoDetectEnabled by remember { mutableStateOf(true) }
    var notificationEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RemoraBg)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Header
        Text(
            text = "cài đặt",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = RemoraSubtleText
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "tùy chỉnh bộ não remora.",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = RemoraNavyDark
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Settings Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Auto Detect Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "tự động nhận diện ảnh chụp",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RemoraNavyDark
                        )
                        Text(
                            text = "bật cửa sổ lưu bài học khi bạn chụp màn hình",
                            fontSize = 12.sp,
                            color = RemoraSubtleText
                        )
                    }
                    Switch(
                        checked = autoDetectEnabled,
                        onCheckedChange = { autoDetectEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = RemoraNavyDark
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Notification Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "thông báo gợi nhớ kiến thức",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RemoraNavyDark
                        )
                        Text(
                            text = "nhắc nhở khi phát hiện liên kết với bài học cũ",
                            fontSize = 12.sp,
                            color = RemoraSubtleText
                        )
                    }
                    Switch(
                        checked = notificationEnabled,
                        onCheckedChange = { notificationEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = RemoraNavyDark
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Demo Data Action Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "dữ liệu mẫu bài giảng",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RemoraNavyDark
                )
                Text(
                    text = "khôi phục các slide xác suất thống kê để demo và thử nghiệm tìm kiếm ngữ nghĩa.",
                    fontSize = 12.sp,
                    color = RemoraSubtleText
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onResetDemoData,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RemoraNavyDark,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Nạp lại",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("nạp lại bộ bài giảng mẫu", fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Privacy Guarantee Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = RemoraBlueSoft.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, RemoraBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Bảo mật",
                    tint = RemoraBlue,
                    modifier = Modifier.size(24.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "cam kết quyền riêng tư tuyệt đối",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RemoraNavyDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ảnh chỉ rời khỏi thiết bị khi bạn bấm 'lưu vào remora'. không có hình ảnh hoặc văn bản ocr nào bị đồng bộ âm thầm trong nền.",
                        fontSize = 12.sp,
                        color = RemoraSubtleText,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Footer version
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "remora v1.0.0 · hackathon build",
                fontSize = 12.sp,
                color = RemoraSubtleText
            )
            Text(
                text = "your second brain for everything you capture",
                fontSize = 11.sp,
                color = RemoraSubtleText.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
