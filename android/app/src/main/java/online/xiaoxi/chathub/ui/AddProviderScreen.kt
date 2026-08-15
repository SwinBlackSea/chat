package online.xiaoxi.chathub.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import online.xiaoxi.chathub.data.ApiClient
import online.xiaoxi.chathub.data.ModelInput
import online.xiaoxi.chathub.data.TemplateDto
import online.xiaoxi.chathub.theme.WxGreen
import online.xiaoxi.chathub.theme.WxText2

@Composable
fun AddProviderScreen(onBack: () -> Unit) {
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var templates by remember { mutableStateOf<List<TemplateDto>>(emptyList()) }
    var selected by remember { mutableStateOf<String>("deepseek") }
    var name by remember { mutableStateOf("DeepSeek") }
    var baseUrl by remember { mutableStateOf("https://api.deepseek.com") }
    var apiKey by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedModels by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun selectTemplate(template: TemplateDto) {
        selected = template.kind
        name = template.name
        baseUrl = template.baseUrl
        apiKey = ""
        selectedModels = template.defaultModels.map { it.modelId }.toSet()
    }

    LaunchedEffect(Unit) {
        try {
            templates = api.templates()
            templates.firstOrNull { it.kind == selected }?.let(::selectTemplate)
            loadError = null
        } catch (e: Exception) {
            loadError = "模板加载失败：${e.message}"
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "添加服务商",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.width(40.dp))
            IconButton(enabled = false, onClick = {}) {}
        }

        Text(
            "选择模板",
            fontSize = 13.sp,
            color = WxText2,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
        )
        if (loadError != null) {
            Text(
                loadError!!,
                color = online.xiaoxi.chathub.theme.WxRed,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            templates.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { template ->
                        val isSelected = selected == template.kind
                        Column(
                            Modifier
                                .weight(1f)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) WxGreen else online.xiaoxi.chathub.theme.WxLine,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { selectTemplate(template) }
                                .padding(13.dp),
                        ) {
                            Text(template.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (template.note.isNotEmpty()) {
                                Text(template.note, fontSize = 12.sp, color = WxText2)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API 端点（baseUrl）") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            val currentTemplate = templates.firstOrNull { it.kind == selected }
            if (currentTemplate?.defaultModels?.isNotEmpty() == true) {
                Spacer(Modifier.height(14.dp))
                Text("添加为联系人的模型", fontSize = 13.sp, color = WxText2)
                currentTemplate.defaultModels.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedModels = if (model.modelId in selectedModels) {
                                    selectedModels - model.modelId
                                } else {
                                    selectedModels + model.modelId
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = model.modelId in selectedModels,
                            onCheckedChange = { checked ->
                                selectedModels = if (checked) {
                                    selectedModels + model.modelId
                                } else {
                                    selectedModels - model.modelId
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = WxGreen),
                        )
                        Column {
                            Text(model.displayName, fontSize = 14.sp)
                            Text(model.modelId, fontSize = 12.sp, color = WxText2)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    "此模板没有固定模型；保存后可在设置中添加模型 ID 或接入点 ID。",
                    fontSize = 12.sp,
                    color = WxText2,
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (baseUrl.isBlank()) {
                        Toast.makeText(context, "baseUrl 不能为空", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    saving = true
                    scope.launch {
                        try {
                            val selectedTemplate = templates.firstOrNull { it.kind == selected }
                            val models = selectedTemplate?.defaultModels.orEmpty()
                                .filter { it.modelId in selectedModels }
                                .map {
                                    ModelInput(
                                        modelId = it.modelId,
                                        displayName = it.displayName,
                                        contextLength = it.contextLength,
                                    )
                                }
                            val provider = api.createProvider(
                                selected,
                                name,
                                baseUrl,
                                apiKey,
                                models,
                            )
                            val result = try {
                                api.testProvider(provider.id)
                            } catch (e: Exception) {
                                online.xiaoxi.chathub.data.TestResult(false, "连接测试失败：${e.message}")
                            }
                            Toast.makeText(
                                context,
                                if (result.ok) "已保存，${result.message}" else "已保存，但${result.message}",
                                Toast.LENGTH_LONG,
                            ).show()
                            onBack()
                        } catch (e: Exception) {
                            Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = WxGreen),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "保存中…" else "保存并测试连接", fontSize = 15.sp)
            }
        }
    }
}
