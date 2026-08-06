package com.adder.shared.engine

import com.adder.shared.model.Tab
import com.adder.shared.model.TabSession
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * In-memory [PersistentStore] implementation for testing. Allows inspecting
 * what was persisted and injecting corrupt or missing data for fallback tests.
 */
class InMemoryPersistentStore : PersistentStore {
    private val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) { map[key] = value }

    fun clear() = map.clear()
    fun put(key: String, value: String) { map[key] = value }
    fun get(key: String): String? = map[key]
}

class TabManagerTest {

    private val json = Json { ignoreUnknownKeys = true }

    // -------------------------------------------------------------------------
    // Creation
    // -------------------------------------------------------------------------

    @Test
    fun defaultConstructorCreatesOneTab() {
        val manager = TabManager()
        assertEquals(1, manager.tabCount)
        assertEquals(TabManager.INITIAL_URL, manager.activeTab.url)
    }

    @Test
    fun createTabIncrementsCount() {
        val manager = TabManager()
        val tab = manager.createTab("https://example.com")
        assertNotNull(tab)
        assertEquals(2, manager.tabCount)
    }

    @Test
    fun createTabAppendsToEnd() {
        val manager = TabManager()
        val firstId = manager.activeTabId
        val second = manager.createTab("https://a.com")!!
        val third = manager.createTab("https://b.com")!!

        assertEquals(firstId, manager.tabs[0].id)
        assertEquals(second.id, manager.tabs[1].id)
        assertEquals(third.id, manager.tabs[2].id)
    }

    @Test
    fun createTabActivatesNewTab() {
        val manager = TabManager()
        val tab = manager.createTab("https://example.com")!!
        assertEquals(tab.id, manager.activeTabId)
    }

    @Test
    fun createTabUsesUrlAsDisplayTitle() {
        val manager = TabManager()
        val tab = manager.createTab("https://example.com")!!
        assertEquals("https://example.com", tab.displayTitle)
    }

    @Test
    fun createdTabsHaveUniqueIds() {
        val manager = TabManager()
        val ids = mutableSetOf(manager.activeTabId)
        repeat(10) {
            val tab = manager.createTab("https://example.com/$it")!!
            assertTrue(ids.add(tab.id), "Duplicate ID: ${tab.id}")
        }
    }

    @Test
    fun createTabDefaultsToInitialUrl() {
        val manager = TabManager()
        val tab = manager.createTab()!!
        assertEquals(TabManager.INITIAL_URL, tab.url)
    }

    // -------------------------------------------------------------------------
    // Tab limit enforcement
    // -------------------------------------------------------------------------

    @Test
    fun createTabReturnsNullAtLimit() {
        val manager = TabManager()
        // Already have 1 tab, create 19 more to hit the limit.
        repeat(TabManager.TAB_LIMIT - 1) {
            assertNotNull(manager.createTab("https://example.com/$it"))
        }
        assertEquals(TabManager.TAB_LIMIT, manager.tabCount)

        // 21st tab should be refused.
        val over = manager.createTab("https://over-limit.com")
        assertNull(over)
        assertEquals(TabManager.TAB_LIMIT, manager.tabCount)
    }

    @Test
    fun createTabAtLimitLeavesActiveUnchanged() {
        val manager = TabManager()
        repeat(TabManager.TAB_LIMIT - 1) {
            manager.createTab("https://example.com/$it")
        }
        val activeBeforeAttempt = manager.activeTabId
        manager.createTab("https://over.com")
        assertEquals(activeBeforeAttempt, manager.activeTabId)
    }

    // -------------------------------------------------------------------------
    // Closing — active tab successor selection
    // -------------------------------------------------------------------------

    @Test
    fun closeActivePicksHigherPositionSuccessor() {
        val manager = TabManager()
        val first = manager.tabs[0]
        val second = manager.createTab("https://second.com")!!
        val third = manager.createTab("https://third.com")!!

        // Activate the middle tab and close it.
        manager.activateTab(second.id)
        manager.closeTab(second.id)

        // Should pick the next higher position (third).
        assertEquals(third.id, manager.activeTabId)
        assertEquals(2, manager.tabCount)
    }

    @Test
    fun closeActiveAtEndPicksLowerPosition() {
        val manager = TabManager()
        val first = manager.tabs[0]
        val second = manager.createTab("https://second.com")!!

        // Active is the last tab (second), close it.
        manager.closeTab(second.id)

        // Should fall back to lower position (first).
        assertEquals(first.id, manager.activeTabId)
        assertEquals(1, manager.tabCount)
    }

    @Test
    fun closeBackgroundTabLeavesActiveUnchanged() {
        val manager = TabManager()
        val first = manager.tabs[0]
        val second = manager.createTab("https://second.com")!!

        // Active is second. Close the first (background) tab.
        manager.closeTab(first.id)

        assertEquals(second.id, manager.activeTabId)
        assertEquals(1, manager.tabCount)
    }

    @Test
    fun closeLastTabCreatesNewInitialTab() {
        val manager = TabManager()
        val onlyTab = manager.activeTabId

        manager.closeTab(onlyTab)

        assertEquals(1, manager.tabCount)
        // New tab has a different ID.
        assertNotEquals(onlyTab, manager.activeTabId)
        assertEquals(TabManager.INITIAL_URL, manager.activeTab.url)
    }

    @Test
    fun closeNonExistentTabIsNoOp() {
        val manager = TabManager()
        val before = manager.tabs.toList()
        val activeBefore = manager.activeTabId

        manager.closeTab("non-existent-id")

        assertEquals(before, manager.tabs)
        assertEquals(activeBefore, manager.activeTabId)
    }

    @Test
    fun closeRetainsRelativeOrder() {
        val manager = TabManager()
        val first = manager.tabs[0]
        val second = manager.createTab("https://second.com")!!
        val third = manager.createTab("https://third.com")!!
        val fourth = manager.createTab("https://fourth.com")!!

        // Close the second tab.
        manager.closeTab(second.id)

        assertEquals(listOf(first.id, third.id, fourth.id), manager.tabs.map { it.id })
    }

    // -------------------------------------------------------------------------
    // Activation
    // -------------------------------------------------------------------------

    @Test
    fun activateTabSetsActive() {
        val manager = TabManager()
        val first = manager.tabs[0]
        manager.createTab("https://second.com")!!

        manager.activateTab(first.id)
        assertEquals(first.id, manager.activeTabId)
    }

    @Test
    fun activateTabPushesToMruFront() {
        val manager = TabManager()
        val first = manager.tabs[0]
        manager.createTab("https://second.com")!!
        manager.createTab("https://third.com")!!

        // After creation: MRU is [third, second, first].
        manager.activateTab(first.id)
        // Now MRU should be [first, third, second, ...].
        assertEquals(first.id, manager.recentlyActivated[0])
    }

    @Test
    fun activateNonExistentIdIsNoOp() {
        val manager = TabManager()
        val activeBefore = manager.activeTabId
        manager.activateTab("bogus-id")
        assertEquals(activeBefore, manager.activeTabId)
    }

    // -------------------------------------------------------------------------
    // MRU ordering
    // -------------------------------------------------------------------------

    @Test
    fun mruReflectsActivationOrder() {
        val manager = TabManager()
        val first = manager.tabs[0]
        val second = manager.createTab("https://second.com")!!
        val third = manager.createTab("https://third.com")!!

        // After creates: third is most recent.
        manager.activateTab(first.id)
        manager.activateTab(second.id)

        // MRU front should be second, then first, then third.
        assertEquals(second.id, manager.recentlyActivated[0])
        assertEquals(first.id, manager.recentlyActivated[1])
        assertEquals(third.id, manager.recentlyActivated[2])
    }

    // -------------------------------------------------------------------------
    // Live WebView budget
    // -------------------------------------------------------------------------

    @Test
    fun liveTabIdsAlwaysIncludesActive() {
        val manager = TabManager()
        assertTrue(manager.liveTabIds().contains(manager.activeTabId))
    }

    @Test
    fun liveTabIdsRespectsBudget() {
        val manager = TabManager()
        repeat(5) { manager.createTab("https://example.com/$it") }

        val liveIds = manager.liveTabIds()
        assertTrue(liveIds.size <= TabManager.LIVE_WEBVIEW_BUDGET)
        assertTrue(liveIds.contains(manager.activeTabId))
    }

    @Test
    fun liveTabIdsUsesRecentlyActivated() {
        val manager = TabManager()
        val first = manager.tabs[0]
        manager.createTab("https://second.com")!!
        val third = manager.createTab("https://third.com")!!
        val fourth = manager.createTab("https://fourth.com")!!
        val fifth = manager.createTab("https://fifth.com")!!

        // Active = fifth (most recent). MRU: [fifth, fourth, third, second, first].
        val live = manager.liveTabIds()
        assertEquals(3, live.size)
        assertTrue(live.contains(fifth.id))  // active
        assertTrue(live.contains(fourth.id)) // 2nd MRU
        assertTrue(live.contains(third.id))  // 3rd MRU
    }

    @Test
    fun liveTabIdsWithFewerTabsThanBudget() {
        val manager = TabManager()
        manager.createTab("https://second.com")!!

        // Only 2 tabs, budget is 3 — both should be live.
        val live = manager.liveTabIds()
        assertEquals(2, live.size)
    }

    // -------------------------------------------------------------------------
    // Update tab metadata
    // -------------------------------------------------------------------------

    @Test
    fun updateTabChangesUrlAndTitle() {
        val manager = TabManager()
        val tabId = manager.activeTabId

        manager.updateTab(tabId, "https://updated.com", "Updated Title")

        assertEquals("https://updated.com", manager.activeTab.url)
        assertEquals("Updated Title", manager.activeTab.displayTitle)
    }

    @Test
    fun updateTabTruncatesTitleToLimit() {
        val manager = TabManager()
        val tabId = manager.activeTabId
        val longTitle = "A".repeat(600)

        manager.updateTab(tabId, "https://example.com", longTitle)

        assertEquals(TabManager.MAX_TITLE_LENGTH, manager.activeTab.displayTitle.length)
    }

    @Test
    fun updateTabFallsBackToUrlIfTitleEmpty() {
        val manager = TabManager()
        val tabId = manager.activeTabId

        manager.updateTab(tabId, "https://notitle.com", "")

        assertEquals("https://notitle.com", manager.activeTab.displayTitle)
    }

    @Test
    fun updateRenderedBlockingModeSetsMode() {
        val manager = TabManager()
        val tabId = manager.activeTabId

        assertNull(manager.activeTab.renderedBlockingMode)

        manager.updateRenderedBlockingMode(tabId, true)
        assertEquals(true, manager.activeTab.renderedBlockingMode)

        manager.updateRenderedBlockingMode(tabId, false)
        assertEquals(false, manager.activeTab.renderedBlockingMode)
    }

    // -------------------------------------------------------------------------
    // Persistence — round-trip
    // -------------------------------------------------------------------------

    @Test
    fun persistsSessionOnCreateTab() {
        val store = InMemoryPersistentStore()
        val manager = TabManager(store)

        manager.createTab("https://new.com")

        val raw = store.get(TabSession.STORAGE_KEY)
        assertNotNull(raw)
        val session = json.decodeFromString<TabSession>(raw)
        assertEquals(2, session.tabs.size)
    }

    @Test
    fun persistsSessionOnCloseTab() {
        val store = InMemoryPersistentStore()
        val manager = TabManager(store)
        val second = manager.createTab("https://second.com")!!

        manager.closeTab(second.id)

        val raw = store.get(TabSession.STORAGE_KEY)!!
        val session = json.decodeFromString<TabSession>(raw)
        assertEquals(1, session.tabs.size)
    }

    @Test
    fun persistsSessionOnActivateTab() {
        val store = InMemoryPersistentStore()
        val manager = TabManager(store)
        val first = manager.tabs[0]
        manager.createTab("https://second.com")!!

        manager.activateTab(first.id)

        val raw = store.get(TabSession.STORAGE_KEY)!!
        val session = json.decodeFromString<TabSession>(raw)
        assertEquals(first.id, session.activeTabId)
    }

    @Test
    fun persistsSessionOnUpdateTab() {
        val store = InMemoryPersistentStore()
        val manager = TabManager(store)
        val tabId = manager.activeTabId

        manager.updateTab(tabId, "https://updated.com", "Updated")

        val raw = store.get(TabSession.STORAGE_KEY)!!
        val session = json.decodeFromString<TabSession>(raw)
        assertEquals("https://updated.com", session.tabs.first().url)
        assertEquals("Updated", session.tabs.first().displayTitle)
    }

    @Test
    fun roundTripRestoresExactState() {
        val store = InMemoryPersistentStore()
        val manager = TabManager(store)
        manager.createTab("https://second.com")
        manager.createTab("https://third.com")
        manager.activateTab(manager.tabs[1].id)

        // Build a new manager from the same store.
        val restored = TabManager(store)

        assertEquals(manager.tabCount, restored.tabCount)
        assertEquals(manager.activeTabId, restored.activeTabId)
        assertEquals(manager.tabs.map { it.id }, restored.tabs.map { it.id })
        assertEquals(manager.tabs.map { it.url }, restored.tabs.map { it.url })
    }

    // -------------------------------------------------------------------------
    // Persistence — fallback behavior
    // -------------------------------------------------------------------------

    @Test
    fun fallsBackToFreshTabOnEmptyStore() {
        val store = InMemoryPersistentStore()
        val manager = TabManager(store)

        assertEquals(1, manager.tabCount)
        assertEquals(TabManager.INITIAL_URL, manager.activeTab.url)
    }

    @Test
    fun fallsBackToFreshTabOnCorruptJson() {
        val store = InMemoryPersistentStore()
        store.put(TabSession.STORAGE_KEY, "not valid json {{{")

        val manager = TabManager(store)

        assertEquals(1, manager.tabCount)
        assertEquals(TabManager.INITIAL_URL, manager.activeTab.url)
    }

    @Test
    fun fallsBackToFreshTabOnEmptyTabsList() {
        val store = InMemoryPersistentStore()
        val emptySession = TabSession(tabs = emptyList(), activeTabId = "gone")
        store.put(TabSession.STORAGE_KEY, json.encodeToString(TabSession.serializer(), emptySession))

        val manager = TabManager(store)

        assertEquals(1, manager.tabCount)
        assertEquals(TabManager.INITIAL_URL, manager.activeTab.url)
    }

    @Test
    fun restoresWithInvalidActiveTabFallsToFirst() {
        val store = InMemoryPersistentStore()
        val tabs = listOf(
            Tab(id = "a", url = "https://a.com", displayTitle = "A"),
            Tab(id = "b", url = "https://b.com", displayTitle = "B")
        )
        val session = TabSession(tabs = tabs, activeTabId = "non-existent")
        store.put(TabSession.STORAGE_KEY, json.encodeToString(TabSession.serializer(), session))

        val manager = TabManager(store)

        assertEquals(2, manager.tabCount)
        assertEquals("a", manager.activeTabId) // Falls back to first tab.
    }

    @Test
    fun restoreTrimsToTabLimit() {
        val store = InMemoryPersistentStore()
        val tabs = (1..25).map { Tab(id = "tab-$it", url = "https://$it.com", displayTitle = "Tab $it") }
        val session = TabSession(tabs = tabs, activeTabId = "tab-1")
        store.put(TabSession.STORAGE_KEY, json.encodeToString(TabSession.serializer(), session))

        val manager = TabManager(store)

        assertEquals(TabManager.TAB_LIMIT, manager.tabCount)
        assertEquals("tab-1", manager.activeTabId)
    }

    @Test
    fun persistFailureDoesNotCrash() {
        // A store that always throws on write.
        val failingStore = object : PersistentStore {
            override fun getString(key: String): String? = null
            override fun putString(key: String, value: String) {
                throw RuntimeException("Disk full")
            }
        }

        val manager = TabManager(failingStore)
        // Operations should succeed without exception.
        manager.createTab("https://example.com")
        manager.closeTab(manager.tabs[0].id)
        assertEquals(1, manager.tabCount)
    }

    @Test
    fun noStoreConstructorWorksWithoutPersistence() {
        val manager = TabManager()
        manager.createTab("https://test.com")
        assertEquals(2, manager.tabCount)
        // No crash, just no persistence.
    }
}
