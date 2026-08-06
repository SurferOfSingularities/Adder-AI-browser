package com.adder.shared.model

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents a single browser tab's state.
 *
 * A Tab holds its current URL, display title, and the blocking mode under which
 * its currently rendered page was produced. This is the persisted unit — the
 * [TabManager] owns a collection of these and enforces all invariants (ordering,
 * limit, budgeting).
 */
@Serializable
data class Tab(
    /** Unique identifier, generated at creation time. */
    val id: String,
    /** Current URL loaded (or being loaded) in this tab. */
    val url: String,
    /** Label shown in the tab switcher: page title or URL fallback, max 512 chars. */
    val displayTitle: String,
    /**
     * The [blockingEnabled] value that was active when this tab's page last
     * finished loading, or `null` if the tab has never completed a page load
     * (e.g. freshly created or restored from persistence before its first load).
     */
    val renderedBlockingMode: Boolean? = null
)

/**
 * Generates a new unique tab identifier.
 */
@OptIn(ExperimentalUuidApi::class)
fun generateTabId(): String = Uuid.random().toString()
