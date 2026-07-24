package com.adder.shared.detection

import com.adder.shared.model.DomElement

/**
 * Platform-specific LLM classifier interface.
 * Android: Gemini Nano via ML Kit GenAI Prompt API
 * iOS: Apple Foundation Models
 */
expect class LlmClassifier() {
    /**
     * Check if the on-device LLM is available and ready for use.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Classify a batch of ambiguous DOM elements using the on-device LLM.
     * Returns a list of booleans — true means the element is an ad.
     */
    suspend fun classifyElements(elements: List<DomElement>): List<Boolean>
}
