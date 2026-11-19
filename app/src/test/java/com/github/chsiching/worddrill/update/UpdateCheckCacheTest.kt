package com.github.chsiching.worddrill.update

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckCacheTest {

    private class CountingChecker(var result: UpdateCheckResult) : UpdateChecker {
        var calls = 0
        override suspend fun checkForUpdate(): UpdateCheckResult {
            calls++
            return result
        }
    }

    @Test
    fun secondCall_replaysCachedResult_withoutRefetching() = runTest {
        val checker = CountingChecker(UpdateCheckResult.UpToDate)
        val cache = UpdateCheckCache()

        val first = cache.getOrFetch(checker)
        val second = cache.getOrFetch(checker)

        assertThat(checker.calls).isEqualTo(1)
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun concurrentCallers_whileFetchInFlight_shareOneFetch() = runTest(StandardTestDispatcher()) {
        val checker = CountingChecker(UpdateCheckResult.UpToDate)
        val cache = UpdateCheckCache()

        val a = async { cache.getOrFetch(checker) }
        val b = async { cache.getOrFetch(checker) }
        advanceUntilIdle()

        assertThat(checker.calls).isEqualTo(1)
        assertThat(a.await()).isEqualTo(b.await())
    }

    @Test
    fun failureResult_isAlsoCached_neverRefetched() = runTest {
        val checker = CountingChecker(UpdateCheckResult.CheckFailed(com.github.chsiching.worddrill.R.string.update_error_dns))
        val cache = UpdateCheckCache()

        cache.getOrFetch(checker)
        cache.getOrFetch(checker)

        // The launch check throttles the checker to once per launch, failures included.
        assertThat(checker.calls).isEqualTo(1)
    }
}
