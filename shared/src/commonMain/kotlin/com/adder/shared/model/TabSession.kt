package com.adder.shared.model

import kotlinx.serialization.Serializable

/**
 * Persisted representation of the tab collection. Stored as a single JSON blob
 * in the [PersistentStore] under key [TabSession.STORAGE_KEY].
 *
 * Contains all information needed to reconstruct the tab collection on next
 * app launch: each tab's URL and title, their order, and which one was active.
 */
@Serializable
data class TabSession(
    /** Tabs in their display order (position 0 = leftmost). */
    val tabs: List<Tab>,
    /** ID of the tab that was active when the session was persisted. */
    val activeTabId: String
) {
    companion object {
        const val STORAGE_KEY = "adder.tab_session.v1"
    }
}
