package online.xiaoxi.chathub.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WxGreen = Color(0xFF07C160)
val WxGreenDark = Color(0xFF06AD56)
val WxBubbleMe = Color(0xFF95EC69)
val WxBackground = Color(0xFFF7F7F7)
val WxChatBackground = Color(0xFFEDEDED)
val WxLine = Color(0xFFE5E5E5)
val WxLineStrong = Color(0xFFD9D9D9)
val WxText = Color(0xFF111111)
val WxText2 = Color(0xFF888888)
val WxText3 = Color(0xFFB2B2B2)
val WxRed = Color(0xFFFA5151)

private val ColorScheme = lightColorScheme(
    primary = WxGreen,
    onPrimary = Color.White,
    secondary = WxGreen,
    background = WxBackground,
    onBackground = WxText,
    surface = Color.White,
    onSurface = WxText,
    error = WxRed,
)

@Composable
fun ChatHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}
