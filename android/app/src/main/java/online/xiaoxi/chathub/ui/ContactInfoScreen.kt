package online.xiaoxi.chathub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import online.xiaoxi.chathub.data.ConversationDto
import online.xiaoxi.chathub.theme.WxGreen
import online.xiaoxi.chathub.theme.WxLine
import online.xiaoxi.chathub.theme.WxRed
import online.xiaoxi.chathub.theme.WxText2
import online.xiaoxi.chathub.theme.WxText3

@Composable
fun ContactInfoScreen(conversationId: Int, onBack: () -> Unit, onChat: () -> Unit) {
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    var conv by remember { mutableStateOf<ConversationDto?>(null) }
    var showPersona by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conversationId) {
        try {
            conv = api.conversations().firstOrNull { it.id == conversationId }
        } catch (e: Exception) {
            error = "加载失败：${e.message}"
        }
    }

    Column(Modifier.fillMaxSize().background(online.xiaoxi.chathub.theme.WxBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "联系人资料",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.width(40.dp))
        }

        val current = conv
        if (error != null) {
            Text(error!!, color = WxRed, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        }
        if (current != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(current.contactName, current.avatarColor, size = 62.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(current.contactName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        buildString {
                            append(current.modelCode)
                            if (current.contextLength != null) {
                                append(" · ${current.contextLength / 1000}K 上下文")
                            }
                        },
                        fontSize = 13.sp,
                        color = WxText2,
                    )
                }
            }

            Button(
                onClick = onChat,
                colors = ButtonDefaults.buttonColors(containerColor = WxGreen),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp),
            ) {
                Text("发消息", fontSize = 16.sp)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp)
                    .background(Color.White, RoundedCornerShape(10.dp)),
            ) {
                InfoCell("服务商", current.providerName)
                Spacer(Modifier.padding(start = 16.dp).height(1.dp).fillMaxWidth().background(WxLine))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPersona = true }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("人设（system prompt）", fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (current.systemPrompt.isNullOrBlank()) "未设置" else "已设置",
                        fontSize = 14.sp,
                        color = WxText3,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = WxText3,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp)
                    .background(Color.White, RoundedCornerShape(10.dp)),
            ) {
                Text(
                    "清空聊天记录",
                    color = WxRed,
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClear = true }
                        .padding(vertical = 15.dp),
                )
            }
        }
    }

    if (showPersona && conv != null) {
        var draft by remember { mutableStateOf(conv!!.systemPrompt.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showPersona = false },
            title = { Text("设置人设") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.height(160.dp).fillMaxWidth(),
                    placeholder = { Text("例如：你是一位资深 Python 工程师") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPersona = false
                    scope.launch {
                        try {
                            api.updateConversation(conversationId, draft)
                            conv = conv!!.copy(systemPrompt = draft.ifBlank { null })
                        } catch (e: Exception) {
                            error = "保存失败：${e.message}"
                        }
                    }
                }) { Text("保存", color = WxGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showPersona = false }) { Text("取消") }
            },
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清空聊天记录？") },
            text = { Text("将删除该联系人的全部消息并重置上下文。") },
            confirmButton = {
                TextButton(onClick = {
                    showClear = false
                    scope.launch {
                        try {
                            api.clearMessages(conversationId)
                            onChat()
                        } catch (e: Exception) {
                            error = "清空失败：${e.message}"
                        }
                    }
                }) { Text("清空", color = WxRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun InfoCell(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = WxText3)
    }
}
