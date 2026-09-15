package com.tkrz.qrtix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable input box with row numbers, scrollable content, scrollbar, and clear button.
 * Shared between ManagementScreen and GeneratorScreen.
 */
@Composable
fun NumberedInputBox(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = value.split("\n")
    val lineCount = lines.size
    val scrollState = rememberScrollState()

    val numberText = (1..lineCount).joinToString("\n") { "$it" }

    val baseLineStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 24.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val topPadDp = 8.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "No",
                    modifier = Modifier.width(32.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Divider(
                    modifier = Modifier.height(20.dp).width(1.dp),
                    color = Color.Gray.copy(alpha = 0.5f)
                )
                Text(
                    label,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Kosongkan",
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp)
                            .clickable { onClear() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .drawWithContent {
                        val strokeWidth = 1.dp.toPx()
                        val scrollY = scrollState.value.toFloat()
                        val numColW = 32.dp.toPx()
                        val topPad = topPadDp.toPx()

                        drawLine(
                            Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(numColW, 0f),
                            end = Offset(numColW, size.height),
                            strokeWidth = strokeWidth
                        )

                        val layout = textLayoutResult
                        if (layout != null && layout.lineCount > 1) {
                            for (i in 0 until layout.lineCount - 1) {
                                val viewY = topPad + layout.getLineBottom(i) - scrollY
                                if (viewY > size.height) break
                                if (viewY > 0f) {
                                    drawLine(
                                        Color.LightGray.copy(alpha = 0.5f),
                                        start = Offset(0f, viewY),
                                        end = Offset(size.width, viewY),
                                        strokeWidth = strokeWidth
                                    )
                                }
                            }
                        }

                        drawContent()

                        val maxScroll = scrollState.maxValue.toFloat()
                        if (maxScroll > 0f) {
                            val viewH = size.height
                            val contentH = viewH + maxScroll
                            val thumbH = (viewH / contentH * viewH).coerceAtLeast(20.dp.toPx())
                            val thumbY = (scrollY / maxScroll) * (viewH - thumbH)
                            val barW = 4.dp.toPx()
                            val barX = size.width - barW - 2.dp.toPx()
                            drawRoundRect(
                                color = Color.Gray.copy(alpha = 0.4f),
                                topLeft = Offset(barX, thumbY),
                                size = Size(barW, thumbH),
                                cornerRadius = CornerRadius(barW / 2f)
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = numberText,
                        modifier = Modifier
                            .width(32.dp)
                            .padding(top = topPadDp, bottom = 8.dp),
                        style = baseLineStyle.copy(
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    )

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, end = 8.dp, top = topPadDp, bottom = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        textStyle = baseLineStyle.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        onTextLayout = { result ->
                            textLayoutResult = result
                        },
                        decorationBox = { innerTextField ->
                            if (value.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = baseLineStyle.copy(
                                        color = Color.LightGray
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }
    }
}
