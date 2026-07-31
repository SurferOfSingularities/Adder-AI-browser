package com.adder.shared.engine

import platform.Foundation.NSUserDefaults

actual fun platformPersistentStore(): PersistentStore = UserDefaultsStore()

/**
 * iOS [PersistentStore] backed by `NSUserDefaults.standardUserDefaults`.
 *
 * Reads and writes are wrapped so any failure degrades to in-memory operation
 * instead of propagating out of the store.
 */
internal class UserDefaultsStore : PersistentStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? =
        runCatching { defaults.stringForKey(key) }.getOrNull()

    override fun putString(key: String, value: String) {
        runCatching { defaults.setObject(value, forKey = key) }
    }
}
