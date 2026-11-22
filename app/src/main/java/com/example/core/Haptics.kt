package com.example.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * One gate for the app's subtle haptics. The preference is mirrored here so every
 * call site (Compose, ViewModel, biometric callbacks) can stay terse and the
 * setting switches the whole app off instantly.
 */
object Haptics {

    @Volatile
    var enabled: Boolean = true

    /** Tick for toggles, chips and tab changes. Noticeably firmer than v1.7.21. */
    fun tick(context: Context) = vibrate(context, durationMs = 10L, amplitude = 100)

    /**
     * Lighter tick for continuous controls (sliders). Still stronger than v1.7.21,
     * but avoids a buzzing motor when dragging across many steps.
     */
    fun sliderTick(context: Context) = vibrate(context, durationMs = 7L, amplitude = 70)

    /** Fuller confirmation, used sparingly (unlock / save success). */
    fun success(context: Context) = vibrate(context, durationMs = 18L, amplitude = 170)

    private fun vibrate(context: Context, durationMs: Long, amplitude: Int) {
        if (!enabled) return
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        amplitude.coerceIn(1, 255),
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    }
}
