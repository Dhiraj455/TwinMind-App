package com.example.twinmind2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.twinmind2.ui.theme.TextPrimary

// ─── LaTeX preprocessing ──────────────────────────────────────────────────────

private fun toSuperscript(c: Char): String = when (c) {
    '0' -> "⁰"; '1' -> "¹"; '2' -> "²"; '3' -> "³"; '4' -> "⁴"
    '5' -> "⁵"; '6' -> "⁶"; '7' -> "⁷"; '8' -> "⁸"; '9' -> "⁹"
    'n' -> "ⁿ"; 'i' -> "ⁱ"; '+' -> "⁺"; '-' -> "⁻"; 'a' -> "ᵃ"
    'b' -> "ᵇ"; 'c' -> "ᶜ"; 'd' -> "ᵈ"; 'e' -> "ᵉ"; 'k' -> "ᵏ"
    'm' -> "ᵐ"; 'o' -> "ᵒ"; 'p' -> "ᵖ"; 'r' -> "ʳ"; 's' -> "ˢ"
    't' -> "ᵗ"; 'u' -> "ᵘ"; 'v' -> "ᵛ"; 'w' -> "ʷ"; 'x' -> "ˣ"; 'y' -> "ʸ"
    else -> c.toString()
}

private fun toSubscript(c: Char): String = when (c) {
    '0' -> "₀"; '1' -> "₁"; '2' -> "₂"; '3' -> "₃"; '4' -> "₄"
    '5' -> "₅"; '6' -> "₆"; '7' -> "₇"; '8' -> "₈"; '9' -> "₉"
    'i' -> "ᵢ"; 'j' -> "ⱼ"; 'n' -> "ₙ"; 'u' -> "ᵤ"; 'v' -> "ᵥ"
    'a' -> "ₐ"; 'e' -> "ₑ"; 'o' -> "ₒ"; 'x' -> "ₓ"; 'k' -> "ₖ"
    else -> c.toString()
}

/** Transforms LaTeX content string into Unicode-readable math text. */
private fun transformLatex(src: String): String {
    var s = src

    for (cmd in listOf("\\\\text", "\\\\mathrm", "\\\\mathbf", "\\\\mathit", "\\\\operatorname")) {
        s = s.replace(Regex("$cmd\\{([^{}]*)\\}")) { it.groupValues[1] }
    }

    repeat(4) {
        s = s.replace(Regex("\\\\frac\\{([^{}]*)\\}\\{([^{}]*)\\}")) { m ->
            "(${m.groupValues[1]})/(${m.groupValues[2]})"
        }
    }

    s = s.replace(Regex("\\\\sqrt\\{([^{}]*)\\}")) { "√(${it.groupValues[1]})" }
    s = s.replace("\\sqrt", "√")

    val greek = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
        "\\theta" to "θ", "\\vartheta" to "θ", "\\iota" to "ι", "\\kappa" to "κ",
        "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ",
        "\\pi" to "π", "\\varpi" to "π", "\\rho" to "ρ", "\\varrho" to "ρ",
        "\\sigma" to "σ", "\\varsigma" to "ς", "\\tau" to "τ", "\\upsilon" to "υ",
        "\\phi" to "φ", "\\varphi" to "φ", "\\chi" to "χ", "\\psi" to "ψ",
        "\\omega" to "ω", "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ",
        "\\Lambda" to "Λ", "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ",
        "\\Upsilon" to "Υ", "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω"
    )
    greek.forEach { (k, v) -> s = s.replace(k, v) }

    val ops = mapOf(
        "\\sum" to "Σ", "\\prod" to "Π", "\\int" to "∫", "\\oint" to "∮",
        "\\partial" to "∂", "\\nabla" to "∇", "\\infty" to "∞",
        "\\times" to "×", "\\div" to "÷", "\\cdot" to "·", "\\pm" to "±", "\\mp" to "∓",
        "\\geq" to "≥", "\\ge" to "≥", "\\leq" to "≤", "\\le" to "≤",
        "\\neq" to "≠", "\\ne" to "≠", "\\approx" to "≈", "\\equiv" to "≡",
        "\\in" to "∈", "\\notin" to "∉", "\\subset" to "⊂", "\\supset" to "⊃",
        "\\subseteq" to "⊆", "\\supseteq" to "⊇", "\\cup" to "∪", "\\cap" to "∩",
        "\\forall" to "∀", "\\exists" to "∃", "\\nexists" to "∄",
        "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←",
        "\\leftrightarrow" to "↔", "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐",
        "\\Leftrightarrow" to "⇔", "\\mapsto" to "↦",
        "\\max" to "max", "\\min" to "min", "\\sup" to "sup", "\\inf" to "inf",
        "\\log" to "log", "\\ln" to "ln", "\\exp" to "exp",
        "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan",
        "\\lim" to "lim", "\\det" to "det", "\\dim" to "dim", "\\ker" to "ker",
        "\\cdots" to "⋯", "\\ldots" to "…", "\\vdots" to "⋮", "\\ddots" to "⋱"
    )
    ops.forEach { (k, v) -> s = s.replace(k, v) }

    repeat(3) {
        s = s.replace(Regex("\\^\\{([^{}]*)\\}")) { m ->
            m.groupValues[1].map { toSuperscript(it) }.joinToString("")
        }
    }
    s = s.replace(Regex("\\^([a-zA-Z0-9+\\-])")) { m ->
        m.groupValues[1].map { toSuperscript(it) }.joinToString("")
    }

    repeat(3) {
        s = s.replace(Regex("_\\{([^{}]*)\\}")) { m ->
            m.groupValues[1].map { toSubscript(it) }.joinToString("")
        }
    }
    s = s.replace(Regex("_([a-zA-Z0-9])")) { m ->
        m.groupValues[1].map { toSubscript(it) }.joinToString("")
    }

    s = s.replace("\\left(", "(").replace("\\right)", ")")
        .replace("\\left[", "[").replace("\\right]", "]")
        .replace("\\left|", "|").replace("\\right|", "|")
        .replace("\\left\\{", "{").replace("\\right\\}", "}")

    s = s.replace(Regex("\\\\[a-zA-Z]+\\{([^{}]*)\\}")) { it.groupValues[1] }
    s = s.replace(Regex("\\\\[a-zA-Z]+"), "")
    s = s.replace("{", "").replace("}", "")

    return s.trim()
}

/**
 * Pre-process a full message text:
 *  - $$...$$ → [FORMULA:...] marker (becomes a dedicated formula block)
 *  - $...$ inline → `monospace code` (stands out as math notation)
 */
private fun preprocessLatex(text: String): String {
    if (!text.contains('$') && !text.contains('\\')) return text

    var s = text

    // Protect currency amounts ($100,000, $1.50, etc.) from being treated as LaTeX delimiters
    val currencies = mutableListOf<String>()
    val currencyRegex = Regex("""\$[\d,]+(?:\.\d+)?""")
    s = currencyRegex.replace(s) { match ->
        currencies.add(match.value)
        "«CUR${currencies.size - 1}»"
    }

    s = s.replace(Regex("""\$\$([^\$]+?)\$\$""", RegexOption.DOT_MATCHES_ALL)) { m ->
        val formula = transformLatex(m.groupValues[1].trim().replace('\n', ' '))
        "\n[FORMULA:$formula]\n"
    }

    s = s.replace(Regex("""\$([^\$\n]+?)\$""")) { m ->
        "`${transformLatex(m.groupValues[1].trim())}`"
    }

    // Restore currency amounts
    currencies.forEachIndexed { i, curr -> s = s.replace("«CUR$i»", curr) }

    return s
}

// ─── Markdown data model ──────────────────────────────────────────────────────

private sealed class MdBlock {
    data class Heading(val level: Int, val content: String) : MdBlock()
    data class Paragraph(val content: String) : MdBlock()
    data class BulletItem(val content: String) : MdBlock()
    data class OrderedItem(val number: Int, val content: String) : MdBlock()
    data class CodeBlock(val language: String, val code: String) : MdBlock()
    data class FormulaBlock(val formula: String) : MdBlock()
    object HRule : MdBlock()
}

private fun parseMarkdown(raw: String): List<MdBlock> {
    val text = preprocessLatex(raw)
    val lines = text.lines()
    val result = mutableListOf<MdBlock>()
    var i = 0

    while (i < lines.size) {
        val trimmed = lines[i].trim()

        when {
            trimmed.isEmpty() -> { i++; continue }

            trimmed.startsWith("[FORMULA:") && trimmed.endsWith("]") -> {
                result += MdBlock.FormulaBlock(
                    trimmed.removePrefix("[FORMULA:").dropLast(1).trim()
                )
                i++
            }

            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                i++
                result += MdBlock.CodeBlock(lang, code.toString().trimEnd())
            }

            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length
                result += MdBlock.Heading(level.coerceIn(1, 6), trimmed.drop(level).trim())
                i++
            }

            trimmed.matches(Regex("[-*_]{3,}")) -> { result += MdBlock.HRule; i++ }

            trimmed.matches(Regex("^[-•+]\\s+.*")) || trimmed.matches(Regex("^\\*\\s+.*")) -> {
                result += MdBlock.BulletItem(trimmed.replaceFirst(Regex("^[-*•+]\\s+"), ""))
                i++
            }

            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val num = trimmed.substringBefore(".").toIntOrNull() ?: 1
                result += MdBlock.OrderedItem(num, trimmed.replaceFirst(Regex("^\\d+\\.\\s+"), ""))
                i++
            }

            looksLikeFormula(trimmed.replace("**", "").replace("*", "").trim()) -> {
                result += MdBlock.FormulaBlock(
                    trimmed.replace("**", "").replace("*", "").trim()
                )
                i++
            }

            else -> {
                val buf = StringBuilder()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (l.isEmpty()) break
                    if (l.startsWith("#") || l.startsWith("```")) break
                    if (l.matches(Regex("^[-•+]\\s+.*")) || l.matches(Regex("^\\*\\s+.*"))) break
                    if (l.matches(Regex("^\\d+\\.\\s+.*")) || l.matches(Regex("[-*_]{3,}"))) break
                    if (l.startsWith("[FORMULA:")) break
                    val stripped = l.replace("**", "").replace("*", "").trim()
                    if (looksLikeFormula(stripped)) break
                    if (buf.isNotEmpty()) buf.append(" ")
                    buf.append(l); i++
                }
                val content = buf.toString().trim()
                if (content.isNotBlank()) result += MdBlock.Paragraph(content)
            }
        }
    }
    return result
}

private fun looksLikeFormula(text: String): Boolean {
    if (text.isBlank() || text.length > 80) return false
    val hasMath = text.contains('=') || text.contains('∫') || text.contains('∑') ||
        text.contains('√') || text.contains('≈') || text.contains('≠') || text.contains('∞')
    if (!hasMath) return false
    return Regex("[a-zA-Z]{4,}").findAll(text).count() == 0
}

private val INLINE_REGEX = Regex(
    """\*\*\*(.+?)\*\*\*|\*\*(.+?)\*\*|`(.+?)`|\*([^*\n]+?)\*|_([^_\n]+?)_"""
)

private fun parseInlines(text: String) = buildAnnotatedString {
    var cursor = 0
    INLINE_REGEX.findAll(text).forEach { match ->
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        when {
            match.groupValues[1].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(match.groupValues[1])
                }
            match.groupValues[2].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[2]) }
            match.groupValues[3].isNotEmpty() ->
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFFEEEEEE),
                        color = Color(0xFF333333),
                        fontSize = 13.sp
                    )
                ) { append(match.groupValues[3]) }
            match.groupValues[4].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[4]) }
            match.groupValues[5].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[5]) }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

fun geminiResponseToPlainText(text: String): String {
    var s = preprocessLatex(text)
    s = s.replace(Regex("\\[FORMULA:([^]]*)]"), "$1")
    s = s.replace(Regex("""\*\*\*(.+?)\*\*\*"""), "$1")
    s = s.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    s = s.replace(Regex("""`(.+?)`"""), "$1")
    s = s.replace(Regex("""\*([^*\n]+?)\*"""), "$1")
    s = s.replace(Regex("""_([^_\n]+?)_"""), "$1")
    s = s.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
    s = s.replace(Regex("^[-*•+]\\s+", RegexOption.MULTILINE), "• ")
    s = s.replace(Regex("```[\\s\\S]*?```"), "")
    return s.trim()
}

/** Renders Gemini model text with the same markdown and inline-math rules as chat. */
@Composable
fun GeminiMarkdownContent(
    text: String,
    textColor: Color = TextPrimary,
    fontSize: TextUnit = 15.sp,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val (fs, fw) = when (block.level) {
                        1 -> 19.sp to FontWeight.Bold
                        2 -> 17.sp to FontWeight.Bold
                        else -> 15.sp to FontWeight.SemiBold
                    }
                    Text(
                        text = parseInlines(block.content),
                        fontSize = fs,
                        fontWeight = fw,
                        color = textColor,
                        lineHeight = fs * 1.4f
                    )
                }
                is MdBlock.BulletItem -> {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            "•",
                            color = textColor,
                            fontSize = fontSize,
                            modifier = Modifier.padding(top = 2.dp).width(18.dp)
                        )
                        Text(
                            text = parseInlines(block.content),
                            fontSize = fontSize,
                            color = textColor,
                            lineHeight = fontSize * 1.5f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MdBlock.OrderedItem -> {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            "${block.number}.",
                            color = textColor,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp).widthIn(min = 24.dp)
                        )
                        Text(
                            text = parseInlines(block.content),
                            fontSize = fontSize,
                            color = textColor,
                            lineHeight = fontSize * 1.5f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MdBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0F0F0))
                            .border(1.dp, Color(0xFFD0D0D0), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFF2D2D2D),
                            lineHeight = 20.sp
                        )
                    }
                }
                is MdBlock.FormulaBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFF8E7))
                            .border(1.dp, Color(0xFFE8D088), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = block.formula,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = Color(0xFF5C3D00),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = parseInlines(block.content),
                        fontSize = fontSize,
                        color = textColor,
                        lineHeight = fontSize * 1.5f
                    )
                }
                MdBlock.HRule -> {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE0E0E0)))
                }
            }
        }
    }
}
