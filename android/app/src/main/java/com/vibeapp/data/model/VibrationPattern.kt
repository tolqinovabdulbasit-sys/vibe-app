package com.vibeapp.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class StepType { VIBRATE, PAUSE }

@Serializable
data class PatternStep(
    val type: StepType,
    val durationMs: Long
)

@Serializable
data class VibrationPattern(
    val slot: Int,           // 1–10 for saved patterns, 0 for manual
    val name: String,
    val steps: List<PatternStep>,
    val repeat: Int = 1,
    val enabled: Boolean = true
)

/** Default 10 patterns seeded on first launch */
object DefaultPatterns {
    val all = listOf(
        VibrationPattern(
            slot = 1, name = "Short",
            steps = listOf(PatternStep(StepType.VIBRATE, 100))
        ),
        VibrationPattern(
            slot = 2, name = "Long",
            steps = listOf(PatternStep(StepType.VIBRATE, 700))
        ),
        VibrationPattern(
            slot = 3, name = "Double Short",
            steps = listOf(
                PatternStep(StepType.VIBRATE, 100),
                PatternStep(StepType.PAUSE, 120),
                PatternStep(StepType.VIBRATE, 100)
            )
        ),
        VibrationPattern(
            slot = 4, name = "Long + Short",
            steps = listOf(
                PatternStep(StepType.VIBRATE, 600),
                PatternStep(StepType.PAUSE, 120),
                PatternStep(StepType.VIBRATE, 100)
            )
        ),
        VibrationPattern(
            slot = 5, name = "Short + Long",
            steps = listOf(
                PatternStep(StepType.VIBRATE, 100),
                PatternStep(StepType.PAUSE, 120),
                PatternStep(StepType.VIBRATE, 600)
            )
        ),
        VibrationPattern(
            slot = 6, name = "Triple Short",
            steps = listOf(
                PatternStep(StepType.VIBRATE, 100),
                PatternStep(StepType.PAUSE, 100),
                PatternStep(StepType.VIBRATE, 100),
                PatternStep(StepType.PAUSE, 100),
                PatternStep(StepType.VIBRATE, 100)
            )
        ),
        VibrationPattern(
            slot = 7, name = "SOS",
            steps = listOf(
                // ... (3 short)
                PatternStep(StepType.VIBRATE, 80), PatternStep(StepType.PAUSE, 80),
                PatternStep(StepType.VIBRATE, 80), PatternStep(StepType.PAUSE, 80),
                PatternStep(StepType.VIBRATE, 80), PatternStep(StepType.PAUSE, 200),
                // --- (3 long)
                PatternStep(StepType.VIBRATE, 400), PatternStep(StepType.PAUSE, 100),
                PatternStep(StepType.VIBRATE, 400), PatternStep(StepType.PAUSE, 100),
                PatternStep(StepType.VIBRATE, 400), PatternStep(StepType.PAUSE, 200),
                // ... (3 short)
                PatternStep(StepType.VIBRATE, 80), PatternStep(StepType.PAUSE, 80),
                PatternStep(StepType.VIBRATE, 80), PatternStep(StepType.PAUSE, 80),
                PatternStep(StepType.VIBRATE, 80)
            )
        ),
        VibrationPattern(
            slot = 8, name = "Heartbeat",
            steps = listOf(
                PatternStep(StepType.VIBRATE, 100),
                PatternStep(StepType.PAUSE, 80),
                PatternStep(StepType.VIBRATE, 200)
            ),
            repeat = 2
        ),
        VibrationPattern(
            slot = 9, name = "Urgent",
            steps = listOf(
                PatternStep(StepType.VIBRATE, 300),
                PatternStep(StepType.PAUSE, 80)
            ),
            repeat = 4
        ),
        VibrationPattern(
            slot = 10, name = "Gentle",
            steps = listOf(PatternStep(StepType.VIBRATE, 50)),
            repeat = 3
        )
    )
}
