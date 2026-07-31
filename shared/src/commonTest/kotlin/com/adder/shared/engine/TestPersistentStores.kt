package com.adder.shared.engine

import com.adder.shared.model.HistoryEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * In-memory [PersistentStore] standing in for the platform store. Exposes its
 * contents so tests can assert on what was actually persisted, not just on what the
 * store reports in memory.
 */
class FakePersistentStore : PersistentStore {

    private val values = mutableMapOf<String, String>()

    /** Number of successful writes, for asserting that a no-op really wrote nothing. */
    var writeCount: Int = 0
        private set

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
        writeCount++
    }

    /** Immutable view of the backing map, for before/after comparisons. */
    fun snapshot(): Map<String, String> = values.toMap()
}

/**
 * A [PersistentStore] that fails on every operation, used to prove the history
 * store degrades to in-memory behaviour instead of propagating storage errors.
 */
class FailingPersistentStore : PersistentStore {

    override fun getString(key: String): String? =
        throw IllegalStateException("storage unavailable (read)")

    override fun putString(key: String, value: String): Unit =
        throw IllegalStateException("storage unavailable (write)")
}

private val testJson = Json { ignoreUnknownKeys = true }

/** Decodes the history payload the store wrote, or an empty list if it never wrote. */
fun FakePersistentStore.persistedEntries(): List<HistoryEntry> =
    getString(HistoryStore.STORAGE_KEY)
        ?.let { testJson.decodeFromString(ListSerializer(HistoryEntry.serializer()), it) }
        ?: emptyList()
