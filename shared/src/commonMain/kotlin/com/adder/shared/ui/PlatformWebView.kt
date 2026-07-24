package com.adder.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific WebView composable.
 * - Android: uses AndroidView with android.webkit.WebView
 * - iOS: uses UIKitView with WKWebView
 *
 * Both implementations hook into the ad-blocking pipeline on page load and
 * report navigation/loading state back to the caller (the ViewModel) via the
 * callbacks below. The [controller] provides the imperative command bridge.
 */
@Composable
expect fun PlatformWebView(
    url: String,
    controller: WebViewController,
    blockingEnabled: Boolean,
    modifier: Modifier = Modifier,
    onPageStarted: (url: String) -> Unit = {},
    onPageFinished: (url: String) -> Unit = {},
    onNavStateChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit = { _, _ -> },
    onModelBusyChanged: (busy: Boolean) -> Unit = {}
)
