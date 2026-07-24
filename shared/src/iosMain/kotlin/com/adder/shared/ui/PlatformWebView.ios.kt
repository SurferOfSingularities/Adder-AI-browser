package com.adder.shared.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.adder.shared.detection.LlmClassifier
import com.adder.shared.engine.AdBlockEngine
import com.adder.shared.js.DomExtractor
import com.adder.shared.js.DomRemover
import com.adder.shared.js.EarlyCssInjector
import com.adder.shared.model.parseExtractedElements
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURL
import platform.WebKit.*
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformWebView(
    url: String,
    controller: WebViewController,
    blockingEnabled: Boolean,
    modifier: Modifier,
    onPageStarted: (url: String) -> Unit,
    onPageFinished: (url: String) -> Unit,
    onNavStateChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onModelBusyChanged: (busy: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val adBlockEngine = remember { AdBlockEngine(LlmClassifier()) }
    // Read the latest toggle value inside the WebView's long-lived callbacks
    // (the factory closure would otherwise capture a stale value).
    val currentBlockingEnabled by rememberUpdatedState(blockingEnabled)

    UIKitView(
        factory = {
            val config = WKWebViewConfiguration()
            val webView = WKWebView(frame = kotlinx.cinterop.cValue { }, configuration = config)

            val navigationDelegate = WebViewNavigationDelegate(
                onStart = { pageUrl ->
                    onPageStarted(pageUrl)
                    // Inject early CSS only when blocking is enabled
                    if (currentBlockingEnabled) {
                        webView.evaluateJavaScript(EarlyCssInjector.earlyHideCss, null)
                    }
                },
                onFinish = { pageUrl ->
                    onPageFinished(pageUrl)
                    onNavStateChanged(webView.canGoBack, webView.canGoForward)

                    // Run ad blocking pipeline only when blocking is enabled
                    if (currentBlockingEnabled) {
                        scope.launch {
                            runIosAdBlockPipeline(webView, adBlockEngine, pageUrl, onModelBusyChanged)
                        }
                    }
                }
            )
            webView.navigationDelegate = navigationDelegate
            webView.allowsBackForwardNavigationGestures = true

            // Wire imperative commands to the controller
            controller.onLoadUrl = { newUrl ->
                NSURL.URLWithString(newUrl)?.let { nsUrl ->
                    webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
                }
            }
            controller.onGoBack = { webView.goBack() }
            controller.onGoForward = { webView.goForward() }
            controller.onReload = { webView.reload() }

            // Load initial URL
            NSURL.URLWithString(url)?.let { nsUrl ->
                webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
            }

            webView
        },
        modifier = modifier
    )
}

private suspend fun runIosAdBlockPipeline(
    webView: WKWebView,
    engine: AdBlockEngine,
    pageUrl: String,
    onModelBusyChanged: (Boolean) -> Unit
) {
    try {
        onModelBusyChanged(true)
        // Extract DOM elements
        val jsonResult = evaluateJsAsyncIos(webView, DomExtractor.extractionScript)
        val elements = parseExtractedElements(jsonResult)

        if (elements.isEmpty()) {
            onModelBusyChanged(false)
            return
        }

        // Run pipeline
        val result = engine.runPipeline(elements, pageUrl)
        if (result.skipped || result.selectorsToRemove.isEmpty()) {
            onModelBusyChanged(false)
            return
        }

        // Remove ads
        val removalScript = DomRemover.buildRemovalScript(result.selectorsToRemove)
        evaluateJsAsyncIos(webView, removalScript)

        // Install MutationObserver
        val observerScript = DomRemover.buildMutationObserverScript(DomRemover.defaultObserverPatterns)
        evaluateJsAsyncIos(webView, observerScript)
    } catch (e: Exception) {
        println("[PlatformWebView] Pipeline error: ${e.message}")
    } finally {
        onModelBusyChanged(false)
    }
}

private suspend fun evaluateJsAsyncIos(webView: WKWebView, script: String): String {
    return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        webView.evaluateJavaScript(script) { result, error ->
            val text = result?.toString() ?: ""
            continuation.resumeWith(Result.success(text))
        }
    }
}

private class WebViewNavigationDelegate(
    private val onStart: (String) -> Unit,
    private val onFinish: (String) -> Unit
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(
        webView: WKWebView,
        didStartProvisionalNavigation: WKNavigation?
    ) {
        val url = webView.URL?.absoluteString ?: ""
        onStart(url)
    }

    override fun webView(
        webView: WKWebView,
        didFinishNavigation: WKNavigation?
    ) {
        val url = webView.URL?.absoluteString ?: ""
        onFinish(url)
    }
}
