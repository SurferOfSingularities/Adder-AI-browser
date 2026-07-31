package com.adder.shared.engine

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of

/** A single recordable visit. */
data class Visit(val url: String, val title: String, val timestamp: Long)

/**
 * A mutable clock handed to [HistoryStore] as its `now` provider, so tests can pin
 * the exact timestamp of each recorded visit.
 */
class TestClock(var now: Long = 0L) {
    fun read(): Long = now
}

/**
 * Builds a [HistoryStore] over [store] whose timestamps are driven by [clock].
 */
fun historyStore(store: PersistentStore, clock: TestClock): HistoryStore =
    HistoryStore(store, clock::read)

/** Records [visits] in order, advancing the clock to each visit's timestamp. */
fun HistoryStore.recordAll(visits: List<Visit>, clock: TestClock) {
    visits.forEach { visit ->
        clock.now = visit.timestamp
        record(visit.url, visit.title)
    }
}

// ---------------------------------------------------------------------------
// Generators
//
// URLs are drawn from a small pool so collisions are frequent and deduplication is
// genuinely exercised; generators for URLs known to be absent use disjoint prefixes.
// Titles are built from a fixed ASCII alphabet rather than Arb.string so that JSON
// round-trips are deterministic (a random unpaired surrogate would make the
// persistence property flaky for reasons unrelated to the store).
// ---------------------------------------------------------------------------

val urlPool = listOf(
    "https://example.com",
    "https://example.com/page",
    "https://kotlinlang.org/docs/multiplatform.html",
    "https://news.example.org/article/1",
    "https://a.test/",
    "https://b.test/index.html"
)

private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -_.,"

private fun titleOfLength(length: Int): String =
    buildString(length) {
        for (i in 0 until length) append(ALPHABET[i % ALPHABET.length])
    }

/** URLs that collide often, to exercise dedup. */
val arbUrl: Arb<String> = Arb.of(urlPool)

/** URLs guaranteed not to be in [urlPool], to exercise insertion. */
val arbFreshUrl: Arb<String> = Arb.int(0..1_000_000).map { "https://fresh-$it.example.net/page" }

/** URLs guaranteed not to be in [urlPool], to exercise absent-key operations. */
val arbAbsentUrl: Arb<String> = Arb.int(0..1_000_000).map { "https://absent-$it.example.org/" }

/** URLs the store must refuse to record. */
val arbExcludedUrl: Arb<String> = Arb.of("about:blank", "About:Blank", "about:srcdoc", "", "   ")

/**
 * Titles spanning the interesting cases: empty, whitespace-only, ordinary, and
 * longer than [HistoryStore.MAX_TITLE_LENGTH].
 */
val arbTitle: Arb<String> = Arb.choose(
    2 to Arb.of("", "   ", "\t"),
    5 to Arb.int(1..80).map { titleOfLength(it) },
    3 to Arb.int(513..900).map { titleOfLength(it) }
)

val arbTimestamp: Arb<Long> = Arb.long(1_600_000_000_000L..1_800_000_000_000L)

/** Timestamps drawn from a narrow set so equal-timestamp ties happen often. */
val arbCollidingTimestamp: Arb<Long> = Arb.of(
    1_700_000_000_000L,
    1_700_000_000_001L,
    1_700_000_000_002L
)

val arbVisit: Arb<Visit> = Arb.bind(arbUrl, arbTitle, arbTimestamp) { url, title, ts ->
    Visit(url, title, ts)
}

/** Visits biased toward timestamp ties, for the ordering property. */
val arbTiedVisit: Arb<Visit> = Arb.bind(arbUrl, arbTitle, arbCollidingTimestamp) { url, title, ts ->
    Visit(url, title, ts)
}

/** A mutating operation, for the persistence round-trip and degradation properties. */
sealed interface HistoryOp {
    data class Record(val visit: Visit) : HistoryOp
    data class Delete(val url: String) : HistoryOp
    data object Clear : HistoryOp
}

/**
 * Operation sequences weighted toward recording, so generated histories are usually
 * non-trivial rather than repeatedly cleared.
 */
val arbHistoryOp: Arb<HistoryOp> = Arb.choose(
    8 to arbVisit.map { HistoryOp.Record(it) },
    3 to arbUrl.map { HistoryOp.Delete(it) },
    1 to Arb.of<HistoryOp>(HistoryOp.Clear)
)

/** Applies [op] to this store, advancing [clock] for record operations. */
fun HistoryStore.applyOp(op: HistoryOp, clock: TestClock) {
    when (op) {
        is HistoryOp.Record -> {
            clock.now = op.visit.timestamp
            record(op.visit.url, op.visit.title)
        }
        is HistoryOp.Delete -> delete(op.url)
        HistoryOp.Clear -> clear()
    }
}
