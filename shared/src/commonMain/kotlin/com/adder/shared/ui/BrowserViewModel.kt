package com.adder.shared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.adder.shared.UrlUtils
import com.adder.shared.detection.currentModelName
import com.adder.shared.engine.HistoryStore
import com.adder.shared.engine.TabManager
import com.adder.shared.engine.platformPersistentStore
import com.adder.shared.model.HistoryEntry
import com.adder.shared.model.Tab

/**
 * Owns all observable UI state for the browser screen and exposes the intents
 * that drive it. Multi-tab state is delegated to [TabManager]; per-tab
 * transient UI state (loading, nav, model busy) is held in [tabUiStates].
 *
 * Each tab gets its own [WebViewController] instance stored in [tabControllers].
 */
class BrowserViewModel(
    private val tabManager: TabManager = TabManager(platformPersistentStore()),
    private val historyStore: HistoryStore = HistoryStore()
) : ViewModel() {

    // -------------------------------------------------------------------------
    // Per-tab transient UI state
    // -------------------------------------------------------------------------

    /**
     * Transient per-tab state not persisted across sessions (loading, nav controls,
     * model busy). Separate from the persisted [Tab] model.
     */
    class TabUiState {
        var isLoading by mutableStateOf(false)
        var isModelBusy by mutableStateOf(false)
        var canGoBack by mutableStateOf(false)
        var canGoForward by mutableStateOf(false)
        var inputText by mutableStateOf("")
    }

    private val tabUiStates = mutableMapOf<String, TabUiState>()
    private val tabControllers = mutableMapOf<String, WebViewController>()

    private fun getOrCreateUiState(tabId: String): TabUiState {
        return tabUiStates.getOrPut(tabId) {
            val tab = tabManager.tabs.firstOrNull { it.id == tabId }
            TabUiState().also { it.inputText = tab?.url ?: INITIAL_URL }
        }
    }

    private fun getOrCreateController(tabId: String): WebViewController {
        return tabControllers.getOrPut(tabId) {
            WebViewController().also { ctrl ->
                ctrl.onNewWindowRequest = { targetUrl ->
                    createTab(targetUrl.ifEmpty { INITIAL_URL })
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Active tab derived state (what BrowserScreen observes)
    // -------------------------------------------------------------------------

    private val activeUiState: TabUiState
        get() = getOrCreateUiState(tabManager.activeTabId)

    /** Current URL of the active tab (from TabManager's persisted state). */
    val url: String
        get() = tabManager.activeTab.url

    /** Text shown in the address bar (may differ from [url] while user is typing). */
    var inputText: String
        get() = activeUiState.inputText
        private set(value) { activeUiState.inputText = value }

    val canGoBack: Boolean
        get() = activeUiState.canGoBack

    val canGoForward: Boolean
        get() = activeUiState.canGoForward

    val isLoading: Boolean
        get() = activeUiState.isLoading

    val isModelBusy: Boolean
        get() = activeUiState.isModelBusy

    /** The active tab's WebViewController. */
    val webViewController: WebViewController
        get() = getOrCreateController(tabManager.activeTabId)

    // -------------------------------------------------------------------------
    // Tab collection state
    // -------------------------------------------------------------------------

    /** Ordered list of all tabs. */
    var tabs by mutableStateOf(tabManager.tabs)
        private set

    /** ID of the currently active tab. */
    var activeTabId by mutableStateOf(tabManager.activeTabId)
        private set

    /** Number of open tabs. */
    val tabCount: Int
        get() = tabs.size

    /** Whether the tab switcher overlay is visible. */
    var tabSwitcherVisible by mutableStateOf(false)
        private set

    // -------------------------------------------------------------------------
    // Blocking toggle
    // -------------------------------------------------------------------------

    var blockingEnabled by mutableStateOf(true)
        private set

    var toggleNotice by mutableStateOf<String?>(null)
        private set

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    var modelName by mutableStateOf(currentModelName())
        private set

    var historyVisible by mutableStateOf(false)
        private set

    var historyEntries by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set

    // -------------------------------------------------------------------------
    // Snackbar notices (shared slot for toggle + tab limit)
    // -------------------------------------------------------------------------

    var snackbarNotice by mutableStateOf<String?>(null)
        private set

    init {
        historyEntries = historyStore.entries()
        // Initialize UI state for the active tab.
        getOrCreateUiState(tabManager.activeTabId)
        getOrCreateController(tabManager.activeTabId)
        syncTabState()
    }

    // -------------------------------------------------------------------------
    // Tab intents
    // -------------------------------------------------------------------------

    /**
     * Creates a new tab, optionally with a specific URL.
     * Shows a snackbar if the tab limit is reached.
     */
    fun createTab(url: String = INITIAL_URL) {
        val tab = tabManager.createTab(url)
        if (tab == null) {
            snackbarNotice = NOTICE_TAB_LIMIT
            return
        }
        syncTabState()
    }

    /** Closes the tab with [id]. */
    fun closeTab(id: String) {
        tabManager.closeTab(id)
        // Clean up transient state for the closed tab.
        tabUiStates.remove(id)
        tabControllers.remove(id)
        syncTabState()
    }

    /**
     * Activates the tab with [id]. If the tab's rendered blocking mode differs
     * from the current [blockingEnabled], a reload is triggered automatically.
     */
    fun activateTab(id: String) {
        if (tabManager.tabs.none { it.id == id }) return
        tabManager.activateTab(id)
        syncTabState()

        // Lazy reconciliation: reload if blocking mode is stale.
        val tab = tabManager.activeTab
        if (tab.renderedBlockingMode != null && tab.renderedBlockingMode != blockingEnabled) {
            getOrCreateController(id).reload()
        }
    }

    fun openTabSwitcher() {
        tabSwitcherVisible = true
    }

    fun closeTabSwitcher() {
        tabSwitcherVisible = false
    }

    // -------------------------------------------------------------------------
    // Browser intents (dispatched to active tab)
    // -------------------------------------------------------------------------

    fun onInputChange(text: String) {
        activeUiState.inputText = text
    }

    fun onUrlSubmit() {
        val normalized = UrlUtils.normalizeUrl(activeUiState.inputText)
        activeUiState.inputText = normalized
        webViewController.loadUrl(normalized)
    }

    fun onBack() = webViewController.goBack()

    fun onForward() = webViewController.goForward()

    fun onRefresh() = webViewController.reload()

    fun toggleBlocking() {
        blockingEnabled = !blockingEnabled
        toggleNotice = if (blockingEnabled) NOTICE_BLOCKING_ON else NOTICE_BLOCKING_OFF
        webViewController.reload()
    }

    fun onToggleNoticeShown() {
        toggleNotice = null
    }

    fun onSnackbarNoticeShown() {
        snackbarNotice = null
    }

    /** Re-reads the platform model name (may have changed after LLM availability check). */
    fun refreshModelName() {
        modelName = currentModelName()
    }

    // -------------------------------------------------------------------------
    // History intents
    // -------------------------------------------------------------------------

    fun openHistory() {
        historyEntries = historyStore.entries()
        historyVisible = true
    }

    fun closeHistory() {
        historyVisible = false
    }

    fun onRevisit(entry: HistoryEntry) {
        historyVisible = false
        activeUiState.inputText = entry.url
        webViewController.loadUrl(entry.url)
    }

    fun onDeleteHistory(entry: HistoryEntry) {
        historyStore.delete(entry.url)
        historyEntries = historyStore.entries()
    }

    fun onClearHistory() {
        historyStore.clear()
        historyEntries = historyStore.entries()
    }

    // -------------------------------------------------------------------------
    // Events reported by the platform WebView (routed by tabId)
    // -------------------------------------------------------------------------

    fun onPageStarted(tabId: String, newUrl: String) {
        val state = getOrCreateUiState(tabId)
        state.isLoading = true
        state.isModelBusy = false
        state.inputText = newUrl
    }

    fun onPageFinished(tabId: String, newUrl: String) {
        val state = getOrCreateUiState(tabId)
        state.isLoading = false
        state.inputText = newUrl

        // Update persisted tab metadata.
        val title = getOrCreateController(tabId).currentTitle().orEmpty()
        tabManager.updateTab(tabId, newUrl, title, blockingEnabled)
        syncTabState()

        // Refresh model name in case LLM availability was resolved during this page load.
        refreshModelName()

        // Record in unified history regardless of which tab finished.
        if (HistoryStore.isRecordable(newUrl)) {
            historyStore.record(newUrl, title)
            historyEntries = historyStore.entries()
        }
    }

    fun onNavStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        val state = getOrCreateUiState(tabId)
        state.canGoBack = canGoBack
        state.canGoForward = canGoForward
    }

    fun onModelBusyChanged(tabId: String, busy: Boolean) {
        val state = getOrCreateUiState(tabId)
        state.isModelBusy = busy
    }

    // -------------------------------------------------------------------------
    // Convenience: controller for a specific tab (used by BrowserScreen)
    // -------------------------------------------------------------------------

    fun controllerForTab(tabId: String): WebViewController = getOrCreateController(tabId)

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Synchronizes the Compose-observable [tabs] and [activeTabId] from TabManager. */
    private fun syncTabState() {
        tabs = tabManager.tabs
        activeTabId = tabManager.activeTabId
    }

    private companion object {
        const val INITIAL_URL = "https://www.google.com"
        const val NOTICE_BLOCKING_ON = "Blocking : On"
        const val NOTICE_BLOCKING_OFF = "Blocking : Off"
        const val NOTICE_TAB_LIMIT = "Tab limit reached (20)"
    }
}
