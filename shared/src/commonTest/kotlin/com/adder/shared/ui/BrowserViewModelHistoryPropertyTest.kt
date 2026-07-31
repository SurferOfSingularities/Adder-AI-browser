package com.adder.shared.ui

import com.adder.shared.engine.FakePersistentStore
import com.adder.shared.engine.HistoryStore
import com.adder.shared.engine.TestClock
import com.adder.shared.engine.arbTimestamp
import com.adder.shared.engine.arbTitle
import com.adder.shared.engine.arbUrl
import com.adder.shared.engine.historyStore
import io.kotest.matchers.shouldBe
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property-based test for the history intents on [BrowserViewModel].
 */
class BrowserViewModelHistoryPropertyTest {

    private val config = PropTestConfig(iterations = 100)

    // Feature: browsing-history, Property 7: Revisiting an entry loads it, closes
    // history, and updates the address bar — the revisit intent issues a load of the
    // entry's URL through the WebView controller, sets historyVisible to false, and
    // sets the address-bar URL to the entry's URL.
    @Test
    fun `Property 7 - revisiting an entry loads it closes history and updates the address bar`() =
        runTest {
            checkAll(config, arbUrl, arbTitle, arbTimestamp) { url, title, timestamp ->
                val clock = TestClock(timestamp)
                val store: HistoryStore = historyStore(FakePersistentStore(), clock)
                store.record(url, title)

                val viewModel = BrowserViewModel(store)
                val webView = RecordingWebView(viewModel.webViewController)

                viewModel.openHistory()
                val entry = viewModel.historyEntries.single { it.url == url }

                viewModel.onRevisit(entry)

                webView.loadedUrls shouldBe listOf(entry.url)
                viewModel.historyVisible shouldBe false
                viewModel.url shouldBe entry.url
                viewModel.inputText shouldBe entry.url
            }
        }
}
