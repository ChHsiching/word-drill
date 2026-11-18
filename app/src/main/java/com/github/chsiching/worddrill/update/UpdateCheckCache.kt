package com.github.chsiching.worddrill.update

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The once-per-launch throttle behind both the silent launch check and the Me tab's
 * manual 检查更新 row: the first caller fetches through [UpdateChecker], every later
 * caller in the same process replays the cached result (failures included, per #59).
 * Singleton so the activity-scoped launch ViewModel and the Me destination's manual
 * one share it; the mutex dedups a manual check arriving while the launch fetch is
 * still in flight.
 */
@Singleton
class UpdateCheckCache @Inject constructor() {
    private val mutex = Mutex()
    private var cached: UpdateCheckResult? = null

    suspend fun getOrFetch(checker: UpdateChecker): UpdateCheckResult = mutex.withLock {
        cached ?: checker.checkForUpdate().also { cached = it }
    }
}
