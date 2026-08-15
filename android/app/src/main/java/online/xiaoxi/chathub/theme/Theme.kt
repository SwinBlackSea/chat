package online.xiaoxi.chathub.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 极简色板：中性浅灰层次 + 微信绿克制点缀（Apple Notes 式干净）
val WxGreen = Color(0xFF07C160)
val WxGreenDark = Color(0xFF06AD56)
val WxBubbleMe = Color(0xFF95EC69)
val WxBackground = Color(0xFFF5F5F7)      // 页面底（iOS 浅灰）
val WxChatBackground = Color(0xFFF2F2F4)  // 聊天窗口底（更浅）
val WxCard = Color(0xFFFFFFFF)            // 卡片白
val WxLine = Color(0xFFE8E8EA)            // 发丝分割线
val WxLineStrong = Color(0xFFDDDDDF)
val WxText = Color(0xFF1A1A1A)            // 主文字
val WxText2 = Color(0xFF8A8A8E)           // 次级文字（iOS 灰）
val WxText3 = Color(0xFFC0C0C4)           // 弱化文字
val WxRed = Color(0xFFFA5151)

// 气泡圆角规范
val BubbleRadius = 12.dp
val CardRadius = 12.dp

private val ColorScheme = lightColorScheme(
    primary = WxGreen,
    onPrimary = Color.White,
    secondary = WxGreen,
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
