package com.adder.shared.detection

import com.adder.shared.model.DomElement

/**
 * Result of classifying a single DOM element.
 */
data class ClassificationResult(
    val element: DomElement,
    val classification: AdClassification,
    /** Human-readable reason for the classification (for debugging) */
    val reason: String
)
