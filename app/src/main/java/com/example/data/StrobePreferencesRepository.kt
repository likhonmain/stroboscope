package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DriverMode
import com.example.model.SysfsProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.strobeDataStore by preferencesDataStore(name = "stroboscope_prefs")

data class StoredStrobeConfig(
    val driverMode: DriverMode = DriverMode.NON_ROOT_CAMERA,
    val sysfsProfile: SysfsProfile = SysfsProfile.ONEPLUS_5T_DUMPLING,
    val dutyCyclePercent: Float = 15f,
    val ledIntensityPercent: Int = 100,
    val pmicKeepAliveDimming: Boolean = true,
    val screenStrobeSync: Boolean = true,
    val customPrimaryPath: String = "/sys/class/leds/led:torch_0/brightness",
    val customSecondaryPath: String = "/sys/class/leds/led:torch_1/brightness",
    val customSwitchPath: String = "/sys/class/leds/led:switch_0/brightness",
    val customMaxBrightness: Int = 200
)

class StrobePreferencesRepository(private val context: Context) {

    private object Keys {
        val DRIVER_MODE = stringPreferencesKey("driver_mode")
        val SYSFS_PROFILE = stringPreferencesKey("sysfs_profile")
        val DUTY_CYCLE = floatPreferencesKey("duty_cycle")
        val LED_INTENSITY = intPreferencesKey("led_intensity")
        val PMIC_KEEP_ALIVE = booleanPreferencesKey("pmic_keep_alive")
        val SCREEN_STROBE = booleanPreferencesKey("screen_strobe")
        val CUSTOM_PRIMARY = stringPreferencesKey("custom_primary_path")
        val CUSTOM_SECONDARY = stringPreferencesKey("custom_secondary_path")
        val CUSTOM_SWITCH = stringPreferencesKey("custom_switch_path")
        val CUSTOM_MAX_BRIGHTNESS = intPreferencesKey("custom_max_brightness")
    }

    val configFlow: Flow<StoredStrobeConfig> = context.strobeDataStore.data.map { prefs ->
        StoredStrobeConfig(
            driverMode = DriverMode.fromId(prefs[Keys.DRIVER_MODE] ?: DriverMode.NON_ROOT_CAMERA.id),
            sysfsProfile = SysfsProfile.fromId(prefs[Keys.SYSFS_PROFILE] ?: SysfsProfile.ONEPLUS_5T_DUMPLING.id),
            dutyCyclePercent = prefs[Keys.DUTY_CYCLE] ?: 15f,
            ledIntensityPercent = prefs[Keys.LED_INTENSITY] ?: 100,
            pmicKeepAliveDimming = prefs[Keys.PMIC_KEEP_ALIVE] ?: true,
            screenStrobeSync = prefs[Keys.SCREEN_STROBE] ?: true,
            customPrimaryPath = prefs[Keys.CUSTOM_PRIMARY] ?: "/sys/class/leds/led:torch_0/brightness",
            customSecondaryPath = prefs[Keys.CUSTOM_SECONDARY] ?: "/sys/class/leds/led:torch_1/brightness",
            customSwitchPath = prefs[Keys.CUSTOM_SWITCH] ?: "/sys/class/leds/led:switch_0/brightness",
            customMaxBrightness = prefs[Keys.CUSTOM_MAX_BRIGHTNESS] ?: 200
        )
    }

    suspend fun saveDriverConfig(
        driverMode: DriverMode,
        sysfsProfile: SysfsProfile,
        dutyCyclePercent: Float,
        ledIntensityPercent: Int,
        pmicKeepAliveDimming: Boolean,
        screenStrobeSync: Boolean,
        customPrimaryPath: String,
        customSecondaryPath: String,
        customSwitchPath: String,
        customMaxBrightness: Int
    ) {
        context.strobeDataStore.edit { prefs ->
            prefs[Keys.DRIVER_MODE] = driverMode.id
            prefs[Keys.SYSFS_PROFILE] = sysfsProfile.id
            prefs[Keys.DUTY_CYCLE] = dutyCyclePercent
            prefs[Keys.LED_INTENSITY] = ledIntensityPercent
            prefs[Keys.PMIC_KEEP_ALIVE] = pmicKeepAliveDimming
            prefs[Keys.SCREEN_STROBE] = screenStrobeSync
            prefs[Keys.CUSTOM_PRIMARY] = customPrimaryPath
            prefs[Keys.CUSTOM_SECONDARY] = customSecondaryPath
            prefs[Keys.CUSTOM_SWITCH] = customSwitchPath
            prefs[Keys.CUSTOM_MAX_BRIGHTNESS] = customMaxBrightness
        }
    }
}
