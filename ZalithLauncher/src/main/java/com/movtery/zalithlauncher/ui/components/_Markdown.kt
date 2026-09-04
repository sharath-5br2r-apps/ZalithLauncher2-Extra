package com.movtery.zalithlauncher.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.iffly.compose.markdown.config.MarkdownRenderConfig
import com.iffly.compose.markdown.style.ListTheme
import com.iffly.compose.markdown.style.MarkdownTheme
import com.iffly.compose.markdown.MarkdownView as ComposeMarkdownView
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.vladsch.flexmark.util.ast.Node

@Composable
fun MarkdownView(
    content: String,
    modifier: Modifier = Modifier,
    config: MarkdownRenderConfig = defaultMarkdownConfig(),
) {
    ComposeMarkdownView(
        content = content,
        markdownRenderConfig = config,
        modifier = modifier,
    )
}

@Composable
fun MarkdownView(
    node: Node,
    modifier: Modifier = Modifier,
    config: MarkdownRenderConfig = defaultMarkdownConfig(),
) {
    ComposeMarkdownView(
        node = node,
        markdownRenderConfig = config,
        modifier = modifier,
    )
}

@Composable
fun defaultMarkdownConfig(
    influencedByBackground: Boolean = true,
    codeBackground: Color = cardColor(influencedByBackground),
): MarkdownRenderConfig {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(influencedByBackground, codeBackground, primary, secondary, onSurface) {
        MarkdownRenderConfig.Builder()
            .markdownTheme(
                MarkdownTheme(
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = onSurface,
                    ),
                    headStyle = mapOf(
                        1 to TextStyle(
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                        ),
                        2 to TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                        ),
                        3 to TextStyle(
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                        ),
                        4 to TextStyle(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                        ),
                        5 to TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                        ),
                        6 to TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                        ),
                    ),
                    code = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = onSurface,
                        background = codeBackground,
                    ),
                    listTheme = ListTheme(
                        markerTextStyle = TextStyle(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.End,
                            color = onSurface,
                        ),
                    ),
                    link = TextLinkStyles(
                        style = SpanStyle(
                            color = primary,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Bold,
                        ),
                        pressedStyle = SpanStyle(
                            color = primary,
                            background = secondary.copy(alpha = 0.4f),
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Bold,
                        ),
                    ),
                )
            )
            .build()
    }
}
