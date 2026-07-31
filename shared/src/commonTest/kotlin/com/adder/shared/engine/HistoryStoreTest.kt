package com.adder.shared.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Example-based tests for [HistoryStore], covering the concrete behaviours that are
 * not universally quantified and so are not a good fit for a property.
 */
class HistoryStoreTest {

    @Test
    fun `fresh store over empty storage has no entries`() {
        val store = HistoryStore(FakePersistentStore()) { 0L }

        assertTrue(store.entries().isEmpty())
    }

    @Test
    fun `visits accumulate across navigations on one store instance`() {
        val clock = TestClock()
        val store = historyStore(FakePersistentStore(), clock)

        listOf(
            "https://one.example.com/",
            "https://two.example.com/",
            "https://three.example.com/"
        ).forEachIndexed { index, url ->
            clock.now = 1_000L + index
            store.record(url, "Page $index")
        }

        assertEquals(3, store.entries().size)
        assertEquals(
            listOf(
                "https://three.example.com/",
                "https://two.example.com/",
                "https://one.example.com/"
            ),
            store.entries().map { it.url }
        )
    }

    @Test
    fun `accumulation stops at the history limit`() {
        val clock = TestClock()
        val store = historyStore(FakePersistentStore(), clock)

        repeat(HistoryStore.HISTORY_LIMIT + 50) { index ->
            clock.now = 1_000L + index
            store.record("https://page-$index.example.com/", "Page $index")
        }

        assertEquals(HistoryStore.HISTORY_LIMIT, store.entries().size)
        // The newest visit survives; the very first is long gone.
        assertEquals("https://page-249.example.com/", store.entries().first().url)
        assertTrue(store.entries().none { it.url == "https://page-0.example.com/" })
    }

    @Test
    fun `about blank is not recorded`() {
        val store = historyStore(FakePersistentStore(), TestClock(1_000L))

        store.record("about:blank", "")

        assertTrue(store.entries().isEmpty())
    }

    @Test
    fun `a title at exactly the limit is kept intact and a longer one is truncated`() {
        val store = historyStore(FakePersistentStore(), TestClock(1_000L))
        val exact = "t".repeat(HistoryStore.MAX_TITLE_LENGTH)
        val tooLong = "t".repeat(HistoryStore.MAX_TITLE_LENGTH + 100)

        store.record("https://exact.example.com/", exact)
        store.record("https://long.example.com/", tooLong)

        assertEquals(
            exact,
            store.entries().single { it.url == "https://exact.example.com/" }.displayLabel
        )
        assertEquals(
            HistoryStore.MAX_TITLE_LENGTH,
            store.entries().single { it.url == "https://long.example.com/" }.displayLabel.length
        )
    }

    @Test
    fun `re-recording a URL refreshes its label`() {
        val clock = TestClock()
        val store = historyStore(FakePersistentStore(), clock)
        val url = "https://renamed.example.com/"

        clock.now = 1_000L
        store.record(url, "Old title")
        clock.now = 2_000L
        store.record(url, "New title")

        assertEquals(1, store.entries().size)
        assertEquals("New title", store.entries().single().displayLabel)
        assertEquals(2_000L, store.entries().single().visitTimestamp)
    }

    @Test
    fun `entries persisted by an earlier session are restored newest first`() {
        val backing = FakePersistentStore()
        val clock = TestClock()
        val first = historyStore(backing, clock)

        clock.now = 1_000L
        first.record("https://older.example.com/", "Older")
        clock.now = 2_000L
        first.record("https://newer.example.com/", "Newer")

        val restored = HistoryStore(backing) { 0L }

        assertEquals(
            listOf("https://newer.example.com/", "https://older.example.com/"),
            restored.entries().map { it.url }
        )
        assertEquals(listOf("Newer", "Older"), restored.entries().map { it.displayLabel })
    }
}
