package com.adder.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adder.shared.model.HistoryEntry
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Browsing history surface, shown as an opaque overlay above the page while
 * [BrowserViewModel.historyVisible] is true.
 *
 * [entries] is rendered in the order given — the ViewModel supplies it already
 * sorted newest-first — so this composable stays a pure function of its inputs.
 */
@Composable
fun HistoryView(
    entries: List<HistoryEntry>,
    onEntryClick: (HistoryEntry) -> Unit,
    onEntryDelete: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HistoryTopBar(
                canClearAll = entries.isNotEmpty(),
                onClearAll = onClearAll,
                onClose = onClose
            )

            HorizontalDivider()

            if (entries.isEmpty()) {
                HistoryEmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = entries, key = { it.url }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onClick = { onEntryClick(entry) },
                            onDelete = { onEntryDelete(entry) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTopBar(
    canClearAll: Boolean,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
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
                    contentDescription = "Close history"
                )
            }

            Text(
                text = "History",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onClearAll, enabled = canClearAll) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = "Clear all history"
                )
            }
        }
    }
}

/**
 * A single history entry: the display label on top, the full URL beneath it, and a
 * delete action. Tapping the row revisits the page.
 */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove ${entry.displayLabel} from history"
                )
            }
        }
    }
}

/** Shown in place of the list when nothing has been recorded yet. */
@Composable
private fun HistoryEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No history recorded",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pages you visit will show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val sampleEntries = listOf(
    HistoryEntry(
        url = "https://www.google.com",
        displayLabel = "Google",
        visitTimestamp = 1_700_000_300_000
    ),
    HistoryEntry(
        url = "https://kotlinlang.org/docs/multiplatform.html",
        displayLabel = "Kotlin Multiplatform | Kotlin Documentation",
        visitTimestamp = 1_700_000_200_000
    ),
    HistoryEntry(
        url = "https://news.example.com/2026/07/a-very-long-article-slug-that-keeps-going-and-going",
        displayLabel = "https://news.example.com/2026/07/a-very-long-article-slug-that-keeps-going-and-going",
        visitTimestamp = 1_700_000_100_000
    )
)

@Preview
@Composable
private fun HistoryRowWithTitlePreview() {
    MaterialTheme {
        HistoryRow(entry = sampleEntries[1], onClick = {}, onDelete = {})
    }
}

@Preview
@Composable
private fun HistoryRowUrlOnlyPreview() {
    MaterialTheme {
        HistoryRow(entry = sampleEntries[2], onClick = {}, onDelete = {})
    }
}

@Preview
@Composable
private fun HistoryTopBarPreview() {
    MaterialTheme {
        HistoryTopBar(canClearAll = true, onClearAll = {}, onClose = {})
    }
}

@Preview
@Composable
private fun HistoryTopBarEmptyPreview() {
    MaterialTheme {
        HistoryTopBar(canClearAll = false, onClearAll = {}, onClose = {})
    }
}

@Preview
@Composable
private fun HistoryEmptyStatePreview() {
    MaterialTheme {
        HistoryEmptyState()
    }
}

@Preview
@Composable
private fun HistoryViewPopulatedPreview() {
    MaterialTheme {
        HistoryView(
            entries = sampleEntries,
            onEntryClick = {},
            onEntryDelete = {},
            onClearAll = {},
            onClose = {}
        )
    }
}

@Preview
@Composable
private fun HistoryViewEmptyPreview() {
    MaterialTheme {
        HistoryView(
            entries = emptyList(),
            onEntryClick = {},
            onEntryDelete = {},
            onClearAll = {},
            onClose = {}
        )
    }
}
