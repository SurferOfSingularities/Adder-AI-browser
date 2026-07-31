package com.adder.shared.ui

import com.adder.shared.engine.FakePersistentStore
import com.adder.shared.engine.HistoryStore
import com.adder.shared.engine.TestClock
import com.adder.shared.engine.historyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Example-based tests for the history behaviour of [BrowserViewModel]: the concrete
 * state transitions the properties do not cover.
 */
class BrowserViewModelHistoryTest {

    private class Fixture(val clock: TestClock = TestClock()) {
        val backing = FakePersistentStore()
        val store: HistoryStore = historyStore(backing, clock)
        val viewModel = BrowserViewModel(store)
        val webView = RecordingWebView(viewModel.webViewController)
    }

    @Test
    fun `closing history leaves the current page untouched`() {
        val fixture = Fixture()
        val urlBefore = fixture.viewModel.url

        fixture.viewModel.openHistory()
        assertTrue(fixture.viewModel.historyVisible)

        fixture.viewModel.closeHistory()

        assertFalse(fixture.viewModel.historyVisible)
        assertEquals(urlBefore, fixture.viewModel.url)
        // No reload and no navigation — the loaded page is exactly as it was.
        assertTrue(fixture.webView.loadedUrls.isEmpty())
        assertEquals(0, fixture.webView.reloadCount)
    }

    @Test
    fun `revisiting an entry keeps it in history`() {
        val fixture = Fixture()
        fixture.clock.now = 1_000L
        fixture.store.record("https://kept.example.com/", "Kept")
        fixture.viewModel.openHistory()
        val entry = fixture.viewModel.historyEntries.single()

        fixture.viewModel.onRevisit(entry)

        assertTrue(fixture.store.entries().any { it.url == entry.url })
    }

    @Test
    fun `a finished page load is recorded and published to the UI`() {
        val fixture = Fixture()
        fixture.clock.now = 5_000L
        fixture.webView.title = "Example Domain"

        fixture.viewModel.onPageFinished("https://example.com/")

        assertEquals(1, fixture.viewModel.historyEntries.size)
        val entry = fixture.viewModel.historyEntries.single()
        assertEquals("https://example.com/", entry.url)
        assertEquals("Example Domain", entry.displayLabel)
        assertEquals(5_000L, entry.visitTimestamp)
    }

    @Test
    fun `a finished page load with no title falls back to the URL`() {
        val fixture = Fixture()
        fixture.clock.now = 5_000L
        fixture.webView.title = null

        fixture.viewModel.onPageFinished("https://example.com/")

        assertEquals("https://example.com/", fixture.viewModel.historyEntries.single().displayLabel)
    }

    @Test
    fun `an about blank load records nothing`() {
        val fixture = Fixture()

        fixture.viewModel.onPageFinished("about:blank")

        assertTrue(fixture.viewModel.historyEntries.isEmpty())
        assertTrue(fixture.store.entries().isEmpty())
    }

    @Test
    fun `deleting an entry refreshes the published list`() {
        val fixture = Fixture()
        fixture.clock.now = 1_000L
        fixture.store.record("https://one.example.com/", "One")
        fixture.clock.now = 2_000L
        fixture.store.record("https://two.example.com/", "Two")
        fixture.viewModel.openHistory()
        assertEquals(2, fixture.viewModel.historyEntries.size)

        val target = fixture.viewModel.historyEntries.single { it.url == "https://two.example.com/" }
        fixture.viewModel.onDeleteHistory(target)

        assertEquals(
            listOf("https://one.example.com/"),
            fixture.viewModel.historyEntries.map { it.url }
        )
    }

    @Test
    fun `clearing empties the published list`() {
        val fixture = Fixture()
        fixture.clock.now = 1_000L
        fixture.store.record("https://one.example.com/", "One")
        fixture.viewModel.openHistory()

        fixture.viewModel.onClearHistory()

        assertTrue(fixture.viewModel.historyEntries.isEmpty())
    }

    @Test
    fun `history persisted by an earlier session is available on startup`() {
        val backing = FakePersistentStore()
        val clock = TestClock(1_000L)
        historyStore(backing, clock).record("https://previous.example.com/", "Previous")

        val viewModel = BrowserViewModel(HistoryStore(backing) { 0L })

        assertEquals(
            listOf("https://previous.example.com/"),
            viewModel.historyEntries.map { it.url }
        )
    }

    @Test
    fun `reloading the same page after a blocking toggle does not duplicate the entry`() {
        val fixture = Fixture()
        fixture.webView.title = "Example Domain"

        fixture.clock.now = 1_000L
        fixture.viewModel.onPageFinished("https://example.com/")
        fixture.viewModel.toggleBlocking()
        fixture.clock.now = 2_000L
        fixture.viewModel.onPageFinished("https://example.com/")

        assertEquals(1, fixture.viewModel.historyEntries.size)
        assertEquals(2_000L, fixture.viewModel.historyEntries.single().visitTimestamp)
        assertEquals(1, fixture.webView.reloadCount)
    }
}
