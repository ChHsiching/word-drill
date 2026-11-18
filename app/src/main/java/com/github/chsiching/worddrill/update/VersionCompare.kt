package com.github.chsiching.worddrill.update

/**
 * Semantic compare of a release tag against the running build's version name.
 * Release tags carry a leading "v" (release.yml publishes v* tags); VERSION_NAME does not.
 * Segments compare numerically; missing segments count as 0, so "0.2" equals "0.2.0".
 * Anything non-numeric inside a segment ("0.2.1-beta") is read as its leading digits.
 */
internal fun isNewerVersion(remote: String, current: String): Boolean {
    val r = versionSegments(remote)
    val c = versionSegments(current)
    for (i in 0 until maxOf(r.size, c.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = c.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun versionSegments(version: String): List<Int> =
    version
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.')
        .map { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
