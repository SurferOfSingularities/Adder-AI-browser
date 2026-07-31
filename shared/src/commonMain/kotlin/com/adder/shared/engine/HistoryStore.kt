package com.adder.shared.engine

import com.adder.shared.model.HistoryEntry
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Owns the browsing history and all of its invariants: recording, ordering,
 * deduplication, eviction at [HISTORY_LIMIT], and durable persistence.
 *
 * Entries are held in memory in insertion order with a monotonic sequence number
 * per entry, and sorted on read. That keeps writes cheap and gives a deterministic
 * tie-break when two visits share a timestamp (most recently touched first).
 *
 * Every persistence call is failure-tolerant: if the [PersistentStore] is broken or
 * unavailable, the in-memory list stays authoritative for the session and nothing
 * throws.
 *
 * @param store durable backing storage; defaults to the platform store.
 * @param now supplies the visit timestamp in epoch milliseconds.
 */
class HistoryStore(
    private val store: PersistentStore = platformPersistentStore(),
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {

    /** In-memory entry plus its insertion sequence (not persisted). */
    private class Record(var entry: HistoryEntry, var seq: Long)

    private val records = mutableListOf<Record>()
    private var nextSeq = 0L

    init {
        load()
    }

    /**
     * Records a completed page load.
     *
     * Blank and `about:` URLs are ignored. If [url] exactly matches an existing
     * entry, that entry's timestamp and label are refreshed and it becomes the most
     * recent — no duplicate is created. Otherwise a new entry is appended, evicting
     * the oldest entries if that would exceed [HISTORY_LIMIT].
     *
     * @param title the page title; blank means [url] is used as the display label.
     */
    fun record(url: String, title: String) {
        if (!isRecordable(url)) return

        val timestamp = now()
        val label = if (title.isBlank()) url else title.take(MAX_TITLE_LENGTH)
        val existing = records.firstOrNull { it.entry.url == url }

        if (existing != null) {
            existing.entry = existing.entry.copy(
                displayLabel = label,
                visitTimestamp = timestamp
            )
            existing.seq = nextSeq++
        } else {
            records.add(Record(HistoryEntry(url, label, timestamp), nextSeq++))
            evictOverflow()
        }

        persist()
    }

    /**
     * All entries ordered by [HistoryEntry.visitTimestamp] descending, ties broken
     * by insertion order with the most recently inserted or updated entry first.
     * Repeated calls return the same order.
     */
    fun entries(): List<HistoryEntry> = records
        .sortedWith(
            compareByDescending<Record> { it.entry.visitTimestamp }
                .thenByDescending { it.seq }
        )
        .map { it.entry }

    /** Removes the entry whose URL matches [url]. Does nothing (including no write) if absent. */
    fun delete(url: String) {
        val removed = records.removeAll { it.entry.url == url }
        if (removed) persist()
    }

    /** Removes every entry and persists the empty set. */
    fun clear() {
        records.clear()
        persist()
    }

    /**
     * Drops the oldest entries — smallest `(visitTimestamp, seq)` first — until the
     * count is back within [HISTORY_LIMIT].
     */
    private fun evictOverflow() {
        while (records.size > HISTORY_LIMIT) {
            val oldest = records.minWithOrNull(
                compareBy<Record> { it.entry.visitTimestamp }.thenBy { it.seq }
            ) ?: break
            records.remove(oldest)
        }
    }

    /**
     * Reads and restores the persisted entries. The payload is stored in descending
     * order, so sequence numbers are reassigned in reverse of load position to keep
     * the equal-timestamp ordering identical across a save/load round trip.
     *
     * Any failure — unavailable storage, corrupt JSON — leaves the store empty
     * rather than propagating.
     */
    private fun load() {
        val stored = runCatching {
            store.getString(STORAGE_KEY)?.let { json.decodeFromString(entryListSerializer, it) }
        }.getOrNull() ?: return

        val ordered = stored
            .sortedByDescending { it.visitTimestamp }
            .take(HISTORY_LIMIT)

        records.clear()
        val lastIndex = ordered.size - 1
        ordered.forEachIndexed { index, entry ->
            records.add(Record(entry, (lastIndex - index).toLong()))
        }
        nextSeq = ordered.size.toLong()
    }

    /** Writes the current ordered entry set to durable storage, ignoring failures. */
    private fun persist() {
        runCatching {
            store.putString(STORAGE_KEY, json.encodeToString(entryListSerializer, entries()))
        }
    }

    companion object {
        /** Maximum number of entries retained, in memory and persisted. */
        const val HISTORY_LIMIT = 200

        /** Page titles longer than this are truncated before being stored. */
        const val MAX_TITLE_LENGTH = 512

        internal const val STORAGE_KEY = "adder.browsing_history.v1"

        private val json = Json { ignoreUnknownKeys = true }

        private val entryListSerializer = ListSerializer(HistoryEntry.serializer())

        /**
         * Whether [url] represents a real visited page worth recording. Excludes
         * blank URLs and the `about:` scheme (`about:blank` and friends are never
         * pages the user navigated to).
         */
        internal fun isRecordable(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.isNotEmpty() && !trimmed.startsWith("about:", ignoreCase = true)
        }
    }
}
