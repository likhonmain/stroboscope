package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.StrobePreferencesRepository
import com.example.hardware.StrobeHardwareEngine
import com.example.model.DriverMode
import com.example.model.StrobeUiState
import com.example.model.SysfsProfile
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StrobeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = StrobePreferencesRepository(application.applicationContext)
    private val hardwareEngine = StrobeHardwareEngine(application.applicationContext, viewModelScope)

    private val _uiState = MutableStateFlow(StrobeUiState())
    val uiState: StateFlow<StrobeUiState> = _uiState.asStateFlow()

    init {
        // Load persisted hardware mode and sysfs settings
        viewModelScope.launch {
            try {
                val saved = prefsRepo.configFlow.first()
                _uiState.update { state ->
                    state.copy(
                        driverMode = saved.driverMode,
                        sysfsProfile = saved.sysfsProfile,
                        dutyCyclePercent = saved.dutyCyclePercent,
                        ledIntensityPercent = saved.ledIntensityPercent,
                        pmicKeepAliveDimming = saved.pmicKeepAliveDimming,
                        screenStrobeSync = saved.screenStrobeSync,
                        customPrimaryPath = saved.customPrimaryPath,
                        customSecondaryPath = saved.customSecondaryPath,
                        customSwitchPath = saved.customSwitchPath,
                        customMaxBrightness = saved.customMaxBrightness
                    )
                }
                pushStateToHardware(_uiState.value)
            } catch (_: Throwable) {}
        }

        // Collect real-time hardware telemetry into UI state
        viewModelScope.launch {
            hardwareEngine.telemetry.collect { telemetry ->
                _uiState.update { it.copy(telemetry = telemetry) }
            }
        }
    }

    /**
     * Immediately pushes updated frequency/hardware settings to the lock-free atomic variables
     * in StrobeHardwareEngine for zero-lag slider response.
     */
    private fun pushStateToHardware(state: StrobeUiState) {
        hardwareEngine.updateParameters(
            effectiveFrequencyHz = state.effectiveFrequencyHz,
            dutyCyclePercent = state.dutyCyclePercent,
            ledIntensityPercent = state.ledIntensityPercent,
            pmicKeepAlive = state.pmicKeepAliveDimming,
            audioSync = state.audioPhaseSync,
            driverMode = state.driverMode,
            sysfsProfile = state.sysfsProfile,
            customPrimaryPath = state.customPrimaryPath,
            customSecondaryPath = state.customSecondaryPath,
            customSwitchPath = state.customSwitchPath,
            customMaxBrightness = state.customMaxBrightness
        )
    }

    private fun persistHardwareConfig(state: StrobeUiState) {
        viewModelScope.launch {
            try {
                prefsRepo.saveDriverConfig(
                    driverMode = state.driverMode,
                    sysfsProfile = state.sysfsProfile,
                    dutyCyclePercent = state.dutyCyclePercent,
                    ledIntensityPercent = state.ledIntensityPercent,
                    pmicKeepAliveDimming = state.pmicKeepAliveDimming,
                    screenStrobeSync = state.screenStrobeSync,
                    customPrimaryPath = state.customPrimaryPath,
                    customSecondaryPath = state.customSecondaryPath,
                    customSwitchPath = state.customSwitchPath,
                    customMaxBrightness = state.customMaxBrightness
                )
            } catch (_: Throwable) {}
        }
    }

    fun toggleStrobeRunning() {
        val nextRunning = !_uiState.value.isStrobeRunning
        _uiState.update { it.copy(isStrobeRunning = nextRunning) }
        val updated = _uiState.value
        pushStateToHardware(updated)
        if (nextRunning) {
            hardwareEngine.startStrobe()
        } else {
            hardwareEngine.stopStrobe()
        }
    }

    fun setStrobeRunning(running: Boolean) {
        if (_uiState.value.isStrobeRunning == running) return
        _uiState.update { it.copy(isStrobeRunning = running) }
        val updated = _uiState.value
        pushStateToHardware(updated)
        if (running) {
            hardwareEngine.startStrobe()
        } else {
            hardwareEngine.stopStrobe()
        }
    }

    // ==================== 4 GRANULAR VERTICAL SLIDER CONTROLS ====================

    /**
     * 1st Big Vertical Slider: Rapid coarse adjustment from 1 Hz to 200 Hz.
     */
    fun setSlider1CoarseHz(hz: Int) {
        val clamped = hz.coerceIn(1, 200)
        _uiState.update { it.copy(slider1CoarseHz = clamped) }
        pushStateToHardware(_uiState.value)
    }

    /**
     * 2nd Vertical Slider: 50 Hz range (-25 Hz to +25 Hz from Slider 1).
     */
    fun setSlider2MediumOffsetHz(offsetHz: Int) {
        val clamped = offsetHz.coerceIn(-25, 25)
        _uiState.update { it.copy(slider2MediumOffsetHz = clamped) }
        pushStateToHardware(_uiState.value)
    }

    /**
     * 3rd Vertical Slider: 10 Hz range (-5 Hz to +5 Hz with strict 1 Hz step accuracy).
     */
    fun setSlider3FineOffsetHz(offsetHz: Int) {
        val clamped = offsetHz.coerceIn(-5, 5)
        _uiState.update { it.copy(slider3FineOffsetHz = clamped) }
        pushStateToHardware(_uiState.value)
    }

    /**
     * 4th Vertical Slider: Decimal point adjustment for sub-hertz precision (0.00 to 0.99 Hz).
     */
    fun setSlider4DecimalHz(decimalHz: Float) {
        val state = _uiState.value
        val quantized = if (state.subHzHundredthsMode) {
            (decimalHz.coerceIn(0f, 0.99f) * 100f).roundToInt() / 100f
        } else {
            (decimalHz.coerceIn(0f, 0.90f) * 10f).roundToInt() / 10f
        }
        _uiState.update { it.copy(slider4DecimalHz = quantized) }
        pushStateToHardware(_uiState.value)
    }

    fun toggleSubHzStepMode() {
        _uiState.update { state ->
            val nextHundredths = !state.subHzHundredthsMode
            val adjustedDec = if (nextHundredths) {
                state.slider4DecimalHz
            } else {
                (state.slider4DecimalHz * 10f).roundToInt().coerceIn(0, 9) / 10f
            }
            state.copy(
                subHzHundredthsMode = nextHundredths,
                slider4DecimalHz = adjustedDec
            )
        }
        pushStateToHardware(_uiState.value)
    }

    /**
     * Resets fine-tuning offset sliders (Slider 2, 3, 4) and folds or zeros them cleanly.
     */
    fun resetFineOffsetsToZero() {
        _uiState.update {
            it.copy(
                slider2MediumOffsetHz = 0,
                slider3FineOffsetHz = 0,
                slider4DecimalHz = 0.00f,
                harmonicMultiplier = 1.0
            )
        }
        pushStateToHardware(_uiState.value)
    }

    /**
     * Consolidates the current effective frequency directly into Slider 1 + Slider 4,
     * re-centering Slider 2 (0 Hz) and Slider 3 (0 Hz) so the user has full ±25 Hz and ±5 Hz headroom!
     */
    fun normalizeSlidersAroundCurrentFrequency() {
        val currentEffective = _uiState.value.effectiveFrequencyHz.coerceIn(1.0, 200.99)
        val intPart = floor(currentEffective).toInt().coerceIn(1, 200)
        val decPart = ((currentEffective - intPart) * 100.0).roundToInt().coerceIn(0, 99) / 100f
        _uiState.update {
            it.copy(
                slider1CoarseHz = intPart,
                slider2MediumOffsetHz = 0,
                slider3FineOffsetHz = 0,
                slider4DecimalHz = decPart,
                harmonicMultiplier = 1.0
            )
        }
        pushStateToHardware(_uiState.value)
    }

    /**
     * Sets a direct target frequency (e.g., from preset chips or harmonic buttons) and maps it
     * cleanly onto Slider 1 (1..200) and Slider 4 (0.00..0.99) with Slider 2 & 3 centered at 0.
     */
    fun setExactFrequencyHz(targetHz: Double) {
        val clamped = targetHz.coerceIn(1.0, 200.99)
        val intPart = floor(clamped).toInt().coerceIn(1, 200)
        val decPart = ((clamped - intPart) * 100.0).roundToInt().coerceIn(0, 99) / 100f
        _uiState.update {
            it.copy(
                slider1CoarseHz = intPart,
                slider2MediumOffsetHz = 0,
                slider3FineOffsetHz = 0,
                slider4DecimalHz = decPart,
                harmonicMultiplier = 1.0
            )
        }
        pushStateToHardware(_uiState.value)
    }

    // ==================== 0.5x AND 2x FREQUENCY OPTIONS ====================

    fun setHarmonicMultiplier(multiplier: Double) {
        _uiState.update { it.copy(harmonicMultiplier = multiplier) }
        pushStateToHardware(_uiState.value)
    }

    // ==================== DRIVER MODE & HARDWARE CONTROLS ====================

    fun selectDriverMode(mode: DriverMode) {
        _uiState.update { it.copy(driverMode = mode) }
        val updated = _uiState.value
        pushStateToHardware(updated)
        persistHardwareConfig(updated)
        if (mode == DriverMode.ROOT_KERNELSU_SYSFS) {
            hardwareEngine.refreshHardwareEnvironment(requestRoot = true)
        } else {
            hardwareEngine.refreshHardwareEnvironment(requestRoot = false)
        }
    }

    fun selectSysfsProfile(profile: SysfsProfile) {
        _uiState.update { state ->
            state.copy(
                sysfsProfile = profile,
                customPrimaryPath = profile.primaryTorchPath,
                customSecondaryPath = profile.secondaryTorchPath ?: "",
                customSwitchPath = profile.masterSwitchPath ?: "",
                customMaxBrightness = profile.defaultMaxBrightness
            )
        }
        val updated = _uiState.value
        pushStateToHardware(updated)
        persistHardwareConfig(updated)
    }

    fun updateCustomSysfsPaths(
        primaryPath: String,
        secondaryPath: String,
        switchPath: String,
        maxBrightness: Int
    ) {
        _uiState.update {
            it.copy(
                sysfsProfile = SysfsProfile.CUSTOM,
                customPrimaryPath = primaryPath.trim(),
                customSecondaryPath = secondaryPath.trim(),
                customSwitchPath = switchPath.trim(),
                customMaxBrightness = maxBrightness.coerceIn(1, 2000)
            )
        }
        val updated = _uiState.value
        pushStateToHardware(updated)
        persistHardwareConfig(updated)
    }

    fun setDutyCyclePercent(duty: Float) {
        val clamped = duty.coerceIn(5f, 50f)
        _uiState.update { it.copy(dutyCyclePercent = clamped) }
        val updated = _uiState.value
        pushStateToHardware(updated)
        persistHardwareConfig(updated)
    }

    fun setLedIntensityPercent(percent: Int) {
        val clamped = percent.coerceIn(10, 100)
        _uiState.update { it.copy(ledIntensityPercent = clamped) }
        val updated = _uiState.value
        pushStateToHardware(updated)
        persistHardwareConfig(updated)
    }

    fun setPmicKeepAliveDimming(enabled: Boolean) {
        _uiState.update { it.copy(pmicKeepAliveDimming = enabled) }
        val updated = _uiState.value
        pushStateToHardware(updated)
        persistHardwareConfig(updated)
    }

    fun setScreenStrobeSync(enabled: Boolean) {
        _uiState.update { it.copy(screenStrobeSync = enabled) }
        persistHardwareConfig(_uiState.value)
    }

    fun setAudioPhaseSync(enabled: Boolean) {
        _uiState.update { it.copy(audioPhaseSync = enabled) }
        pushStateToHardware(_uiState.value)
    }

    fun testAndRequestRootAndScanNodes() {
        hardwareEngine.refreshHardwareEnvironment(requestRoot = true)
    }

    override fun onCleared() {
        super.onCleared()
        hardwareEngine.cleanup()
    }
}
