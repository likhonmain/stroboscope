package com.example.model

import kotlin.math.roundToInt

/**
 * Execution backends supporting Non-Root Android users as well as Root (KernelSU / Magisk)
 * and LSPosed module users (specifically optimized for OnePlus 5T / Qualcomm PMI8998 & general Android).
 */
enum class DriverMode(
    val id: String,
    val title: String,
    val badge: String,
    val description: String
) {
    NON_ROOT_CAMERA(
        id = "non_root",
        title = "Non-Root Camera2 API",
        badge = "STANDARD",
        description = "Uses Android CameraManager Torch API (plus Android 13+ variable strength level when supported). Works on all unrooted devices; hardware HAL typically caps switching around 15–35 Hz."
    ),
    ROOT_KERNELSU_SYSFS(
        id = "root_kernelsu",
        title = "KernelSU / Root Sysfs",
        badge = "ROOT / KSU",
        description = "Bypasses CameraService & HAL via persistent high-priority KernelSU/su shell and direct sysfs file descriptors (/sys/class/leds/led:torch_0 + led:switch_0). Supports OnePlus 5T PMIC Keep-Alive dimming up to 200 Hz."
    ),
    LSPOSED_DIRECT(
        id = "lsposed_direct",
        title = "LSPosed Module Direct",
        badge = "LSPOSED",
        description = "Uses the built-in LSPosed module hook (system_server / SystemUI) to unlock direct 0666 user-space writes to kernel LED sysfs nodes with sub-50µs latency and zero IPC overhead."
    );

    companion object {
        fun fromId(id: String): DriverMode = entries.find { it.id == id } ?: NON_ROOT_CAMERA
    }
}

/**
 * Pre-configured kernel LED sysfs layouts, featuring a dedicated OnePlus 5T (dumpling / msm8998)
 * dual-torch + switch profile alongside auto-detection and custom paths.
 */
enum class SysfsProfile(
    val id: String,
    val displayName: String,
    val primaryTorchPath: String,
    val secondaryTorchPath: String?,
    val masterSwitchPath: String?,
    val defaultMaxBrightness: Int,
    val notes: String
) {
    ONEPLUS_5T_DUMPLING(
        id = "oneplus_5t",
        displayName = "OnePlus 5T (dumpling / PMI8998 Dual Torch)",
        primaryTorchPath = "/sys/class/leds/led:torch_0/brightness",
        secondaryTorchPath = "/sys/class/leds/led:torch_1/brightness",
        masterSwitchPath = "/sys/class/leds/led:switch_0/brightness",
        defaultMaxBrightness = 200,
        notes = "Qualcomm PMI8998 qpnp-flash-led driver: writes current to led:torch_0 & led:torch_1 and triggers via led:switch_0."
    ),
    ONEPLUS_5T_HIGH_FLASH(
        id = "oneplus_5t_flash",
        displayName = "OnePlus 5T (High-Current Flash Strobe)",
        primaryTorchPath = "/sys/class/leds/led:flash_0/brightness",
        secondaryTorchPath = "/sys/class/leds/led:flash_1/brightness",
        masterSwitchPath = "/sys/class/leds/led:switch_0/brightness",
        defaultMaxBrightness = 500,
        notes = "Uses led:flash_0 / led:flash_1 strobe registers for short duty-cycle high-lumen pulses."
    ),
    QUALCOMM_TORCH_LIGHT(
        id = "qcom_torch_light",
        displayName = "Qualcomm Generic (torch-light0)",
        primaryTorchPath = "/sys/class/leds/torch-light0/brightness",
        secondaryTorchPath = "/sys/class/leds/torch-light1/brightness",
        masterSwitchPath = "/sys/class/leds/led:switch_0/brightness",
        defaultMaxBrightness = 255,
        notes = "Legacy & custom kernel Qualcomm sysfs mapping."
    ),
    GENERIC_FLASHLIGHT(
        id = "generic_flashlight",
        displayName = "Generic Android (/sys/class/leds/flashlight)",
        primaryTorchPath = "/sys/class/leds/flashlight/brightness",
        secondaryTorchPath = null,
        masterSwitchPath = null,
        defaultMaxBrightness = 255,
        notes = "Single-node sysfs brightness control."
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom Sysfs Node Paths",
        primaryTorchPath = "/sys/class/leds/led:torch_0/brightness",
        secondaryTorchPath = "/sys/class/leds/led:torch_1/brightness",
        masterSwitchPath = "/sys/class/leds/led:switch_0/brightness",
        defaultMaxBrightness = 200,
        notes = "User-defined sysfs brightness and trigger switch paths."
    );

    companion object {
        fun fromId(id: String): SysfsProfile = entries.find { it.id == id } ?: ONEPLUS_5T_DUMPLING
    }
}

/**
 * Discovered LED sysfs node from scanning `/sys/class/leds/`.
 */
data class SysfsNodeInfo(
    val name: String,
    val brightnessPath: String,
    val maxBrightness: Int,
    val currentBrightness: Int,
    val directWritable: Boolean
)

/**
 * Real-time timing and hardware execution telemetry from the high-precision strobe thread.
 */
data class StrobeTelemetry(
    val targetFrequencyHz: Double = 50.0,
    val measuredFrequencyHz: Double = 0.0,
    val periodMicros: Long = 20_000L,
    val onDurationMicros: Long = 3_000L,
    val jitterMicros: Long = 0L,
    val pulseCount: Long = 0L,
    val isFlashOnPhase: Boolean = false,
    val activeBackendDetail: String = "Standby",
    val rootAvailable: Boolean = false,
    val lsposedActive: Boolean = false,
    val directFdWriteActive: Boolean = false,
    val cameraTorchSupported: Boolean = false,
    val cameraVariableStrengthMax: Int = 1,
    val detectedSysfsNodes: List<SysfsNodeInfo> = emptyList(),
    val statusMessage: String = "Ready — Select mode and press START STROBE",
    val isHardwareWarning: Boolean = false
)

/**
 * Complete UI state for StroboScope Pro.
 *
 * Frequency Calculation:
 *   rawSumHz = slider1CoarseHz (1..200)
 *            + slider2MediumOffsetHz (-25..+25)
 *            + slider3FineOffsetHz (-5..+5, 10Hz range, 1Hz step)
 *            + slider4DecimalHz (0.00..0.99 sub-hertz precision)
 *   effectiveFrequencyHz = (rawSumHz * harmonicMultiplier).coerceIn(0.10, 400.00)
 */
data class StrobeUiState(
    val isStrobeRunning: Boolean = false,
    // 1st big vertical slider: 1 to 200 Hz rapid adjustment
    val slider1CoarseHz: Int = 60,
    // 2nd vertical slider: 50 Hz range (-25 Hz to +25 Hz from Slider 1)
    val slider2MediumOffsetHz: Int = 0,
    // 3rd vertical slider: 10 Hz range (-5 Hz to +5 Hz with 1 Hz step accuracy)
    val slider3FineOffsetHz: Int = 0,
    // 4th vertical slider: Decimal point adjustment for sub-hertz precision (0.00 to 0.99 Hz)
    val slider4DecimalHz: Float = 0.00f,
    // Step precision toggle for 4th slider (true = 0.01 Hz steps, false = 0.10 Hz steps)
    val subHzHundredthsMode: Boolean = true,
    // 0.5x, 1.0x, 2.0x harmonic multiplier option
    val harmonicMultiplier: Double = 1.0,
    // Flash duty cycle percentage (shorter duty cycle = sharper frozen image of spinning object)
    val dutyCyclePercent: Float = 15f,
    // Hardware LED brightness/current percentage (10% to 100%)
    val ledIntensityPercent: Int = 100,
    // OnePlus 5T PMIC Keep-Alive Dimming Mode: keeps boost regulator warm (min brightness 1 or switch held)
    val pmicKeepAliveDimming: Boolean = true,
    // Visual screen strobe bar / overlay sync
    val screenStrobeSync: Boolean = true,
    // Phase-locked acoustic click generator for audio/tachometer synchronization
    val audioPhaseSync: Boolean = false,
    // Active hardware driver mode
    val driverMode: DriverMode = DriverMode.NON_ROOT_CAMERA,
    // Sysfs profile for Root / KernelSU & LSPosed modes
    val sysfsProfile: SysfsProfile = SysfsProfile.ONEPLUS_5T_DUMPLING,
    val customPrimaryPath: String = "/sys/class/leds/led:torch_0/brightness",
    val customSecondaryPath: String = "/sys/class/leds/led:torch_1/brightness",
    val customSwitchPath: String = "/sys/class/leds/led:switch_0/brightness",
    val customMaxBrightness: Int = 200,
    // Telemetry
    val telemetry: StrobeTelemetry = StrobeTelemetry()
) {
    /**
     * Sum of the 4 granular sliders before applying the 0.5x / 1x / 2x multiplier.
     */
    val baseCombinedHz: Double
        get() {
            val dec = (slider4DecimalHz * 100.0).roundToInt() / 100.0
            val sum = slider1CoarseHz + slider2MediumOffsetHz + slider3FineOffsetHz + dec
            return sum.coerceIn(0.10, 250.00)
        }

    /**
     * Final output frequency sent to the real-time stroboscope engine.
     */
    val effectiveFrequencyHz: Double
        get() {
            val multiplied = baseCombinedHz * harmonicMultiplier
            return ((multiplied * 100.0).roundToInt() / 100.0).coerceIn(0.10, 400.00)
        }

    /**
     * Equivalent revolutions per minute (RPM = Hz * 60).
     */
    val effectiveRpm: Double
        get() = effectiveFrequencyHz * 60.0

    /**
     * Period in milliseconds (T = 1000 / f).
     */
    val periodMillis: Double
        get() = if (effectiveFrequencyHz > 0.0) 1000.0 / effectiveFrequencyHz else 0.0
}
