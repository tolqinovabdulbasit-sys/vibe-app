package com.vibeapp.core.vibration

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vibeapp.data.model.StepType
import com.vibeapp.data.model.VibrationPattern
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core vibration engine.
 * - Plays one-shot patterns
 * - Supports manual continuous vibration (START/STOP)
 * - Deduplicates commands via processed command ID cache
 */
@Singleton
class VibrationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private var manualVibrationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // LRU-style dedup cache — last 500 processed command IDs
    private val processedCommandIds: MutableSet<String> = Collections.synchronizedSet(
        object : LinkedHashMap<String, Boolean>(500, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>) = size > 500
        }.keys
    )

    /**
     * Returns true if this commandId is new and was processed.
     * Returns false if it was already processed (duplicate → ignore).
     */
    fun tryMarkProcessed(commandId: String): Boolean {
        return processedCommandIds.add(commandId)
    }

    /**
     * Plays a one-shot vibration pattern.
     */
    fun playPattern(pattern: VibrationPattern) {
        stopManual()
        val timings = buildTimings(pattern)
        val amplitudes = buildAmplitudes(pattern)

        Timber.d("Playing pattern slot=${pattern.slot} name=${pattern.name} timings=${timings.toList()}")

        val effect = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val repeatIndex = if (pattern.repeat > 1) 0 else -1
            VibrationEffect.createWaveform(timings, amplitudes, repeatIndex)
        } else {
            @Suppress("DEPRECATION")
            null
        }

        if (effect != null) {
            if (pattern.repeat > 1) {
                // Cancel after full repeats
                scope.launch {
                    val totalDuration = timings.sum() * pattern.repeat
                    vibrator.vibrate(effect)
                    delay(totalDuration)
                    vibrator.cancel()
                }
            } else {
                vibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    /**
     * Starts continuous manual vibration.
     * Loops until stopManual() is called.
     */
    fun startManual() {
        stopManual()
        Timber.d("Manual vibration started")

        manualVibrationJob = scope.launch {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Repeating waveform: 500ms on, 0ms off (continuous)
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 500), intArrayOf(0, 255), 0
                )
                vibrator.vibrate(effect)
                // Keep alive — cancel will stop the repeating effect
                while (isActive) delay(1000)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500), 0)
                while (isActive) delay(1000)
            }
        }
    }

    /**
     * Stops manual vibration immediately.
     * Called on MANUAL_STOP command or connection loss.
     */
    fun stopManual() {
        manualVibrationJob?.cancel()
        manualVibrationJob = null
        vibrator.cancel()
        Timber.d("Vibration stopped")
    }

    fun stopAll() {
        stopManual()
        vibrator.cancel()
    }

    // --- Helpers ---

    private fun buildTimings(pattern: VibrationPattern): LongArray {
        // VibrationEffect.createWaveform expects alternating off/on starting with "off"
        val result = mutableListOf<Long>(0L) // Leading 0ms silence
        for (step in pattern.steps) {
            result.add(step.durationMs)
        }
        return result.toLongArray()
    }

    private fun buildAmplitudes(pattern: VibrationPattern): IntArray {
        val result = mutableListOf<Int>(0) // Leading 0 amplitude
        for (step in pattern.steps) {
            result.add(
                when (step.type) {
                    StepType.VIBRATE -> 255
                    StepType.PAUSE -> 0
                }
            )
        }
        return result.toIntArray()
    }
}
