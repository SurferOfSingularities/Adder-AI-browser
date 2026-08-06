package com.adder.shared.engine

import com.adder.shared.model.Tab
import com.adder.shared.model.TabSession
import com.adder.shared.model.generateTabId
import kotlinx.serialization.json.Json

/**
 * Owns the tab collection and enforces all invariants: creation, closing,
 * activation, ordering, live-WebView budgeting, and the "always ≥ 1 tab" rule.
 *
 * Persistence is optional: when a [PersistentStore] is supplied, the session is
 * restored on construction and written after every mutation. All persistence
 * operations are wrapped in `runCatching` so a broken store never crashes the
 * browser (requirement 9.7).
 *
 * The tab order is defined by position in [tabs] (index 0 = leftmost). The
 * most-recently-activated list ([recentlyActivated]) is maintained for the
 * live-WebView budget calculation.
 */
class TabManager(
    private val store: PersistentStore? = null
) {

    /** Ordered tab collection. Position 0 is the first (leftmost) tab. */
    var tabs: List<Tab> = emptyList()
        private set

    /** ID of the currently active tab. Always refers to a tab in [tabs]. */
    var activeTabId: String = ""
        private set

    /**
     * Most-recently-activated tab IDs, front = most recent. Used to determine
     * which tabs keep a live WebView when the count exceeds [LIVE_WEBVIEW_BUDGET].
     */
    var recentlyActivated: List<String> = emptyList()
        private set

    init {
        val restored = restore()
        if (restored != null) {
            tabs = restored.tabs
            activeTabId = restored.activeTabId
            recentlyActivated = listOf(activeTabId)
        } else {
            val tab = createInitialTab()
            tabs = listOf(tab)
            activeTabId = tab.id
            recentlyActivated = listOf(tab.id)
        }
    }

    /** The currently active [Tab], guaranteed non-null while the manager is alive. */
    val activeTab: Tab
        get() = tabs.first { it.id == activeTabId }

    /** Current number of tabs. */
    val tabCount: Int
        get() = tabs.size

    // -------------------------------------------------------------------------
    // Intents
    // -------------------------------------------------------------------------

    /**
     * Creates a new tab loading [url], appends it to the end of the tab order,
     * and activates it.
     *
     * @return the created [Tab], or `null` if the [TAB_LIMIT] is reached.
     */
    fun createTab(url: String = INITIAL_URL): Tab? {
        if (tabs.size >= TAB_LIMIT) return null

        val tab = Tab(
            id = generateTabId(),
            url = url,
            displayTitle = url
        )
        tabs = tabs + tab
        activateTabInternal(tab.id)
        persist()
        return tab
    }

    /**
     * Closes the tab with [id]. If it was the active tab, activates the nearest
     * successor per the spec (higher position first, then lower). If it was the
     * last tab, creates a fresh initial tab.
     *
     * Does nothing if [id] is not present in the collection (requirement 3.7).
     */
    fun closeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index == -1) return // Not present — no-op.

        val wasActive = id == activeTabId
        tabs = tabs.filterNot { it.id == id }
        recentlyActivated = recentlyActivated.filter { it != id }

        if (tabs.isEmpty()) {
            // Last tab closed — create a fresh one.
            val fresh = createInitialTab()
            tabs = listOf(fresh)
            activeTabId = fresh.id
            recentlyActivated = listOf(fresh.id)
            persist()
            return
        }

        if (wasActive) {
            // Pick the successor: nearest higher position first, then lower.
            val newIndex = if (index < tabs.size) index else tabs.size - 1
            activeTabId = tabs[newIndex].id
            pushToMru(activeTabId)
        }
        persist()
    }

    /**
     * Sets the tab with [id] as the active tab and records it as most recently
     * activated. Does nothing if [id] is not in the collection.
     */
    fun activateTab(id: String) {
        if (tabs.none { it.id == id }) return
        activateTabInternal(id)
        persist()
    }

    /**
     * Updates the URL and display title for the tab with [tabId].
     * Used when a page finishes loading.
     */
    fun updateTab(tabId: String, url: String, displayTitle: String, renderedBlockingMode: Boolean? = null) {
        tabs = tabs.map { tab ->
            if (tab.id == tabId) {
                tab.copy(
                    url = url,
                    displayTitle = displayTitle.take(MAX_TITLE_LENGTH).ifEmpty { url },
                    renderedBlockingMode = renderedBlockingMode ?: tab.renderedBlockingMode
                )
            } else {
                tab
            }
        }
        persist()
    }

    /**
     * Updates only the rendered blocking mode for a tab.
     */
    fun updateRenderedBlockingMode(tabId: String, mode: Boolean) {
        tabs = tabs.map { tab ->
            if (tab.id == tabId) tab.copy(renderedBlockingMode = mode) else tab
        }
        persist()
    }

    // -------------------------------------------------------------------------
    // Live WebView budget
    // -------------------------------------------------------------------------

    /**
     * Returns the set of tab IDs that should hold a live platform WebView.
     * Always includes the active tab, plus up to [LIVE_WEBVIEW_BUDGET] - 1 most
     * recently activated other tabs.
     */
    fun liveTabIds(): Set<String> {
        val live = mutableSetOf(activeTabId)
        for (id in recentlyActivated) {
            if (live.size >= LIVE_WEBVIEW_BUDGET) break
            if (id != activeTabId && tabs.any { it.id == id }) {
                live.add(id)
            }
        }
        return live
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    /**
     * Attempts to restore the tab session from the [PersistentStore].
     *
     * Returns a valid [TabSession] if restoration succeeds and the data is
     * usable, or `null` if the store is absent, the key is missing, the JSON is
     * corrupt, or the session contains zero tabs.
     *
     * If the persisted session has more than [TAB_LIMIT] tabs, only the first
     * [TAB_LIMIT] are retained (requirement 9.6).
     */
    private fun restore(): TabSession? {
        if (store == null) return null

        val session = runCatching {
            val raw = store.getString(TabSession.STORAGE_KEY) ?: return null
            json.decodeFromString<TabSession>(raw)
        }.getOrNull() ?: return null

        if (session.tabs.isEmpty()) return null

        // Enforce limit on restored tabs.
        val trimmedTabs = session.tabs.take(TAB_LIMIT)

        // Ensure the persisted active tab ID is valid within the trimmed set.
        val activeId = if (trimmedTabs.any { it.id == session.activeTabId }) {
            session.activeTabId
        } else {
            trimmedTabs.first().id
        }

        return TabSession(tabs = trimmedTabs, activeTabId = activeId)
    }

    /**
     * Writes the current tab session to the [PersistentStore]. Failures are
     * silently swallowed (requirement 9.7).
     */
    private fun persist() {
        if (store == null) return
        runCatching {
            val session = TabSession(tabs = tabs, activeTabId = activeTabId)
            store.putString(TabSession.STORAGE_KEY, json.encodeToString(TabSession.serializer(), session))
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun activateTabInternal(id: String) {
        activeTabId = id
        pushToMru(id)
    }

    private fun pushToMru(id: String) {
        recentlyActivated = listOf(id) + recentlyActivated.filter { it != id }
    }

    private fun createInitialTab(): Tab = Tab(
        id = generateTabId(),
        url = INITIAL_URL,
        displayTitle = INITIAL_URL
    )

    companion object {
        /** Maximum number of tabs allowed. */
        const val TAB_LIMIT = 20

        /** Maximum number of tabs that hold a live platform WebView at once. */
        const val LIVE_WEBVIEW_BUDGET = 3

        /** URL loaded into newly created tabs. */
        const val INITIAL_URL = "https://www.google.com"

        /** Maximum display title length (chars). */
        const val MAX_TITLE_LENGTH = 512

        private val json = Json { ignoreUnknownKeys = true }
    }
}
