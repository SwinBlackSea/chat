package online.xiaoxi.chathub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import online.xiaoxi.chathub.data.ApiClient
import online.xiaoxi.chathub.data.ConversationDto
import online.xiaoxi.chathub.data.ModelDto
import online.xiaoxi.chathub.theme.WxRed
import online.xiaoxi.chathub.theme.WxText2
import online.xiaoxi.chathub.theme.WxText3

@Composable
fun ContactsScreen(onOpen: (Int) -> Unit, visible: Boolean = true) {
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    var humans by remember { mutableStateOf<List<ConversationDto>>(emptyList()) }
    var groups by remember { mutableStateOf<List<Pair<String, List<ModelDto>>>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            try {
                // 人：我参与的 human 会话（对方即联系人）
                val convs = api.conversations()
                humans = convs.filter { it.isHuman }
                // 模型：按服务商分组
                val models = api.enabledModels()
                groups = models.groupBy { it.providerName.ifEmpty { "其他" } }.toList()
                error = null
            } catch (e: Exception) {
                error = "加载失败：${e.message}"
            } finally {
                loaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }
    // 切换回本 Tab 时静默刷新（不阻塞显示缓存）
    LaunchedEffect(visible) {
        if (visible && loaded) load()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .alpha(if (visible) 1f else 0f)
            .pointerInput(visible) {
                // 隐藏时消费所有触摸事件，避免挡住下层页面
                if (!visible) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        Text(
            "联系人",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (error != null) {
            Text(error!!, color = WxRed, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            if (humans.isNotEmpty()) {
                item {
                    Text(
                        "联系人",
                        fontSize = 14.sp,
                        color = WxText2,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
                    )
                }
                items(humans) { conv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(conv.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(conv.contactName, conv.avatarColor, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(conv.contactName, fontSize = 16.sp)
                            Text(conv.modelCode, fontSize = 12.sp, color = WxText3)
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = WxText3,
                        )
                    }
                }
            }
            groups.forEach { (provider, models) ->
                item {
                    Text(
                        provider,
                        fontSize = 14.sp,
                        color = WxText2,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
                    )
                }
                items(models) { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    try {
                                        val conv = api.openConversation(model.id)
                                        error = null
                                        onOpen(conv.id)
                                    } catch (e: Exception) {
                                        error = "打开联系人失败：${e.message}"
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(model.displayName, model.avatarColor, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(model.displayName, fontSize = 16.sp)
                            Text(
                                buildString {
                                    append(model.modelId)
                                    if (model.contextLength != null) append(" · ${model.contextLength / 1000}K")
                                },
                                fontSize = 12.sp,
                                color = WxText3,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = WxText3,
                        )
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 16.dp)) }
        }
    }
}
