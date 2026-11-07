package ai.venice.app.ui.components

import ai.venice.app.ui.theme.TextMuted
import ai.venice.app.ui.theme.TextPrimary
import ai.venice.app.ui.theme.TextSecondary
import ai.venice.app.ui.theme.VeniceBorder
import ai.venice.app.ui.theme.VeniceCyan
import ai.venice.app.ui.theme.VeniceTangerine
import ai.venice.app.ui.theme.VeniceObsidian
import ai.venice.app.ui.theme.VeniceSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    data class BulletItem(val bullet: String, val text: String) : MarkdownBlock()
    data object Divider : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = TextPrimary
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val clipboard = LocalClipboardManager.current

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val fontSize = when (block.level) {
                            1 -> 21.sp
                            2 -> 18.sp
                            3 -> 16.sp
                            else -> 15.sp
                        }
                        Text(
                            text = parseInlineMarkdown(block.text),
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = VeniceCyan,
                            lineHeight = (fontSize.value + 6).sp
                        )
                    }
                    is MarkdownBlock.CodeBlock -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(VeniceObsidian)
                                .border(1.dp, VeniceBorder, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(VeniceSurface)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = block.language.ifBlank { "code" }.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VeniceCyan
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            clipboard.setText(AnnotatedString(block.code))
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = block.code,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                    is MarkdownBlock.Quote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(VeniceSurface.copy(alpha = 0.6f))
                                .padding(vertical = 6.dp, horizontal = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(VeniceTangerine, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = parseInlineMarkdown(block.text),
                                fontSize = 14.sp,
                                fontStyle = FontStyle.Italic,
                                color = TextSecondary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                    is MarkdownBlock.BulletItem -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = block.bullet,
                                fontWeight = FontWeight.Bold,
                                color = VeniceCyan,
                                fontSize = 15.sp,
                                modifier = Modifier.width(22.dp)
                            )
                            Text(
                                text = parseInlineMarkdown(block.text),
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MarkdownBlock.Divider -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = VeniceBorder
                        )
                    }
                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = parseInlineMarkdown(block.text),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    val paragraphBuffer = StringBuilder()

    fun flushParagraph() {
        val text = paragraphBuffer.toString().trim()
        if (text.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(text))
        }
        paragraphBuffer.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val lang = trimmed.removePrefix("```").trim()
                val codeBuilder = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (codeBuilder.isNotEmpty()) codeBuilder.append('\n')
                    codeBuilder.append(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(lang, codeBuilder.toString()))
            }
            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Divider)
            }
            trimmed.startsWith("#") && trimmed.contains(" ") -> {
                val hashes = trimmed.takeWhile { it == '#' }
                if (hashes.length in 1..6 && trimmed.getOrNull(hashes.length) == ' ') {
                    flushParagraph()
                    blocks.add(
                        MarkdownBlock.Heading(
                            level = hashes.length,
                            text = trimmed.substring(hashes.length + 1).trim()
                        )
                    )
                } else {
                    if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append('\n')
                    paragraphBuffer.append(line)
                }
            }
            trimmed.startsWith("> ") || trimmed == ">" -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Quote(trimmed.removePrefix(">").trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.BulletItem("•", trimmed.substring(2).trim()))
            }
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                flushParagraph()
                val dotIndex = trimmed.indexOf('.')
                val num = trimmed.substring(0, dotIndex + 1)
                val rest = trimmed.substring(dotIndex + 1).trim()
                blocks.add(MarkdownBlock.BulletItem(num, rest))
            }
            trimmed.isEmpty() -> {
                flushParagraph()
            }
            else -> {
                if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append('\n')
                paragraphBuffer.append(line)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        val len = text.length
        while (idx < len) {
            when {
                text.startsWith("`", idx) -> {
                    val end = text.indexOf('`', idx + 1)
                    if (end != -1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFF1F273B),
                                color = VeniceCyan,
                                fontSize = 13.5.sp
                            )
                        )
                        append(" ${text.substring(idx + 1, end)} ")
                        pop()
                        idx = end + 1
                    } else {
                        append(text[idx])
                        idx++
                    }
                }
                text.startsWith("**", idx) -> {
                    val end = text.indexOf("**", idx + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White))
                        append(text.substring(idx + 2, end))
                        pop()
                        idx = end + 2
                    } else {
                        append(text[idx])
                        idx++
                    }
                }
                text.startsWith("*", idx) && !text.startsWith("**", idx) -> {
                    val end = text.indexOf('*', idx + 1)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.substring(idx + 1, end))
                        pop()
                        idx = end + 1
                    } else {
                        append(text[idx])
                        idx++
                    }
                }
                text.startsWith("[", idx) -> {
                    val closeBracket = text.indexOf(']', idx + 1)
                    val openParen = if (closeBracket != -1) text.indexOf('(', closeBracket + 1) else -1
                    val closeParen = if (openParen == closeBracket + 1) text.indexOf(')', openParen + 1) else -1
                    if (closeBracket != -1 && openParen == closeBracket + 1 && closeParen != -1) {
                        val label = text.substring(idx + 1, closeBracket)
                        val url = text.substring(openParen + 1, closeParen)
                        pushStringAnnotation(tag = "URL", annotation = url)
                        pushStyle(
                            SpanStyle(
                                color = VeniceCyan,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        append(label)
                        pop()
                        pop()
                        idx = closeParen + 1
                    } else {
                        append(text[idx])
                        idx++
                    }
                }
                else -> {
                    append(text[idx])
                    idx++
                }
            }
        }
    }
}
