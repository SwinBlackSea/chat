package online.xiaoxi.chathub.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val keyUrl = stringPreferencesKey("server_url")
    private val keyUserId = stringPreferencesKey("user_id")
    private val keyDisplayName = stringPreferencesKey("display_name")

    val serverUrl: Flow<String> = context.dataStore.data.map { it[keyUrl] ?: "" }

    /** 自托管身份：user_id 即"我是谁"（人人聊天用），无登录体系。 */
    val userId: Flow<String> = context.dataStore.data.map { it[keyUserId] ?: "" }
    val displayName: Flow<String> = context.dataStore.data.map { it[keyDisplayName] ?: "" }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[keyUrl] = url }
    }

    suspend fun setIdentity(userId: String, displayName: String) {
        context.dataStore.edit {
            it[keyUserId] = userId
            it[keyDisplayName] = displayName
        }
    }
}
