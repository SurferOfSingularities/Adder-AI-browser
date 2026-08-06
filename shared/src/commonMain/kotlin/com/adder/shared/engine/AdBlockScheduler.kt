package com.adder.shared.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ensures at most one [ClassificationPass] runs at a time across all tabs, with
 * active-tab priority preemption.
 *
 * When the active tab requests a classification while a background tab's pass is
 * running, the background pass is cancelled and the active tab's pass starts
 * immediately. Background requests are queued and drained FIFO after the current
 * pass completes (active-tab requests jump to the front).
 *
 * Thread-safety is achieved via a [Mutex] protecting mutable scheduler state.
 */
class AdBlockScheduler(
    private val scope: CoroutineScope
) {

    private val mutex = Mutex()

    private var currentJob: Job? = null
    private var currentTabId: String? = null
    private var currentIsActive: Boolean = false

    /**
     * Pending classification requests, ordered with active-tab requests first.
     */
    private val queue = ArrayDeque<PendingPass>()

    private data class PendingPass(
        val tabId: String,
        val isActiveTab: Boolean,
        val block: suspend () -> Unit
    )

    /**
     * Requests a classification pass for [tabId].
     *
     * - If [isActiveTab] and a background pass is running → cancels it, starts immediately.
     * - If another pass is running and this is not the active tab → queues it.
     * - If nothing is running → starts immediately.
     */
    fun requestClassification(
        tabId: String,
        isActiveTab: Boolean,
        block: suspend () -> Unit
    ) {
        scope.launch {
            mutex.withLock {
                // Remove any existing queued request for this tab (superseded).
                queue.removeAll { it.tabId == tabId }

                if (currentJob == null || currentJob?.isActive != true) {
                    // Nothing running — start directly.
                    startPass(tabId, isActiveTab, block)
                } else if (isActiveTab && !currentIsActive) {
                    // Active tab preempts a running background pass.
                    currentJob?.cancel()
                    startPass(tabId, isActiveTab, block)
                } else {
                    // Queue the request: active-tab requests go to front, background to back.
                    val pending = PendingPass(tabId, isActiveTab, block)
                    if (isActiveTab) {
                        queue.addFirst(pending)
                    } else {
                        queue.addLast(pending)
                    }
                }
            }
        }
    }

    /**
     * Cancels any running or queued pass for [tabId] (e.g. when a tab is closed).
     */
    fun cancelForTab(tabId: String) {
        scope.launch {
            mutex.withLock {
                queue.removeAll { it.tabId == tabId }
                if (currentTabId == tabId) {
                    currentJob?.cancel()
                    currentJob = null
                    currentTabId = null
                    currentIsActive = false
                    drainNext()
                }
            }
        }
    }

    private fun startPass(tabId: String, isActiveTab: Boolean, block: suspend () -> Unit) {
        currentTabId = tabId
        currentIsActive = isActiveTab
        currentJob = scope.launch {
            try {
                block()
            } finally {
                mutex.withLock {
                    currentJob = null
                    currentTabId = null
                    currentIsActive = false
                    drainNext()
                }
            }
        }
    }

    /**
     * Starts the next queued pass, if any. Must be called while holding [mutex].
     */
    private fun drainNext() {
        if (queue.isEmpty()) return
        val next = queue.removeFirst()
        startPass(next.tabId, next.isActiveTab, next.block)
    }
}
