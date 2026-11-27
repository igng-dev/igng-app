package net.igng.core.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.igng.core.designsystem.IgngColors
import net.igng.core.designsystem.IgngContentImage
import net.igng.core.designsystem.IgngHairlineDivider
import net.igng.core.designsystem.IgngHapticKind
import net.igng.core.designsystem.IgngStroke
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.copyTextToClipboard
import net.igng.core.designsystem.igngPressScale
import net.igng.core.designsystem.readerSurfaceColors
import net.igng.core.designsystem.rememberIgngHaptics
import net.igng.core.model.ReaderTypographyPrefs

/**
 * Shared IGNG Markdown document renderer used across all IGNG family readers.
 */
@Composable
fun IgngMarkdownDocumentView(
    document: MarkdownDocument,
    typography: ReaderTypographyPrefs = ReaderTypographyPrefs(),
    onImageClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
    onInlineCodeClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        document.blocks.forEach { block ->
            IgngMarkdownBlockView(
                block = block,
                typography = typography,
                onImageClick = onImageClick,
                onLinkClick = onLinkClick,
                onMentionClick = onMentionClick,
                onInlineCodeClick = onInlineCodeClick,
            )
        }
    }
}

@Composable
fun IgngMarkdownBlockView(
    block: MarkdownBlock,
    typography: ReaderTypographyPrefs = ReaderTypographyPrefs(),
    onImageClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
    onInlineCodeClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    val radii = IgngTheme.radii
    val (_, primaryText, secondaryText) = colors.readerSurfaceColors(typography.canvasTone)
    val fontFamily = if (typography.preferSerif) FontFamily.Serif else FontFamily.SansSerif
    val bodySize = typography.fontSizeSp.sp
    val bodyLineHeight = (typography.fontSizeSp * typography.lineHeightMultiplier).sp
    val baseBodyStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = bodySize,
        lineHeight = bodyLineHeight,
        color = primaryText,
    )

    when (block) {
        is MarkdownBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.44f
                2 -> 1.26f
                3 -> 1.13f
                else -> 1.04f
            }
            val topSpace = when (block.level) {
                1 -> 14.dp
                2 -> 12.dp
                3 -> 8.dp
                else -> 4.dp
            }
            val annotated = remember(block.inlines, colors, onLinkClick, onMentionClick, onInlineCodeClick) {
                buildInlineAnnotatedString(block.inlines, colors, onLinkClick, onMentionClick, onInlineCodeClick)
            }
            Column(modifier = modifier.padding(top = topSpace)) {
                Text(
                    text = annotated,
                    style = baseBodyStyle.copy(
                        fontSize = (typography.fontSizeSp * scale).sp,
                        lineHeight = (typography.fontSizeSp * scale * 1.35f).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    ),
                )
                if (block.level <= 2) {
                    Spacer(Modifier.height(6.dp))
                    IgngHairlineDivider()
                }
            }
        }

        is MarkdownBlock.Paragraph -> {
            val annotated = remember(block.inlines, colors, onLinkClick, onMentionClick, onInlineCodeClick) {
                buildInlineAnnotatedString(block.inlines, colors, onLinkClick, onMentionClick, onInlineCodeClick)
            }
            Text(
                text = annotated,
                style = baseBodyStyle,
                modifier = modifier.fillMaxWidth(),
            )
        }

        is MarkdownBlock.BulletList -> {
            Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                block.items.forEach { item ->
                    val annotated = remember(item, colors, onLinkClick, onMentionClick, onInlineCodeClick) {
                        buildInlineAnnotatedString(item, colors, onLinkClick, onMentionClick, onInlineCodeClick)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            text = "•",
                            style = baseBodyStyle.copy(color = colors.accent, fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(18.dp),
                        )
                        Text(
                            text = annotated,
                            style = baseBodyStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        is MarkdownBlock.OrderedList -> {
            Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                block.items.forEachIndexed { idx, item ->
                    val annotated = remember(item, colors, onLinkClick, onMentionClick, onInlineCodeClick) {
                        buildInlineAnnotatedString(item, colors, onLinkClick, onMentionClick, onInlineCodeClick)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${block.start + idx}.",
                            style = baseBodyStyle.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                            modifier = Modifier.widthIn(min = 24.dp),
                        )
                        Text(
                            text = annotated,
                            style = baseBodyStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        is MarkdownBlock.TaskList -> {
            Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                block.items.forEach { item ->
                    val annotated = remember(item.inlines, colors, onLinkClick, onMentionClick, onInlineCodeClick) {
                        buildInlineAnnotatedString(item.inlines, colors, onLinkClick, onMentionClick, onInlineCodeClick)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (item.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (item.checked) colors.success else colors.textMuted,
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .size(18.dp),
                        )
                        Text(
                            text = annotated,
                            style = baseBodyStyle.copy(
                                color = if (item.checked) secondaryText else primaryText,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        is MarkdownBlock.Blockquote -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(radii.sm)
                    .background(colors.tileSoft)
                    .border(IgngStroke.hairline, colors.line, radii.sm)
                    .padding(end = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(colors.quoteBar),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    block.lines.forEach { line ->
                        val annotated = remember(line, colors, onLinkClick, onMentionClick, onInlineCodeClick) {
                            buildInlineAnnotatedString(line, colors, onLinkClick, onMentionClick, onInlineCodeClick)
                        }
                        Text(
                            text = annotated,
                            style = baseBodyStyle.copy(color = secondaryText),
                        )
                    }
                }
            }
        }

        is MarkdownBlock.CodeBlock -> {
            val context = LocalContext.current
            val haptic = rememberIgngHaptics()
            var copied by remember(block.code) { mutableStateOf(false) }
            LaunchedEffect(copied) {
                if (copied) {
                    delay(1800L)
                    copied = false
                }
            }
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(radii.md)
                    .background(colors.codeBackground)
                    .border(IgngStroke.hairline, colors.line, radii.md),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.tileSoft)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = block.language.ifBlank { "代码" },
                        style = IgngTheme.type.mono.copy(fontSize = 11.5.sp),
                        color = colors.textSecondary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(radii.xs)
                            .clickable {
                                copyTextToClipboard(context, "code", block.code)
                                haptic(IgngHapticKind.Confirm)
                                copied = true
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "复制代码",
                            tint = if (copied) colors.success else colors.accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (copied) "已复制" else "复制",
                            style = IgngTheme.type.badge,
                            color = if (copied) colors.success else colors.accent,
                        )
                    }
                }
                IgngHairlineDivider()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    Text(
                        text = block.code,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = (typography.fontSizeSp - 2.5f).coerceAtLeast(12f).sp,
                            lineHeight = (typography.fontSizeSp * 1.4f).sp,
                            color = colors.textPrimary,
                        ),
                    )
                }
            }
        }

        is MarkdownBlock.Table -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(radii.md)
                    .border(IgngStroke.hairline, colors.line, radii.md)
                    .horizontalScroll(rememberScrollState()),
            ) {
                Column {
                    Row(modifier = Modifier.background(colors.tileSoft)) {
                        block.headers.forEach { cell ->
                            val annotated = remember(cell, colors, onLinkClick, onMentionClick, onInlineCodeClick) {
                                buildInlineAnnotatedString(cell, colors, onLinkClick, onMentionClick, onInlineCodeClick)
                            }
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 104.dp, max = 220.dp)
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = annotated,
                                    style = IgngTheme.type.caption.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText,
                                    ),
                                )
                            }
                        }
                    }
                    block.rows.forEachIndexed { rIdx, row ->
                        IgngHairlineDivider()
                        Row(modifier = Modifier.background(if (rIdx % 2 == 0) colors.tile else colors.tileSoft.copy(alpha = 0.5f))) {
                            val cols = block.headers.size.coerceAtLeast(row.size)
                            for (c in 0 until cols) {
                                val cell = row.getOrNull(c).orEmpty()
                                val annotated = remember(cell, colors, onLinkClick, onMentionClick, onInlineCodeClick) {
                                    buildInlineAnnotatedString(cell, colors, onLinkClick, onMentionClick, onInlineCodeClick)
                                }
                                Box(
                                    modifier = Modifier
                                        .widthIn(min = 104.dp, max = 220.dp)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = annotated,
                                        style = IgngTheme.type.caption.copy(color = primaryText),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        is MarkdownBlock.Divider -> {
            IgngHairlineDivider(modifier = modifier.padding(vertical = 8.dp))
        }

        is MarkdownBlock.Image -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IgngContentImage(
                    model = block.url,
                    contentDescription = block.alt.ifBlank { "插图" },
                    aspectRatioHint = block.aspectRatio,
                    shape = radii.md,
                    onClick = { onImageClick(block.url) },
                )
                if (block.alt.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = block.alt,
                        style = IgngTheme.type.meta,
                        color = secondaryText,
                    )
                }
            }
        }

        is MarkdownBlock.VideoEmbed -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(radii.md)
                    .background(colors.tileSoft)
                    .border(IgngStroke.hairline, colors.line, radii.md)
                    .igngPressScale(haptic = true, onClick = { onLinkClick(block.url) })
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircleOutline,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = block.label,
                        style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = block.url,
                        style = IgngTheme.type.meta,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun buildInlineAnnotatedString(
    spans: List<InlineSpan>,
    colors: IgngColors,
    onLinkClick: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    onInlineCodeClick: ((String) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
    fun appendSpans(list: List<InlineSpan>) {
        list.forEach { span ->
            when (span) {
                is InlineSpan.Text -> append(span.text)
                is InlineSpan.Bold -> {
                    val start = length
                    appendSpans(span.children)
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                }
                is InlineSpan.Italic -> {
                    val start = length
                    appendSpans(span.children)
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                }
                is InlineSpan.Strike -> {
                    val start = length
                    appendSpans(span.children)
                    addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, length)
                }
                is InlineSpan.InlineCode -> {
                    val start = length
                    append(" ${span.code} ")
                    val codeStyle = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = colors.codeBackground,
                        color = colors.accent,
                    )
                    if (onInlineCodeClick != null && span.code.isNotBlank()) {
                        addLink(
                            LinkAnnotation.Clickable(
                                tag = "CODE",
                                styles = TextLinkStyles(style = codeStyle),
                                linkInteractionListener = { onInlineCodeClick(span.code) },
                            ),
                            start = start,
                            end = length,
                        )
                    } else {
                        addStyle(codeStyle, start, length)
                    }
                }
                is InlineSpan.Highlight -> {
                    val start = length
                    append(span.text)
                    addStyle(SpanStyle(background = colors.warningLow), start, length)
                }
                is InlineSpan.Colored -> {
                    val start = length
                    appendSpans(span.children)
                    parseSafeInlineColor(span.hexColor, isDark = colors.isDark)?.let { tint ->
                        addStyle(SpanStyle(color = tint), start, length)
                    }
                }
                is InlineSpan.Link -> {
                    val start = length
                    append(span.label)
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "URL",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = colors.accent,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                            linkInteractionListener = { onLinkClick(span.url) },
                        ),
                        start = start,
                        end = length,
                    )
                }
                is InlineSpan.Mention -> {
                    val start = length
                    append("@${span.handle}")
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "MENTION",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = colors.accent,
                                    fontWeight = FontWeight.Medium,
                                ),
                            ),
                            linkInteractionListener = { onMentionClick(span.handle) },
                        ),
                        start = start,
                        end = length,
                    )
                }
            }
        }
    }
    appendSpans(spans)
}

private fun parseSafeInlineColor(hex: String?, isDark: Boolean): androidx.compose.ui.graphics.Color? {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    if (clean.length != 6) return null
    val rgb = clean.toLongOrNull(16) ?: return null
    val r = ((rgb shr 16) and 0xFF) / 255f
    val g = ((rgb shr 8) and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
    // Ensure legible contrast on both light paper and dark surfaces
    val (adjR, adjG, adjB) = when {
        !isDark && luminance > 0.72f -> Triple(r * 0.72f, g * 0.72f, b * 0.72f)
        isDark && luminance < 0.28f -> Triple(
            (r + (1f - r) * 0.45f).coerceIn(0f, 1f),
            (g + (1f - g) * 0.45f).coerceIn(0f, 1f),
            (b + (1f - b) * 0.45f).coerceIn(0f, 1f),
        )
        else -> Triple(r, g, b)
    }
    return androidx.compose.ui.graphics.Color(red = adjR, green = adjG, blue = adjB, alpha = 1f)
}
