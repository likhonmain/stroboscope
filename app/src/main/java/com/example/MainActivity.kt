package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.HardwareDriverSheet
import com.example.ui.components.TopMeterDisplay
import com.example.ui.components.VerticalStrobeSliderBank
import com.example.ui.theme.FineEmerald
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.StrobeCyan
import com.example.ui.theme.TachAmber
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.StrobeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                StroboScopeApp()
            }
        }
    }
}

@Composable
fun StroboScopeApp(
    strobeViewModel: StrobeViewModel = viewModel()
) {
    val state by strobeViewModel.uiState.collectAsStateWithLifecycle()
    var showHardwareSheet by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showHardwareSheet) {
        showHardwareSheet = false
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
            ) {
                // 1. Top Meter Display (Total Frequency Hz, RPM, Period, 0.5x & 2.0x Harmonic Options, Mode Switcher)
                TopMeterDisplay(
                    state = state,
                    onToggleStrobe = strobeViewModel::toggleStrobeRunning,
                    onSelectHarmonic = strobeViewModel::setHarmonicMultiplier,
                    onNormalizeSliders = strobeViewModel::normalizeSlidersAroundCurrentFrequency,
                    onResetFineOffsets = strobeViewModel::resetFineOffsetsToZero,
                    onSelectDriverMode = strobeViewModel::selectDriverMode,
                    onOpenHardwareSheet = { showHardwareSheet = true },
                    onToggleAudioSync = { strobeViewModel.setAudioPhaseSync(!state.audioPhaseSync) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Main 4-Slider Vertical Granular Control Console
                VerticalStrobeSliderBank(
                    state = state,
                    onCoarseChanged = strobeViewModel::setSlider1CoarseHz,
                    onMediumChanged = strobeViewModel::setSlider2MediumOffsetHz,
                    onFineChanged = strobeViewModel::setSlider3FineOffsetHz,
                    onDecimalChanged = strobeViewModel::setSlider4DecimalHz,
                    onToggleSubHzMode = strobeViewModel::toggleSubHzStepMode,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Bottom Quick Hardware Strip (Duty Cycle, LED Current Intensity, OnePlus 5T PMIC Fast-Dimming)
                BottomHardwareQuickBar(
                    dutyCyclePercent = state.dutyCyclePercent.toInt(),
                    ledIntensityPercent = state.ledIntensityPercent,
                    pmicKeepAlive = state.pmicKeepAliveDimming,
                    sysfsProfileName = state.sysfsProfile.id.uppercase(),
                    onToggleKeepAlive = { strobeViewModel.setPmicKeepAliveDimming(!state.pmicKeepAliveDimming) },
                    onCycleDuty = {
                        val nextDuty = when (state.dutyCyclePercent.toInt()) {
                            10 -> 15f
                            15 -> 25f
                            25 -> 40f
                            40 -> 5f
                            else -> 10f
                        }
                        strobeViewModel.setDutyCyclePercent(nextDuty)
                    },
                    onCycleIntensity = {
                        val nextInt = when (state.ledIntensityPercent) {
                            100 -> 75
                            75 -> 50
                            50 -> 25
                            else -> 100
                        }
                        strobeViewModel.setLedIntensityPercent(nextInt)
                    },
                    onOpenDriverSheet = { showHardwareSheet = true }
                )
            }
        }

        if (showHardwareSheet) {
            HardwareDriverSheet(
                state = state,
                onDismiss = { showHardwareSheet = false },
                onSelectDriverMode = strobeViewModel::selectDriverMode,
                onSelectSysfsProfile = strobeViewModel::selectSysfsProfile,
                onUpdateCustomPaths = strobeViewModel::updateCustomSysfsPaths,
                onDutyCycleChanged = strobeViewModel::setDutyCyclePercent,
                onIntensityChanged = strobeViewModel::setLedIntensityPercent,
                onKeepAliveChanged = strobeViewModel::setPmicKeepAliveDimming,
                onScreenStrobeChanged = strobeViewModel::setScreenStrobeSync,
                onTestRootAndScan = strobeViewModel::testAndRequestRootAndScanNodes,
                onSelectPresetHz = strobeViewModel::setExactFrequencyHz
            )
        }
    }
}

@Composable
private fun BottomHardwareQuickBar(
    dutyCyclePercent: Int,
    ledIntensityPercent: Int,
    pmicKeepAlive: Boolean,
    sysfsProfileName: String,
    onToggleKeepAlive: () -> Unit,
    onCycleDuty: () -> Unit,
    onCycleIntensity: () -> Unit,
    onOpenDriverSheet: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Duty Cycle Quick Chip
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianBg)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                .clickable { onCycleDuty() }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "DUTY $dutyCyclePercent%",
                style = MaterialTheme.typography.labelSmall,
                color = FineEmerald,
                fontWeight = FontWeight.Bold
            )
        }

        // LED Current / Intensity Quick Chip
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianBg)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                .clickable { onCycleIntensity() }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "LED $ledIntensityPercent%",
                style = MaterialTheme.typography.labelSmall,
                color = TachAmber,
                fontWeight = FontWeight.Bold
            )
        }

        // OnePlus 5T PMIC Fast-Dimming Quick Chip
        Box(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (pmicKeepAlive) StrobeCyan.copy(alpha = 0.16f) else ObsidianBg)
                .border(1.dp, if (pmicKeepAlive) StrobeCyan else ObsidianBorder, RoundedCornerShape(8.dp))
                .clickable { onToggleKeepAlive() }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (pmicKeepAlive) "PMIC FAST: ON" else "PMIC FAST: OFF",
                style = MaterialTheme.typography.labelSmall,
                color = if (pmicKeepAlive) StrobeCyan else TextSecondary,
                fontWeight = FontWeight.Bold
            )
        }

        // Active Kernel Profile Quick Chip
        Box(
            modifier = Modifier
                .weight(1.1f)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianBg)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                .clickable { onOpenDriverSheet() }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = sysfsProfileName,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}
