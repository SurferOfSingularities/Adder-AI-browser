package com.adder.shared

import android.content.Context
import androidx.startup.Initializer

/**
 * Holds the application [Context] so shared-module code can reach Android-only
 * APIs (currently `SharedPreferences`) without threading a Context through the
 * common API surface.
 *
 * Populated by [AdderContextInitializer] at app startup. Consumers must treat a
 * null [appContext] as "storage unavailable" rather than an error, since the
 * initializer may not have run yet (e.g. in unit tests or previews).
 */
object AppContextHolder {
    @Volatile
    var appContext: Context? = null
        internal set
}

/**
 * App Startup initializer that captures the application [Context] into
 * [AppContextHolder]. Registered in the shared module's `AndroidManifest.xml`, so
 * host apps get it automatically via manifest merging — no wiring required in
 * `MainActivity` or a custom `Application`.
 */
class AdderContextInitializer : Initializer<Context> {

    override fun create(context: Context): Context {
        val appContext = context.applicationContext
        AppContextHolder.appContext = appContext
        return appContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
