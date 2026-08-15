package online.xiaoxi.chathub.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import online.xiaoxi.chathub.data.ApiClient
import online.xiaoxi.chathub.data.ConversationDto
import online.xiaoxi.chathub.data.ChatGenerationTracker
import online.xiaoxi.chathub.data.UiCache
import online.xiaoxi.chathub.data.WsEvent
import online.xiaoxi.chathub.data.WsHub
import online.xiaoxi.chathub.theme.WxLine
import online.xiaoxi.chathub.theme.Accent
import online.xiaoxi.chathub.theme.WxRed
import online.xiaoxi.chathub.theme.WxText2
import online.xiaoxi.chathub.theme.WxText3

@Composable
fun ChatListScreen(
    onOpen: (Int) -> Unit,
    onAddContact: () -> Unit,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val activeConversations by ChatGenerationTracker.activeConversations.collectAsState()
    var items by remember { mutableStateOf<List<ConversationDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    fun load() {
        // 先显示缓存（导航重建时瞬时渲染），后台刷新
        UiCache.conversations?.let { items = it; loaded = true }
        scope.launch {
            try {
                val list = api.conversations()
                UiCache.conversations = list
                items = list
                error = null
            } catch (e: Exception) {
                error = "加载失败：${e.message}"
            } finally {
                loaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }
    // 切回本 Tab 时静默刷新（不阻塞显示缓存）
    LaunchedEffect(visible) {
        if (visible && loaded) load()
    }
    // App 回到前台时刷新（ws 断线期间错过的消息在此补齐）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && loaded) load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(activeConversations) {
        if (loaded) load()
    }
    // 实时通道新消息/回执到达 → 刷新列表
    DisposableEffect(Unit) {
        val listener: (WsEvent) -> Unit = { event ->
            if (event is WsEvent.Message || event is WsEvent.Ack) {
                mainHandler.post { load() }
            }
        }
        WsHub.addListener(listener)
        onDispose { WsHub.removeListener(listener) }
    }

    Column(
        modifier
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ChatHub", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onAddContact) { Icon(Icons.Filled.Add, contentDescription = "添加联系人") }
            IconButton(onClick = { load() }) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") }
        }
        when {
            error != null -> Text(
                error!!,
                color = WxRed,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp),
            )
            !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Accent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("加载中…", color = WxText3, fontSize = 14.sp)
                }
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Forum,
                        contentDescription = null,
                        tint = WxText3,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("还没有对话", color = WxText2, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("去通讯录选一位联系人，或点右上角 + 加人", color = WxText3, fontSize = 12.sp)
                }
            }
            else -> LazyColumn {
                itemsIndexed(items) { index, conv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(conv.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Avatar(conv.contactName, conv.avatarColor, size = 48.dp)
                            // 微信式未读红点（数字角标，头像右上角）
                            if (conv.unreadCount > 0) {
                                Badge(
                                    count = conv.unreadCount,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-6).dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    conv.contactName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatWhen(conv.lastMessageTime ?: conv.updatedAt),
                                    fontSize = 12.sp,
                                    color = WxText3,
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (conv.id in activeConversations) {
                                        "正在回答…"
                                    } else {
                                        conv.lastMessagePreview ?: ""
                                    },
                                    fontSize = 13.sp,
                                    color = if (conv.id in activeConversations) {
                                        online.xiaoxi.chathub.theme.WxGreen
                                    } else {
                                        WxText3
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (conv.unreadCount > 0 && conv.id in activeConversations) {
                                    Text("未读", fontSize = 11.sp, color = WxRed)
                                }
                            }
                        }
                    }
                    if (index < items.size - 1) {
                        Spacer(
                            Modifier.padding(start = 76.dp).height(0.5.dp).fillMaxWidth().background(WxLine),
                        )
                    }
                }
            }
        }
    }
}

/** 微信式未读红点：红底白字数字（定位由调用方 modifier 提供）。 */
@Composable
private fun Badge(count: Int, modifier: Modifier = Modifier) {
    val text = if (count > 99) "99+" else count.toString()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .background(WxRed)
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
