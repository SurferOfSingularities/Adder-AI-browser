package com.adder.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adder.shared.model.Tab
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Full-screen overlay listing all open tabs. Follows the same visual pattern
 * as [HistoryView]: opaque surface that covers the page area.
 */
@Composable
fun TabSwitcherView(
    tabs: List<Tab>,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabSwitcherTopBar(
                onClose = onClose,
                onNewTab = onNewTab
            )

            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = tabs, key = { it.id }) { tab ->
                    TabRow(
                        tab = tab,
                        isActive = tab.id == activeTabId,
                        onSelect = { onTabSelected(tab.id) },
                        onClose = { onTabClose(tab.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TabSwitcherTopBar(
    onClose: () -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close tab switcher"
                )
            }

            Text(
                text = "Tabs",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onNewTab) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New tab"
                )
            }
        }
    }
}

/**
 * A single tab row: left accent bar (if active), title + URL, and close button.
 */
@Composable
private fun TabRow(
    tab: Tab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onSelect,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active indicator — 4dp vertical bar on the left edge.
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else {
                Spacer(modifier = Modifier.width(4.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    text = tab.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close tab: ${tab.displayTitle}"
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val sampleTabs = listOf(
    Tab(id = "1", url = "https://www.google.com", displayTitle = "Google"),
    Tab(id = "2", url = "https://kotlinlang.org/docs/multiplatform.html", displayTitle = "Kotlin Multiplatform | Kotlin Documentation"),
    Tab(id = "3", url = "https://developer.android.com", displayTitle = "Android Developers")
)

@Preview
@Composable
private fun TabSwitcherViewPopulatedPreview() {
    MaterialTheme {
        TabSwitcherView(
            tabs = sampleTabs,
            activeTabId = "1",
            onTabSelected = {},
            onTabClose = {},
            onNewTab = {},
            onClose = {}
        )
    }
}

@Preview
@Composable
private fun TabSwitcherViewSingleTabPreview() {
    MaterialTheme {
        TabSwitcherView(
            tabs = listOf(sampleTabs[0]),
            activeTabId = "1",
            onTabSelected = {},
            onTabClose = {},
            onNewTab = {},
            onClose = {}
        )
    }
}

@Preview
@Composable
private fun TabRowActivePreview() {
    MaterialTheme {
        TabRow(
            tab = sampleTabs[0],
            isActive = true,
            onSelect = {},
            onClose = {}
        )
    }
}

@Preview
@Composable
private fun TabRowInactivePreview() {
    MaterialTheme {
        TabRow(
            tab = sampleTabs[1],
            isActive = false,
            onSelect = {},
            onClose = {}
        )
    }
}

@Preview
@Composable
private fun TabSwitcherTopBarPreview() {
    MaterialTheme {
        TabSwitcherTopBar(onClose = {}, onNewTab = {})
    }
}
