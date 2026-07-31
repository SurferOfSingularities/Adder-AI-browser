package com.adder.shared.engine

import android.content.Context
import com.adder.shared.AppContextHolder

actual fun platformPersistentStore(): PersistentStore = SharedPreferencesStore()

/**
 * Android [PersistentStore] backed by `SharedPreferences`.
 *
 * A null application Context (App Startup initializer not yet run, or running in a
 * plain JVM unit test) is treated the same as a failed read/write: reads return
 * null, writes no-op. Callers degrade to in-memory operation.
 */
internal class SharedPreferencesStore : PersistentStore {

    private val prefs by lazy {
        AppContextHolder.appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun getString(key: String): String? =
        runCatching { prefs?.getString(key, null) }.getOrNull()

    override fun putString(key: String, value: String) {
        runCatching { prefs?.edit()?.putString(key, value)?.apply() }
    }

    private companion object {
        const val PREFS_NAME = "adder_prefs"
    }
}
