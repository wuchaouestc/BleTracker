package com.example.bletracker.ui.custom.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bletracker.data.ble.RadarTarget
import com.example.bletracker.ui.theme.*
import kotlin.math.*

/**
 * 蓝牙定位雷达图组件
 *
 * 以极坐标方式显示目标蓝牙设备的相对位置。
 * 中心圆点表示「本机」，目标圆点表示被追踪的蓝牙设备。
 * 包含同心圆刻度（1/3/5/10米）、方向线、设备标签和信号连接线。
 */
@Composable
fun RadarView(
    targets: List<RadarTarget>,
    maxDistance: Float = 10f,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 1f
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(16.dp)
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = min(size.width, size.height) / 2 * 0.9f

        // 1. 绘制雷达背景（半透明圆）
        drawRadarBackground(center, maxRadius)

        // 2. 绘制同心圆刻度线
        drawRadarCircles(center, maxRadius, maxDistance, strokeWidth)

        // 3. 绘制方向线（十字准线）
        drawDirectionLines(center, maxRadius, strokeWidth)

        // 4. 绘制刻度标签
        drawRadiusLabels(center, maxRadius, maxDistance, textMeasurer)

        // 5. 绘制中心点（本设备）
        drawCenterPoint(center, textMeasurer)

        // 6. 绘制目标设备点（NaN/Inf 保护）
        targets.forEach { target ->
            if (target.distance.isFinite() && target.angle.isFinite()
                && target.distance >= 0f) {
                drawTargetPoint(center, maxRadius, maxDistance, target, textMeasurer)
            }
        }
    }
}

/**
 * 绘制雷达背景
 */
private fun DrawScope.drawRadarBackground(center: Offset, maxRadius: Float) {
    drawCircle(
        color = Color.Black.copy(alpha = 0.3f),
        radius = maxRadius,
        center = center
    )
}

/**
 * 绘制同心圆刻度线（1m/3m/5m/10m）
 */
private fun DrawScope.drawRadarCircles(
    center: Offset,
    maxRadius: Float,
    maxDistance: Float,
    strokeWidth: Float
) {
    val distances = listOf(1f, 3f, 5f, 10f).filter { it <= maxDistance }

    distances.forEach { distance ->
        val radius = (distance / maxDistance) * maxRadius
        drawCircle(
            color = RadarGrid,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
    }
}

/**
 * 绘制方向线（十字准线 + 对角虚线）
 */
private fun DrawScope.drawDirectionLines(
    center: Offset,
    maxRadius: Float,
    strokeWidth: Float
) {
    // 水平线
    drawLine(
        color = RadarGrid.copy(alpha = 0.5f),
        start = Offset(center.x - maxRadius, center.y),
        end = Offset(center.x + maxRadius, center.y),
        strokeWidth = strokeWidth / 2
    )

    // 垂直线
    drawLine(
        color = RadarGrid.copy(alpha = 0.5f),
        start = Offset(center.x, center.y - maxRadius),
        end = Offset(center.x, center.y + maxRadius),
        strokeWidth = strokeWidth / 2
    )

    // 对角虚线
    val diagonalRadius = maxRadius * 0.7f
    listOf(45f, 135f, 225f, 315f).forEach { degrees ->
        val rad = Math.toRadians(degrees.toDouble())
        val endX = center.x + diagonalRadius * cos(rad).toFloat()
        val endY = center.y + diagonalRadius * sin(rad).toFloat()
        drawLine(
            color = RadarGrid.copy(alpha = 0.2f),
            start = center,
            end = Offset(endX, endY),
            strokeWidth = strokeWidth / 3,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
    }
}

/**
 * 绘制刻度标签（如 1m, 3m, 5m, 10m）
 */
private fun DrawScope.drawRadiusLabels(
    center: Offset,
    maxRadius: Float,
    maxDistance: Float,
    textMeasurer: TextMeasurer
) {
    val labelStyle = TextStyle(
        color = RadarGrid,
        fontSize = 9.sp,
        fontWeight = FontWeight.Light
    )
    val distances = listOf(1f, 3f, 5f, 10f).filter { it <= maxDistance }
    distances.forEach { distance ->
        val r = (distance / maxDistance) * maxRadius
        val labelY = center.y - r + 4f  // 在圆的顶部偏下
        val label = "${distance.toInt()}m"
        val measured = textMeasurer.measure(label, labelStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(center.x - measured.size.width / 2f, labelY)
        )
    }
}

/**
 * 绘制中心点（本设备位置）+ "本机"标签
 */
private fun DrawScope.drawCenterPoint(center: Offset, textMeasurer: TextMeasurer) {
    // 外光环
    drawCircle(
        color = RadarCenter.copy(alpha = 0.3f),
        radius = 12f,
        center = center
    )
    // 内实点
    drawCircle(
        color = RadarCenter,
        radius = 6f,
        center = center
    )

    // "本机" 标签（显示在中心点下方）
    val labelStyle = TextStyle(
        color = RadarCenter,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )
    val measured = textMeasurer.measure("本机", labelStyle)
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            center.x - measured.size.width / 2f,
            center.y + 16f
        )
    )
}

/**
 * 绘制目标设备点
 */
private fun DrawScope.drawTargetPoint(
    center: Offset,
    maxRadius: Float,
    maxDistance: Float,
    target: RadarTarget,
    textMeasurer: TextMeasurer
) {
    val radius = (target.distance / maxDistance).coerceIn(0f, 1f) * maxRadius
    val x = (center.x + radius * cos(target.angle)).coerceIn(0f, size.width)
    val y = (center.y + radius * sin(target.angle)).coerceIn(0f, size.height)

    val targetOffset = Offset(x, y)

    // 选择颜色
    val color = when {
        target.isTracked -> TrackedTarget
        target.signalStrength > 0.7f -> SignalStrong
        target.signalStrength > 0.4f -> SignalMedium
        else -> SignalWeak
    }

    val dotRadius = if (target.isTracked) 10f else 7f

    // 绘制连接线（从中心到目标）
    drawLine(
        color = color.copy(alpha = 0.3f),
        start = center,
        end = targetOffset,
        strokeWidth = 1f
    )

    // 绘制目标点
    drawCircle(
        color = color.copy(alpha = 0.3f),
        radius = dotRadius + 4f,
        center = targetOffset
    )
    drawCircle(
        color = color,
        radius = dotRadius,
        center = targetOffset
    )

    // 被追踪设备添加脉冲环
    if (target.isTracked) {
        drawCircle(
            color = TrackedTarget.copy(alpha = 0.5f),
            radius = dotRadius + 8f,
            center = targetOffset,
            style = Stroke(width = 2f)
        )
    }

    // 设备名称标签（显示在目标点上方）
    val labelText = if (target.name.length > 8) target.name.take(6) + ".." else target.name
    val labelStyle = TextStyle(
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium
    )
    val measured = textMeasurer.measure(labelText, labelStyle)
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            targetOffset.x - measured.size.width / 2f,
            targetOffset.y - dotRadius - measured.size.height - 2f
        )
    )

    // 距离标签（显示在目标点下方）
    val distText = formatDistanceLabel(target.distance)
    val distStyle = TextStyle(
        color = color.copy(alpha = 0.8f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Light
    )
    val distMeasured = textMeasurer.measure(distText, distStyle)
    drawText(
        textLayoutResult = distMeasured,
        topLeft = Offset(
            targetOffset.x - distMeasured.size.width / 2f,
            targetOffset.y + dotRadius + 4f
        )
    )
}

/** 格式化距离标签 */
private fun formatDistanceLabel(distance: Float): String {
    if (!distance.isFinite() || distance < 0f) return "--"
    return if (distance < 1.0f) "${(distance * 100).toInt()}cm"
    else "%.1fm".format(distance)
}

/**
 * 将极坐标距离转换为雷达图半径比例
 */
fun distanceToRadiusRatio(distance: Float, maxDistance: Float): Float {
    return (distance / maxDistance).coerceIn(0f, 1f)
}
