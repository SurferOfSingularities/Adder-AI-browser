package com.adder.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific WebView composable.
 * - Android: uses AndroidView with android.webkit.WebView
 * - iOS: uses UIKitView with WKWebView
 *
 * Both implementations hook into the ad-blocking pipeline on page load.
 */
@Composable
expect fun PlatformWebView(
    url: String,
    state: WebViewState,
    modifier: Modifier = Modifier,
    onPageStarted: (url: String) -> Unit = {},
    onPageFinished: (url: String) -> Unit = {}
)
