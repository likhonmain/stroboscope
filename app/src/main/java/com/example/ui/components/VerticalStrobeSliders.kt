package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StrobeUiState
import com.example.ui.theme.FineEmerald
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.StrobeCyan
import com.example.ui.theme.SubHzViolet
import com.example.ui.theme.TachAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 4-Stage Granular Vertical Fader Console:
 * 1. Slider 1 (COARSE): 1 to 200 Hz rapid macro adjustment (wider primary vertical slider)
 * 2. Slider 2 (MEDIUM): -25 to +25 Hz (50 Hz span around Slider 1)
 * 3. Slider 3 (FINE): -5 to +5 Hz (10 Hz span with strict 1 Hz step detents)
 * 4. Slider 4 (DECIMAL): .00 to .99 Hz sub-hertz decimal precision
 */
@Composable
fun VerticalStrobeSliderBank(
    state: StrobeUiState,
    onCoarseChanged: (Int) -> Unit,
    onMediumChanged: (Int) -> Unit,
    onFineChanged: (Int) -> Unit,
    onDecimalChanged: (Float) -> Unit,
    onToggleSubHzMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ==================== SLIDER 1: 1 to 200 Hz (BIG COARSE SLIDER) ====================
        VerticalPrecisionSliderColumn(
            title = "1–200 Hz",
            subtitle = "COARSE",
            valueDisplay = "${state.slider1CoarseHz} Hz",
            accentColor = StrobeCyan,
            fraction = ((state.slider1CoarseHz - 1).toFloat() / 199f).coerceIn(0f, 1f),
            bipolarCenter = false,
            tickLabels = listOf(
                1.0f to "200",
                0.75f to "150",
                0.50f to "100",
                0.25f to "50",
                0.0f to "1"
            ),
            stepDetentCount = 0,
            onFractionChanged = { frac ->
                val newHz = (1 + (frac * 199f).roundToInt()).coerceIn(1, 200)
                if (newHz != state.slider1CoarseHz) {
                    onCoarseChanged(newHz)
                }
            },
            onIncrement = {
                val next = (state.slider1CoarseHz + 1).coerceIn(1, 200)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCoarseChanged(next)
            },
            onDecrement = {
                val prev = (state.slider1CoarseHz - 1).coerceIn(1, 200)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCoarseChanged(prev)
            },
            onHeaderClick = null,
            testTagPrefix = "slider_1_coarse",
            modifier = Modifier
                .weight(1.32f)
                .fillMaxHeight()
        )

        // ==================== SLIDER 2: -25 to +25 Hz (50 Hz RANGE) ====================
        val s2Display = if (state.slider2MediumOffsetHz > 0) {
            "+${state.slider2MediumOffsetHz} Hz"
        } else {
            "${state.slider2MediumOffsetHz} Hz"
        }
        VerticalPrecisionSliderColumn(
            title = "±25 Hz",
            subtitle = "50Hz SPAN",
            valueDisplay = s2Display,
            accentColor = TachAmber,
            fraction = ((state.slider2MediumOffsetHz + 25).toFloat() / 50f).coerceIn(0f, 1f),
            bipolarCenter = true,
            tickLabels = listOf(
                1.0f to "+25",
                0.75f to "+12",
                0.50f to "0",
                0.25f to "-12",
                0.0f to "-25"
            ),
            stepDetentCount = 0,
            onFractionChanged = { frac ->
                val newOffset = (-25 + (frac * 50f).roundToInt()).coerceIn(-25, 25)
                if (newOffset != state.slider2MediumOffsetHz) {
                    if (newOffset == 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onMediumChanged(newOffset)
                }
            },
            onIncrement = {
                val next = (state.slider2MediumOffsetHz + 1).coerceIn(-25, 25)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onMediumChanged(next)
            },
            onDecrement = {
                val prev = (state.slider2MediumOffsetHz - 1).coerceIn(-25, 25)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onMediumChanged(prev)
            },
            onHeaderClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onMediumChanged(0)
            },
            testTagPrefix = "slider_2_medium",
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
        )

        // ==================== SLIDER 3: -5 to +5 Hz (10 Hz RANGE, 1 Hz STEP) ====================
        val s3Display = if (state.slider3FineOffsetHz > 0) {
            "+${state.slider3FineOffsetHz} Hz"
        } else {
            "${state.slider3FineOffsetHz} Hz"
        }
        VerticalPrecisionSliderColumn(
            title = "10Hz (1Hz)",
            subtitle = "±5 STEP",
            valueDisplay = s3Display,
            accentColor = FineEmerald,
            fraction = ((state.slider3FineOffsetHz + 5).toFloat() / 10f).coerceIn(0f, 1f),
            bipolarCenter = true,
            tickLabels = listOf(
                1.0f to "+5",
                0.8f to "+3",
                0.5f to "0",
                0.2f to "-3",
                0.0f to "-5"
            ),
            stepDetentCount = 11,
            onFractionChanged = { frac ->
                val newFine = (-5 + (frac * 10f).roundToInt()).coerceIn(-5, 5)
                if (newFine != state.slider3FineOffsetHz) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFineChanged(newFine)
                }
            },
            onIncrement = {
                val next = (state.slider3FineOffsetHz + 1).coerceIn(-5, 5)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onFineChanged(next)
            },
            onDecrement = {
                val prev = (state.slider3FineOffsetHz - 1).coerceIn(-5, 5)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onFineChanged(prev)
            },
            onHeaderClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onFineChanged(0)
            },
            testTagPrefix = "slider_3_fine",
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
        )

        // ==================== SLIDER 4: SUB-HERTZ DECIMAL (.00 to .99 Hz) ====================
        val s4Display = String.format(Locale.US, "+%.2f", state.slider4DecimalHz)
        val maxDec = if (state.subHzHundredthsMode) 0.99f else 0.90f
        val stepUnit = if (state.subHzHundredthsMode) 0.01f else 0.10f
        VerticalPrecisionSliderColumn(
            title = "SUB-Hz",
            subtitle = if (state.subHzHundredthsMode) "0.01 STEP" else "0.10 STEP",
            valueDisplay = s4Display,
            accentColor = SubHzViolet,
            fraction = (state.slider4DecimalHz / maxDec).coerceIn(0f, 1f),
            bipolarCenter = false,
            tickLabels = listOf(
                1.0f to ".99",
                0.75f to ".75",
                0.50f to ".50",
                0.25f to ".25",
                0.0f to ".00"
            ),
            stepDetentCount = if (state.subHzHundredthsMode) 0 else 10,
            onFractionChanged = { frac ->
                val raw = (frac * maxDec).coerceIn(0f, maxDec)
                val quantized = if (state.subHzHundredthsMode) {
                    (raw * 100f).roundToInt() / 100f
                } else {
                    (raw * 10f).roundToInt() / 10f
                }
                if (quantized != state.slider4DecimalHz) {
                    onDecimalChanged(quantized)
                }
            },
            onIncrement = {
                val next = (state.slider4DecimalHz + stepUnit).coerceIn(0f, maxDec)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDecimalChanged(next)
            },
            onDecrement = {
                val prev = (state.slider4DecimalHz - stepUnit).coerceIn(0f, maxDec)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDecimalChanged(prev)
            },
            onHeaderClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleSubHzMode()
            },
            testTagPrefix = "slider_4_decimal",
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun VerticalPrecisionSliderColumn(
    title: String,
    subtitle: String,
    valueDisplay: String,
    accentColor: Color,
    fraction: Float,
    bipolarCenter: Boolean,
    tickLabels: List<Pair<Float, String>>,
    stepDetentCount: Int,
    onFractionChanged: (Float) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onHeaderClick: (() -> Unit)?,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    val currentOnFractionChanged by rememberUpdatedState(onFractionChanged)
    val textMeasurer = rememberTextMeasurer()
    var trackHeightPx by remember { mutableIntStateOf(1) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header badge (tappable to zero or toggle step resolution)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianBg)
                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .then(
                    if (onHeaderClick != null) {
                        Modifier.clickable { onHeaderClick() }
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 5.dp, horizontal = 4.dp)
                .testTag("${testTagPrefix}_header"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1
            )
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.testTag("${testTagPrefix}_value")
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.5.sp,
                color = TextMuted,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Top (+) micro-step button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianSurfaceElevated)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                .clickable { onIncrement() }
                .testTag("${testTagPrefix}_plus_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Increase $title",
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main Vertical Touch Track (Zero-Lag Immediate Drag + Tap)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianBg)
                .border(1.dp, ObsidianBorder.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val verticalPad = 20.dp.toPx()
                        val usableHeight = (height - 2f * verticalPad).coerceAtLeast(1f)

                        fun updateFromY(y: Float) {
                            val clampedY = (y - verticalPad).coerceIn(0f, usableHeight)
                            // Top of slider = 1.0, Bottom = 0.0
                            val newFrac = (1f - (clampedY / usableHeight)).coerceIn(0f, 1f)
                            currentOnFractionChanged(newFrac)
                        }

                        updateFromY(down.position.y)
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) break
                            updateFromY(change.position.y)
                            change.consume()
                        }
                    }
                }
                .testTag("${testTagPrefix}_track")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                trackHeightPx = size.height.toInt()
                val w = size.width
                val h = size.height
                val vPad = 20.dp.toPx()
                val usableH = (h - 2f * vPad).coerceAtLeast(1f)
                val centerX = w * 0.56f
                val trackW = 12.dp.toPx()

                // Background rail slot
                drawRoundRect(
                    color = Color(0xFF141D2B),
                    topLeft = Offset(centerX - trackW / 2f, vPad),
                    size = Size(trackW, usableH),
                    cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
                )

                // Draw step detent ticks if stepDetentCount > 0, else standard ruler ticks
                val totalMinorTicks = if (stepDetentCount > 1) stepDetentCount - 1 else 20
                for (i in 0..totalMinorTicks) {
                    val tickFrac = i.toFloat() / totalMinorTicks.toFloat()
                    val y = vPad + (1f - tickFrac) * usableH
                    val isMajor = (i % 5 == 0) || (stepDetentCount > 0)
                    val tickLen = if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                    drawLine(
                        color = if (isMajor) TextSecondary.copy(alpha = 0.55f) else TextMuted.copy(alpha = 0.3f),
                        start = Offset(centerX - trackW / 2f - tickLen - 3.dp.toPx(), y),
                        end = Offset(centerX - trackW / 2f - 3.dp.toPx(), y),
                        strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx()
                    )
                }

                // Draw tick scale numbers along left edge
                val labelStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                for ((tickFrac, labelText) in tickLabels) {
                    val y = vPad + (1f - tickFrac) * usableH
                    val measured = textMeasurer.measure(labelText, style = labelStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            x = 4.dp.toPx(),
                            y = (y - measured.size.height / 2f).coerceIn(2f, h - measured.size.height - 2f)
                        )
                    )
                }

                // Active Fill Bar
                val thumbY = vPad + (1f - fraction.coerceIn(0f, 1f)) * usableH
                if (bipolarCenter) {
                    val centerY = vPad + 0.5f * usableH
                    // Center zero reference line
                    drawLine(
                        color = accentColor.copy(alpha = 0.65f),
                        start = Offset(centerX - trackW * 1.3f, centerY),
                        end = Offset(centerX + trackW * 1.3f, centerY),
                        strokeWidth = 2.dp.toPx()
                    )
                    val topY = minOf(centerY, thumbY)
                    val barH = kotlin.math.abs(thumbY - centerY).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(accentColor, accentColor.copy(alpha = 0.45f)),
                            startY = topY,
                            endY = topY + barH
                        ),
                        topLeft = Offset(centerX - trackW / 2f, topY),
                        size = Size(trackW, barH),
                        cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
                    )
                } else {
                    val bottomY = vPad + usableH
                    val barH = (bottomY - thumbY).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(accentColor, accentColor.copy(alpha = 0.35f)),
                            startY = thumbY,
                            endY = bottomY
                        ),
                        topLeft = Offset(centerX - trackW / 2f, thumbY),
                        size = Size(trackW, barH),
                        cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
                    )
                }

                // Precision Fader Cap / Thumb Reticle
                val thumbW = (w * 0.72f).coerceAtLeast(28.dp.toPx())
                val thumbH = 24.dp.toPx()
                val thumbLeft = (centerX - thumbW / 2f).coerceIn(2.dp.toPx(), w - thumbW - 2.dp.toPx())

                // Outer glow
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.25f),
                    topLeft = Offset(thumbLeft - 2.dp.toPx(), thumbY - thumbH / 2f - 2.dp.toPx()),
                    size = Size(thumbW + 4.dp.toPx(), thumbH + 4.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                // Fader body
                drawRoundRect(
                    color = Color(0xFF192436),
                    topLeft = Offset(thumbLeft, thumbY - thumbH / 2f),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Fader border
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(thumbLeft, thumbY - thumbH / 2f),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Center laser indicator line inside fader cap
                drawLine(
                    color = Color.White,
                    start = Offset(thumbLeft + 5.dp.toPx(), thumbY),
                    end = Offset(thumbLeft + thumbW - 5.dp.toPx(), thumbY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom (-) micro-step button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianSurfaceElevated)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                .clickable { onDecrement() }
                .testTag("${testTagPrefix}_minus_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = "Decrease $title",
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
