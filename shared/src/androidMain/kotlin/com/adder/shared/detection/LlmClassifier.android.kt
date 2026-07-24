package com.adder.shared.detection

import android.util.Log
import com.adder.shared.model.DomElement
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "LlmClassifier"

/**
 * Android implementation of LlmClassifier using Gemini Nano via ML Kit GenAI Prompt API.
 */
actual class LlmClassifier actual constructor() {

    private var generativeModel: GenerativeModel? = null
    private var available: Boolean? = null

    actual suspend fun isAvailable(): Boolean {
        if (available != null) return available!!

        return withContext(Dispatchers.Main) {
            try {
                val model = Generation.getClient()
                val status = model.checkStatus()

                when (status) {
                    FeatureStatus.AVAILABLE -> {
                        generativeModel = model
                        available = true
                        Log.d(TAG, "Gemini Nano available and ready")
                        true
                    }
                    FeatureStatus.DOWNLOADABLE -> {
                        Log.d(TAG, "Gemini Nano downloadable, starting download...")
                        model.download().collect { downloadStatus ->
                            Log.d(TAG, "Download status: $downloadStatus")
                        }
                        // After download, check again
                        generativeModel = model
                        available = true
                        true
                    }
                    else -> {
                        Log.w(TAG, "Gemini Nano not available: status=$status")
                        available = false
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking LLM availability", e)
                available = false
                false
            }
        }
    }

    actual suspend fun classifyElements(elements: List<DomElement>): List<Boolean> {
        if (elements.isEmpty()) return emptyList()

        val model = generativeModel
        if (model == null) {
            Log.w(TAG, "Model not available, returning all-false classifications")
            return List(elements.size) { false }
        }

        return withContext(Dispatchers.Main) {
            try {
                val prompt = PromptBuilder.buildBatchClassificationPrompt(elements)
                Log.d(TAG, "Sending ${elements.size} elements to Gemini Nano (prompt: ${prompt.length} chars)")

                val response = model.generateContent(prompt)
                val responseText = response.candidates.firstOrNull()?.text ?: ""
                Log.d(TAG, "LLM response: $responseText")

                if (responseText.isNotEmpty()) {
                    PromptBuilder.parseBatchResponse(responseText, elements.size)
                } else {
                    List(elements.size) { false }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during LLM classification", e)
                List(elements.size) { false }
            }
        }
    }
}
