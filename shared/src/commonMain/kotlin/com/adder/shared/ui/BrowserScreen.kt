package com.adder.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adder.shared.model.HistoryEntry
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Main browser screen composable — shared across Android and iOS.
 * Contains the URL bar, navigation buttons, tab count, and a platform-specific WebView.
 * All UI state is hoisted into [BrowserViewModel] and observed via a single [StateFlow].
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = viewModel { BrowserViewModel() }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BrowserScreenContent(
        state = state,
        webViewController = viewModel.webViewController,
        onInputChange = viewModel::onInputChange,
        onNavigate = viewModel::onUrlSubmit,
        onBack = viewModel::onBack,
        onForward = viewModel::onForward,
        onRefresh = viewModel::onRefresh,
        onToggleBlocking = viewModel::toggleBlocking,
        onToggleNoticeShown = viewModel::onToggleNoticeShown,
        onSnackbarNoticeShown = viewModel::onSnackbarNoticeShown,
        onPageStarted = viewModel::onPageStarted,
        onPageFinished = viewModel::onPageFinished,
        onNavStateChanged = viewModel::onNavStateChanged,
        onModelBusyChanged = viewModel::onModelBusyChanged,
        onOpenHistory = viewModel::openHistory,
        onCloseHistory = viewModel::closeHistory,
        onRevisit = viewModel::onRevisit,
        onDeleteHistory = viewModel::onDeleteHistory,
        onClearHistory = viewModel::onClearHistory,
        onOpenTabSwitcher = viewModel::openTabSwitcher,
        onCloseTabSwitcher = viewModel::closeTabSwitcher,
        onTabSelected = { id ->
            viewModel.closeTabSwitcher()
            viewModel.activateTab(id)
        },
        onTabClose = viewModel::closeTab,
        onNewTab = viewModel::createTab
    )
}

@Composable
private fun BrowserScreenContent(
    state: BrowserScreenUiState,
    webViewController: WebViewController,
    onInputChange: (String) -> Unit,
    onNavigate: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onToggleBlocking: () -> Unit,
    onToggleNoticeShown: () -> Unit,
    onSnackbarNoticeShown: () -> Unit,
    onPageStarted: (String, String) -> Unit,
    onPageFinished: (String, String) -> Unit,
    onNavStateChanged: (String, Boolean, Boolean) -> Unit,
    onModelBusyChanged: (String, Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onCloseHistory: () -> Unit,
    onRevisit: (HistoryEntry) -> Unit,
    onDeleteHistory: (HistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onCloseTabSwitcher: () -> Unit,
    onTabSelected: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit
) {

    // Merge toggle + general notices into a single toast message slot.
    // Toggle notice takes priority; a new notice replaces an existing one.
    val activeToast = state.toggleNotice ?: state.snackbarNotice

    // Auto-dismiss: clear the notice after a short delay.
    LaunchedEffect(activeToast) {
        if (activeToast != null) {
            delay(2000L)
            if (state.toggleNotice != null) onToggleNoticeShown()
            if (state.snackbarNotice != null) onSnackbarNoticeShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // WebView area — keyed by activeTabId so switching tabs re-composes
            // with the correct controller and URL.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val currentTabId = state.activeTabId
                key(currentTabId) {
                    PlatformWebView(
                        url = state.url,
                        controller = webViewController,
                        blockingEnabled = state.blockingEnabled,
                        modifier = Modifier.fillMaxSize(),
                        onPageStarted = { url -> onPageStarted(currentTabId, url) },
                        onPageFinished = { url -> onPageFinished(currentTabId, url) },
                        onNavStateChanged = { back, fwd -> onNavStateChanged(currentTabId, back, fwd) },
                        onModelBusyChanged = { busy -> onModelBusyChanged(currentTabId, busy) }
                    )
                }

                // Floating model name popup + ad-blocking toggle button
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    key(currentTabId) {
                        ModelNamePopup(modelName = state.modelName)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    AdBlockToggleButton(
                        blockingEnabled = state.blockingEnabled,
                        enabled = !state.isLoading && !state.isModelBusy,
                        onToggle = onToggleBlocking
                    )
                }

                // Toast notification for toggle / tab-limit notices
                ToastMessage(
                    message = activeToast,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 88.dp)
                )
            }

            // Loading indicator
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }

            // Toolbar (bottom)
            BrowserToolbar(
                inputText = state.inputText,
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                tabCount = state.tabCount,
                onInputChange = onInputChange,
                onNavigate = onNavigate,
                onBack = onBack,
                onForward = onForward,
                onRefresh = onRefresh,
                onHistory = onOpenHistory,
                onTabSwitcher = onOpenTabSwitcher,
                modelName = state.modelName
            )
        }

        // Tab switcher overlay
        if (state.tabSwitcherVisible) {
            TabSwitcherView(
                tabs = state.tabs,
                activeTabId = state.activeTabId,
                onTabSelected = onTabSelected,
                onTabClose = onTabClose,
                onNewTab = { onNewTab() },
                onClose = onCloseTabSwitcher,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            )
        }

        // History overlay
        if (state.historyVisible) {
            HistoryView(
                entries = state.historyEntries,
                onEntryClick = onRevisit,
                onEntryDelete = onDeleteHistory,
                onClearAll = onClearHistory,
                onClose = onCloseHistory,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            )
        }

        // Model Inference Spinner
        if (state.isModelBusy) {
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

/**
 * Lightweight toast-style notification — a compact, centered pill that fades in/out.
 * Replaces the full-width Material 3 Snackbar for less visual clutter.
 */
@Composable
private fun ToastMessage(
    message: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

// Fixed colors for the ad-blocking toggle.
private val BlockingOnContainer = Color(0xFF2E7D32)
private val BlockingOnIcon = Color(0xFFFFFFFF)
private val BlockingOffContainer = Color(0xFFF9C82E)
private val BlockingOffIcon = Color(0xFF1F1B00)

@Composable
private fun AdBlockToggleButton(
    blockingEnabled: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (blockingEnabled) BlockingOnContainer else BlockingOffContainer
    val iconColor = if (blockingEnabled) BlockingOnIcon else BlockingOffIcon
    val description = if (blockingEnabled) {
        "Ad blocking on. Tap to turn off."
    } else {
        "Ad blocking off. Tap to turn on."
    }

    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = iconColor,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = description,
            modifier = Modifier
                .padding(12.dp)
                .size(24.dp)
        )
    }
}

/**
 * Transient popup that displays the on-device LLM model name.
 * Fades in on composition, stays visible for ~2 seconds, then fades out.
 */
@Composable
private fun ModelNamePopup(
    modelName: String,
    modifier: Modifier = Modifier
) {
    var showPopup by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2000L)
        showPopup = false
    }

    AnimatedVisibility(
        visible = showPopup,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 4.dp
        ) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun BrowserToolbar(
    inputText: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onInputChange: (String) -> Unit,
    onNavigate: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onHistory: () -> Unit,
    onTabSwitcher: () -> Unit,
    modelName: String
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    // Highlight selected address-bar text in green.
    val greenSelectionColors = TextSelectionColors(
        handleColor = BlockingOnContainer,
        backgroundColor = BlockingOnContainer.copy(alpha = 0.4f)
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides greenSelectionColors) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 3-dot menu (was hamburger)
            AppMenu(modelName = modelName, onHistory = onHistory)

            // Back button
            IconButton(
                onClick = onBack,
                enabled = canGoBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
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

            // URL input.
            // `inputText` (from the ViewModel) is the source of truth for the text; the
            // selection is derived from focus so the whole URL is highlighted the moment
            // the address bar gains focus, and stays highlighted until the user types.
            var selectAll by remember { mutableStateOf(false) }
            val selection = if (selectAll) TextRange(0, inputText.length) else TextRange(inputText.length)
            OutlinedTextField(
                value = TextFieldValue(text = inputText, selection = selection),
                onValueChange = {
                    // First edit after focus clears the select-all highlight.
                    selectAll = false
                    if (it.text != inputText) onInputChange(it.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .onFocusChanged { selectAll = it.isFocused },
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

            Spacer(modifier = Modifier.width(4.dp))

            // Tab count button
            TabCountButton(
                count = tabCount,
                onClick = onTabSwitcher
            )
        }
        }
    }
}

/**
 * Compact button showing the current open tab count. Tapping opens the tab switcher.
 */
@Composable
internal fun TabCountButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = "$count open tabs"
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 4.dp,
        modifier = modifier.semantics { contentDescription = description }
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
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
                imageVector = Icons.Filled.MoreVert,
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

            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = { expanded = false }
            )

            HorizontalDivider()

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
// ---------------------------------------------------------------------------

@Preview
@Composable
private fun AdBlockToggleButtonBlockingOnPreview() {
    MaterialTheme {
        AdBlockToggleButton(
            blockingEnabled = true,
            enabled = true,
            onToggle = {}
        )
    }
}

@Preview
@Composable
private fun AdBlockToggleButtonBlockingOffPreview() {
    MaterialTheme {
        AdBlockToggleButton(
            blockingEnabled = false,
            enabled = true,
            onToggle = {}
        )
    }
}

@Preview
@Composable
private fun AdBlockToggleButtonDisabledPreview() {
    MaterialTheme {
        AdBlockToggleButton(
            blockingEnabled = true,
            enabled = false,
            onToggle = {}
        )
    }
}

@Preview
@Composable
private fun ToggleNoticeToastOnPreview() {
    MaterialTheme {
        ToastMessage(message = "Blocking : On")
    }
}

@Preview
@Composable
private fun ToggleNoticeToastOffPreview() {
    MaterialTheme {
        ToastMessage(message = "Blocking : Off")
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
            tabCount = 3,
            onInputChange = {},
            onNavigate = {},
            onBack = {},
            onForward = {},
            onRefresh = {},
            onHistory = {},
            onTabSwitcher = {},
            modelName = "Gemini Nano"
        )
    }
}

@Preview
@Composable
private fun TabCountButton1Preview() {
    MaterialTheme {
        TabCountButton(count = 1, onClick = {})
    }
}

@Preview
@Composable
private fun TabCountButton5Preview() {
    MaterialTheme {
        TabCountButton(count = 5, onClick = {})
    }
}

@Preview
@Composable
private fun TabCountButton20Preview() {
    MaterialTheme {
        TabCountButton(count = 20, onClick = {})
    }
}

@Preview
@Composable
private fun AppMenuPreview() {
    MaterialTheme {
        AppMenu(modelName = "Gemini Nano", onHistory = {})
    }
}

@Preview
@Composable
private fun ModelNamePopupPreview() {
    MaterialTheme {
        ModelNamePopup(modelName = "Gemini Nano")
    }
}

@Preview
@Composable
private fun ModelNamePopupLongNamePreview() {
    MaterialTheme {
        ModelNamePopup(modelName = "Apple Intelligence")
    }
}
