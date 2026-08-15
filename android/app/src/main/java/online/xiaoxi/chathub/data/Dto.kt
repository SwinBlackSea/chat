package online.xiaoxi.chathub.data

import org.json.JSONObject

data class ProviderDto(
    val id: Int,
    val name: String,
    val kind: String,
    val baseUrl: String,
    val apiKeyMasked: String,
    val isEnabled: Boolean,
    val modelCount: Int,
) {
    companion object {
        fun fromJson(o: JSONObject) = ProviderDto(
            id = o.getInt("id"),
            name = o.getString("name"),
            kind = o.getString("kind"),
            baseUrl = o.getString("base_url"),
            apiKeyMasked = o.getString("api_key_masked"),
            isEnabled = o.getBoolean("is_enabled"),
            modelCount = o.getInt("model_count"),
        )
    }
}

data class ModelDto(
    val id: Int,
    val providerId: Int,
    val providerName: String,
    val modelId: String,
    val displayName: String,
    val avatarColor: String?,
    val contextLength: Int?,
    val isEnabled: Boolean,
) {
    companion object {
        fun fromJson(o: JSONObject) = ModelDto(
            id = o.getInt("id"),
            providerId = o.getInt("provider_id"),
            providerName = o.optString("provider_name"),
            modelId = o.getString("model_id"),
            displayName = o.getString("display_name"),
            avatarColor = o.optString("avatar_color").takeIf { it.isNotEmpty() && it != "null" },
            contextLength = if (o.isNull("context_length")) null else o.getInt("context_length"),
            isEnabled = o.getBoolean("is_enabled"),
        )
    }
}

data class ConversationDto(
    val id: Int,
    val kind: String,
    val modelId: Int?,
    val contactName: String,
    val modelCode: String,
    val providerName: String,
    val avatarColor: String?,
    val contextLength: Int?,
    val systemPrompt: String?,
    val peerUserId: String?,
    val lastMessagePreview: String?,
    val lastMessageTime: String?,
    val updatedAt: String,
    val unreadCount: Int = 0,
) {
    val isHuman: Boolean get() = kind == "human"

    companion object {
        fun fromJson(o: JSONObject) = ConversationDto(
            id = o.getInt("id"),
            kind = o.getString("kind"),
            modelId = if (o.isNull("model_id")) null else o.getInt("model_id"),
            contactName = o.getString("contact_name"),
            modelCode = o.getString("model_code"),
            providerName = o.getString("provider_name"),
            avatarColor = o.optString("avatar_color").takeIf { it.isNotEmpty() && it != "null" },
            contextLength = if (o.isNull("context_length")) null else o.getInt("context_length"),
            systemPrompt = if (o.isNull("system_prompt")) null else o.getString("system_prompt"),
            peerUserId = if (o.isNull("peer_user_id")) null else o.getString("peer_user_id"),
            lastMessagePreview =
                if (o.isNull("last_message_preview")) null else o.getString("last_message_preview"),
            lastMessageTime =
                if (o.isNull("last_message_time")) null else o.getString("last_message_time"),
            updatedAt = o.getString("updated_at"),
            unreadCount = o.optInt("unread_count", 0),
        )
    }
}

data class UserDto(
    val id: Int,
    val userId: String,
    val displayName: String,
    val avatarColor: String?,
) {
    companion object {
        fun fromJson(o: JSONObject) = UserDto(
            id = o.getInt("id"),
            userId = o.getString("user_id"),
            displayName = o.getString("display_name"),
            avatarColor = o.optString("avatar_color").takeIf { it.isNotEmpty() && it != "null" },
        )
    }
}

data class MessageDto(
    val id: Int,
    val role: String,
    val content: String,
    val modelId: String?,
    val senderUserId: String?,
    val error: String?,
    val createdAt: String,
    val read: Boolean = false,
) {
    companion object {
        fun fromJson(o: JSONObject) = MessageDto(
            id = o.getInt("id"),
            role = o.getString("role"),
            content = o.getString("content"),
            modelId = if (o.isNull("model_id")) null else o.getString("model_id"),
            senderUserId = if (o.isNull("sender_user_id")) null else o.getString("sender_user_id"),
            error = if (o.isNull("error")) null else o.getString("error"),
            createdAt = o.getString("created_at"),
            read = o.optBoolean("read", false),
        )
    }
}

data class DefaultModelDto(
    val modelId: String,
    val displayName: String,
    val contextLength: Int?,
)

data class TemplateDto(
    val kind: String,
    val name: String,
    val baseUrl: String,
    val note: String,
    val defaultModels: List<DefaultModelDto>,
) {
    companion object {
        fun fromJson(o: JSONObject): TemplateDto {
            val models = mutableListOf<DefaultModelDto>()
            val arr = o.optJSONArray("default_models")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    models.add(
                        DefaultModelDto(
                            modelId = m.getString("model_id"),
                            displayName = m.getString("display_name"),
                            contextLength = if (m.isNull("context_length")) {
                                null
                            } else {
                                m.getInt("context_length")
                            },
                        ),
                    )
                }
            }
            return TemplateDto(
                kind = o.getString("kind"),
                name = o.getString("name"),
                baseUrl = o.getString("base_url"),
                note = o.optString("note"),
                defaultModels = models,
            )
        }
    }
}

data class TestResult(val ok: Boolean, val message: String)

sealed class ChatEvent {
    data class Token(val delta: String) : ChatEvent()
    data class Done(val messageId: Int) : ChatEvent()
    data class Error(val code: String, val message: String) : ChatEvent()
}
