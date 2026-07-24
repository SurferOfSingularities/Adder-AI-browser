package com.adder.shared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared state holder for the platform WebView.
 * Allows Compose to communicate navigation commands to the WebView.
 */
class WebViewState {
    var canGoBack: Boolean by mutableStateOf(false)
        internal set
    var canGoForward: Boolean by mutableStateOf(false)
        internal set

    var isModelBusy: Boolean by mutableStateOf(false)
        internal set

    // Command callbacks — set by the platform implementation
    internal var onLoadUrl: ((String) -> Unit)? = null
    internal var onGoBack: (() -> Unit)? = null
    internal var onGoForward: (() -> Unit)? = null
    internal var onReload: (() -> Unit)? = null

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
}
