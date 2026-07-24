package com.adder.shared.detection

import com.adder.shared.model.DomElement

/**
 * iOS implementation of LlmClassifier.
 * Delegates to Apple Foundation Models via Swift interop.
 * The actual Swift implementation will be injected at runtime.
 */
actual class LlmClassifier actual constructor() {

    /**
     * Swift-side classifier that gets set from the iOS app layer.
     */
    var swiftClassifier: IosLlmClassifierDelegate? = null

    actual suspend fun isAvailable(): Boolean {
        return swiftClassifier?.isAvailable() ?: false
    }

    actual suspend fun classifyElements(elements: List<DomElement>): List<Boolean> {
        val classifier = swiftClassifier ?: return List(elements.size) { false }
        return classifier.classifyElements(elements)
    }
}

/**
 * Protocol/interface for the Swift-side LLM implementation.
 * The iOS app will provide an implementation using Apple Foundation Models.
 */
interface IosLlmClassifierDelegate {
    suspend fun isAvailable(): Boolean
    suspend fun classifyElements(elements: List<DomElement>): List<Boolean>
}
