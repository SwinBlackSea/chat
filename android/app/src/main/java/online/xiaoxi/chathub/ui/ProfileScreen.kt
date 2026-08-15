package online.xiaoxi.chathub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import online.xiaoxi.chathub.data.ApiClient
import online.xiaoxi.chathub.data.AvatarLoader
import online.xiaoxi.chathub.data.Backend
import online.xiaoxi.chathub.data.SettingsStore
import online.xiaoxi.chathub.data.WsHub
import online.xiaoxi.chathub.theme.WxBackground
import online.xiaoxi.chathub.theme.WxCard
import online.xiaoxi.chathub.theme.WxGreen
import online.xiaoxi.chathub.theme.WxLine
import online.xiaoxi.chathub.theme.WxRed
import online.xiaoxi.chathub.theme.WxText2
import online.xiaoxi.chathub.theme.WxText3

/** "我"页（微信风格）：个人信息卡 + 设置入口。 */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    settingsStore: SettingsStore,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storeUserId by settingsStore.userId.collectAsState(initial = "")
    val storeDisplayName by settingsStore.displayName.collectAsState(initial = "")
    var editingIdentity by remember { mutableStateOf(false) }
    var avatarMsg by remember { mutableStateOf<String?>(null) }

    // 进入"我"页/身份就绪后拉取我的资料（含头像），上传后也据此刷新
    LaunchedEffect(Backend.userId) {
        if (Backend.userId.isNotEmpty()) {
            try {
                val me = api.me()
                if (me.avatar != null) Backend.myAvatar = me.avatar
            } catch (_: Exception) {
            }
        }
    }

    // 系统 Photo Picker 选图（免存储权限）→ 上传
    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val resolver = context.contentResolver
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        avatarMsg = "读取图片失败"
                        return@launch
                    }
                    val mime = resolver.getType(uri) ?: "image/*"
                    avatarMsg = null
                    val path = api.uploadAvatar(bytes, mime)
                    if (path != null) {
                        Backend.myAvatar = path
                        AvatarLoader.clearAll() // 兜底清内存缓存，防旧图残留
                        avatarMsg = "头像已更新"
                    } else {
                        avatarMsg = "上传失败"
                    }
                } catch (e: Exception) {
                    avatarMsg = "上传失败：${e.message}"
                }
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(WxBackground)
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
            }
            .verticalScroll(rememberScrollState()),
    ) {
        // 顶部个人信息卡（微信风格：大头像 + 名字 + id）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { editingIdentity = true }
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                storeDisplayName.ifEmpty { "?" },
                null,
                size = 64.dp,
                onClick = {
                    pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                imageUrl = Backend.myAvatar,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    storeDisplayName.ifEmpty { "未设置昵称" },
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A1A),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (storeUserId.isEmpty()) "点击设置 user_id" else "user_id：$storeUserId",
                    fontSize = 13.sp,
                    color = if (storeUserId.isEmpty()) WxRed else WxText2,
                )
                if (avatarMsg != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(avatarMsg!!, fontSize = 12.sp, color = WxText2)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 设置入口
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(WxCard, RoundedCornerShape(12.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = Color(0xFF8A8A8E),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text("设置", fontSize = 16.sp, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = WxText3,
                )
            }
            Spacer(Modifier.height(1.dp).fillMaxWidth().background(WxLine))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingIdentity = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("我的身份", fontSize = 16.sp, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
                Text(
                    if (storeUserId.isEmpty()) "未设置" else storeUserId,
                    fontSize = 13.sp,
                    color = WxText3,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = WxText3,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "ChatHub · 人和 AI 都在这里",
            fontSize = 12.sp,
            color = WxText3,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                            Backend.myAvatar = null // 身份变化，头像待拉取
                            WsHub.restart()
                        } catch (_: Exception) {
                        }
                    }
                }) { Text("保存", color = WxGreen) }
            },
            dismissButton = { TextButton(onClick = { editingIdentity = false }) { Text("取消") } },
        )
    }
}
