package com.example.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import com.example.model.DriverMode
import com.example.model.StrobeTelemetry
import com.example.model.SysfsNodeInfo
import com.example.model.SysfsProfile
import com.example.xposed.StrobeXposedInit
import com.example.xposed.XposedStatus
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lock-free, real-time stroboscope hardware engine supporting:
 * 1. Non-Root CameraManager API (with Android 13+ variable strength support)
 * 2. Root / KernelSU Direct Sysfs Driver (OnePlus 5T `dumpling` dual-torch + switch_0 & generic sysfs)
 * 3. LSPosed Module Direct Sysfs Driver (zero-IPC direct FileDescriptor write unlocked by system_server hook)
 */
class StrobeHardwareEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private var torchCameraId: String? = null
    private var cameraTorchSupported: Boolean = false
    private var cameraMaxStrengthLevel: Int = 1

    // Lock-free atomic parameters updated immediately by UI sliders without restarting the timing loop
    private val isRunningAtomic = AtomicBoolean(false)
    private val targetFrequencyBits = AtomicLong(java.lang.Double.doubleToRawLongBits(60.0))
    private val dutyCycleBits = AtomicLong(java.lang.Double.doubleToRawLongBits(15.0))
    private val ledIntensityPercentAtomic = AtomicInteger(100)
    private val pmicKeepAliveAtomic = AtomicBoolean(true)
    private val audioSyncAtomic = AtomicBoolean(false)
    private val driverModeRef = AtomicReference(DriverMode.NON_ROOT_CAMERA)
    private val sysfsProfileRef = AtomicReference(SysfsProfile.ONEPLUS_5T_DUMPLING)

    private val customPrimaryPathRef = AtomicReference("/sys/class/leds/led:torch_0/brightness")
    private val customSecondaryPathRef = AtomicReference("/sys/class/leds/led:torch_1/brightness")
    private val customSwitchPathRef = AtomicReference("/sys/class/leds/led:switch_0/brightness")
    private val customMaxBrightnessAtomic = AtomicInteger(200)

    private var strobeThread: Thread? = null

    // Persistent Root (KernelSU / Magisk) shell session for zero-fork sysfs toggling
    private var rootProcess: java.lang.Process? = null
    private var rootWriter: BufferedWriter? = null

    private val _telemetry = MutableStateFlow(StrobeTelemetry())
    val telemetry: StateFlow<StrobeTelemetry> = _telemetry.asStateFlow()

    init {
        discoverCameraCapabilities()
        scope.launch(Dispatchers.IO) {
            refreshHardwareEnvironment(requestRoot = false)
        }
    }

    private fun discoverCameraCapabilities() {
        try {
            val cm = cameraManager ?: return
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (hasFlash) {
                    torchCameraId = id
                    cameraTorchSupported = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val maxLevel = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                        cameraMaxStrengthLevel = max(1, maxLevel)
                    }
                    break
                }
            }
        } catch (_: Throwable) {
            cameraTorchSupported = false
        }
    }

    /**
     * Scans `/sys/class/leds/`, checks LSPosed hook status, and optionally tests KernelSU / su root access.
     */
    fun refreshHardwareEnvironment(requestRoot: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            val lsposedActive = try {
                XposedStatus.isModuleActiveInLSPosed()
            } catch (_: Throwable) {
                false
            }

            var rootGranted = _telemetry.value.rootAvailable
            if (requestRoot) {
                rootGranted = checkAndPrepareRootSession()
            } else {
                // Quick non-blocking check if su binary exists on PATH
                val suBinaryExists = listOf(
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/sbin/su",
                    "/debug_ramdisk/su",
                    "/data/adb/ksu/bin/su",
                    "/data/adb/magisk/su"
                ).any { File(it).exists() }
                if (suBinaryExists && rootProcess != null) {
                    rootGranted = true
                }
            }

            val nodes = scanSysfsLedNodes(useRoot = rootGranted)
            val directWritable = nodes.any { it.directWritable } ||
                XposedStatus.hasDirectWritableSysfsNode(StrobeXposedInit.KNOWN_TORCH_SYSFS_NODES)

            val current = _telemetry.value
            val statusMsg = buildString {
                if (lsposedActive) append("LSPosed Module Active • ")
                if (rootGranted) append("KernelSU/Root Ready • ")
                if (nodes.isNotEmpty()) {
                    append("${nodes.size} LED sysfs nodes detected")
                } else if (cameraTorchSupported) {
                    append("Camera2 Torch Ready (ID=${torchCameraId ?: "0"})")
                } else {
                    append("Screen & Audio Strobe Ready (No Rear Flash HAL)")
                }
            }

            _telemetry.value = current.copy(
                rootAvailable = rootGranted,
                lsposedActive = lsposedActive,
                directFdWriteActive = directWritable,
                cameraTorchSupported = cameraTorchSupported,
                cameraVariableStrengthMax = cameraMaxStrengthLevel,
                detectedSysfsNodes = nodes,
                statusMessage = statusMsg
            )
        }
    }

    /**
     * Requests root via `su` (KernelSU / Magisk) and unlocks target sysfs LED nodes with `chmod 0666`
     * so user-space RandomAccessFile descriptors can write at microsecond speed.
     */
    private fun checkAndPrepareRootSession(): Boolean {
        return try {
            val proc = ProcessBuilder("su").redirectErrorStream(true).start()
            val writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))

            // Unlock all known torch sysfs nodes so direct FD writes also work
            for (path in StrobeXposedInit.KNOWN_TORCH_SYSFS_NODES) {
                writer.write("chmod 0666 $path 2>/dev/null\n")
            }
            val customP = customPrimaryPathRef.get()
            val customS = customSecondaryPathRef.get()
            val customSw = customSwitchPathRef.get()
            if (customP.isNotBlank()) writer.write("chmod 0666 $customP 2>/dev/null\n")
            if (customS.isNotBlank()) writer.write("chmod 0666 $customS 2>/dev/null\n")
            if (customSw.isNotBlank()) writer.write("chmod 0666 $customSw 2>/dev/null\n")

            writer.write("echo ROOT_OK_STROBOSCOPE\n")
            writer.flush()

            var confirmed = false
            val deadline = System.currentTimeMillis() + 3500L
            while (System.currentTimeMillis() < deadline) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    if (line.contains("ROOT_OK_STROBOSCOPE")) {
                        confirmed = true
                        break
                    }
                } else {
                    Thread.sleep(25)
                }
            }

            if (confirmed) {
                synchronized(this) {
                    try {
                        rootWriter?.close()
                        rootProcess?.destroy()
                    } catch (_: Throwable) {}
                    rootProcess = proc
                    rootWriter = writer
                }
                true
            } else {
                proc.destroy()
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun scanSysfsLedNodes(useRoot: Boolean): List<SysfsNodeInfo> {
        val results = mutableListOf<SysfsNodeInfo>()
        try {
            val ledsDir = File("/sys/class/leds")
            val children = ledsDir.listFiles()
            if (!children.isNullOrEmpty()) {
                for (dir in children) {
                    val name = dir.name
                    if (name.contains("torch", ignoreCase = true) ||
                        name.contains("flash", ignoreCase = true) ||
                        name.contains("switch", ignoreCase = true) ||
                        name.contains("led:", ignoreCase = true)
                    ) {
                        val bFile = File(dir, "brightness")
                        val maxFile = File(dir, "max_brightness")
                        val maxB = try {
                            maxFile.readText().trim().toIntOrNull() ?: 200
                        } catch (_: Throwable) {
                            200
                        }
                        val curB = try {
                            bFile.readText().trim().toIntOrNull() ?: 0
                        } catch (_: Throwable) {
                            0
                        }
                        results.add(
                            SysfsNodeInfo(
                                name = name,
                                brightnessPath = bFile.absolutePath,
                                maxBrightness = maxB,
                                currentBrightness = curB,
                                directWritable = bFile.canWrite()
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            // Ignored
        }

        // If standard directory listing was restricted by SELinux, probe known OnePlus 5T / Qualcomm paths
        if (results.isEmpty()) {
            for (path in StrobeXposedInit.KNOWN_TORCH_SYSFS_NODES) {
                val f = File(path)
                val parentName = f.parentFile?.name ?: path
                if (f.exists() || useRoot) {
                    val maxFile = File(f.parentFile, "max_brightness")
                    val maxB = try {
                        maxFile.readText().trim().toIntOrNull() ?: 200
                    } catch (_: Throwable) {
                        200
                    }
                    results.add(
                        SysfsNodeInfo(
                            name = parentName,
                            brightnessPath = path,
                            maxBrightness = maxB,
                            currentBrightness = 0,
                            directWritable = f.canWrite()
                        )
                    )
                }
            }
        }
        return results.sortedByDescending {
            it.name.contains("torch") || it.name.contains("switch")
        }
    }

    /**
     * Immediately updates the target frequency and hardware parameters without locking or restarting
     * the real-time strobe loop. Ensures zero slider lag.
     */
    fun updateParameters(
        effectiveFrequencyHz: Double,
        dutyCyclePercent: Float,
        ledIntensityPercent: Int,
        pmicKeepAlive: Boolean,
        audioSync: Boolean,
        driverMode: DriverMode,
        sysfsProfile: SysfsProfile,
        customPrimaryPath: String,
        customSecondaryPath: String,
        customSwitchPath: String,
        customMaxBrightness: Int
    ) {
        val safeFreq = effectiveFrequencyHz.coerceIn(0.10, 400.00)
        targetFrequencyBits.set(java.lang.Double.doubleToRawLongBits(safeFreq))
        dutyCycleBits.set(java.lang.Double.doubleToRawLongBits(dutyCyclePercent.coerceIn(2f, 80f).toDouble()))
        ledIntensityPercentAtomic.set(ledIntensityPercent.coerceIn(5, 100))
        pmicKeepAliveAtomic.set(pmicKeepAlive)
        audioSyncAtomic.set(audioSync)
        driverModeRef.set(driverMode)
        sysfsProfileRef.set(sysfsProfile)
        customPrimaryPathRef.set(customPrimaryPath)
        customSecondaryPathRef.set(customSecondaryPath)
        customSwitchPathRef.set(customSwitchPath)
        customMaxBrightnessAtomic.set(customMaxBrightness.coerceIn(1, 2000))

        if (!isRunningAtomic.get()) {
            val periodUs = (1_000_000.0 / safeFreq).toLong()
            val onUs = (periodUs * (dutyCyclePercent / 100.0)).toLong()
            _telemetry.value = _telemetry.value.copy(
                targetFrequencyHz = safeFreq,
                periodMicros = periodUs,
                onDurationMicros = onUs
            )
        }
    }

    /**
     * Starts the high-priority, phase-locked stroboscope thread.
     */
    fun startStrobe() {
        if (isRunningAtomic.getAndSet(true)) return

        val thread = Thread({
            runRealTimeStrobeLoop()
        }, "StroboScope-RT-Engine")
        thread.isDaemon = true
        thread.priority = Thread.MAX_PRIORITY
        strobeThread = thread
        thread.start()
    }

    /**
     * Stops the stroboscope thread and guarantees the hardware LED is turned off safely.
     */
    fun stopStrobe() {
        isRunningAtomic.set(false)
        strobeThread?.interrupt()
        strobeThread = null
    }

    /**
     * Dedicated real-time execution loop running at THREAD_PRIORITY_URGENT_AUDIO.
     * Uses hybrid LockSupport.parkNanos + final nanosecond spin-wait and dynamic slider
     * phase adaptation so sliding from 1 Hz to 200 Hz responds within < 1.5 ms.
     */
    private fun runRealTimeStrobeLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Throwable) {}

        // Optional AudioTrack for acoustic click synchronization
        val audioClickHelper = ShortPulseAudioGenerator()

        // Direct RandomAccessFile cache for sysfs nodes (used in LSPosed Mode and Root 0666 Mode)
        var openedPrimaryPath = ""
        var openedSecondaryPath = ""
        var openedSwitchPath = ""
        var primaryRaf: RandomAccessFile? = null
        var secondaryRaf: RandomAccessFile? = null
        var switchRaf: RandomAccessFile? = null

        fun closeDirectFds() {
            try { primaryRaf?.close() } catch (_: Throwable) {}
            try { secondaryRaf?.close() } catch (_: Throwable) {}
            try { switchRaf?.close() } catch (_: Throwable) {}
            primaryRaf = null
            secondaryRaf = null
            switchRaf = null
            openedPrimaryPath = ""
            openedSecondaryPath = ""
            openedSwitchPath = ""
        }

        // If user selected Root mode, ensure persistent root session is ready
        if (driverModeRef.get() == DriverMode.ROOT_KERNELSU_SYSFS && rootWriter == null) {
            val ok = checkAndPrepareRootSession()
            _telemetry.value = _telemetry.value.copy(rootAvailable = ok)
        }

        var pulseCounter = 0L
        var lastLoopStartNanos = System.nanoTime()
        var nextCycleStartNanos = lastLoopStartNanos
        var lastTelemetryUpdateNanos = lastLoopStartNanos
        var smoothedJitterMicros = 0L
        var activeBackendLabel = "Initializing..."

        try {
            while (isRunningAtomic.get() && !Thread.currentThread().isInterrupted) {
                val cycleStartNanos = System.nanoTime()
                val freqHz = java.lang.Double.longBitsToDouble(targetFrequencyBits.get()).coerceIn(0.10, 400.0)
                val dutyPercent = java.lang.Double.longBitsToDouble(dutyCycleBits.get()).coerceIn(2.0, 80.0)
                val intensityPct = ledIntensityPercentAtomic.get()
                val keepAlive = pmicKeepAliveAtomic.get()
                val mode = driverModeRef.get()
                val profile = sysfsProfileRef.get()

                val periodNanos = (1_000_000_000.0 / freqHz).toLong().coerceAtLeast(1_000_000L)
                val onDurationNanos = ((periodNanos * dutyPercent) / 100.0).toLong()
                    .coerceIn(250_000L, periodNanos - 250_000L)

                // Resolve active sysfs paths for Root / LSPosed modes
                val primaryPath = if (profile == SysfsProfile.CUSTOM) customPrimaryPathRef.get() else profile.primaryTorchPath
                val secondaryPath = if (profile == SysfsProfile.CUSTOM) customSecondaryPathRef.get() else (profile.secondaryTorchPath ?: "")
                val switchPath = if (profile == SysfsProfile.CUSTOM) customSwitchPathRef.get() else (profile.masterSwitchPath ?: "")
                val maxHwBrightness = if (profile == SysfsProfile.CUSTOM) customMaxBrightnessAtomic.get() else profile.defaultMaxBrightness

                val targetBrightness = max(1, ((maxHwBrightness * intensityPct) / 100.0).roundToInt())
                // In OnePlus 5T PMIC Keep-Alive dimming mode, we can optionally keep a 0/1 warm floor
                val offBrightness = 0

                // Ensure direct RandomAccessFile descriptors are opened if paths changed
                if (mode == DriverMode.LSPOSED_DIRECT || mode == DriverMode.ROOT_KERNELSU_SYSFS) {
                    if (primaryPath != openedPrimaryPath || secondaryPath != openedSecondaryPath || switchPath != openedSwitchPath) {
                        closeDirectFds()
                        openedPrimaryPath = primaryPath
                        openedSecondaryPath = secondaryPath
                        openedSwitchPath = switchPath
                        try {
                            if (primaryPath.isNotBlank() && File(primaryPath).canWrite()) {
                                primaryRaf = RandomAccessFile(primaryPath, "rw")
                            }
                            if (secondaryPath.isNotBlank() && File(secondaryPath).canWrite()) {
                                secondaryRaf = RandomAccessFile(secondaryPath, "rw")
                            }
                            if (switchPath.isNotBlank() && File(switchPath).canWrite()) {
                                switchRaf = RandomAccessFile(switchPath, "rw")
                            }
                        } catch (_: Throwable) {
                            // Will use persistent root shell or fallback
                        }
                    }
                } else if (openedPrimaryPath.isNotEmpty()) {
                    closeDirectFds()
                }

                // ==================== PHASE 1: FLASH ON ====================
                activeBackendLabel = setHardwareFlashState(
                    on = true,
                    mode = mode,
                    onBrightness = targetBrightness,
                    offBrightness = offBrightness,
                    keepAlive = keepAlive,
                    primaryPath = primaryPath,
                    secondaryPath = secondaryPath,
                    switchPath = switchPath,
                    primaryRaf = primaryRaf,
                    secondaryRaf = secondaryRaf,
                    switchRaf = switchRaf
                )

                if (audioSyncAtomic.get()) {
                    audioClickHelper.triggerClick()
                }

                // Update visual flash indicator for UI screen strobe sync
                if (freqHz <= 30.0 || (pulseCounter % 2L == 0L)) {
                    _telemetry.value = _telemetry.value.copy(isFlashOnPhase = true)
                }

                // Wait for ON duration, dynamically checking if slider moved to a higher frequency
                val onDeadlineNanos = nextCycleStartNanos + onDurationNanos
                precisionWaitDynamic(
                    initialDeadlineNanos = onDeadlineNanos,
                    cycleOriginNanos = nextCycleStartNanos,
                    isOnPhase = true
                )

                if (!isRunningAtomic.get()) break

                // ==================== PHASE 2: FLASH OFF ====================
                setHardwareFlashState(
                    on = false,
                    mode = mode,
                    onBrightness = targetBrightness,
                    offBrightness = offBrightness,
                    keepAlive = keepAlive,
                    primaryPath = primaryPath,
                    secondaryPath = secondaryPath,
                    switchPath = switchPath,
                    primaryRaf = primaryRaf,
                    secondaryRaf = secondaryRaf,
                    switchRaf = switchRaf
                )

                if (freqHz <= 30.0 || (pulseCounter % 2L == 0L)) {
                    _telemetry.value = _telemetry.value.copy(isFlashOnPhase = false)
                }

                // Wait for end of period, dynamically recalculating periodNanos if user drags any slider!
                precisionWaitDynamic(
                    initialDeadlineNanos = nextCycleStartNanos + periodNanos,
                    cycleOriginNanos = nextCycleStartNanos,
                    isOnPhase = false
                )

                val nowNanos = System.nanoTime()
                val actualCycleNanos = nowNanos - lastLoopStartNanos
                lastLoopStartNanos = nowNanos
                pulseCounter++

                // Re-read current frequency in case slider moved during the cycle
                val updatedFreqHz = java.lang.Double.longBitsToDouble(targetFrequencyBits.get()).coerceIn(0.10, 400.0)
                val updatedPeriodNanos = (1_000_000_000.0 / updatedFreqHz).toLong().coerceAtLeast(1_000_000L)

                // Phase-lock advance: prevent cumulative drift while snapping immediately on slider changes
                nextCycleStartNanos += updatedPeriodNanos
                if (nowNanos - nextCycleStartNanos > updatedPeriodNanos || nextCycleStartNanos - nowNanos > updatedPeriodNanos) {
                    // Slider changed significantly or system hiccuped: snap phase cleanly to nowNanos
                    nextCycleStartNanos = nowNanos
                }

                // Calculate real-time jitter & update UI telemetry at ~15 Hz
                val actualPeriodMicros = actualCycleNanos / 1_000L
                val targetPeriodMicros = updatedPeriodNanos / 1_000L
                val instantJitterMicros = abs(actualPeriodMicros - targetPeriodMicros)
                smoothedJitterMicros = ((smoothedJitterMicros * 3) + instantJitterMicros) / 4

                if (nowNanos - lastTelemetryUpdateNanos >= 65_000_000L) {
                    lastTelemetryUpdateNanos = nowNanos
                    val measuredHz = if (actualCycleNanos > 0) 1_000_000_000.0 / actualCycleNanos.toDouble() else updatedFreqHz
                    val updatedDuty = java.lang.Double.longBitsToDouble(dutyCycleBits.get())
                    val onUs = ((targetPeriodMicros * updatedDuty) / 100.0).toLong()
                    val isHalWarning = (mode == DriverMode.NON_ROOT_CAMERA && updatedFreqHz > 35.0)

                    _telemetry.value = _telemetry.value.copy(
                        targetFrequencyHz = updatedFreqHz,
                        measuredFrequencyHz = (measuredHz * 100.0).roundToInt() / 100.0,
                        periodMicros = targetPeriodMicros,
                        onDurationMicros = onUs,
                        jitterMicros = smoothedJitterMicros,
                        pulseCount = pulseCounter,
                        activeBackendDetail = activeBackendLabel,
                        directFdWriteActive = (primaryRaf != null),
                        isHardwareWarning = isHalWarning,
                        statusMessage = if (isHalWarning) {
                            "Non-Root Camera HAL may cap LED above ~35Hz. Switch to Root/KernelSU or LSPosed for 35–200Hz hardware strobe."
                        } else {
                            "Locked: $activeBackendLabel • Jitter ±${smoothedJitterMicros}µs"
                        }
                    )
                }
            }
        } finally {
            // Guaranteed safe turn-off on stop
            try {
                val mode = driverModeRef.get()
                val profile = sysfsProfileRef.get()
                val primaryPath = if (profile == SysfsProfile.CUSTOM) customPrimaryPathRef.get() else profile.primaryTorchPath
                val secondaryPath = if (profile == SysfsProfile.CUSTOM) customSecondaryPathRef.get() else (profile.secondaryTorchPath ?: "")
                val switchPath = if (profile == SysfsProfile.CUSTOM) customSwitchPathRef.get() else (profile.masterSwitchPath ?: "")

                setHardwareFlashState(
                    on = false,
                    mode = mode,
                    onBrightness = 0,
                    offBrightness = 0,
                    keepAlive = false,
                    primaryPath = primaryPath,
                    secondaryPath = secondaryPath,
                    switchPath = switchPath,
                    primaryRaf = primaryRaf,
                    secondaryRaf = secondaryRaf,
                    switchRaf = switchRaf
                )
            } catch (_: Throwable) {}
            closeDirectFds()
            audioClickHelper.release()

            _telemetry.value = _telemetry.value.copy(
                isFlashOnPhase = false,
                measuredFrequencyHz = 0.0,
                activeBackendDetail = "Standby"
            )
        }
    }

    /**
     * Waits until deadlineNanos while re-evaluating the deadline every 1.2ms in case the user
     * moves any of the 4 vertical sliders. This guarantees zero lag even when sweeping from 1 Hz to 200 Hz.
     */
    private fun precisionWaitDynamic(
        initialDeadlineNanos: Long,
        cycleOriginNanos: Long,
        isOnPhase: Boolean
    ) {
        var deadline = initialDeadlineNanos
        while (isRunningAtomic.get()) {
            val now = System.nanoTime()
            // Recompute dynamic deadline from current atomic slider values
            val currentFreq = java.lang.Double.longBitsToDouble(targetFrequencyBits.get()).coerceIn(0.10, 400.0)
            val currentDuty = java.lang.Double.longBitsToDouble(dutyCycleBits.get()).coerceIn(2.0, 80.0)
            val dynamicPeriodNanos = (1_000_000_000.0 / currentFreq).toLong().coerceAtLeast(1_000_000L)
            val dynamicDeadline = if (isOnPhase) {
                val dynamicOn = ((dynamicPeriodNanos * currentDuty) / 100.0).toLong()
                    .coerceIn(250_000L, dynamicPeriodNanos - 250_000L)
                cycleOriginNanos + dynamicOn
            } else {
                cycleOriginNanos + dynamicPeriodNanos
            }
            deadline = min(deadline, dynamicDeadline)
            if (dynamicDeadline > deadline) {
                deadline = dynamicDeadline
            }

            val remainingNanos = deadline - now
            if (remainingNanos <= 0L) break

            if (remainingNanos > 450_000L) {
                // Park in short slices (max 1.2 ms) so slider changes interrupt long waits immediately
                val parkSlice = min(remainingNanos - 300_000L, 1_200_000L)
                LockSupport.parkNanos(parkSlice)
            } else {
                // Final sub-450µs precision spin-wait
                Thread.onSpinWait()
            }
        }
    }

    /**
     * Executes the actual hardware LED toggle using the selected backend mode:
     * - Direct Sysfs FD (LSPosed / Root 0666)
     * - Persistent Root Shell (`su` pipe for KernelSU / Magisk)
     * - Non-Root CameraManager (`turnOnTorchWithStrengthLevel` or `setTorchMode`)
     */
    private fun setHardwareFlashState(
        on: Boolean,
        mode: DriverMode,
        onBrightness: Int,
        offBrightness: Int,
        keepAlive: Boolean,
        primaryPath: String,
        secondaryPath: String,
        switchPath: String,
        primaryRaf: RandomAccessFile?,
        secondaryRaf: RandomAccessFile?,
        switchRaf: RandomAccessFile?
    ): String {
        // 1. Direct Kernel Sysfs FileDescriptor Write (Used by LSPosed Mode & Root chmod 0666 Mode)
        if ((mode == DriverMode.LSPOSED_DIRECT || mode == DriverMode.ROOT_KERNELSU_SYSFS) && primaryRaf != null) {
            try {
                if (on) {
                    val bBytes = "$onBrightness\n".toByteArray(Charsets.US_ASCII)
                    primaryRaf.seek(0)
                    primaryRaf.write(bBytes)
                    if (secondaryRaf != null) {
                        secondaryRaf.seek(0)
                        secondaryRaf.write(bBytes)
                    }
                    if (switchRaf != null) {
                        switchRaf.seek(0)
                        switchRaf.write(BYTES_ONE)
                    }
                } else {
                    if (keepAlive && switchRaf != null) {
                        // OnePlus 5T PMIC Keep-Alive: drop torch current to 0 (or 1) rapidly
                        primaryRaf.seek(0)
                        primaryRaf.write(BYTES_ZERO)
                        if (secondaryRaf != null) {
                            secondaryRaf.seek(0)
                            secondaryRaf.write(BYTES_ZERO)
                        }
                        switchRaf.seek(0)
                        switchRaf.write(BYTES_ZERO)
                    } else {
                        if (switchRaf != null) {
                            switchRaf.seek(0)
                            switchRaf.write(BYTES_ZERO)
                        }
                        primaryRaf.seek(0)
                        primaryRaf.write(BYTES_ZERO)
                        if (secondaryRaf != null) {
                            secondaryRaf.seek(0)
                            secondaryRaf.write(BYTES_ZERO)
                        }
                    }
                }
                return if (mode == DriverMode.LSPOSED_DIRECT) {
                    "LSPosed Direct FD ($onBrightness)"
                } else {
                    "KernelSU Direct FD ($onBrightness)"
                }
            } catch (_: Throwable) {
                // Fall through to persistent root shell or CameraManager
            }
        }

        // 2. Persistent KernelSU / Root Shell Stream (when SELinux requires root context per write)
        if (mode == DriverMode.ROOT_KERNELSU_SYSFS || mode == DriverMode.LSPOSED_DIRECT) {
            val writer = rootWriter
            if (writer != null) {
                try {
                    if (on) {
                        writer.write("echo $onBrightness > $primaryPath")
                        if (secondaryPath.isNotBlank()) {
                            writer.write("; echo $onBrightness > $secondaryPath")
                        }
                        if (switchPath.isNotBlank()) {
                            writer.write("; echo 1 > $switchPath")
                        }
                        writer.write("\n")
                        writer.flush()
                    } else {
                        if (switchPath.isNotBlank()) {
                            writer.write("echo 0 > $switchPath; ")
                        }
                        writer.write("echo $offBrightness > $primaryPath")
                        if (secondaryPath.isNotBlank()) {
                            writer.write("; echo $offBrightness > $secondaryPath")
                        }
                        writer.write("\n")
                        writer.flush()
                    }
                    return "KernelSU Root Shell ($onBrightness)"
                } catch (_: Throwable) {
                    // Fall through to CameraManager fallback
                }
            }
        }

        // 3. Non-Root Standard Android CameraManager API (or automatic fallback if unrooted)
        val cm = cameraManager
        val camId = torchCameraId
        if (cm != null && camId != null && cameraTorchSupported) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && cameraMaxStrengthLevel > 1) {
                    if (on) {
                        val level = max(1, ((cameraMaxStrengthLevel * ledIntensityPercentAtomic.get()) / 100.0).roundToInt())
                        cm.turnOnTorchWithStrengthLevel(camId, level)
                    } else {
                        cm.setTorchMode(camId, false)
                    }
                    return "Camera2 Strength API"
                } else {
                    cm.setTorchMode(camId, on)
                    return if (mode == DriverMode.NON_ROOT_CAMERA) {
                        "Camera2 Torch API"
                    } else {
                        "Camera2 Fallback (Enable Root/LSPosed)"
                    }
                }
            } catch (_: Throwable) {
                return "Screen Strobe (Torch Busy)"
            }
        }

        return "Optical Screen Strobe"
    }

    fun cleanup() {
        stopStrobe()
        synchronized(this) {
            try {
                rootWriter?.write("exit\n")
                rootWriter?.flush()
                rootWriter?.close()
                rootProcess?.destroy()
            } catch (_: Throwable) {}
            rootWriter = null
            rootProcess = null
        }
    }

    companion object {
        private val BYTES_ZERO = "0\n".toByteArray(Charsets.US_ASCII)
        private val BYTES_ONE = "1\n".toByteArray(Charsets.US_ASCII)
    }
}

/**
 * Ultra-low-latency acoustic click generator for phase-locked audio/stroboscope calibration.
 */
private class ShortPulseAudioGenerator {
    private var audioTrack: AudioTrack? = null
    private val clickPcm = ShortArray(48)

    init {
        try {
            val sampleRate = 48000
            // Crisp 1ms damped impulse click
            for (i in clickPcm.indices) {
                val env = 1.0 - (i.toDouble() / clickPcm.size)
                clickPcm[i] = (Short.MAX_VALUE * 0.75 * env * if (i % 4 < 2) 1 else -1).toInt().toShort()
            }
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(clickPcm.size * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(clickPcm, 0, clickPcm.size)
            audioTrack = track
        } catch (_: Throwable) {
            audioTrack = null
        }
    }

    fun triggerClick() {
        try {
            val track = audioTrack ?: return
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.reloadStaticData()
            track.play()
        } catch (_: Throwable) {}
    }

    fun release() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Throwable) {}
        audioTrack = null
    }
}
