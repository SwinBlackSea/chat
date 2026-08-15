package online.xiaoxi.chathub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import online.xiaoxi.chathub.data.ApiClient
import online.xiaoxi.chathub.data.Backend
import online.xiaoxi.chathub.data.SettingsStore
import online.xiaoxi.chathub.data.normalizeServerUrl
import online.xiaoxi.chathub.theme.WxGreen
import online.xiaoxi.chathub.theme.WxRed
import online.xiaoxi.chathub.theme.WxText2

@Composable
fun SetupScreen(store: SettingsStore) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ChatHub", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("聚合多家 AI 模型的微信式聊天客户端", fontSize = 14.sp, color = WxText2)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("后端服务器地址") },
            placeholder = { Text("https://example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val candidate = normalizeServerUrl(url)
                if (candidate == null) {
                    error = "请填写有效的 http(s) 服务器地址"
                    return@Button
                }
                checking = true
                error = null
                scope.launch {
                    val ok = ApiClient(candidate).health()
                    checking = false
                    if (ok) {
                        Backend.baseUrl = candidate
                        store.setServerUrl(candidate)
                    } else {
                        error = "连接失败，请检查地址与网络"
                    }
                }
            },
            enabled = !checking,
            colors = ButtonDefaults.buttonColors(containerColor = WxGreen),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (checking) "连接中…" else "连接", fontSize = 16.sp)
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = WxRed, fontSize = 13.sp)
        }
    }
}
