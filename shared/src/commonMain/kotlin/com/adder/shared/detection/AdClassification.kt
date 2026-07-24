package com.adder.shared.detection

/**
 * Classification result for a DOM element after heuristic analysis.
 */
enum class AdClassification {
    /** Definitely an ad — remove immediately */
    DEFINITE_AD,
    /** Very likely an ad — remove immediately */
    LIKELY_AD,
    /** Cannot determine with heuristics alone — send to LLM */
    AMBIGUOUS,
    /** Not an ad — leave in place */
    NOT_AD
}
