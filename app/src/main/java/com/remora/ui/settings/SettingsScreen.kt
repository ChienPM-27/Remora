package com.remora.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.remora.RemoraApp
import com.remora.capture.CaptureForegroundService
import com.remora.capture.CapturePermissions
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onClearAllCards: () -> Unit, modifier: Modifier = Modifier, isClearing: Boolean = false) {
    val context = LocalContext.current
    val container = (context.applicationContext as RemoraApp).container
    val dao = container.database.appSettingDao()
    val settings by remember(dao) { dao.getSettingFlow() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var showClear by remember { mutableStateOf(false) }
    var canRead by remember { mutableStateOf(CapturePermissions.canReadImages(context)) }
    var canNotify by remember { mutableStateOf(CapturePermissions.canNotify(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canRead = CapturePermissions.canReadImages(context)
                canNotify = CapturePermissions.canNotify(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        canRead = CapturePermissions.canReadImages(context)
        canNotify = CapturePermissions.canNotify(context)
        if (canRead && settings?.paused == false) CaptureForegroundService.start(context)
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Cài đặt", style = MaterialTheme.typography.headlineMedium)
        Text("Bạn quyết định điều Remora được ghi nhớ.", style = MaterialTheme.typography.bodyMedium)
        AiConnectionCard(container.aiGatewayClient) { container.saveCoordinator.resumeAfterSignIn() }
        val value = settings
        if (value == null) {
            CircularProgressIndicator()
        } else {
            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingSwitch("Theo dõi ảnh chụp màn hình", "Gợi ý lưu ảnh mới; không tự tải ảnh lên.",
                        checked = !value.paused, onCheckedChange = { enabled ->
                            scope.launch {
                                dao.setPaused(!enabled)
                                if (!enabled) CaptureForegroundService.stop(context)
                                else if (canRead) CaptureForegroundService.start(context)
                                else permissionRequest.launch(arrayOf(CapturePermissions.imagePermission))
                            }
                        })
                    HorizontalDivider()
                    SettingSwitch("Thông báo", "Thông báo lưu ảnh và các liên kết học tập mới.",
                        checked = value.notificationsEnabled, onCheckedChange = { enabled ->
                            scope.launch { dao.setNotificationsEnabled(enabled) }
                            if (enabled && Build.VERSION.SDK_INT >= 33 && !canNotify)
                                permissionRequest.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        })
                    if ((!value.paused && !canRead) || (value.notificationsEnabled && !canNotify)) {
                        Text("Một số quyền đang tắt. Bạn vẫn có thể thêm ảnh bằng nút Thêm ảnh trong Thư viện.",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")))
                        }) { Text("Mở quyền ứng dụng") }
                    }
                    Text(if (value.paused) "Theo dõi đang tắt" else if (canRead) "Theo dõi đang bật" else "Đang chờ quyền đọc ảnh",
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dữ liệu trên thiết bị", style = MaterialTheme.typography.titleMedium)
                Text("Thẻ, ghi chú và bản sao ảnh được lưu trong Remora. Xóa thẻ không xóa ảnh gốc trong thư viện điện thoại.")
                OutlinedButton(enabled = !isClearing, onClick = { showClear = true }) {
                    Text(if (isClearing) "Đang xóa thẻ…" else "Xóa toàn bộ thẻ")
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quyền riêng tư", style = MaterialTheme.typography.titleMedium)
                Text("Chỉ sau khi bạn bấm Lưu, ảnh và ghi chú được gửi qua máy chủ Remora tới Google Gemini. Tìm kiếm gửi câu hỏi; gợi nhớ gửi tóm tắt các thẻ liên quan. Remora không lưu ảnh hoặc OCR trên máy chủ.")
                Text("Chất lượng AI có thể thay đổi. Hãy đối chiếu tóm tắt với ảnh gốc khi học công thức hoặc dữ kiện quan trọng.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Remora · Ghi nhớ điều đáng học", style = MaterialTheme.typography.labelMedium)
    }
    if (showClear) AlertDialog(
        onDismissRequest = { showClear = false },
        title = { Text("Xóa toàn bộ thẻ?") },
        text = { Text("Thẻ, liên kết và các bản sao ảnh trong Remora sẽ bị xóa. Thao tác không thể hoàn tác. Ảnh gốc trên điện thoại được giữ lại.") },
        confirmButton = { TextButton(enabled = !isClearing, onClick = { showClear = false; onClearAllCards() }) { Text("Xóa toàn bộ") } },
        dismissButton = { TextButton(onClick = { showClear = false }) { Text("Giữ lại") } }
    )
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
