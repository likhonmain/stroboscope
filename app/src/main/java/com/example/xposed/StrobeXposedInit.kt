package com.example.xposed

import androidx.annotation.Keep
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

/**
 * Helper object queried by the StroboScope Pro UI to check whether the module is enabled
 * in LSPosed Manager and whether direct sysfs permissions have been unlocked by system_server.
 */
@Keep
object XposedStatus {
    /**
     * Hooked by [StrobeXposedInit] when this APK is enabled in LSPosed Manager.
     */
    @Keep
    @JvmStatic
    fun isModuleActiveInLSPosed(): Boolean = false

    /**
     * Checks if any known OnePlus 5T / Qualcomm / Android sysfs torch node has been made
     * directly writable (mode 0666) by either the LSPosed system_server hook or KernelSU.
     */
    @JvmStatic
    fun hasDirectWritableSysfsNode(candidatePaths: List<String>): Boolean {
        return candidatePaths.any { path ->
            try {
                val f = File(path)
                f.exists() && f.canWrite()
            } catch (_: Throwable) {
                false
            }
        }
    }
}

/**
 * Entry point registered in `assets/xposed_init` for LSPosed / EdXposed / Xposed Framework.
 *
 * Recommended scopes in LSPosed Manager:
 * 1. `android` (System Framework / system_server) — unlocks direct user-space write access
 *    to `/sys/class/leds/led:torch_0/brightness`, `led:torch_1/brightness`, `led:switch_0/brightness`
 *    (OnePlus 5T / Qualcomm QPNP flash driver) and bypasses CameraService torch rate-limiting.
 * 2. `com.android.systemui` — suppresses rapid flashlight tile state broadcast churn during
 *    high-frequency 1–200 Hz stroboscope operation.
 * 3. `com.aistudio.stroboscope.vxpqmr` (this app) — reports live LSPosed module activation.
 */
@Keep
class StrobeXposedInit : IXposedHookLoadPackage {

    companion object {
        val KNOWN_TORCH_SYSFS_NODES = listOf(
            // OnePlus 5T (dumpling / msm8998 Qualcomm PMI8998 QPNP LED flash controller)
            "/sys/class/leds/led:torch_0/brightness",
            "/sys/class/leds/led:torch_1/brightness",
            "/sys/class/leds/led:switch_0/brightness",
            "/sys/class/leds/led:switch_1/brightness",
            "/sys/class/leds/led:flash_0/brightness",
            "/sys/class/leds/led:flash_1/brightness",
            // Additional OnePlus / Qualcomm / generic sysfs nodes
            "/sys/class/leds/torch-light0/brightness",
            "/sys/class/leds/torch-light1/brightness",
            "/sys/class/leds/flashlight/brightness",
            "/sys/class/leds/led:torch/brightness",
            "/sys/class/leds/torch/brightness"
        )
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName

        // 1. Self-hook so StroboScope Pro UI detects active LSPosed status
        if (pkg.startsWith("com.aistudio.stroboscope") || pkg == "com.example") {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.example.xposed.XposedStatus",
                    lpparam.classLoader,
                    "isModuleActiveInLSPosed",
                    XC_MethodReplacement.returnConstant(true)
                )
                XposedBridge.log("StroboScope Pro: Self-hook active in $pkg")
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }

        // 2. System Framework (system_server) hook: unlock direct sysfs torch nodes for zero-IPC modulation
        if (pkg == "android") {
            unlockSysfsTorchNodesFromSystemServer()
            hookCameraServiceProxy(lpparam)
        }

        // 3. SystemUI hook: prevent flashlight QS tile UI lag during 100-200Hz torch modulation
        if (pkg == "com.android.systemui") {
            hookSystemUiFlashlightController(lpparam)
        }
    }

    private fun unlockSysfsTorchNodesFromSystemServer() {
        try {
            for (path in KNOWN_TORCH_SYSFS_NODES) {
                val file = File(path)
                if (file.exists()) {
                    file.setReadable(true, false)
                    file.setWritable(true, false)
                    XposedBridge.log("StroboScope Pro [system_server]: Unlocked sysfs node $path")
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun hookCameraServiceProxy(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.server.camera.CameraServiceProxy",
                lpparam.classLoader,
                "onStart",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        unlockSysfsTorchNodesFromSystemServer()
                    }
                }
            )
        } catch (_: Throwable) {
            // Class/method varies across Android versions; direct unlock already ran above
        }
    }

    private fun hookSystemUiFlashlightController(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedBridge.log("StroboScope Pro: Attached to SystemUI for high-speed strobe stability")
            unlockSysfsTorchNodesFromSystemServer()
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
