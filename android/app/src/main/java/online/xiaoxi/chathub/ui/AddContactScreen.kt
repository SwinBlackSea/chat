package online.xiaoxi.chathub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import online.xiaoxi.chathub.data.ApiClient
import online.xiaoxi.chathub.data.Backend
import online.xiaoxi.chathub.data.UserDto
import online.xiaoxi.chathub.theme.WxGreen
import online.xiaoxi.chathub.theme.WxRed
import online.xiaoxi.chathub.theme.WxText2
import online.xiaoxi.chathub.theme.WxText3

@Composable
fun AddContactScreen(onBack: () -> Unit, onOpenConversation: (Int) -> Unit) {
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<UserDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        error = null
        scope.launch {
            try {
                found = api.searchUser(q)
            } catch (e: Exception) {
                found = null
                error = "未找到用户：${e.message}"
            } finally {
                searching = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(online.xiaoxi.chathub.theme.WxBackground).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "添加联系人",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.width(40.dp))
        }

        Column(Modifier.padding(16.dp)) {
            if (Backend.userId.isEmpty()) {
                Text(
                    "请先在「设置 → 我的身份」中配置自己的 user_id，才能添加联系人",
                    color = WxRed,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("对方 user_id") },
                    placeholder = { Text("如 zhangsan") },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { search() },
                    enabled = !searching && query.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = WxGreen),
                ) {
                    Text(if (searching) "搜索中…" else "搜索", fontSize = 14.sp)
                }
            }
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, color = WxRed, fontSize = 13.sp)
            }
            found?.let { user ->
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(user.displayName, user.avatarColor, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.displayName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(user.userId, fontSize = 12.sp, color = WxText3)
                    }
                    Button(
                        onClick = {
                            adding = true
                            error = null
                            scope.launch {
                                try {
                                    val conv = api.openHumanConversation(user.userId)
                                    onOpenConversation(conv.id)
                                } catch (e: Exception) {
                                    error = "添加失败：${e.message}"
                                } finally {
                                    adding = false
                                }
                            }
                        },
                        enabled = !adding && Backend.userId.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = WxGreen),
                    ) {
                        Text(if (adding) "添加中…" else "添加并聊天", fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "提示：需要对方也在这台服务器注册过身份（设置页配置 user_id）。",
                fontSize = 12.sp,
                color = WxText2,
            )
        }
    }
}
