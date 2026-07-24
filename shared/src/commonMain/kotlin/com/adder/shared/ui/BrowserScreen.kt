package com.adder.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.jetbrains.compose.ui.tooling.preview.Preview

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

        // Floating ad-blocking toggle pill — stays fixed while the page scrolls
        AdBlockTogglePill(
            blockingEnabled = webViewState.blockingEnabled,
            enabled = !isLoading && !webViewState.isModelBusy,
            onToggle = { webViewState.toggleBlocking() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun AdBlockTogglePill(
    blockingEnabled: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (blockingEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (blockingEnabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Text(
            text = if (blockingEnabled) "\uD83D\uDEE1 Blocking On" else "\uD83D\uDEE1 Blocking Off",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
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

// ---------------------------------------------------------------------------
// Previews
//
// Note: BrowserScreen / App / PlatformWebView embed the expect/actual platform
// WebView, which needs a live native WebView and cannot render in a preview.
// Only the standalone, stateless composables are previewed here.
// ---------------------------------------------------------------------------

@Preview
@Composable
private fun AdBlockTogglePillBlockingOnPreview() {
    MaterialTheme {
        AdBlockTogglePill(
            blockingEnabled = true,
            enabled = true,
            onToggle = {}
        )
    }
}

@Preview
@Composable
private fun AdBlockTogglePillBlockingOffPreview() {
    MaterialTheme {
        AdBlockTogglePill(
            blockingEnabled = false,
            enabled = true,
            onToggle = {}
        )
    }
}

@Preview
@Composable
private fun AdBlockTogglePillDisabledPreview() {
    MaterialTheme {
        AdBlockTogglePill(
            blockingEnabled = true,
            enabled = false,
            onToggle = {}
        )
    }
}

@Preview
@Composable
private fun BrowserToolbarPreview() {
    MaterialTheme {
        BrowserToolbar(
            inputText = "https://www.google.com",
            canGoBack = true,
            canGoForward = false,
            onInputChange = {},
            onNavigate = {},
            onBack = {},
            onForward = {},
            onRefresh = {}
        )
    }
}
