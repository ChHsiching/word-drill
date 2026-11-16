package com.github.chsiching.worddrill.data.settings

/** Dwell time (GLOSSARY.md): how long auto-advance stays on a card before advancing.
 *  Stored as one global float preference; every write is snapped to a 0.5 s step
 *  inside the 1–30 s range so the stored value is always a legal slider position. */
object DwellInterval {

    const val DEFAULT_SECONDS = 5f
    const val MIN_SECONDS = 1f
    const val MAX_SECONDS = 30f
    const val STEP_SECONDS = 0.5f

    /** Number of 0.5 s steps between the bounds (58), i.e. value positions minus one. */
    val STEPS_BETWEEN_BOUNDS: Int = ((MAX_SECONDS - MIN_SECONDS) / STEP_SECONDS).toInt() - 1

    /** Snaps to the nearest 0.5 s step, then clamps into 1–30 s. */
    fun normalize(seconds: Float): Float =
        (Math.round(seconds / STEP_SECONDS) * STEP_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS)
}
