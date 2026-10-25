package com.github.tvbox.osc.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 列表区的表格纹理：在背景上画一层细网格线。
 *
 * 站点数量少时，列表下方是空白，网格纹理填充这块空白，
 * 视觉上仍是一张"表"，不会显得空荡；站点多时卡片盖住纹理，只露边缘。
 */
fun Modifier.tableGridTexture(
    cellWidth: Dp = 30.dp,
    cellHeight: Dp = 30.dp,
    lineColor: Color,
    lineWidth: Dp = 1.dp,
): Modifier = this.drawBehind {
    val cw = cellWidth.toPx().coerceAtLeast(8f)
    val ch = cellHeight.toPx().coerceAtLeast(8f)
    val lw = lineWidth.toPx()

    var x = 0f
    while (x <= size.width + 0.5f) {
        val px = x.coerceAtMost(size.width)
        drawLine(
            color = lineColor,
            start = Offset(px, 0f),
            end = Offset(px, size.height),
            strokeWidth = lw,
        )
        x += cw
    }

    var y = 0f
    while (y <= size.height + 0.5f) {
        val py = y.coerceAtMost(size.height)
        drawLine(
            color = lineColor,
            start = Offset(0f, py),
            end = Offset(size.width, py),
            strokeWidth = lw,
        )
        y += ch
    }
}
