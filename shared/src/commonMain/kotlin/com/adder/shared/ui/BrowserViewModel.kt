package com.adder.shared.ui

import androidx.lifecycle.ViewModel
import com.adder.shared.UrlUtils
import com.adder.shared.detection.currentModelName
import com.adder.shared.engine.HistoryStore
import com.adder.shared.engine.TabManager
import com.adder.shared.engine.platformPersistentStore
import com.adder.shared.model.HistoryEntry
import com.adder.shared.model.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Consolidated UI state for [BrowserScreen]. A single snapshot of everything
 * the screen needs to render, emitted via [BrowserViewModel.uiState] StateFlow.
 */
data class BrowserScreenUiState(
    val url: String = INITIAL_URL,
    val inputText: String = INITIAL_URL,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val isModelBusy: Boolean = false,
    val blockingEnabled: Boolean = true,
    val tabs: List<Tab> = emptyList(),
    val activeTabId: String = "",
    val tabCount: Int = 0,
    val tabSwitcherVisible: Boolean = false,
    val historyVisible: Boolean = false,
    val historyEntries: List<HistoryEntry> = emptyList(),
    val modelName: String = "",
    val toggleNotice: String? = null,
    val snackbarNotice: String? = null
) {
    companion object {
        const val INITIAL_URL = "https://www.google.com"
    }
}

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
    // StateFlow: single source of truth for BrowserScreen
    // -------------------------------------------------------------------------

    private val _uiState = MutableStateFlow(BrowserScreenUiState())

    /** Observable UI state consumed by [BrowserScreen]. */
    val uiState: StateFlow<BrowserScreenUiState> = _uiState.asStateFlow()

    // -------------------------------------------------------------------------
    // Per-tab transient UI state (internal bookkeeping, not directly observed)
    // -------------------------------------------------------------------------

    /**
     * Transient per-tab state not persisted across sessions (loading, nav controls,
     * model busy). Separate from the persisted [Tab] model.
     */
    private class TabUiState(
        var isLoading: Boolean = false,
        var isModelBusy: Boolean = false,
        var canGoBack: Boolean = false,
        var canGoForward: Boolean = false,
        var inputText: String = ""
    )

    private val tabUiStates = mutableMapOf<String, TabUiState>()
    private val tabControllers = mutableMapOf<String, WebViewController>()

    private fun getOrCreateUiState(tabId: String): TabUiState {
        return tabUiStates.getOrPut(tabId) {
            val tab = tabManager.tabs.firstOrNull { it.id == tabId }
            TabUiState(inputText = tab?.url ?: INITIAL_URL)
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
    // Internal mutable fields (drive the StateFlow via emitState())
    // -------------------------------------------------------------------------

    private var blockingEnabled: Boolean = true
    private var tabSwitcherVisible: Boolean = false
    private var historyVisible: Boolean = false
    private var historyEntries: List<HistoryEntry> = emptyList()
    private var modelName: String = currentModelName()
    private var toggleNotice: String? = null
    private var snackbarNotice: String? = null

    /** The active tab's WebViewController. */
    val webViewController: WebViewController
        get() = getOrCreateController(tabManager.activeTabId)

    init {
        historyEntries = historyStore.entries()
        getOrCreateUiState(tabManager.activeTabId)
        getOrCreateController(tabManager.activeTabId)
        emitState()
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
            emitState()
            return
        }
        emitState()
    }

    /** Closes the tab with [id]. */
    fun closeTab(id: String) {
        tabManager.closeTab(id)
        tabUiStates.remove(id)
        tabControllers.remove(id)
        emitState()
    }

    /**
     * Activates the tab with [id]. If the tab's rendered blocking mode differs
     * from the current [blockingEnabled], a reload is triggered automatically.
     */
    fun activateTab(id: String) {
        if (tabManager.tabs.none { it.id == id }) return
        tabManager.activateTab(id)
        emitState()

        // Lazy reconciliation: reload if blocking mode is stale.
        val tab = tabManager.activeTab
        if (tab.renderedBlockingMode != null && tab.renderedBlockingMode != blockingEnabled) {
            getOrCreateController(id).reload()
        }
    }

    fun openTabSwitcher() {
        tabSwitcherVisible = true
        emitState()
    }

    fun closeTabSwitcher() {
        tabSwitcherVisible = false
        emitState()
    }

    // -------------------------------------------------------------------------
    // Browser intents (dispatched to active tab)
    // -------------------------------------------------------------------------

    fun onInputChange(text: String) {
        getOrCreateUiState(tabManager.activeTabId).inputText = text
        emitState()
    }

    fun onUrlSubmit() {
        val activeState = getOrCreateUiState(tabManager.activeTabId)
        val normalized = UrlUtils.normalizeUrl(activeState.inputText)
        activeState.inputText = normalized
        webViewController.loadUrl(normalized)
        emitState()
    }

    fun onBack() = webViewController.goBack()

    fun onForward() = webViewController.goForward()

    fun onRefresh() = webViewController.reload()

    fun toggleBlocking() {
        blockingEnabled = !blockingEnabled
        toggleNotice = if (blockingEnabled) NOTICE_BLOCKING_ON else NOTICE_BLOCKING_OFF
        webViewController.reload()
        emitState()
    }

    fun onToggleNoticeShown() {
        toggleNotice = null
        emitState()
    }

    fun onSnackbarNoticeShown() {
        snackbarNotice = null
        emitState()
    }

    /** Re-reads the platform model name (may have changed after LLM availability check). */
    fun refreshModelName() {
        modelName = currentModelName()
        emitState()
    }

    // -------------------------------------------------------------------------
    // History intents
    // -------------------------------------------------------------------------

    fun openHistory() {
        historyEntries = historyStore.entries()
        historyVisible = true
        emitState()
    }

    fun closeHistory() {
        historyVisible = false
        emitState()
    }

    fun onRevisit(entry: HistoryEntry) {
        historyVisible = false
        val activeState = getOrCreateUiState(tabManager.activeTabId)
        activeState.inputText = entry.url
        webViewController.loadUrl(entry.url)
        emitState()
    }

    fun onDeleteHistory(entry: HistoryEntry) {
        historyStore.delete(entry.url)
        historyEntries = historyStore.entries()
        emitState()
    }

    fun onClearHistory() {
        historyStore.clear()
        historyEntries = historyStore.entries()
        emitState()
    }

    // -------------------------------------------------------------------------
    // Events reported by the platform WebView (routed by tabId)
    // -------------------------------------------------------------------------

    fun onPageStarted(tabId: String, newUrl: String) {
        val state = getOrCreateUiState(tabId)
        state.isLoading = true
        state.isModelBusy = false
        state.inputText = newUrl
        emitState()
    }

    fun onPageFinished(tabId: String, newUrl: String) {
        val state = getOrCreateUiState(tabId)
        state.isLoading = false
        state.inputText = newUrl

        // Update persisted tab metadata.
        val title = getOrCreateController(tabId).currentTitle().orEmpty()
        tabManager.updateTab(tabId, newUrl, title, blockingEnabled)

        // Refresh model name in case LLM availability was resolved during this page load.
        modelName = currentModelName()

        // Record in unified history regardless of which tab finished.
        if (HistoryStore.isRecordable(newUrl)) {
            historyStore.record(newUrl, title)
            historyEntries = historyStore.entries()
        }

        emitState()
    }

    fun onNavStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        val state = getOrCreateUiState(tabId)
        state.canGoBack = canGoBack
        state.canGoForward = canGoForward
        emitState()
    }

    fun onModelBusyChanged(tabId: String, busy: Boolean) {
        val state = getOrCreateUiState(tabId)
        state.isModelBusy = busy
        emitState()
    }

    // -------------------------------------------------------------------------
    // Convenience: controller for a specific tab (used by BrowserScreen)
    // -------------------------------------------------------------------------

    fun controllerForTab(tabId: String): WebViewController = getOrCreateController(tabId)

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Snapshots the current internal state into a new [BrowserScreenUiState]
     * and pushes it to [_uiState].
     */
    private fun emitState() {
        val activeTabId = tabManager.activeTabId
        val activeState = getOrCreateUiState(activeTabId)
        _uiState.update {
            BrowserScreenUiState(
                url = tabManager.activeTab.url,
                inputText = activeState.inputText,
                canGoBack = activeState.canGoBack,
                canGoForward = activeState.canGoForward,
                isLoading = activeState.isLoading,
                isModelBusy = activeState.isModelBusy,
                blockingEnabled = blockingEnabled,
                tabs = tabManager.tabs,
                activeTabId = activeTabId,
                tabCount = tabManager.tabs.size,
                tabSwitcherVisible = tabSwitcherVisible,
                historyVisible = historyVisible,
                historyEntries = historyEntries,
                modelName = modelName,
                toggleNotice = toggleNotice,
                snackbarNotice = snackbarNotice
            )
        }
    }

    private companion object {
        const val INITIAL_URL = "https://www.google.com"
        const val NOTICE_BLOCKING_ON = "Blocking : On"
        const val NOTICE_BLOCKING_OFF = "Blocking : Off"
        const val NOTICE_TAB_LIMIT = "Tab limit reached (20)"
    }
}
