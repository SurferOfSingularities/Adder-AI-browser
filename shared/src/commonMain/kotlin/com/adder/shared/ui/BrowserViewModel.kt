package com.adder.shared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.adder.shared.UrlUtils
import com.adder.shared.detection.currentModelName
import com.adder.shared.engine.HistoryStore
import com.adder.shared.model.HistoryEntry

/**
 * Owns all observable UI state for the browser screen and exposes the intents
 * that drive it. The imperative bridge to the live platform WebView lives in
 * [webViewController]; this ViewModel calls into it and receives events back
 * through the `on*` handlers below.
 */
class BrowserViewModel(
    private val historyStore: HistoryStore = HistoryStore()
) : ViewModel() {

    var url by mutableStateOf(INITIAL_URL)
        private set
    var inputText by mutableStateOf(INITIAL_URL)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isModelBusy by mutableStateOf(false)
        private set

    /**
     * Whether ad blocking is active. When true, page loads run the ad-block
     * pipeline; when false, pages load untouched ("virgin" view). Persists
     * across navigation for the lifetime of this ViewModel.
     */
    var blockingEnabled by mutableStateOf(true)
        private set

    /**
     * Pending toggle confirmation message, or `null` when there is nothing to
     * show. Single-consumption: the screen calls [onToggleNoticeShown] once it
     * has presented the message. A single nullable slot is what makes "at most
     * one pending notice" structural rather than enforced — a second
     * publication overwrites the first because there is nowhere for it to queue.
     *
     * Assumption: the screen keys its presenting effect on the notice *value*,
     * so two consecutive notices must differ. That holds today because the only
     * producer is [toggleBlocking], a strict flip that always alternates
     * `Blocking : On` / `Blocking : Off`. If a second producer is ever added
     * (for example a whitelist rule forcing blocking off), two identical
     * consecutive messages become possible and this must become an
     * identity-bearing wrapper, e.g.
     * `data class ToggleNotice(val message: String, val id: Long)`.
     */
    var toggleNotice by mutableStateOf<String?>(null)
        private set

    /** Human-readable name of the on-device LLM on this platform. */
    val modelName: String = currentModelName()

    /** Imperative command bridge to the live platform WebView. */
    val webViewController = WebViewController()

    /** Whether the history overlay is currently shown over the page. */
    var historyVisible by mutableStateOf(false)
        private set

    /** Recorded visits, most recent first. Kept in sync with [historyStore]. */
    var historyEntries by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set

    init {
        // Surface history persisted by earlier sessions as soon as the screen opens.
        historyEntries = historyStore.entries()
    }

    // --- Intents from the UI ---

    fun onInputChange(text: String) {
        inputText = text
    }

    fun onUrlSubmit() {
        val normalized = UrlUtils.normalizeUrl(inputText)
        url = normalized
        inputText = normalized
        webViewController.loadUrl(normalized)
    }

    fun onBack() = webViewController.goBack()

    fun onForward() = webViewController.goForward()

    fun onRefresh() = webViewController.reload()

    /**
     * Flips ad blocking on/off, publishes the confirmation message for the new
     * state, and reloads the current page so the new mode takes effect. Blocked
     * mode runs the pipeline; virgin mode skips it.
     */
    fun toggleBlocking() {
        blockingEnabled = !blockingEnabled
        // Derived *after* the flip, so the message names the state this tap
        // produced rather than the one it replaced.
        toggleNotice = if (blockingEnabled) NOTICE_BLOCKING_ON else NOTICE_BLOCKING_OFF
        // Imperative side effect on the live WebView; reads neither piece of
        // state above, so it goes last.
        webViewController.reload()
    }

    /** Marks the pending [toggleNotice] as presented, so it is shown only once. */
    fun onToggleNoticeShown() {
        toggleNotice = null
    }

    // --- History intents ---

    /** Opens the history overlay, refreshing the list first. */
    fun openHistory() {
        historyEntries = historyStore.entries()
        historyVisible = true
    }

    /** Dismisses the history overlay. The loaded page is left untouched. */
    fun closeHistory() {
        historyVisible = false
    }

    /** Closes history and navigates to the selected entry's URL. */
    fun onRevisit(entry: HistoryEntry) {
        historyVisible = false
        url = entry.url
        inputText = entry.url
        webViewController.loadUrl(entry.url)
    }

    /** Removes a single entry from history. Harmless if it is already gone. */
    fun onDeleteHistory(entry: HistoryEntry) {
        historyStore.delete(entry.url)
        historyEntries = historyStore.entries()
    }

    /** Removes every history entry. */
    fun onClearHistory() {
        historyStore.clear()
        historyEntries = historyStore.entries()
    }

    // --- Events reported by the platform WebView ---

    fun onPageStarted(newUrl: String) {
        isLoading = true
        isModelBusy = false
        inputText = newUrl
    }

    fun onPageFinished(newUrl: String) {
        isLoading = false
        inputText = newUrl

        // The WebView only reports this for completed loads, so failed navigations
        // are excluded from history without extra handling.
        if (HistoryStore.isRecordable(newUrl)) {
            historyStore.record(newUrl, webViewController.currentTitle().orEmpty())
            historyEntries = historyStore.entries()
        }
    }

    fun onNavStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
        this.canGoBack = canGoBack
        this.canGoForward = canGoForward
    }

    fun onModelBusyChanged(busy: Boolean) {
        isModelBusy = busy
    }

    private companion object {
        const val INITIAL_URL = "https://www.google.com"

        // Exact wording, spaces around the colon included, so the literals live
        // in one place.
        const val NOTICE_BLOCKING_ON = "Blocking : On"
        const val NOTICE_BLOCKING_OFF = "Blocking : Off"
    }
}
