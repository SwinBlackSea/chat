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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import online.xiaoxi.chathub.data.ApiClient
import online.xiaoxi.chathub.data.Backend
import online.xiaoxi.chathub.data.SettingsStore
import online.xiaoxi.chathub.data.UiCache
import online.xiaoxi.chathub.data.WsHub
import online.xiaoxi.chathub.data.normalizeServerUrl
import online.xiaoxi.chathub.theme.WxGreen
import online.xiaoxi.chathub.theme.WxLine
import online.xiaoxi.chathub.theme.WxRed
import online.xiaoxi.chathub.theme.WxText2
import online.xiaoxi.chathub.theme.WxText3

@Composable
fun SettingsScreen(
    onAddProvider: () -> Unit,
    onOpenProviders: () -> Unit,
    settingsStore: SettingsStore,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var providerCount by remember { mutableStateOf(0) }
    var enabledCount by remember { mutableStateOf(0) }
    var editingServer by remember { mutableStateOf(false) }
    var editingIdentity by remember { mutableStateOf(false) }
    val storeUserId by settingsStore.userId.collectAsState(initial = "")
    val storeDisplayName by settingsStore.displayName.collectAsState(initial = "")

    fun load() {
        // 先显示缓存（导航重建时瞬时渲染），后台刷新
        UiCache.providers?.let { providerCount = it.size; enabledCount = it.count { p -> p.isEnabled } }
        scope.launch {
            try {
                val list = api.providers()
                UiCache.providers = list
                providerCount = list.size
                enabledCount = list.count { it.isEnabled }
            } catch (e: Exception) {
                snackbar.showSnackbar("加载失败：${e.message}")
            }
        }
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(visible) {
        if (visible) load()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = online.xiaoxi.chathub.theme.WxBackground,
        modifier = modifier
            .fillMaxSize()
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
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                "设置",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            SectionLabel("服务")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingServer = true }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("后端地址", fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text(Backend.baseUrl.removePrefix("https://"), fontSize = 13.sp, color = WxText2)
                }
            }

            SectionLabel("我的身份")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingIdentity = true }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("user_id", fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        storeUserId.ifEmpty { "未设置" },
                        fontSize = 13.sp,
                        color = if (storeUserId.isEmpty()) WxRed else WxText2,
                    )
                }
                Spacer(Modifier.height(1.dp).fillMaxWidth().background(WxLine))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingIdentity = true }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("显示名", fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text(storeDisplayName.ifEmpty { "未设置" }, fontSize = 13.sp, color = WxText2)
                }
            }

            SectionLabel("模型")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProviders() }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("服务商", fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$providerCount 个 · $enabledCount 启用",
                        fontSize = 13.sp,
                        color = WxText3,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = WxText3,
                    )
                }
                Spacer(Modifier.height(1.dp).fillMaxWidth().background(WxLine))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onAddProvider() }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("添加服务商", fontSize = 16.sp, color = WxGreen)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = WxText3,
                    )
                }
            }
        }
    }

    if (editingServer) {
        var draftUrl by remember { mutableStateOf(Backend.baseUrl) }
        AlertDialog(
            onDismissRequest = { editingServer = false },
            title = { Text("后端地址") },
            text = {
                OutlinedTextField(
                    value = draftUrl,
                    onValueChange = { draftUrl = it },
                    label = { Text("http(s)://服务器地址") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val candidate = normalizeServerUrl(draftUrl)
                    if (candidate == null) return@TextButton
                    editingServer = false
                    scope.launch {
                        try {
                            if (ApiClient(candidate).health()) {
                                Backend.baseUrl = candidate
                                settingsStore.setServerUrl(candidate)
                                WsHub.restart()
                                snackbar.showSnackbar("后端地址已更新")
                            } else {
                                snackbar.showSnackbar("连接失败，请检查地址")
                            }
                        } catch (e: Exception) {
                            snackbar.showSnackbar("连接失败：${e.message}")
                        }
                    }
                }) { Text("保存", color = WxGreen) }
            },
            dismissButton = { TextButton(onClick = { editingServer = false }) { Text("取消") } },
        )
    }

    if (editingIdentity) {
        var draftUserId by remember { mutableStateOf(storeUserId) }
        var draftName by remember { mutableStateOf(storeDisplayName) }
        AlertDialog(
            onDismissRequest = { editingIdentity = false },
            title = { Text("我的身份") },
            text = {
                Column {
                    Text("user_id 是「我是谁」的唯一标识，别人通过它添加你。", fontSize = 12.sp, color = WxText2)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftUserId,
                        onValueChange = { draftUserId = it },
                        label = { Text("user_id") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text("显示名") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uid = draftUserId.trim()
                    if (uid.isEmpty()) return@TextButton
                    editingIdentity = false
                    scope.launch {
                        try {
                            api.registerUser(uid, draftName.trim().ifEmpty { uid })
                            settingsStore.setIdentity(uid, draftName.trim().ifEmpty { uid })
                            Backend.userId = uid
                            WsHub.restart()
                            snackbar.showSnackbar("身份已保存")
                        } catch (e: Exception) {
                            snackbar.showSnackbar("保存失败：${e.message}")
                        }
                    }
                }) { Text("保存", color = WxGreen) }
            },
            dismissButton = { TextButton(onClick = { editingIdentity = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = WxText2,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
    )
}
