package com.github.tvbox.osc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 屏幕智能适配。
 *
 * 以 360dp 短边为基准等比缩放，比例夹在 [MIN_SCALE, MAX_SCALE] 之间，
 * 避免小屏挤成一团、大屏元素被拉得过大。弹窗、列表、卡片、文字都走同一套比例，
 * 保证不同屏幕下 UI 比例协调。
 */
object UiDimens {

    private const val BASE_SHORT_DP = 360f
    private const val MIN_SCALE = 0.85f
    private const val MAX_SCALE = 1.30f

    @JvmStatic
    fun scaleOf(widthDp: Int, heightDp: Int): Float {
        val short = minOf(widthDp, heightDp)
        if (short <= 0) return 1f
        return (short / BASE_SHORT_DP).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /** 弹窗占屏幕高度的比例：屏幕越矮，给弹窗留的比例越大（否则内容放不下）。 */
    @JvmStatic
    fun sheetHeightFraction(heightDp: Int): Float = when {
        heightDp <= 0 -> 0.9f
        heightDp < 560 -> 0.92f
        heightDp < 720 -> 0.88f
        heightDp < 900 -> 0.82f
        else -> 0.72f
    }

    /**
     * 订阅源弹窗占屏幕高度的固定比例。
     *
     * 用单一固定值、不按屏幕分档：窗口大小只跟屏幕走，
     * 不随站点数量变化——站点大量减少时窗口不会再停在刷新前的高度。
     */
    @JvmStatic
    fun sheetFixedFraction(): Float = 0.72f

    /** 列表区表格纹理的格子边长：随全局缩放走，保证不同屏幕下纹理疏密一致。 */
    @JvmStatic
    fun tableCellDp(scale: Float): Dp = (30f * scale).dp

    /** 弹窗内列表的最大高度：随屏幕高度走，不再写死 420dp。 */
    @JvmStatic
    fun sheetListMaxHeightDp(heightDp: Int): Dp {
        val base = (heightDp * 0.46f).dp
        return base.coerceIn(200.dp, 560.dp)
    }

    /** 左右分栏时，单侧列表的最大高度（比整宽列表矮一些，两栏并排才协调）。 */
    @JvmStatic
    fun sheetPaneMaxHeightDp(heightDp: Int): Dp {
        val base = (heightDp * 0.42f).dp
        return base.coerceIn(180.dp, 480.dp)
    }
}

/** 当前屏幕的全局缩放因子，屏幕尺寸变化时自动重算。 */
@Composable
fun rememberUiScale(): Float {
    val cfg = LocalConfiguration.current
    return remember(cfg.screenWidthDp, cfg.screenHeightDp) {
        UiDimens.scaleOf(cfg.screenWidthDp, cfg.screenHeightDp)
    }
}

@Composable
fun scaledDp(base: Dp): Dp = base * rememberUiScale()

@Composable
fun scaledSp(base: TextUnit): TextUnit = (base.value * rememberUiScale()).sp
