package online.xiaoxi.chathub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import online.xiaoxi.chathub.theme.WxBackground
import online.xiaoxi.chathub.theme.WxGreen
import online.xiaoxi.chathub.theme.WxLineStrong
import online.xiaoxi.chathub.theme.WxText2
import online.xiaoxi.chathub.data.SettingsStore

@Composable
fun MainScreen(
    onOpenConversation: (Int) -> Unit,
    onAddProvider: () -> Unit,
    onAddContact: () -> Unit,
    settingsStore: SettingsStore,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = WxBackground,
        bottomBar = {
            NavigationBar(
                containerColor = WxBackground,
                tonalElevation = 0.dp,
            ) {
                val items = listOf("聊天" to Icons.Filled.ChatBubble, "联系人" to Icons.Filled.People, "设置" to Icons.Filled.Settings)
                items.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = WxGreen,
                            selectedTextColor = WxGreen,
                            unselectedIconColor = WxText2,
                            unselectedTextColor = WxText2,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        // 三个 Tab 常驻组合：切换只改可见性，不重建不重新请求（瞬时切换）
        Box(Modifier.padding(padding)) {
            ChatListScreen(onOpenConversation, onAddContact, visible = tab == 0)
            ContactsScreen(onOpenConversation, visible = tab == 1)
            SettingsScreen(onAddProvider, settingsStore, visible = tab == 2)
        }
    }
}
