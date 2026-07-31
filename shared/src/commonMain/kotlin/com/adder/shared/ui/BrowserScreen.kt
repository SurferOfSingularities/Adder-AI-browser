package com.adder.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Main browser screen composable — shared across Android and iOS.
 * Contains the URL bar, navigation buttons, and a platform-specific WebView.
 * All UI state is hoisted into [BrowserViewModel].
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = viewModel { BrowserViewModel() }
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Keep app content clear of the status bar (top) and the
                // navigation/gesture bar + keyboard (bottom). safeDrawing is the
                // union of system bars, display cutout, and IME insets.
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // WebView area — takes all remaining space above the bottom toolbar.
            // The floating pill lives here so it sits just above the toolbar and
            // never under the system navigation bar.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                PlatformWebView(
                    url = viewModel.url,
                    controller = viewModel.webViewController,
                    blockingEnabled = viewModel.blockingEnabled,
                    modifier = Modifier.fillMaxSize(),
                    onPageStarted = viewModel::onPageStarted,
                    onPageFinished = viewModel::onPageFinished,
                    onNavStateChanged = viewModel::onNavStateChanged,
                    onModelBusyChanged = viewModel::onModelBusyChanged
                )

                // Floating ad-blocking toggle pill — floats above the toolbar
                AdBlockTogglePill(
                    blockingEnabled = viewModel.blockingEnabled,
                    enabled = !viewModel.isLoading && !viewModel.isModelBusy,
                    onToggle = viewModel::toggleBlocking,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                )
            }

            // Loading indicator (for page load)
            if (viewModel.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }

            // Toolbar (bottom)
            BrowserToolbar(
                inputText = viewModel.inputText,
                canGoBack = viewModel.canGoBack,
                canGoForward = viewModel.canGoForward,
                onInputChange = viewModel::onInputChange,
                onNavigate = viewModel::onUrlSubmit,
                onBack = viewModel::onBack,
                onForward = viewModel::onForward,
                onRefresh = viewModel::onRefresh,
                onHistory = viewModel::openHistory,
                modelName = viewModel.modelName
            )
        }

        // History overlay — covers the page (which stays loaded underneath, so
        // closing it returns to the same page without a reload).
        if (viewModel.historyVisible) {
            HistoryView(
                entries = viewModel.historyEntries,
                onEntryClick = viewModel::onRevisit,
                onEntryDelete = viewModel::onDeleteHistory,
                onClearAll = viewModel::onClearHistory,
                onClose = viewModel::closeHistory,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            )
        }

        // Model Inference Spinner
        if (viewModel.isModelBusy) {
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (blockingEnabled) "Blocking On" else "Blocking Off",
                style = MaterialTheme.typography.labelLarge
            )
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
    onRefresh: () -> Unit,
    onHistory: () -> Unit,
    modelName: String
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
            // Hamburger menu
            AppMenu(modelName = modelName, onHistory = onHistory)

            // Back button
            IconButton(
                onClick = onBack,
                enabled = canGoBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Backl"
                )
            }

            // Forward button
            IconButton(
                onClick = onForward,
                enabled = canGoForward
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward"
                )
            }

            // Refresh button
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh"
                )
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

@Composable
private fun AppMenu(
    modelName: String,
    onHistory: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Menu"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("History") },
                onClick = {
                    expanded = false
                    onHistory()
                }
            )

            // Settings (no navigation yet)
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = { expanded = false }
            )

            HorizontalDivider()

            // Current model — label with the active model name below it
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Current model",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
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
            onRefresh = {},
            onHistory = {},
            modelName = "Gemini Nano"
        )
    }
}

@Preview
@Composable
private fun AppMenuPreview() {
    MaterialTheme {
        AppMenu(modelName = "Gemini Nano", onHistory = {})
    }
}
