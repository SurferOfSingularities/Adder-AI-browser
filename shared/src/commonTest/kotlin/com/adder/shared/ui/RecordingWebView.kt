package com.adder.shared.ui

/**
 * Test double standing in for the live platform WebView.
 *
 * [WebViewController] is a handler-registration bridge rather than an interface, so
 * the fake registers recording handlers on a real controller — exactly how
 * `PlatformWebView` wires itself up on each platform.
 */
class RecordingWebView(controller: WebViewController) {

    /** URLs the ViewModel asked the WebView to load, in order. */
    val loadedUrls = mutableListOf<String>()

    var reloadCount: Int = 0
        private set
    var backCount: Int = 0
        private set
    var forwardCount: Int = 0
        private set

    /** Title the fake WebView reports for the current page. */
    var title: String? = null

    init {
        controller.onLoadUrl = { loadedUrls.add(it) }
        controller.onReload = { reloadCount++ }
        controller.onGoBack = { backCount++ }
        controller.onGoForward = { forwardCount++ }
        controller.onCurrentTitle = { title }
    }
}
