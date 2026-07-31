package com.adder.shared.ui

/**
 * Imperative command bridge between the shared UI and the live platform WebView.
 *
 * The platform WebView registers its command handlers here on creation; the
 * [BrowserViewModel] invokes them in response to user intents. This holds no
 * observable UI state — all state lives in [BrowserViewModel].
 */
class WebViewController {
    internal var onLoadUrl: ((String) -> Unit)? = null
    internal var onGoBack: (() -> Unit)? = null
    internal var onGoForward: (() -> Unit)? = null
    internal var onReload: (() -> Unit)? = null
    internal var onCurrentTitle: (() -> String?)? = null

    fun loadUrl(url: String) {
        onLoadUrl?.invoke(url)
    }

    fun goBack() {
        onGoBack?.invoke()
    }

    fun goForward() {
        onGoForward?.invoke()
    }

    fun reload() {
        onReload?.invoke()
    }

    /**
     * The title of the page currently loaded in the WebView, or null when no
     * WebView is attached or the page reports no title. Used to label history
     * entries.
     */
    fun currentTitle(): String? = onCurrentTitle?.invoke()
}
