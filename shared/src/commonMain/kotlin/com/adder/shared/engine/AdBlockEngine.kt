package com.adder.shared.engine

import com.adder.shared.detection.AdClassification
import com.adder.shared.detection.AdDetector
import com.adder.shared.detection.ClassificationResult
import com.adder.shared.detection.LlmClassifier
import com.adder.shared.model.DomElement

/**
 * Orchestrates the full ad detection pipeline:
 * 1. Checks whitelist (skip if whitelisted)
 * 2. Checks cache for previously detected ads on this domain
 * 3. Runs heuristic classification (fast)
 * 4. Sends ambiguous elements to LLM (if available)
 * 5. Caches results and returns selectors to remove
 *
 * This class lives in the shared module and is used by both platforms.
 */
class AdBlockEngine(
    private val llmClassifier: LlmClassifier
) {
    private val adDetector = AdDetector()
    private val cache = DetectionCache()
    val whitelist = SiteWhitelist()
    private var llmAvailable: Boolean? = null

    /**
     * Result of running the ad detection pipeline on a set of DOM elements.
     */
    data class PipelineResult(
        /** CSS selectors of elements identified as ads (to be removed) */
        val selectorsToRemove: List<String>,
        /** Total elements analyzed */
        val totalAnalyzed: Int,
        /** Elements removed by heuristics alone */
        val heuristicRemovals: Int,
        /** Elements removed after LLM classification */
        val llmRemovals: Int,
        /** Elements that were ambiguous but LLM was unavailable */
        val unresolved: Int,
        /** Whether the site was whitelisted (pipeline skipped) */
        val skipped: Boolean = false
    )

    /**
     * Run the full detection pipeline on extracted DOM elements.
     * Returns the selectors of elements that should be removed.
     *
     * @param elements Extracted DOM elements to classify
     * @param pageUrl Current page URL (used for whitelist and cache lookup)
     */
    suspend fun runPipeline(elements: List<DomElement>, pageUrl: String = ""): PipelineResult {
        // Check whitelist
        if (pageUrl.isNotEmpty() && whitelist.isWhitelisted(pageUrl)) {
            return PipelineResult(
                selectorsToRemove = emptyList(),
                totalAnalyzed = 0,
                heuristicRemovals = 0,
                llmRemovals = 0,
                unresolved = 0,
                skipped = true
            )
        }

        if (elements.isEmpty()) {
            return PipelineResult(
                selectorsToRemove = emptyList(),
                totalAnalyzed = 0,
                heuristicRemovals = 0,
                llmRemovals = 0,
                unresolved = 0
            )
        }

        // Step 1: Heuristic classification
        val classifications = adDetector.classify(elements)

        // Step 2: Separate results by classification
        val definiteAds = mutableListOf<ClassificationResult>()
        val ambiguous = mutableListOf<ClassificationResult>()

        for (result in classifications) {
            when (result.classification) {
                AdClassification.DEFINITE_AD,
                AdClassification.LIKELY_AD -> definiteAds.add(result)
                AdClassification.AMBIGUOUS -> ambiguous.add(result)
                AdClassification.NOT_AD -> { /* leave alone */ }
            }
        }

        // Step 3: LLM classification for ambiguous elements (batched)
        var llmRemovals = 0
        val llmAdSelectors = mutableListOf<String>()

        if (ambiguous.isNotEmpty()) {
            val isLlmReady = checkLlmAvailability()

            if (isLlmReady) {
                // Batch elements for efficient LLM processing
                val batches = ambiguous.chunked(LLM_BATCH_SIZE)

                for (batch in batches) {
                    val batchElements = batch.map { it.element }
                    val llmResults = llmClassifier.classifyElements(batchElements)

                    for (i in llmResults.indices) {
                        if (llmResults[i]) {
                            val selector = batch[i].element.selector
                            if (selector.isNotEmpty()) {
                                llmAdSelectors.add(selector)
                                llmRemovals++
                            }
                        }
                    }
                }
            }
        }

        // Step 4: Collect all selectors to remove
        val heuristicSelectors = definiteAds
            .map { it.element.selector }
            .filter { it.isNotEmpty() }

        val allSelectors = heuristicSelectors + llmAdSelectors
        val unresolved = if (checkLlmAvailability()) 0 else ambiguous.size

        // Step 5: Cache results for this domain
        if (pageUrl.isNotEmpty() && allSelectors.isNotEmpty()) {
            cache.put(pageUrl, allSelectors.toSet())
        }

        return PipelineResult(
            selectorsToRemove = allSelectors,
            totalAnalyzed = elements.size,
            heuristicRemovals = heuristicSelectors.size,
            llmRemovals = llmRemovals,
            unresolved = unresolved
        )
    }

    /**
     * Get cached ad selectors for a URL (from previous visits).
     * Returns null if nothing is cached for this domain.
     */
    fun getCachedSelectors(url: String): Set<String>? {
        return cache.get(url)
    }

    /**
     * Clear the detection cache.
     */
    fun clearCache() {
        cache.clear()
    }

    private suspend fun checkLlmAvailability(): Boolean {
        if (llmAvailable == null) {
            llmAvailable = try {
                llmClassifier.isAvailable()
            } catch (e: Exception) {
                false
            }
        }
        return llmAvailable!!
    }

    companion object {
        /** Max elements to send to LLM in a single batch */
        private const val LLM_BATCH_SIZE = 10
    }
}
