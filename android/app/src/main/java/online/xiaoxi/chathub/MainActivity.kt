package online.xiaoxi.chathub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import online.xiaoxi.chathub.data.Backend
import online.xiaoxi.chathub.data.SettingsStore
import online.xiaoxi.chathub.data.WsHub
import online.xiaoxi.chathub.theme.ChatHubTheme
import online.xiaoxi.chathub.ui.AddContactScreen
import online.xiaoxi.chathub.ui.AddProviderScreen
import online.xiaoxi.chathub.ui.ChatScreen
import online.xiaoxi.chathub.ui.ContactInfoScreen
import online.xiaoxi.chathub.ui.MainScreen
import online.xiaoxi.chathub.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore(applicationContext)
        setContent {
            ChatHubTheme {
                val url by store.serverUrl.collectAsState(initial = null)
                val userId by store.userId.collectAsState(initial = "")
                val displayName by store.displayName.collectAsState(initial = "")
                when {
                    url == null -> Box(Modifier.fillMaxSize())
                    url.isNullOrBlank() -> SetupScreen(store)
                    else -> {
                        Backend.baseUrl = url!!
                        Backend.userId = userId
                        // 身份配置后建立 ws 连接；身份变化时由设置页调用 WsHub.restart
                        LaunchedEffect(userId, displayName) {
                            if (userId.isNotEmpty()) WsHub.start() else WsHub.stop()
                        }
                        AppRoot(store)
                    }
                }
            }
        }
    }
}

@Composable
fun AppRoot(store: SettingsStore) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            MainScreen(
                onOpenConversation = { id -> nav.navigate("chat/$id") },
                onAddProvider = { nav.navigate("addprovider") },
                onAddContact = { nav.navigate("addcontact") },
                settingsStore = store,
            )
        }
        composable("chat/{convId}") { entry ->
            val id = entry.arguments?.getString("convId")?.toIntOrNull() ?: 0
            ChatScreen(
                conversationId = id,
                onBack = { nav.popBackStack() },
                onInfo = { nav.navigate("info/$id") },
            )
        }
        composable("info/{convId}") { entry ->
            val id = entry.arguments?.getString("convId")?.toIntOrNull() ?: 0
            ContactInfoScreen(
                conversationId = id,
                onBack = { nav.popBackStack() },
                onChat = { nav.popBackStack() },
            )
        }
        composable("addprovider") {
            AddProviderScreen(onBack = { nav.popBackStack() })
        }
        composable("addcontact") {
            AddContactScreen(
                onBack = { nav.popBackStack() },
                onOpenConversation = { convId ->
                    nav.popBackStack()
                    nav.navigate("chat/$convId")
                },
            )
        }
    }
}
