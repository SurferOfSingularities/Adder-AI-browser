package com.adder.shared.engine

import kotlinx.datetime.Clock

/**
 * Simple in-memory LRU cache for ad detection results.
 * Caches which selectors were identified as ads on a per-domain basis,
 * so subsequent visits to the same domain can skip re-analysis of known patterns.
 */
class DetectionCache(private val maxSize: Int = 50) {

    private val cache = LinkedHashMap<String, CacheEntry>(maxSize, 0.75f, true)

    data class CacheEntry(
        val knownAdSelectors: Set<String>,
        val timestamp: Long
    )

    /**
     * Get cached ad selectors for a domain, or null if not cached.
     * Entries older than 30 minutes are considered stale.
     */
    fun get(domain: String): Set<String>? {
        val entry = cache[normalizeDomain(domain)] ?: return null
        val age = currentTimeMillis() - entry.timestamp
        if (age > CACHE_TTL_MS) {
            cache.remove(normalizeDomain(domain))
            return null
        }
        return entry.knownAdSelectors
    }

    /**
     * Store ad selectors for a domain.
     */
    fun put(domain: String, selectors: Set<String>) {
        val key = normalizeDomain(domain)
        cache[key] = CacheEntry(selectors, currentTimeMillis())

        // Evict oldest entries if over capacity
        while (cache.size > maxSize) {
            val oldest = cache.entries.firstOrNull() ?: break
            cache.remove(oldest.key)
        }
    }

    /**
     * Clear the entire cache.
     */
    fun clear() {
        cache.clear()
    }

    private fun normalizeDomain(domain: String): String {
        return domain.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    private fun currentTimeMillis(): Long {
        return Clock.System.now().toEpochMilliseconds()
    }

    companion object {
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
    }
}
