package com.adder.shared.engine

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property-based tests for [HistoryStore], covering the correctness properties from
 * the browsing-history design. Each property is exercised by exactly one test at a
 * minimum of 100 generated iterations.
 */
class HistoryStorePropertyTest {

    private val config = PropTestConfig(iterations = 100)

    // Feature: browsing-history, Property 1: History is ordered by recency —
    // for any sequence of recorded visits, entries() is ordered by visitTimestamp
    // descending; entries sharing a timestamp appear most-recently-inserted first,
    // and repeated calls return the same order.
    @Test
    fun `Property 1 - history is ordered by recency`() = runTest {
        checkAll(config, Arb.list(arbTiedVisit, 1..30)) { visits ->
            val clock = TestClock()
            val store = historyStore(FakePersistentStore(), clock)

            // Independent mirror of last-touch order: the most recently recorded
            // URL ends up last in this list.
            val touchOrder = mutableListOf<String>()
            visits.forEach { visit ->
                clock.now = visit.timestamp
                store.record(visit.url, visit.title)
                touchOrder.remove(visit.url)
                touchOrder.add(visit.url)
            }

            val entries = store.entries()

            // Timestamps are non-increasing.
            entries.zipWithNext().forEach { (earlier, later) ->
                (earlier.visitTimestamp >= later.visitTimestamp) shouldBe true
            }

            // Within a group of equal timestamps, the most recently touched URL
            // comes first, so touch positions descend.
            entries.groupBy { it.visitTimestamp }.forEach { (_, group) ->
                val touchPositions = group.map { touchOrder.indexOf(it.url) }
                touchPositions shouldBe touchPositions.sortedDescending()
            }

            // Reading twice yields the same order.
            store.entries() shouldBe entries
        }
    }

    // Feature: browsing-history, Property 2: Recording a distinct valid URL inserts
    // one entry — the count grows by exactly one and the new entry carries the
    // recorded URL and timestamp.
    @Test
    fun `Property 2 - recording a distinct valid URL inserts one entry`() = runTest {
        checkAll(
            config,
            Arb.list(arbVisit, 0..20),
            arbFreshUrl,
            arbTitle,
            arbTimestamp
        ) { seed, url, title, timestamp ->
            val clock = TestClock()
            val store = historyStore(FakePersistentStore(), clock)
            // Seeded well below the cap, so this insert can never trigger eviction.
            store.recordAll(seed, clock)

            val countBefore = store.entries().size
            clock.now = timestamp
            store.record(url, title)

            store.entries().size shouldBe countBefore + 1
            val inserted = store.entries().single { it.url == url }
            inserted.visitTimestamp shouldBe timestamp
        }
    }

    // Feature: browsing-history, Property 3: Exact-URL dedup updates the timestamp
    // without duplicating — re-recording a URL already present leaves the count
    // unchanged and refreshes that entry's timestamp. This also covers reloading the
    // current page after the ad-blocking toggle flips.
    @Test
    fun `Property 3 - exact-URL dedup updates the timestamp without duplicating`() = runTest {
        checkAll(
            config,
            arbUrl,
            arbTitle,
            arbTimestamp,
            arbTitle,
            arbTimestamp
        ) { url, firstTitle, firstTimestamp, secondTitle, secondTimestamp ->
            val clock = TestClock()
            val store = historyStore(FakePersistentStore(), clock)

            // Filler visit so the target is not trivially the only entry.
            clock.now = firstTimestamp
            store.record("https://filler.example.net/", "Filler")
            clock.now = firstTimestamp
            store.record(url, firstTitle)

            val countBefore = store.entries().size

            clock.now = secondTimestamp
            store.record(url, secondTitle)

            store.entries().size shouldBe countBefore
            store.entries().single { it.url == url }.visitTimestamp shouldBe secondTimestamp
        }
    }

    // Feature: browsing-history, Property 4: Display label is the truncated title or
    // the URL — the label is the title capped at MAX_TITLE_LENGTH when the title is
    // non-blank, and the URL when it is blank or whitespace.
    @Test
    fun `Property 4 - display label is the truncated title or the URL`() = runTest {
        checkAll(config, arbUrl, arbTitle, arbTimestamp) { url, title, timestamp ->
            val store = historyStore(FakePersistentStore(), TestClock(timestamp))
            store.record(url, title)

            val expected =
                if (title.isBlank()) url else title.take(HistoryStore.MAX_TITLE_LENGTH)
            store.entries().single { it.url == url }.displayLabel shouldBe expected
        }
    }

    // Feature: browsing-history, Property 5: The store never exceeds the 200-entry
    // limit — in-memory and persisted counts both stay at or below HISTORY_LIMIT,
    // and the entries removed are the oldest by visitTimestamp.
    @Test
    fun `Property 5 - the store never exceeds the 200-entry limit`() = runTest {
        checkAll(config, Arb.list(arbTimestamp, 201..260)) { timestamps ->
            val fake = FakePersistentStore()
            val clock = TestClock()
            val store = historyStore(fake, clock)

            // Distinct URLs, so every visit is an insert rather than a dedup.
            timestamps.forEachIndexed { index, timestamp ->
                clock.now = timestamp
                store.record("https://capped-$index.example.net/", "Page $index")
            }

            store.entries().size shouldBe HistoryStore.HISTORY_LIMIT
            fake.persistedEntries().size shouldBe HistoryStore.HISTORY_LIMIT

            // What survives is exactly the newest HISTORY_LIMIT visits.
            store.entries().map { it.visitTimestamp }.sorted() shouldBe
                timestamps.sorted().takeLast(HistoryStore.HISTORY_LIMIT)
        }
    }

    // Feature: browsing-history, Property 6: Excluded URLs are never recorded —
    // recording about:blank (or a blank URL) leaves the entry set unchanged.
    @Test
    fun `Property 6 - excluded URLs are never recorded`() = runTest {
        checkAll(
            config,
            Arb.list(arbVisit, 0..15),
            arbExcludedUrl,
            arbTitle,
            arbTimestamp
        ) { seed, excludedUrl, title, timestamp ->
            val fake = FakePersistentStore()
            val clock = TestClock()
            val store = historyStore(fake, clock)
            store.recordAll(seed, clock)

            val entriesBefore = store.entries()
            val persistedBefore = fake.snapshot()

            clock.now = timestamp
            store.record(excludedUrl, title)

            store.entries() shouldBe entriesBefore
            fake.snapshot() shouldBe persistedBefore
        }
    }

    // Feature: browsing-history, Property 8: Deleting an entry removes only that
    // entry and persists the removal — other entries are retained in order, and a
    // store reloaded from the same backing storage no longer contains the URL.
    @Test
    fun `Property 8 - deleting an entry removes only that entry and persists the removal`() =
        runTest {
            checkAll(config, Arb.list(arbVisit, 1..20), Arb.int(0..10_000)) { visits, pick ->
                val fake = FakePersistentStore()
                val clock = TestClock()
                val store = historyStore(fake, clock)
                store.recordAll(visits, clock)

                val before = store.entries()
                val target = before[pick % before.size]

                store.delete(target.url)

                store.entries() shouldBe before.filter { it.url != target.url }
                HistoryStore(fake) { 0L }.entries().any { it.url == target.url } shouldBe false
            }
        }

    // Feature: browsing-history, Property 9: Deleting an absent entry is a no-op —
    // neither the in-memory entry set nor the persisted payload changes.
    @Test
    fun `Property 9 - deleting an absent entry is a no-op`() = runTest {
        checkAll(config, Arb.list(arbVisit, 0..20), arbAbsentUrl) { visits, absentUrl ->
            val fake = FakePersistentStore()
            val clock = TestClock()
            val store = historyStore(fake, clock)
            store.recordAll(visits, clock)

            val entriesBefore = store.entries()
            val persistedBefore = fake.snapshot()
            val writesBefore = fake.writeCount

            store.delete(absentUrl)

            store.entries() shouldBe entriesBefore
            fake.snapshot() shouldBe persistedBefore
            // Not merely an unchanged payload — no write was attempted at all.
            fake.writeCount shouldBe writesBefore
        }
    }

    // Feature: browsing-history, Property 10: Clearing removes all entries and
    // persists the empty state.
    @Test
    fun `Property 10 - clearing removes all entries and persists the empty state`() = runTest {
        checkAll(config, Arb.list(arbVisit, 0..25)) { visits ->
            val fake = FakePersistentStore()
            val clock = TestClock()
            val store = historyStore(fake, clock)
            store.recordAll(visits, clock)

            store.clear()

            store.entries() shouldBe emptyList()
            HistoryStore(fake) { 0L }.entries() shouldBe emptyList()
        }
    }

    // Feature: browsing-history, Property 11: Persistence round-trip preserves the
    // ordered set — a new store over the same PersistentStore yields entries() equal
    // in URLs, labels, timestamps, and descending order.
    @Test
    fun `Property 11 - persistence round-trip preserves the ordered set`() = runTest {
        checkAll(config, Arb.list(arbHistoryOp, 0..40)) { ops ->
            val fake = FakePersistentStore()
            val clock = TestClock()
            val store = historyStore(fake, clock)
            ops.forEach { store.applyOp(it, clock) }

            val reloaded = HistoryStore(fake) { 0L }
            reloaded.entries() shouldBe store.entries()
        }
    }

    // Feature: browsing-history, Property 12: Storage failures degrade gracefully —
    // against a store that throws on every read and write, no operation throws and
    // the ordering, dedup, and cap invariants still hold in memory.
    @Test
    fun `Property 12 - storage failures degrade gracefully`() = runTest {
        checkAll(config, Arb.list(arbHistoryOp, 0..40)) { ops ->
            val clock = TestClock()
            // Construction itself reads from storage, and that read throws.
            val store = HistoryStore(FailingPersistentStore(), clock::read)

            ops.forEach { store.applyOp(it, clock) }

            val entries = store.entries()
            (entries.size <= HistoryStore.HISTORY_LIMIT) shouldBe true
            entries.map { it.url }.distinct().size shouldBe entries.size
            entries.zipWithNext().forEach { (earlier, later) ->
                (earlier.visitTimestamp >= later.visitTimestamp) shouldBe true
            }
        }
    }
}
