package com.github.chsiching.worddrill.update

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The once-per-launch throttle behind the silent launch check: the first caller
 * fetches through [UpdateChecker], every later caller in the same process replays
 * the cached result (failures included). Singleton because the launch check's
 * per-ViewModel started flag does not survive activity recreation within a
 * process; the mutex dedups a second launch check arriving while the first fetch
 * is still in flight. The Me tab's manual 检查更新 row deliberately bypasses
 * this cache and always re-fetches (reopened #59).
 */
@Singleton
class UpdateCheckCache @Inject constructor() {
    private val mutex = Mutex()
    private var cached: UpdateCheckResult? = null

    suspend fun getOrFetch(checker: UpdateChecker): UpdateCheckResult = mutex.withLock {
        cached ?: checker.checkForUpdate().also { cached = it }
    }
}
