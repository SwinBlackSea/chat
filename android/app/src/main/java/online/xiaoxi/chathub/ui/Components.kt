package online.xiaoxi.chathub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.abs
import online.xiaoxi.chathub.theme.WxText2

private val AVATAR_PALETTE = listOf(
    Color(0xFFE3EEF2) to Color(0xFF3A6B7C),
    Color(0xFFF6E8DC) to Color(0xFF9A6B4F),
    Color(0xFFE6E4F0) to Color(0xFF5E5487),
    Color(0xFFDCEFE3) to Color(0xFF2F6B46),
    Color(0xFFF4E4E8) to Color(0xFF96556A),
)

@Composable
fun Avatar(letter: String, colorHex: String?, size: Dp = 44.dp) {
    val (bg, fg) = remember(letter, colorHex) {
        val parsed = colorHex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)
        if (parsed != null) {
            Color(0xFF000000 or (parsed and 0xFFFFFF)) to Color(0xFFFFFFFF)
        } else {
            AVATAR_PALETTE[abs((letter.hashCode())) % AVATAR_PALETTE.size]
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter.take(1).uppercase(),
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String) : MarkdownBlock
    data class Code(val language: String, val content: String) : MarkdownBlock
}

private val unorderedItem = Regex("^\\s*[-+*]\\s+(.+)$")
private val orderedItem = Regex("^\\s*(\\d+)\\.\\s+(.+)$")

private fun parseMarkdown(text: String): List<MarkdownBlock> {
    val lines = text.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            line.startsWith("```") -> {
                flushParagraph()
                val language = line.removePrefix("```").trim()
                val code = mutableListOf<String>()
                index += 1
                while (index < lines.size && !lines[index].startsWith("```")) {
                    code += lines[index]
                    index += 1
                }
                blocks += MarkdownBlock.Code(language, code.joinToString("\n").trimEnd())
            }
            line.isBlank() -> flushParagraph()
            line.startsWith("#") && line.trimStart('#').startsWith(" ") -> {
                flushParagraph()
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                blocks += MarkdownBlock.Heading(level, line.drop(level).trim())
            }
            line.startsWith("> ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(line.removePrefix("> "))
            }
            unorderedItem.matches(line) -> {
                flushParagraph()
                blocks += MarkdownBlock.ListItem("•", unorderedItem.matchEntire(line)!!.groupValues[1])
            }
            orderedItem.matches(line) -> {
                flushParagraph()
                val match = orderedItem.matchEntire(line)!!
                blocks += MarkdownBlock.ListItem("${match.groupValues[1]}.", match.groupValues[2])
            }
            else -> paragraph += line
        }
        index += 1
    }
    flushParagraph()
    return blocks
}

private fun inlineMarkdown(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < text.length) {
        val boldMarker = when {
            text.startsWith("**", cursor) -> "**"
            text.startsWith("__", cursor) -> "__"
            else -> null
        }
        when {
            boldMarker != null -> {
                val end = text.indexOf(boldMarker, cursor + 2)
                if (end > cursor + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                        append(text.substring(cursor + 2, end))
                    }
                    cursor = end + 2
                } else {
                    append(text[cursor])
                    cursor += 1
                }
            }
            text[cursor] == '`' -> {
                val end = text.indexOf('`', cursor + 1)
                if (end > cursor + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x1A000000),
                            color = color,
                        ),
                    ) {
                        append(text.substring(cursor + 1, end))
                    }
                    cursor = end + 1
                } else {
                    append(text[cursor])
                    cursor += 1
                }
            }
            text[cursor] == '*' || text[cursor] == '_' -> {
                val marker = text[cursor]
                val end = text.indexOf(marker, cursor + 1)
                if (end > cursor + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color)) {
                        append(text.substring(cursor + 1, end))
                    }
                    cursor = end + 1
                } else {
                    append(marker)
                    cursor += 1
                }
            }
            text[cursor] == '[' -> {
                val labelEnd = text.indexOf(']', cursor + 1)
                val urlStart = if (labelEnd >= 0 && labelEnd + 1 < text.length && text[labelEnd + 1] == '(') {
                    labelEnd + 2
                } else {
                    -1
                }
                val urlEnd = if (urlStart >= 0) text.indexOf(')', urlStart) else -1
                if (urlEnd > urlStart) {
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF576B95),
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        append(text.substring(cursor + 1, labelEnd))
                    }
                    cursor = urlEnd + 1
                } else {
                    append(text[cursor])
                    cursor += 1
                }
            }
            else -> {
                val next = listOf("**", "__", "`", "*", "_", "[")
                    .map { text.indexOf(it, cursor + 1) }
                    .filter { it >= 0 }
                    .minOrNull() ?: text.length
                append(text.substring(cursor, next))
                cursor = next
            }
        }
    }
}

private val CODE_KEYWORDS = setOf(
    "as", "async", "await", "break", "case", "catch", "class", "const", "continue",
    "data", "def", "do", "else", "enum", "false", "finally", "for", "fun", "function",
    "if", "import", "in", "interface", "is", "let", "new", "null", "object", "package",
    "pass", "private", "protected", "public", "return", "sealed", "static", "suspend", "this",
    "throw", "true", "try", "type", "val", "var", "void", "when", "while", "yield",
)

private val codeToken = Regex(
    "//[^\\n]*|#[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|" +
        "\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b",
)

private fun highlightedCode(code: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    codeToken.findAll(code).forEach { match ->
        append(code.substring(cursor, match.range.first))
        val token = match.value
        val style = when {
            token.startsWith("//") || token.startsWith("#") -> SpanStyle(Color(0xFF7F848E))
            token.startsWith('"') || token.startsWith('\'') -> SpanStyle(Color(0xFF98C379))
            token.firstOrNull()?.isDigit() == true -> SpanStyle(Color(0xFFD19A66))
            token in CODE_KEYWORDS -> SpanStyle(Color(0xFFC678DD), fontWeight = FontWeight.Medium)
            else -> SpanStyle(Color(0xFF61AFEF))
        }
        withStyle(style) { append(token) }
        cursor = match.range.last + 1
    }
    append(code.substring(cursor))
}

@Composable
fun MessageContent(text: String, textColor: Color) {
    val blocks = remember(text) { parseMarkdown(text) }
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> {
                    var copied by remember(block.content) { mutableStateOf(false) }
                    Column(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .background(Color(0xFF282C34), RoundedCornerShape(6.dp))
                        .fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                block.language.ifBlank { "代码" },
                                color = Color(0xFFABB2BF),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(block.content))
                                    copied = true
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                    contentDescription = if (copied) "已复制" else "复制代码",
                                    tint = if (copied) Color(0xFF98C379) else Color(0xFFABB2BF),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                        SelectionContainer {
                            Text(
                                text = highlightedCode(block.content),
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFFDCDFE4),
                            )
                        }
                    }
                }
                is MarkdownBlock.Heading -> Text(
                    text = inlineMarkdown(block.text, textColor),
                    color = textColor,
                    fontSize = (20 - block.level).coerceAtLeast(15).sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 3.dp, bottom = 2.dp),
                )
                is MarkdownBlock.ListItem -> Row(Modifier.padding(vertical = 1.dp)) {
                    Text(block.marker, color = textColor, modifier = Modifier.width(24.dp))
                    Text(
                        inlineMarkdown(block.text, textColor),
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                    )
                }
                is MarkdownBlock.Quote -> Row(Modifier.padding(vertical = 3.dp)) {
                    Box(Modifier.width(3.dp).size(width = 3.dp, height = 22.dp).background(WxText2))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        inlineMarkdown(block.text, WxText2),
                        color = WxText2,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                }
                is MarkdownBlock.Paragraph -> Text(
                    text = inlineMarkdown(block.text, textColor),
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                )
            }
        }
    }
}

fun formatWhen(iso: String?): String {
    if (iso == null) return ""
    return try {
        val date = iso.substring(0, 10)
        if (date == LocalDate.now().toString()) {
            iso.substring(11, 16)
        } else {
            date.substring(5).replace('-', '/')
        }
    } catch (_: Exception) {
        ""
    }
}
