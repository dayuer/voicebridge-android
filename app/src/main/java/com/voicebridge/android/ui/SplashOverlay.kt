package com.voicebridge.android.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicebridge.android.ui.theme.VoiceBridgeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 品牌启动动画 — 声波成桥（对齐 iOS SplashView.swift）
 *
 * 声波弧线两侧滑入 → 桥体笔画描边绘出 → 品牌字浮起 → 整层淡出。
 * 纯展示层，不承载任何门禁/下载逻辑；主内容在下层照常构建。
 * 系统动画时长缩放为 0（减弱动态）时降级为静态展示后淡出。
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }

    val arcsOuter = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val arcsInner = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val bridgeTrim = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val wordmark = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            wordmark.animateTo(1f, tween(400))
            delay(900)
        } else {
            launch {
                arcsOuter.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
            }
            launch {
                delay(80)
                arcsInner.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
            }
            launch {
                delay(150)
                bridgeTrim.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
            }
            launch {
                delay(500)
                wordmark.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
            }
            delay(1250)
        }
        overlayAlpha.animateTo(0f, tween(300, easing = LinearOutSlowInEasing))
        onFinished()
    }

    val bgCanvas = VoiceBridgeTheme.bgCanvas
    val textPrimary = VoiceBridgeTheme.textPrimary
    val textTertiary = VoiceBridgeTheme.textTertiary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(bgCanvas)
            .pointerInput(Unit) {}, // 拦截触摸，避免误触下层内容
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            Canvas(modifier = Modifier.size(240.dp, 132.dp)) {
                drawBrandMark(
                    arcsOuter = arcsOuter.value,
                    arcsInner = arcsInner.value,
                    bridgeTrim = bridgeTrim.value
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .alpha(wordmark.value)
                    .offset(y = (12 * (1f - wordmark.value)).dp)
            ) {
                Text(
                    text = "畅译",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 8.sp,
                    color = textPrimary
                )
                Text(
                    text = "VOICEBRIDGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 5.sp,
                    color = textTertiary
                )
            }
        }
    }
}

// MARK: - 矢量笔画（200×110 设计坐标，随画布等比缩放，对齐 iOS BridgeShape/SoundArcShape）

/** 品牌 mark 渐变（取自 App 图标）— 仅 splash 使用，不进主题语义层 */
private val markGradientColors = listOf(Color(0xFF6C63F0), Color(0xFF35D6EE))

private fun DrawScope.drawBrandMark(arcsOuter: Float, arcsInner: Float, bridgeTrim: Float) {
    val w = size.width
    val h = size.height
    fun pt(x: Float, y: Float) = Offset(x / 200f * w, y / 110f * h)

    val strokeWidth = 8f / 200f * w
    val brush = Brush.horizontalGradient(markGradientColors)
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

    fun arc(startX: Float, startY: Float, cX: Float, cY: Float, endX: Float, endY: Float): Path =
        Path().apply {
            moveTo(pt(startX, startY).x, pt(startX, startY).y)
            quadraticBezierTo(pt(cX, cY).x, pt(cX, cY).y, pt(endX, endY).x, pt(endX, endY).y)
        }

    // 四道声波弧「(」— 左右各两道，外道先入场，内道错峰 80ms
    val arcs = listOf(
        Triple(arc(26f, 34f, 12f, 55f, 26f, 76f), arcsOuter, 14f),
        Triple(arc(46f, 26f, 28f, 55f, 46f, 84f), arcsInner, 14f),
        Triple(arc(174f, 34f, 188f, 55f, 174f, 76f), arcsOuter, -14f),
        Triple(arc(154f, 26f, 172f, 55f, 154f, 84f), arcsInner, -14f)
    )
    for ((path, progress, slide) in arcs) {
        val slidePx = slide / 200f * w * (1f - progress)
        translate(left = slidePx) {
            drawPath(path, brush, alpha = progress.coerceIn(0f, 1f), style = stroke)
        }
    }

    // 双塔悬索桥笔画：左索 → 左塔 → 中央垂索 → 右塔 → 右索，trim 沿此顺序绘出
    val bridge = Path().apply {
        moveTo(pt(58f, 82f).x, pt(58f, 82f).y)
        quadraticBezierTo(pt(72f, 46f).x, pt(72f, 46f).y, pt(78f, 34f).x, pt(78f, 34f).y)
        lineTo(pt(78f, 82f).x, pt(78f, 82f).y)
        moveTo(pt(78f, 34f).x, pt(78f, 34f).y)
        quadraticBezierTo(pt(100f, 62f).x, pt(100f, 62f).y, pt(122f, 34f).x, pt(122f, 34f).y)
        lineTo(pt(122f, 82f).x, pt(122f, 82f).y)
        moveTo(pt(122f, 34f).x, pt(122f, 34f).y)
        quadraticBezierTo(pt(128f, 46f).x, pt(128f, 46f).y, pt(142f, 82f).x, pt(142f, 82f).y)
    }
    drawPath(trimMultiContourPath(bridge, bridgeTrim), brush, style = stroke)
}

/** 多轮廓路径按总长度比例裁剪（Compose PathMeasure 不支持跨 contour，用平台 PathMeasure） */
private fun trimMultiContourPath(path: Path, trim: Float): Path {
    if (trim >= 1f) return path
    val result = android.graphics.Path()
    if (trim <= 0f) return result.asComposePath()

    val measure = android.graphics.PathMeasure(path.asAndroidPath(), false)
    var total = 0f
    do {
        total += measure.length
    } while (measure.nextContour())

    var remaining = total * trim
    val measure2 = android.graphics.PathMeasure(path.asAndroidPath(), false)
    do {
        val len = measure2.length
        if (remaining <= 0f) break
        val seg = minOf(remaining, len)
        measure2.getSegment(0f, seg, result, true)
        remaining -= seg
    } while (measure2.nextContour())

    return result.asComposePath()
}
