package online.xiaoxi.chathub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import online.xiaoxi.chathub.data.ApiClient
import online.xiaoxi.chathub.data.Backend
import online.xiaoxi.chathub.data.ModelDto
import online.xiaoxi.chathub.data.ProviderDto
import online.xiaoxi.chathub.data.SettingsStore
import online.xiaoxi.chathub.data.WsHub
import online.xiaoxi.chathub.data.normalizeServerUrl
import online.xiaoxi.chathub.theme.WxGreen
import online.xiaoxi.chathub.theme.WxLine
import online.xiaoxi.chathub.theme.WxRed
import online.xiaoxi.chathub.theme.WxText2
import online.xiaoxi.chathub.theme.WxText3

@Composable
fun SettingsScreen(onAddProvider: () -> Unit, settingsStore: SettingsStore) {
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var providers by remember { mutableStateOf<List<ProviderDto>>(emptyList()) }
    var modelsByProvider by remember { mutableStateOf<Map<Int, List<ModelDto>>>(emptyMap()) }
    var editing by remember { mutableStateOf<ProviderDto?>(null) }
    var deleting by remember { mutableStateOf<ProviderDto?>(null) }
    var addingModelFor by remember { mutableStateOf<ProviderDto?>(null) }
    var editingModel by remember { mutableStateOf<ModelDto?>(null) }
    var editingServer by remember { mutableStateOf(false) }
    var editingIdentity by remember { mutableStateOf(false) }
    val storeUserId by settingsStore.userId.collectAsState(initial = "")
    val storeDisplayName by settingsStore.displayName.collectAsState(initial = "")

    fun load() {
        scope.launch {
            try {
                val list = api.providers()
                providers = list
                modelsByProvider = list.associate { it.id to api.providerModels(it.id) }
            } catch (e: Exception) {
                snackbar.showSnackbar("加载失败：${e.message}")
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = online.xiaoxi.chathub.theme.WxBackground,
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
                    .background(Color.White, RoundedCornerShape(10.dp)),
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
                    .background(Color.White, RoundedCornerShape(10.dp)),
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

            SectionLabel("服务商")
            providers.forEach { provider ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 10.dp)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(provider.name, null, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(provider.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (provider.isEnabled) "已启用" else "已停用",
                                    fontSize = 12.sp,
                                    color = if (provider.isEnabled) WxGreen else WxText3,
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${provider.baseUrl.removePrefix("https://")} · ${provider.apiKeyMasked}",
                                fontSize = 12.sp,
                                color = WxText3,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(
                            "测试",
                            color = WxGreen,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val result = try {
                                        api.testProvider(provider.id)
                                    } catch (e: Exception) {
                                        online.xiaoxi.chathub.data.TestResult(false, "请求失败：${e.message}")
                                    }
                                    snackbar.showSnackbar("${provider.name}：${result.message}")
                                }
                            },
                        )
                        Text(
                            "编辑",
                            color = WxGreen,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { editing = provider },
                        )
                        Text(
                            "删除",
                            color = WxRed,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { deleting = provider },
                        )
                    }

                    val models = modelsByProvider[provider.id].orEmpty()
                    if (models.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Spacer(Modifier.height(1.dp).fillMaxWidth().background(WxLine))
                        models.forEach { model ->
                            Row(
                                Modifier.fillMaxWidth().padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.displayName, fontSize = 14.sp)
                                    Text(
                                        buildString {
                                            append(model.modelId)
                                            if (model.contextLength != null) {
                                                append(" · ${model.contextLength / 1000}K")
                                            }
                                        },
                                        fontSize = 11.sp,
                                        color = WxText3,
                                    )
                                }
                                Text(
                                    "编辑",
                                    color = WxGreen,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable { editingModel = model }
                                        .padding(horizontal = 10.dp),
                                )
                                Text(
                                    "删除",
                                    color = WxRed,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch {
                                                try {
                                                    api.deleteModel(model.id)
                                                    load()
                                                } catch (e: Exception) {
                                                    snackbar.showSnackbar("删除失败：${e.message}")
                                                }
                                            }
                                        }
                                        .padding(horizontal = 10.dp),
                                )
                                Switch(
                                    checked = model.isEnabled,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            try {
                                                api.patchModel(model.id, checked)
                                                load()
                                            } catch (e: Exception) {
                                                snackbar.showSnackbar("更新失败：${e.message}")
                                            }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = WxGreen),
                                )
                            }
                        }
                    }
                    Text(
                        "＋ 添加模型",
                        color = WxGreen,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { addingModelFor = provider }.padding(top = 12.dp),
                    )
                }
            }

            Button(
                onClick = onAddProvider,
                colors = ButtonDefaults.buttonColors(containerColor = WxGreen),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
            ) {
                Text("＋ 添加服务商", fontSize = 15.sp)
            }
        }
    }

    editing?.let { provider ->
        EditProviderDialog(
            provider = provider,
            onDismiss = { editing = null },
            onSave = { name, baseUrl, apiKey ->
                scope.launch {
                    try {
                        api.updateProvider(
                            provider.id,
                            name.takeIf { it.isNotBlank() },
                            baseUrl.takeIf { it.isNotBlank() },
                            apiKey.takeIf { it.isNotBlank() },
                        )
                        load()
                    } catch (e: Exception) {
                        snackbar.showSnackbar("保存失败：${e.message}")
                    }
                    editing = null
                }
            },
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

    deleting?.let { provider ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除「${provider.name}」？") },
            text = { Text("其下所有联系人与会话将一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    val target = provider
                    deleting = null
                    scope.launch {
                        try {
                            api.deleteProvider(target.id)
                            load()
                        } catch (e: Exception) {
                            snackbar.showSnackbar("删除失败：${e.message}")
                        }
                    }
                }) { Text("删除", color = WxRed) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }

    addingModelFor?.let { provider ->
        AddModelDialog(
            onDismiss = { addingModelFor = null },
            onSave = { modelId, displayName, contextLength, avatarColor ->
                scope.launch {
                    try {
                        api.addModel(
                            provider.id,
                            modelId,
                            displayName.takeIf { it.isNotBlank() },
                            contextLength,
                            avatarColor,
                        )
                        load()
                    } catch (e: Exception) {
                        snackbar.showSnackbar("添加失败：${e.message}")
                    }
                    addingModelFor = null
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = WxText2,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun EditProviderDialog(
    provider: ProviderDto,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(provider.name) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var apiKey by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑服务商") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("baseUrl") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key（留空则不修改）") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, baseUrl, apiKey) }) { Text("保存", color = WxGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private val MODEL_AVATAR_COLORS = listOf(
    "#DCE8E2" to "#3E6B55",
    "#F3E3D3" to "#A5713D",
    "#DDE5EC" to "#4C657F",
    "#E7E0EC" to "#71588A",
)

@Composable
private fun AddModelDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int?, String?) -> Unit,
) {
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var contextLength by remember { mutableStateOf("") }
    var avatarColor by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加模型联系人") },
        text = {
            Column {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("模型 ID（如 ep-xxx）") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名（可留空）") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = contextLength,
                    onValueChange = { contextLength = it.filter { c -> c.isDigit() } },
                    label = { Text("上下文长度（可选）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(12.dp))
                Text("头像颜色", color = WxText2, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarColorDot(null, avatarColor == null) { avatarColor = null }
                    MODEL_AVATAR_COLORS.forEach { (bg, _) ->
                        AvatarColorDot(bg, avatarColor == bg) { avatarColor = bg }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (modelId.isNotBlank()) {
                        onSave(
                            modelId.trim(),
                            displayName.trim(),
                            contextLength.trim().toIntOrNull(),
                            avatarColor,
                        )
                    }
                },
            ) { Text("添加", color = WxGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AvatarColorDot(colorHex: String?, selected: Boolean, onClick: () -> Unit) {
    val color = colorHex
        ?.removePrefix("#")
        ?.toLongOrNull(16)
        ?.let { Color(0xFF000000 or (it and 0xFFFFFF)) }
        ?: Color(0xFFCFCFCF)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .then(
                if (selected) Modifier.border(2.dp, WxGreen, RoundedCornerShape(6.dp)) else Modifier,
            )
            .clickable(onClick = onClick),
    )
}
