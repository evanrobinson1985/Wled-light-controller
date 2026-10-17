package com.wledcontroller.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Large, touch-friendly slider. With [brush] it renders as a gradient track with a ring thumb
 * (for hue / white / temperature), otherwise as a filled "level" bar.
 * Value is 0..1. Horizontal drags adjust; vertical drags pass through to scrolling parents.
 */
@Composable
fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    label: String? = null,
    valueText: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 40.dp,
    onValueChangeFinished: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val shown = (if (dragging) local else value).coerceIn(0f, 1f)
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    fun update(v: Float) {
        val nv = v.coerceIn(0f, 1f)
        if ((nv == 0f || nv == 1f) && nv != local) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        local = nv
        change(nv)
    }

    Column(modifier) {
        if (label != null || valueText != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                }
                if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (valueText != null) Text(valueText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { o ->
                        local = current
                        update(o.x / size.width)
                        finished()
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; local = current; update(o.x / size.width) },
                        onDragEnd = { dragging = false; finished() },
                        onDragCancel = { dragging = false; finished() },
                        onHorizontalDrag = { ch, _ -> ch.consume(); update(ch.position.x / size.width) },
                    )
                }
        ) {
            val r = size.height / 2f
            val corner = CornerRadius(r, r)
            if (brush != null) {
                drawRoundRect(brush, cornerRadius = corner)
                val cx = r + shown * (size.width - 2 * r)
                val c = Offset(cx, r)
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = r - 1f, center = c)
                drawCircle(Color.White, radius = r - 4.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
            } else {
                drawRoundRect(track, cornerRadius = corner)
                val w = size.height + shown * (size.width - size.height)
                drawRoundRect(fill, size = Size(w, size.height), cornerRadius = corner)
                val knobX = w - r
                drawRoundRect(
                    Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(knobX - 2.dp.toPx(), r * 0.5f),
                    size = Size(4.dp.toPx(), r),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

/** Hue/saturation colour wheel. Hue 0..360, saturation 0..1. */
@Composable
fun ColorWheel(
    hue: Float,
    saturation: Float,
    thumbColor: Color,
    onChange: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier,
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {},
) {
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)
    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                fun handle(p: Offset) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f
                    val d = p - c
                    var ang = Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat()
                    if (ang < 0) ang += 360f
                    change(ang, (d.getDistance() / radius).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    start()
                    handle(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        handle(ch.position)
                        ch.consume()
                    }
                    end()
                }
            }
    ) {
        val r = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(HueColors, center), r)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
        val a = Math.toRadians(hue.toDouble())
        val pos = center + Offset((cos(a) * saturation * r).toFloat(), (sin(a) * saturation * r).toFloat())
        val tr = 16.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.3f), tr + 3.dp.toPx(), pos)
        drawCircle(thumbColor, tr, pos)
        drawCircle(Color.White, tr, pos, style = Stroke(3.dp.toPx()))
    }
}

/** Round colour swatch; long-press for a secondary action. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorSwatch(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 40.dp,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .size(size)
            .shadow(if (selected) 6.dp else 0.dp, CircleShape)
            .clip(CircleShape)
            .background(color)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun PaletteBar(brush: Brush?, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(brush ?: Brush.horizontalGradient(listOf(Color.DarkGray, Color.Gray)))
    )
}

@Composable
fun KeyValueRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
