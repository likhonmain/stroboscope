package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DriverMode
import com.example.model.StrobeUiState
import com.example.ui.theme.DangerRed
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
import com.example.ui.theme.WarningOrange
import java.util.Locale

@Composable
fun TopMeterDisplay(
    state: StrobeUiState,
    onToggleStrobe: () -> Unit,
    onSelectHarmonic: (Double) -> Unit,
    onNormalizeSliders: () -> Unit,
    onResetFineOffsets: () -> Unit,
    onSelectDriverMode: (DriverMode) -> Unit,
    onOpenHardwareSheet: () -> Unit,
    onToggleAudioSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val freq = state.effectiveFrequencyHz
    val freqFormatted = String.format(Locale.US, "%06.2f", freq)
    val dotIndex = freqFormatted.indexOf('.')
    val intPartStr = if (dotIndex >= 0) freqFormatted.substring(0, dotIndex).trimStart('0').ifEmpty { "0" } else freqFormatted
    val decPartStr = if (dotIndex >= 0) freqFormatted.substring(dotIndex) else ".00"

    val strobeFlashActive = state.isStrobeRunning && state.telemetry.isFlashOnPhase && state.screenStrobeSync
    val borderColor by animateColorAsState(
        targetValue = when {
            strobeFlashActive -> StrobeCyan
            state.isStrobeRunning -> StrobeCyan.copy(alpha = 0.55f)
            else -> ObsidianBorder
        },
        animationSpec = tween(durationMillis = 40),
        label = "meterBorder"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianSurfaceElevated,
                        ObsidianSurface
                    )
                )
            )
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("top_meter_display")
    ) {
        // Row 1: Working Mode Selector Pills (Non-Root | Root/KernelSU | LSPosed) + Hardware Config Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DriverMode.entries.forEach { mode ->
                    val selected = state.driverMode == mode
                    val modeColor = when (mode) {
                        DriverMode.NON_ROOT_CAMERA -> StrobeCyan
                        DriverMode.ROOT_KERNELSU_SYSFS -> TachAmber
                        DriverMode.LSPOSED_DIRECT -> SubHzViolet
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) modeColor.copy(alpha = 0.20f) else ObsidianBg.copy(alpha = 0.6f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) modeColor else ObsidianBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectDriverMode(mode) }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("mode_chip_${mode.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) modeColor else TextMuted)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = mode.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) modeColor else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Audio phase click button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (state.audioPhaseSync) FineEmerald.copy(alpha = 0.2f) else ObsidianBg
                        )
                        .border(
                            1.dp,
                            if (state.audioPhaseSync) FineEmerald else ObsidianBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onToggleAudioSync() }
                        .testTag("audio_sync_toggle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.audioPhaseSync) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        contentDescription = "Toggle Audio Phase Click Sync",
                        tint = if (state.audioPhaseSync) FineEmerald else TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Hardware / Sysfs / OnePlus 5T Settings Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianBg)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                        .clickable { onOpenHardwareSheet() }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("open_hardware_config_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.SettingsInputComponent,
                        contentDescription = "Hardware Driver & Root Settings",
                        tint = StrobeCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "DRIVER",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: Main Digital Frequency Meter + RPM/Period Telemetry + Master Strobe Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Digital Frequency & Formula Readout
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = if (state.isStrobeRunning) StrobeCyan else TextPrimary,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMonoFamily
                                )
                            ) {
                                append(intPartStr)
                            }
                            withStyle(
                                SpanStyle(
                                    color = SubHzViolet,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMonoFamily
                                )
                            ) {
                                append(decPartStr)
                            }
                            withStyle(
                                SpanStyle(
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMonoFamily
                                )
                            ) {
                                append(" Hz")
                            }
                        },
                        maxLines = 1,
                        modifier = Modifier.testTag("total_frequency_text")
                    )
                }

                // RPM + Period + Duty Cycle sub-row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "%,.1f RPM", state.effectiveRpm),
                        style = MaterialTheme.typography.labelLarge,
                        color = TachAmber,
                        modifier = Modifier.testTag("rpm_readout_text")
                    )
                    Text(
                        text = String.format(Locale.US, "T=%.2fms", state.periodMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "${state.dutyCyclePercent.toInt()}% DUTY",
                        style = MaterialTheme.typography.labelSmall,
                        color = FineEmerald
                    )
                }

                // Live equation breakdown showing all 4 sliders + multiplier
                val s2Sign = if (state.slider2MediumOffsetHz >= 0) "+${state.slider2MediumOffsetHz}" else "${state.slider2MediumOffsetHz}"
                val s3Sign = if (state.slider3FineOffsetHz >= 0) "+${state.slider3FineOffsetHz}" else "${state.slider3FineOffsetHz}"
                val s4Str = String.format(Locale.US, "+%.2f", state.slider4DecimalHz)
                Text(
                    text = "(${state.slider1CoarseHz} $s2Sign $s3Sign $s4Str) × ${state.harmonicMultiplier}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Master START / STOP Strobe Trigger Button
            val triggerBg = if (state.isStrobeRunning) DangerRed else StrobeCyan
            val triggerContent = if (state.isStrobeRunning) Color.White else Color(0xFF001F24)
            Surface(
                onClick = onToggleStrobe,
                color = triggerBg,
                contentColor = triggerContent,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .height(54.dp)
                    .testTag("master_strobe_toggle_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (state.isStrobeRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isStrobeRunning) "Stop Stroboscope" else "Start Stroboscope",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = if (state.isStrobeRunning) "STOP" else "STROBE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.isStrobeRunning) "ACTIVE" else "ARMED",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 3: 0.5x / 1.0x / 2.0x Harmonic Frequency Options + Lock/Center Offsets + Zero Fine
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val harmonics = listOf(
                0.5 to "0.5×",
                1.0 to "1.0×",
                2.0 to "2.0×"
            )
            harmonics.forEach { (mult, label) ->
                val isSelected = state.harmonicMultiplier == mult
                val tagSuffix = when (mult) {
                    0.5 -> "0_5x"
                    2.0 -> "2_0x"
                    else -> "1_0x"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) TachAmber.copy(alpha = 0.22f) else ObsidianBg
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) TachAmber else ObsidianBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectHarmonic(mult) }
                        .testTag("harmonic_${tagSuffix}_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) TachAmber else TextSecondary
                    )
                }
            }

            // Re-center S2 & S3 around current frequency so user gets full ±25Hz / ±5Hz range back
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBg)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                    .clickable { onNormalizeSliders() }
                    .padding(horizontal = 10.dp)
                    .testTag("normalize_sliders_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = "Lock Effective Frequency to Slider 1 and Center Offsets",
                        tint = StrobeCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "RE-CENTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = StrobeCyan
                    )
                }
            }

            // Zero fine offsets button
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBg)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                    .clickable { onResetFineOffsets() }
                    .padding(horizontal = 8.dp)
                    .testTag("zero_offsets_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ZERO Δ",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Row 4: Live Telemetry & Hardware Status Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ObsidianBg.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (state.telemetry.isHardwareWarning) Icons.Filled.Memory else Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = if (state.telemetry.isHardwareWarning) WarningOrange else FineEmerald,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = state.telemetry.statusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.telemetry.isHardwareWarning) WarningOrange else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.isStrobeRunning) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "±${state.telemetry.jitterMicros}µs",
                    style = MaterialTheme.typography.labelSmall,
                    color = FineEmerald
                )
            }
        }
    }
}
