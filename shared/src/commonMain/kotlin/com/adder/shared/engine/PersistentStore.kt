package com.adder.shared.engine

/**
 * Minimal key/value string storage.
 *
 * Both operations are failure-tolerant by contract: storage being unavailable must
 * never propagate an exception to callers, so a misbehaving platform store can only
 * cost durability, never crash the browser.
 *
 * This is an interface rather than an `expect class` so callers can be handed an
 * in-memory or deliberately-failing implementation in tests.
 */
interface PersistentStore {
    /**
     * Returns the string stored under [key], or `null` if nothing is stored there
     * or the read fails.
     */
    fun getString(key: String): String?

    /**
     * Persists [value] under [key]. Silently does nothing if the write fails or
     * storage is unavailable.
     */
    fun putString(key: String, value: String)
}

/**
 * The durable store provided by the current platform:
 * - Android: `SharedPreferences`
 * - iOS: `NSUserDefaults`
 */
expect fun platformPersistentStore(): PersistentStore
