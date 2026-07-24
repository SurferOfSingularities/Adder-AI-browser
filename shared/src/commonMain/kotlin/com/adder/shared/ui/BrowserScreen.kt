package com.adder.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adder.shared.UrlUtils

/**
 * Main browser screen composable — shared across Android and iOS.
 * Contains the URL bar, navigation buttons, and a platform-specific WebView.
 */
@Composable
fun BrowserScreen() {
    var url by remember { mutableStateOf("https://www.google.com") }
    var inputText by remember { mutableStateOf("https://www.google.com") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // WebView controller for navigation commands
    val webViewState = remember { WebViewState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            BrowserToolbar(
                inputText = inputText,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onInputChange = { inputText = it },
                onNavigate = {
                    val normalized = UrlUtils.normalizeUrl(inputText)
                    url = normalized
                    inputText = normalized
                    webViewState.loadUrl(normalized)
                },
                onBack = { webViewState.goBack() },
                onForward = { webViewState.goForward() },
                onRefresh = { webViewState.reload() }
            )

            // Loading indicator (for page load)
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }

            // Platform WebView
            PlatformWebView(
                url = url,
                state = webViewState,
                modifier = Modifier.fillMaxSize().weight(1f),
                onPageStarted = { newUrl ->
                    isLoading = true
                    inputText = newUrl
                },
                onPageFinished = { newUrl ->
                    isLoading = false
                    inputText = newUrl
                    canGoBack = webViewState.canGoBack
                    canGoForward = webViewState.canGoForward
                }
            )
        }

        // Model Inference Spinner
        if (webViewState.isModelBusy) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Analyzing for ads...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserToolbar(
    inputText: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onInputChange: (String) -> Unit,
    onNavigate: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(
                onClick = onBack,
                enabled = canGoBack
            ) {
                Text("◀", style = MaterialTheme.typography.bodyLarge)
            }

            // Forward button
            IconButton(
                onClick = onForward,
                enabled = canGoForward
            ) {
                Text("▶", style = MaterialTheme.typography.bodyLarge)
            }

            // Refresh button
            IconButton(onClick = onRefresh) {
                Text("↻", style = MaterialTheme.typography.bodyLarge)
            }

            // URL input
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f).height(48.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        onNavigate()
                        keyboardController?.hide()
                    }
                )
            )
        }
    }
}
