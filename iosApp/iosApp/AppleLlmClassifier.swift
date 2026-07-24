import Foundation
import FoundationModels
import shared

/// Swift implementation of the LLM classifier using Apple Foundation Models.
/// This bridges to the KMP IosLlmClassifierDelegate protocol.
@available(iOS 26.0, *)
class AppleLlmClassifier: shared.IosLlmClassifierDelegate {

    private var session: LanguageModelSession?

    init() {
        // Session will be created on first use
    }

    func isAvailable() async throws -> KotlinBoolean {
        let model = SystemLanguageModel.default
        switch model.availability {
        case .available:
            return KotlinBoolean(value: true)
        default:
            return KotlinBoolean(value: false)
        }
    }

    func classifyElements(elements: [shared.DomElement]) async throws -> [KotlinBoolean] {
        guard !elements.isEmpty else { return [] }

        // Create session with ad-detection instructions if not exists
        if session == nil {
            session = LanguageModelSession(instructions: """
                You are an ad detection system. When given HTML element descriptions, \
                classify each one as either an advertisement or not. \
                Respond with only "ad" or "not_ad" for each element, one per line.
                """)
        }

        guard let session = session else {
            return elements.map { _ in KotlinBoolean(value: false) }
        }

        // Build the prompt using the shared PromptBuilder
        let prompt = PromptBuilder.shared.buildBatchClassificationPrompt(elements: elements)

        do {
            let response = try await session.respond(to: prompt)
            let responseText = response.content

            print("[AppleLLM] Response: \(responseText)")

            // Parse the response using shared PromptBuilder
            let classifications = PromptBuilder.shared.parseBatchResponse(
                response: responseText,
                expectedCount: Int32(elements.count)
            )

            return classifications.map { KotlinBoolean(value: $0.boolValue) }
        } catch {
            print("[AppleLLM] Error during classification: \(error.localizedDescription)")
            return elements.map { _ in KotlinBoolean(value: false) }
        }
    }
}

/// Fallback classifier for devices that don't support Foundation Models.
class FallbackLlmClassifier: shared.IosLlmClassifierDelegate {

    func isAvailable() async throws -> KotlinBoolean {
        return KotlinBoolean(value: false)
    }

    func classifyElements(elements: [shared.DomElement]) async throws -> [KotlinBoolean] {
        // Always return false (not an ad) — conservative fallback
        return elements.map { _ in KotlinBoolean(value: false) }
    }
}

/// Factory to create the appropriate classifier based on device capability.
class LlmClassifierFactory {
    static func create() -> shared.IosLlmClassifierDelegate {
        if #available(iOS 26.0, *) {
            return AppleLlmClassifier()
        } else {
            return FallbackLlmClassifier()
        }
    }
}
