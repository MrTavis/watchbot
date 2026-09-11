package com.watchbot.mathsync.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState

sealed class ContentBlock {
    data class Header(val text: String, val level: Int) : ContentBlock()
    data class DisplayMath(val formula: String) : ContentBlock()
    data class Paragraph(val text: String) : ContentBlock()
    data class BulletItem(val text: String) : ContentBlock()
}

@Composable
fun NativeMathViewer(
    markdownContent: String,
    fontSizeSp: Float,
    listState: ScalingLazyListState = rememberScalingLazyListState(),
    modifier: Modifier = Modifier
) {
    if (markdownContent.isBlank()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "⌚", fontSize = 28.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ожидание решения...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Скопируйте текст в Gemini и нажмите «Вставить» на телефоне",
                    fontSize = 11.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val blocks = remember(markdownContent) {
        parseMarkdownToBlocks(markdownContent)
    }

    ScalingLazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentPadding = PaddingValues(top = 42.dp, start = 14.dp, end = 14.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(blocks.size) { index ->
            when (val block = blocks[index]) {
                is ContentBlock.Header -> {
                    Text(
                        text = block.text,
                        fontSize = (fontSizeSp * (if (block.level == 1) 1.25f else 1.1f)).sp,
                        fontWeight = FontWeight.Bold,
                        color = if (block.level == 1) Color(0xFF00E5FF) else Color(0xFFFFB74D),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is ContentBlock.DisplayMath -> {
                    // Display math block in dark card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141416), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatLatexToUnicode(block.formula),
                            fontSize = (fontSizeSp * 1.08f).sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFE0F7FA),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is ContentBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            fontSize = fontSizeSp.sp,
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = renderInlineMarkdown(block.text, fontSizeSp),
                            fontSize = fontSizeSp.sp,
                            color = Color(0xFFE0E0E0),
                            lineHeight = (fontSizeSp * 1.35f).sp
                        )
                    }
                }
                is ContentBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text, fontSizeSp),
                        fontSize = fontSizeSp.sp,
                        color = Color(0xFFE0E0E0),
                        lineHeight = (fontSizeSp * 1.35f).sp
                    )
                }
            }
        }
    }
}

private fun parseMarkdownToBlocks(content: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val lines = content.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i].trim()

        if (line.isBlank()) {
            i++
            continue
        }

        // 1. Block math: $$ ... $$
        if (line.startsWith("$$")) {
            val mathLines = mutableListOf<String>()
            var mathContent = line.removePrefix("$$").trim()
            if (mathContent.endsWith("$$") && mathContent.length > 2) {
                mathContent = mathContent.removeSuffix("$$").trim()
                blocks.add(ContentBlock.DisplayMath(mathContent))
                i++
                continue
            } else {
                mathLines.add(mathContent)
                i++
                while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                    mathLines.add(lines[i].trim())
                    i++
                }
                if (i < lines.size) {
                    mathLines.add(lines[i].trim().removeSuffix("$$").trim())
                    i++
                }
                blocks.add(ContentBlock.DisplayMath(mathLines.joinToString(" ")))
                continue
            }
        }

        // 2. Headers: #, ##, ###
        if (line.startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length
            val text = line.drop(level).trim()
            blocks.add(ContentBlock.Header(text, level))
            i++
            continue
        }

        // 3. Bullet items: * or -
        if (line.startsWith("* ") || line.startsWith("- ") || line.startsWith("• ")) {
            val text = line.drop(2).trim()
            blocks.add(ContentBlock.BulletItem(text))
            i++
            continue
        }

        // 4. Numbered list: 1. 2. etc.
        val numberedMatch = Regex("^\\d+\\.\\s+(.*)").find(line)
        if (numberedMatch != null) {
            val text = numberedMatch.groupValues[1]
            blocks.add(ContentBlock.BulletItem(text))
            i++
            continue
        }

        // 5. Default paragraph
        blocks.add(ContentBlock.Paragraph(line))
        i++
    }

    return blocks
}

private fun renderInlineMarkdown(text: String, fontSizeSp: Float) = buildAnnotatedString {
    // Process math $...$ first into unicode
    val withMath = text.replace(Regex("\\$([^$\\n]+?)\\$")) { match ->
        formatLatexToUnicode(match.groupValues[1])
    }

    // Parse **bold** and *italic*
    val parts = withMath.split(Regex("(\\*\\*|\\*)"))
    var isBold = false
    var isItalic = false

    // Simple parser for **bold**
    var cursor = 0
    val boldPattern = Regex("\\*\\*(.*?)\\*\\*")
    var lastIndex = 0

    boldPattern.findAll(withMath).forEach { match ->
        if (match.range.first > lastIndex) {
            append(withMath.substring(lastIndex, match.range.first))
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
            append(match.groupValues[1])
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < withMath.length) {
        append(withMath.substring(lastIndex))
    }
}

fun formatLatexToUnicode(latex: String): String {
    var s = latex.trim()
        .replace("\\frac", "FRAC")
        .replace("\\sqrt", "√")
        .replace("\\int", "∫")
        .replace("\\infty", "∞")
        .replace("\\hbar", "ℏ")
        .replace("\\nabla", "∇")
        .replace("\\partial", "∂")
        .replace("\\times", "×")
        .replace("\\cdot", "·")
        .replace("\\approx", "≈")
        .replace("\\neq", "≠")
        .replace("\\le", "≤")
        .replace("\\ge", "≥")
        .replace("\\pm", "±")
        .replace("\\mp", "∓")
        .replace("\\Rightarrow", "⇒")
        .replace("\\rightarrow", "→")
        .replace("\\to", "→")
        .replace("\\alpha", "α")
        .replace("\\beta", "β")
        .replace("\\gamma", "γ")
        .replace("\\delta", "δ")
        .replace("\\epsilon", "ε")
        .replace("\\theta", "θ")
        .replace("\\lambda", "λ")
        .replace("\\mu", "μ")
        .replace("\\pi", "π")
        .replace("\\rho", "ρ")
        .replace("\\sigma", "σ")
        .replace("\\tau", "τ")
        .replace("\\phi", "φ")
        .replace("\\psi", "ψ")
        .replace("\\omega", "ω")
        .replace("\\Delta", "Δ")
        .replace("\\Omega", "Ω")
        .replace("\\in", "∈")
        .replace("\\notin", "∉")
        .replace("\\subset", "⊂")
        .replace("\\cup", "∪")
        .replace("\\cap", "∩")
        .replace("\\left(", "(")
        .replace("\\right)", ")")
        .replace("\\left[", "[")
        .replace("\\right]", "]")
        .replace("\\left\\{", "{")
        .replace("\\right\\}", "}")
        .replace("\\{", "{")
        .replace("\\}", "}")
        .replace("\\quad", " ")
        .replace("\\qquad", "  ")

    // Remove \text{...}
    s = s.replace(Regex("\\\\text\\{([^}]+)\\}"), "$1")
    s = s.replace(Regex("\\\\mathrm\\{([^}]+)\\}"), "$1")
    s = s.replace(Regex("\\\\mathbf\\{([^}]+)\\}"), "$1")

    // Fractions: FRAC{a}{b} -> (a) / (b)
    s = s.replace(Regex("FRAC\\{([^}]+)\\}\\{([^}]+)\\}"), "($1)/($2)")

    // Superscripts
    s = s.replace("^2", "²")
        .replace("^3", "³")
        .replace("^0", "⁰")
        .replace("^1", "¹")
        .replace("^4", "⁴")
        .replace("^5", "⁵")
        .replace("^6", "⁶")
        .replace("^7", "⁷")
        .replace("^8", "⁸")
        .replace("^9", "⁹")
        .replace("^n", "ⁿ")
        .replace("^x", "ˣ")
        .replace("^t", "ᵗ")
        .replace("^{+}", "⁺")
        .replace("^{-}", "⁻")

    // Subscripts
    s = s.replace("_0", "₀")
        .replace("_1", "₁")
        .replace("_2", "₂")
        .replace("_3", "₃")
        .replace("_4", "₄")
        .replace("_5", "₅")
        .replace("_6", "₆")
        .replace("_7", "₇")
        .replace("_8", "₈")
        .replace("_9", "₉")
        .replace("_n", "ₙ")
        .replace("_k", "ₖ")
        .replace("_i", "ᵢ")
        .replace("_j", "ⱼ")
        .replace("_x", "ₓ")

    // Clean remaining braces around single items
    s = s.replace(Regex("\\{([a-zA-Z0-9α-ωΑ-Ω∞])\\}"), "$1")

    return s
}
