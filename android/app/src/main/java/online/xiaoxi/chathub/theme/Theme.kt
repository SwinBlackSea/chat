package online.xiaoxi.chathub.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Codex / Apple Notes 克制色板：中性灰白基调 + 单一琥珀强调色，无多色混搭
val Accent = Color(0xFFD97706)          // 琥珀橙（唯一强调色：按钮/选中/在线）
val AccentSoft = Color(0xFFFEF3C7)      // 琥珀淡底（自己消息）
val FriendBubble = Color(0xFFF2F2F7)    // 对方消息淡灰底（skill 规范）
val WxBackground = Color(0xFFF7F7F8)    // 页面底（近白灰）
val WxChatBackground = Color(0xFFF7F7F8) // 聊天窗口底
val WxCard = Color(0xFFFFFFFF)          // 卡片白
val WxLine = Color(0xFFECECEE)          // 发丝分割线
val WxLineStrong = Color(0xFFE0E0E2)
val WxText = Color(0xFF1A1A1A)          // 主文字（近黑）
val WxText2 = Color(0xFF8E8E93)         // 次级（iOS 灰，skill 规范）
val WxText3 = Color(0xFFC7C7CC)         // 弱化（浅灰）
val WxRed = Color(0xFFFF3B30)

// 圆角规范
val BubbleRadius = 10.dp
val CardRadius = 12.dp

// 兼容旧引用（渐次移除）
val WxGreen = Accent
val WxGreenDark = Color(0xFFB45309)
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
