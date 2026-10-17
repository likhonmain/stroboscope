package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DriverMode
import com.example.model.StrobeUiState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("StroboScope Pro", appName)
    }

    @Test
    fun `verify 4-stage granular slider frequency math and harmonic multipliers`() {
        val baseState = StrobeUiState(
            slider1CoarseHz = 100,
            slider2MediumOffsetHz = 15,
            slider3FineOffsetHz = -3,
            slider4DecimalHz = 0.45f,
            harmonicMultiplier = 1.0
        )
        // 100 + 15 - 3 + 0.45 = 112.45 Hz
        assertEquals(112.45, baseState.effectiveFrequencyHz, 0.001)
        assertEquals(112.45 * 60.0, baseState.effectiveRpm, 0.01)

        // 0.5x harmonic option -> 56.23 Hz (rounded to 2 decimal places)
        val halfState = baseState.copy(harmonicMultiplier = 0.5)
        assertEquals(56.23, halfState.effectiveFrequencyHz, 0.01)

        // 2.0x harmonic option -> 224.90 Hz
        val doubleState = baseState.copy(harmonicMultiplier = 2.0)
        assertEquals(224.90, doubleState.effectiveFrequencyHz, 0.01)
    }

    @Test
    fun `verify non-root, root kernelsu, and lsposed working modes`() {
        assertEquals(DriverMode.NON_ROOT_CAMERA, DriverMode.fromId("non_root"))
        assertEquals(DriverMode.ROOT_KERNELSU_SYSFS, DriverMode.fromId("root_kernelsu"))
        assertEquals(DriverMode.LSPOSED_DIRECT, DriverMode.fromId("lsposed_direct"))
    }
}
