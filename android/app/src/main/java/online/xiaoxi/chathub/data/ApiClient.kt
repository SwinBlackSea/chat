package online.xiaoxi.chathub.data

import java.util.concurrent.TimeUnit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ApiException(val status: Int, message: String) : Exception(message)

object Backend {
    @Volatile
    var baseUrl: String = ""

    /** 自托管身份：user_id 即"我是谁"，人人聊天 REST 请求带 X-User-Id 头。
     *  用 State 包装：身份异步加载完成后能触发 UI 重组（气泡左右判断等）。 */
    var userId: String by mutableStateOf("")
}

fun normalizeServerUrl(raw: String): String? {
    val candidate = raw.trim().let {
        when {
            it.isEmpty() -> return null
            it.startsWith("http://") || it.startsWith("https://") -> it
            else -> "https://$it"
        }
    }.trimEnd('/')
    return try {
        val uri = java.net.URI(candidate)
        candidate.takeIf {
            (uri.scheme == "http" || uri.scheme == "https") &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        }
    } catch (_: Exception) {
        null
    }
}

data class ModelInput(
    val modelId: String,
    val displayName: String,
    val contextLength: Int? = null,
    val avatarColor: String? = null,
)

class ApiClient(private val serverUrl: String? = null) {
    private val json = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val streamHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private fun url(path: String): String = (serverUrl ?: Backend.baseUrl).trimEnd('/') + path

    private fun errorMessage(body: String, status: Int): String = try {
        val detail = JSONObject(body).opt("detail")
        when (detail) {
            is String -> detail
            null -> "请求失败（HTTP $status）"
            else -> detail.toString()
        }
    } catch (_: Exception) {
        body.trim().take(300).ifEmpty { "请求失败（HTTP $status）" }
    }

    private suspend fun request(builder: Request.Builder): String = withContext(Dispatchers.IO) {
        val authed = if (Backend.userId.isNotEmpty()) {
            builder.header("X-User-Id", Backend.userId)
        } else {
            builder
        }
        http.newCall(authed.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, errorMessage(body, resp.code))
            body
        }
    }

    private fun jsonBody(map: Map<String, Any?>) =
        JSONObject(map.filterValues { it != null }).toString().toRequestBody(json)

    suspend fun health(): Boolean = try {
        request(Request.Builder().url(url("/api/health")))
        true
    } catch (_: Exception) {
        false
    }

    suspend fun templates(): List<TemplateDto> {
        val arr = JSONArray(request(Request.Builder().url(url("/api/providers/templates"))))
        return (0 until arr.length()).map { TemplateDto.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun providers(): List<ProviderDto> {
        val arr = JSONArray(request(Request.Builder().url(url("/api/providers"))))
        return (0 until arr.length()).map { ProviderDto.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun createProvider(
        kind: String,
        name: String?,
        baseUrl: String?,
        apiKey: String,
        models: List<ModelInput>,
    ): ProviderDto {
        val modelArray = JSONArray().apply {
            models.forEach { model ->
                put(
                    JSONObject(
                        mapOf(
                            "model_id" to model.modelId,
                            "display_name" to model.displayName,
                            "context_length" to model.contextLength,
                            "avatar_color" to model.avatarColor,
                        ).filterValues { it != null },
                    ),
                )
            }
        }
        val body = JSONObject(
            mapOf(
                "kind" to kind,
                "name" to name,
                "base_url" to baseUrl,
                "api_key" to apiKey,
                "models" to modelArray,
            ).filterValues { it != null },
        ).toString().toRequestBody(json)
        return ProviderDto.fromJson(
            JSONObject(request(Request.Builder().url(url("/api/providers")).post(body)))
        )
    }

    suspend fun updateProvider(id: Int, name: String?, baseUrl: String?, apiKey: String?): ProviderDto {
        val body = jsonBody(mapOf("name" to name, "base_url" to baseUrl, "api_key" to apiKey))
        return ProviderDto.fromJson(
            JSONObject(request(Request.Builder().url(url("/api/providers/$id")).put(body)))
        )
    }

    suspend fun deleteProvider(id: Int) {
        request(Request.Builder().url(url("/api/providers/$id")).delete())
    }

    suspend fun testProvider(id: Int): TestResult {
        val body = "{}".toRequestBody(json)
        val o = JSONObject(request(Request.Builder().url(url("/api/providers/$id/test")).post(body)))
        return TestResult(o.getBoolean("ok"), o.getString("message"))
    }

    suspend fun providerModels(providerId: Int): List<ModelDto> {
        val arr = JSONArray(request(Request.Builder().url(url("/api/providers/$providerId/models"))))
        return (0 until arr.length()).map { ModelDto.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun addModel(
        providerId: Int,
        modelId: String,
        displayName: String?,
        contextLength: Int?,
        avatarColor: String?,
    ) {
        val body = jsonBody(
            mapOf(
                "model_id" to modelId,
                "display_name" to displayName,
                "context_length" to contextLength,
                "avatar_color" to avatarColor,
            ),
        )
        request(Request.Builder().url(url("/api/providers/$providerId/models")).post(body))
    }

    suspend fun patchModel(
        id: Int,
        isEnabled: Boolean? = null,
        displayName: String? = null,
        contextLength: Int? = null,
        avatarColor: String? = null,
    ) {
        val body = jsonBody(
            mapOf(
                "is_enabled" to isEnabled,
                "display_name" to displayName,
                "context_length" to contextLength,
                "avatar_color" to avatarColor,
            ),
        )
        request(Request.Builder().url(url("/api/models/$id")).patch(body))
    }

    suspend fun updateModel(
        id: Int,
        displayName: String,
        contextLength: Int?,
        avatarColor: String?,
    ) {
        val body = JSONObject()
            .put("display_name", displayName)
            .put("context_length", contextLength ?: JSONObject.NULL)
            .put("avatar_color", avatarColor ?: JSONObject.NULL)
            .toString()
            .toRequestBody(json)
        request(Request.Builder().url(url("/api/models/$id")).patch(body))
    }

    suspend fun deleteModel(id: Int) {
        request(Request.Builder().url(url("/api/models/$id")).delete())
    }

    suspend fun enabledModels(): List<ModelDto> {
        val arr = JSONArray(request(Request.Builder().url(url("/api/models"))))
        return (0 until arr.length()).map { ModelDto.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun conversations(): List<ConversationDto> {
        val arr = JSONArray(request(Request.Builder().url(url("/api/conversations"))))
        return (0 until arr.length()).map { ConversationDto.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun conversation(id: Int): ConversationDto {
        return ConversationDto.fromJson(
            JSONObject(request(Request.Builder().url(url("/api/conversations/$id"))))
        )
    }

    suspend fun openConversation(modelId: Int): ConversationDto {
        val body = jsonBody(mapOf("kind" to "bot", "model_id" to modelId))
        return ConversationDto.fromJson(
            JSONObject(request(Request.Builder().url(url("/api/conversations")).post(body)))
        )
    }

    suspend fun openHumanConversation(peerUserId: String): ConversationDto {
        val body = jsonBody(mapOf("kind" to "human", "peer_user_id" to peerUserId))
        return ConversationDto.fromJson(
            JSONObject(request(Request.Builder().url(url("/api/conversations")).post(body)))
        )
    }

    suspend fun registerUser(userId: String, displayName: String): UserDto {
        val body = jsonBody(mapOf("user_id" to userId, "display_name" to displayName))
        return UserDto.fromJson(
            JSONObject(request(Request.Builder().url(url("/api/users")).post(body)))
        )
    }

    suspend fun searchUser(userId: String): UserDto {
        return UserDto.fromJson(
            JSONObject(request(Request.Builder().url(url("/api/users/$userId"))))
        )
    }

    suspend fun me(): UserDto {
        return UserDto.fromJson(
            JSONObject(request(Request.Builder().url(url("/api/me"))))
        )
    }

    suspend fun messages(conversationId: Int): List<MessageDto> {
        val arr = JSONArray(request(Request.Builder().url(url("/api/conversations/$conversationId/messages"))))
        return (0 until arr.length()).map { MessageDto.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun updateConversation(conversationId: Int, systemPrompt: String) {
        val body = jsonBody(mapOf("system_prompt" to systemPrompt))
        request(Request.Builder().url(url("/api/conversations/$conversationId")).put(body))
    }

    suspend fun clearMessages(conversationId: Int) {
        request(Request.Builder().url(url("/api/conversations/$conversationId/messages")).delete())
    }

    suspend fun markRead(conversationId: Int) {
        request(Request.Builder().url(url("/api/conversations/$conversationId/read")).post("{}".toRequestBody(json)))
    }

    fun startChat(
        conversationId: Int,
        content: String,
        regenerate: Boolean = false,
        onStart: (Call) -> Unit = {},
        onEvent: (ChatEvent) -> Unit,
    ): Call {
        val body = jsonBody(
            mapOf(
                "conversation_id" to conversationId,
                "content" to content,
                "regenerate" to regenerate,
            ),
        )
        val req = Request.Builder().url(url("/api/chat")).post(body).build()
        val call = streamHttp.newCall(req)
        onStart(call)
        Thread {
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val responseBody = resp.body?.string().orEmpty()
                        onEvent(
                            ChatEvent.Error(
                                "request_error",
                                errorMessage(responseBody, resp.code),
                            ),
                        )
                        return@Thread
                    }
                    val parser = SseParser(onEvent)
                    val source = resp.body!!.source()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        parser.handleLine(line)
                    }
                }
            } catch (e: Exception) {
                if (!call.isCanceled()) {
                    onEvent(ChatEvent.Error("network_error", "网络错误：${e.javaClass.simpleName}"))
                }
            }
        }.apply { name = "ChatHub-SSE-$conversationId" }.start()
        return call
    }
}
