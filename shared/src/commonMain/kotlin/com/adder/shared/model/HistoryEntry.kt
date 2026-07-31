package com.adder.shared.model

import kotlinx.serialization.Serializable

/**
 * A single recorded page visit in the browsing history.
 *
 * Instances are produced by `HistoryStore` when a page finishes loading, and are
 * persisted as a JSON list so history survives app restarts.
 */
@Serializable
data class HistoryEntry(
    /**
     * The full page URL. This is the identity used for deduplication — two visits
     * with the same URL string (exact match) collapse into a single entry whose
     * [visitTimestamp] is refreshed rather than a second entry.
     */
    val url: String,
    /**
     * The label shown in the history list. Resolved once at record time: the page
     * title truncated to 512 characters, or [url] when the page reports no title.
     * Because the fallback is baked in here, the UI never needs its own fallback.
     */
    val displayLabel: String,
    /** Visit time in epoch milliseconds. Drives the descending ordering of the list. */
    val visitTimestamp: Long
)
