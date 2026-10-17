package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DriverMode
import com.example.model.StrobeUiState
import com.example.model.SysfsProfile
import com.example.ui.theme.FineEmerald
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HardwareDriverSheet(
    state: StrobeUiState,
    onDismiss: () -> Unit,
    onSelectDriverMode: (DriverMode) -> Unit,
    onSelectSysfsProfile: (SysfsProfile) -> Unit,
    onUpdateCustomPaths: (String, String, String, Int) -> Unit,
    onDutyCycleChanged: (Float) -> Unit,
    onIntensityChanged: (Int) -> Unit,
    onKeepAliveChanged: (Boolean) -> Unit,
    onScreenStrobeChanged: (Boolean) -> Unit,
    onTestRootAndScan: () -> Unit,
    onSelectPresetHz: (Double) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var primaryPathInput by remember(state.customPrimaryPath) { mutableStateOf(state.customPrimaryPath) }
    var secondaryPathInput by remember(state.customSecondaryPath) { mutableStateOf(state.customSecondaryPath) }
    var switchPathInput by remember(state.customSwitchPath) { mutableStateOf(state.customSwitchPath) }
    var maxBrightnessInput by remember(state.customMaxBrightness) { mutableStateOf(state.customMaxBrightness.toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("hardware_driver_sheet")
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DeveloperBoard,
                        contentDescription = null,
                        tint = StrobeCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Hardware Driver & Root / LSPosed Console",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "OnePlus 5T (dumpling) PMI8998 & Universal Android Strobe Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_hardware_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Hardware Settings",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 1: Working Mode Cards (Non-Root | Root/KernelSU | LSPosed)
            Text(
                text = "1. SELECT WORKING EXECUTION MODE",
                style = MaterialTheme.typography.labelLarge,
                color = StrobeCyan
            )
            Spacer(modifier = Modifier.height(8.dp))

            DriverMode.entries.forEach { mode ->
                val selected = state.driverMode == mode
                val accent = when (mode) {
                    DriverMode.NON_ROOT_CAMERA -> StrobeCyan
                    DriverMode.ROOT_KERNELSU_SYSFS -> TachAmber
                    DriverMode.LSPOSED_DIRECT -> SubHzViolet
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) accent.copy(alpha = 0.12f) else ObsidianBg)
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) accent else ObsidianBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectDriverMode(mode) }
                        .padding(12.dp)
                        .testTag("sheet_mode_${mode.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) accent else TextMuted)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) accent else TextPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = mode.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = mode.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 2: Live Environment Status & KernelSU / LSPosed Diagnostics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurfaceElevated)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = TachAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ROOT / KERNELSU & LSPOSED STATUS",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                    Button(
                        onClick = onTestRootAndScan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TachAmber,
                            contentColor = Color(0xFF1F1400)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("test_root_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GRANT ROOT & SCAN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                StatusDiagnosticRow(
                    label = "KernelSU / Magisk su Shell",
                    value = if (state.telemetry.rootAvailable) "GRANTED (Persistent su Ready)" else "Not Requested / Unrooted",
                    ok = state.telemetry.rootAvailable
                )
                StatusDiagnosticRow(
                    label = "LSPosed Module Active",
                    value = if (state.telemetry.lsposedActive) "ACTIVE (StrobeXposedInit Hooked)" else "Enable in LSPosed Manager -> Scope: System Framework + SystemUI",
                    ok = state.telemetry.lsposedActive
                )
                StatusDiagnosticRow(
                    label = "Direct 0666 Sysfs FD Write",
                    value = if (state.telemetry.directFdWriteActive) "UNLOCKED (<50µs Direct Kernel Write)" else "Using su Stream / CameraManager API",
                    ok = state.telemetry.directFdWriteActive
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Section 3: Pulse Duty Cycle, Hardware LED Intensity & OnePlus 5T PMIC Keep-Alive
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FlashOn,
                    contentDescription = null,
                    tint = FineEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "2. PULSE SHAPING & ONEPLUS 5T LED INTENSITY",
                    style = MaterialTheme.typography.labelLarge,
                    color = FineEmerald
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Duty cycle slider (5% to 50%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Flash Duty Cycle (ON Phase Width)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${state.dutyCyclePercent.roundToInt()}% (${state.telemetry.onDurationMicros} µs)",
                    style = MaterialTheme.typography.labelLarge,
                    color = FineEmerald
                )
            }
            Text(
                text = "Lower duty cycle (10–20%) produces a sharper frozen motion image at high RPM.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Slider(
                value = state.dutyCyclePercent,
                onValueChange = onDutyCycleChanged,
                valueRange = 5f..50f,
                colors = SliderDefaults.colors(
                    thumbColor = FineEmerald,
                    activeTrackColor = FineEmerald
                ),
                modifier = Modifier.testTag("duty_cycle_slider")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // LED Hardware Intensity / Current (10% to 100%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Hardware LED Brightness / Current",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${state.ledIntensityPercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = TachAmber
                )
            }
            Text(
                text = "Controls sysfs brightness current (1..200mA on OnePlus 5T) or Android 13+ Torch Strength.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Slider(
                value = state.ledIntensityPercent.toFloat(),
                onValueChange = { onIntensityChanged(it.roundToInt()) },
                valueRange = 10f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = TachAmber,
                    activeTrackColor = TachAmber
                ),
                modifier = Modifier.testTag("led_intensity_slider")
            )

            // OnePlus 5T PMIC Keep-Alive Dimming Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OnePlus 5T PMIC Fast Dimming Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Modulates led:torch_0 & led:torch_1 current registers before switch latch to avoid PMI8998 regulator ramp-down lag at 50–200 Hz.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = state.pmicKeepAliveDimming,
                    onCheckedChange = onKeepAliveChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = StrobeCyan),
                    modifier = Modifier.testTag("pmic_keep_alive_switch")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Screen Optical Strobe Sync Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Visual Screen Strobe Indicator",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Flashes the top meter bezel in phase with the hardware stroboscope pulse.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = state.screenStrobeSync,
                    onCheckedChange = onScreenStrobeChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = StrobeCyan),
                    modifier = Modifier.testTag("screen_strobe_switch")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Section 4: Kernel LED Sysfs Profile Selector (OnePlus 5T / Qualcomm / Custom)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = SubHzViolet,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "3. KERNEL LED SYSFS NODE PROFILE",
                    style = MaterialTheme.typography.labelLarge,
                    color = SubHzViolet
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            SysfsProfile.entries.forEach { profile ->
                val isSelected = state.sysfsProfile == profile
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) SubHzViolet.copy(alpha = 0.14f) else ObsidianBg)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) SubHzViolet else ObsidianBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectSysfsProfile(profile) }
                        .padding(10.dp)
                        .testTag("sysfs_profile_${profile.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) SubHzViolet else TextPrimary
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Selected",
                                tint = SubHzViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = profile.primaryTorchPath +
                            (profile.masterSwitchPath?.let { " + $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = StrobeCyan
                    )
                    Text(
                        text = profile.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Discovered /sys/class/leds/ Nodes from device scan
            if (state.telemetry.detectedSysfsNodes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Detected Device /sys/class/leds/ Nodes (Tap to Use):",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.telemetry.detectedSysfsNodes.forEach { node ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianBg)
                                .border(
                                    1.dp,
                                    if (node.directWritable) FineEmerald else ObsidianBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    primaryPathInput = node.brightnessPath
                                    maxBrightnessInput = node.maxBrightness.toString()
                                    onUpdateCustomPaths(
                                        node.brightnessPath,
                                        secondaryPathInput,
                                        switchPathInput,
                                        node.maxBrightness
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${node.name} (max:${node.maxBrightness})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (node.directWritable) FineEmerald else TextPrimary
                            )
                        }
                    }
                }
            }

            // Custom Sysfs Node Path Editor
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = primaryPathInput,
                onValueChange = { primaryPathInput = it },
                label = { Text("Primary Torch Brightness Sysfs Path") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StrobeCyan,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = switchPathInput,
                onValueChange = { switchPathInput = it },
                label = { Text("Master Switch Trigger Path (OnePlus led:switch_0)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StrobeCyan,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onUpdateCustomPaths(
                        primaryPathInput,
                        secondaryPathInput,
                        switchPathInput,
                        maxBrightnessInput.toIntOrNull() ?: 200
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SubHzViolet,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apply_custom_sysfs_button")
            ) {
                Text(
                    text = "SAVE & APPLY SYSFS PATHS",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Section 5: Quick Calibration Reference Presets
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = StrobeCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "4. CALIBRATION REFERENCE FREQUENCY PRESETS",
                    style = MaterialTheme.typography.labelLarge,
                    color = StrobeCyan
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            val presets = listOf(
                10.0 to "10 Hz (600 RPM)",
                24.0 to "24 Hz (Cinema)",
                50.0 to "50 Hz (Mains EU/IN)",
                60.0 to "60 Hz (Mains US / 3600 RPM)",
                100.0 to "100 Hz (6000 RPM)",
                120.0 to "120 Hz (7200 RPM HDD)",
                150.0 to "150 Hz (9000 RPM)",
                200.0 to "200 Hz (12000 RPM)"
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { (hz, label) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBg)
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectPresetHz(hz)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("preset_${hz.toInt()}hz_chip")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatusDiagnosticRow(
    label: String,
    value: String,
    ok: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = if (ok) FineEmerald else WarningOrange,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.58f)
        )
    }
}
