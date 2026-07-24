package com.adder.shared.detection

/**
 * Human-readable name of the on-device LLM used for ad classification
 * on the current platform (e.g. "Gemini Nano", "Apple Foundation Models").
 */
expect fun currentModelName(): String
