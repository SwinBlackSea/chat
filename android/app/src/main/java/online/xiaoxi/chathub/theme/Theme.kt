package online.xiaoxi.chathub.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// frontend-design skill 色板：中性灰白基调 + 单一蓝色强调色，无多色混搭
val Accent = Color(0xFF3478F6)          // 蓝（唯一强调色：按钮/选中/发送）
val AccentSoft = Color(0xFFEBF3FF)      // 浅蓝底（自己消息）
val FriendBubble = Color(0xFFF2F2F7)    // 对方消息淡灰底
val WxBackground = Color(0xFFF7F7F8)    // 页面底（近白灰）
val WxChatBackground = Color(0xFFF7F7F8) // 聊天窗口底
val WxCard = Color(0xFFFFFFFF)          // 卡片白
val WxLine = Color(0xFFE5E5EA)          // 发丝分割线
val WxLineStrong = Color(0xFFE0E0E2)
val WxText = Color(0xFF111111)          // 主文字（近黑）
val WxText2 = Color(0xFF8E8E93)         // 次级（iOS 灰）
val WxText3 = Color(0xFFC7C7CC)         // 弱化（浅灰）
val WxRed = Color(0xFFFF3B30)           // 错误（仅错误场景）
val OnlineGreen = Color(0xFF34C759)     // 在线绿点（唯一允许的第二绿）

// 圆角规范
val BubbleRadius = 12.dp
val CardRadius = 12.dp

// 兼容旧引用（渐次移除）
val WxGreen = Accent
val WxGreenDark = Color(0xFF2563EB)
val WxBubbleMe = AccentSoft

private val ColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent,
    background = WxBackground,
    onBackground = WxText,
    surface = WxCard,
    onSurface = WxText,
    error = WxRed,
)

@Composable
fun ChatHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}
