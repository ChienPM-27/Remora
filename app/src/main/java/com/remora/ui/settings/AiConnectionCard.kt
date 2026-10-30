package com.remora.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.remora.ai.AiGatewayClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AiConnectionCard(client: AiGatewayClient, onSignedIn: suspend () -> Unit) {
    val session by client.session.collectAsState()
    val scope = rememberCoroutineScope()
    var endpoint by remember { mutableStateOf(session.endpoint) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Kết nối AI", style = MaterialTheme.typography.titleMedium)
            if (session.isValid()) {
                Text("Đã kết nối. Ảnh đã lưu sẽ được phân tích khi có mạng.")
                TextButton(onClick = { client.invalidateToken(); message = null }) { Text("Đăng xuất AI") }
            } else {
                Text("Thư viện vẫn dùng được khi chưa đăng nhập. Dùng tài khoản do đơn vị vận hành Remora cung cấp; không nhập khóa Gemini.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Địa chỉ máy chủ Remora") },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Tài khoản") },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Mật khẩu") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy,
                    modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && username.isNotBlank() && password.isNotBlank(), onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        try {
                            val result = client.signIn(endpoint, username, password)
                            if (result.isSuccess) {
                                onSignedIn()
                                message = "Kết nối thành công"
                            } else message = "Chưa kết nối được. Kiểm tra địa chỉ HTTPS, tài khoản và mạng rồi thử lại."
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { message = "Đã lưu kết nối; hãy mở lại Remora để tiếp tục hàng đợi." }
                        finally { password = ""; busy = false }
                    }
                }) { Text(if (busy) "Đang kết nối…" else "Kết nối") }
                Text("Remora chỉ giữ phiên đăng nhập trên thiết bị, không lưu mật khẩu.", style = MaterialTheme.typography.bodySmall)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
