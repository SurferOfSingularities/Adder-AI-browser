package com.adder.shared.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adder.shared.detection.LlmClassifier
import com.adder.shared.engine.AdBlockEngine
import com.adder.shared.js.DomExtractor
import com.adder.shared.js.DomRemover
import com.adder.shared.js.EarlyCssInjector
import com.adder.shared.model.parseExtractedElements
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "PlatformWebView"

@SuppressLint("SetJavaScriptEnabled")
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
    var pipelineJob by remember { mutableStateOf<Job?>(null) }
    // Read the latest toggle value inside the WebView's long-lived callbacks
    // (the factory closure would otherwise capture a stale value).
    val currentBlockingEnabled by rememberUpdatedState(blockingEnabled)

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = false

                    override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, pageUrl, favicon)
                        pipelineJob?.cancel()
                        pageUrl?.let { onPageStarted(it) }
                        // Inject early CSS only when blocking is enabled
                        if (currentBlockingEnabled) {
                            view?.evaluateJavascript(EarlyCssInjector.earlyHideCss, null)
                        }
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        pageUrl?.let { onPageFinished(it) }
                        onNavStateChanged(
                            view?.canGoBack() ?: false,
                            view?.canGoForward() ?: false
                        )

                        // Run ad blocking pipeline only when blocking is enabled
                        if (currentBlockingEnabled) {
                            view?.let { wv ->
                                pipelineJob = scope.launch {
                                    runAdBlockPipeline(wv, adBlockEngine, pageUrl ?: "", onModelBusyChanged)
                                }
                            }
                        }
                    }
                }

                webChromeClient = WebChromeClient()

                // Wire imperative commands to the controller
                controller.onLoadUrl = { loadUrl(it) }
                controller.onGoBack = { goBack() }
                controller.onGoForward = { goForward() }
                controller.onReload = { reload() }

                // Load initial URL
                loadUrl(url)
            }
        },
        modifier = modifier,
        update = { /* URL changes handled via controller.loadUrl */ }
    )
}

private suspend fun runAdBlockPipeline(
    webView: WebView,
    engine: AdBlockEngine,
    pageUrl: String,
    onModelBusyChanged: (Boolean) -> Unit
) {
    try {
        onModelBusyChanged(true)
        // Extract DOM elements
        val jsonResult = evaluateJsAsync(webView, DomExtractor.extractionScript)
        val elements = parseExtractedElements(unescapeJsString(jsonResult))
        Log.d(TAG, "Extracted ${elements.size} candidate elements")

        if (elements.isEmpty()) {
            onModelBusyChanged(false)
            return
        }

        // Run pipeline
        val result = engine.runPipeline(elements, pageUrl)
        if (result.skipped || result.selectorsToRemove.isEmpty()) {
            Log.d(TAG, if (result.skipped) "Whitelisted" else "No ads found")
            onModelBusyChanged(false)
            return
        }

        Log.d(TAG, "Removing ${result.selectorsToRemove.size} ads (${result.heuristicRemovals} heuristic, ${result.llmRemovals} LLM)")

        // Remove ads
        val removalScript = DomRemover.buildRemovalScript(result.selectorsToRemove)
        evaluateJsAsync(webView, removalScript)

        // Install MutationObserver
        val observerScript = DomRemover.buildMutationObserverScript(DomRemover.defaultObserverPatterns)
        evaluateJsAsync(webView, observerScript)
    } catch (e: Exception) {
        Log.e(TAG, "Pipeline error", e)
    } finally {
        onModelBusyChanged(false)
    }
}

private suspend fun evaluateJsAsync(webView: WebView, script: String): String {
    return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { result ->
            continuation.resumeWith(Result.success(result ?: ""))
        }
    }
}

private fun unescapeJsString(raw: String): String {
    var result = raw
    if (result.startsWith("\"") && result.endsWith("\"")) {
        result = result.substring(1, result.length - 1)
    }
    return result
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\/", "/")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
}
