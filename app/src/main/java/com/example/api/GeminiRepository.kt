package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class GeminiRepository {

    suspend fun extractTextAndLanguageFromPage(pageBitmap: Bitmap): GeminiExtractionResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY_DEFAULT_VALUE") {
            return@withContext GeminiExtractionResult(
                language = "English",
                extractedText = "Error: Gemini API key is not configured. Please add your key into the Secrets Panel in AI Studio."
            )
        }

        val base64Image = pageBitmap.toBase64()

        // Craft a specific prompt forcing Gemini to return structured JSON
        val systemPrompt = "You are an extreme high-accuracy document reading visual OCR engine. " +
                "You assist visually impaired individuals. Extract all readable text on this page (even if in Sinhala script or English). " +
                "Correct any spacing and reading order so it flows organically. " +
                "Then, identify if the page's primary language is 'English' or 'Sinhala'. " +
                "You MUST return EXACTLY a JSON structure matching this model: " +
                "{\"language\": \"Sinhala\" or \"English\", \"extractedText\": \"The extracted page content here\"}. " +
                "Do not include any markdown format tags like ```json or trailing text. Return only the pure JSON."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = "Please read this document page image, identify the language and extract the text."),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val firstText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Log.d("GeminiRepository", "Raw Response: $firstText")

            if (firstText != null) {
                val cleanedResult = cleanOcrResult(firstText)
                return@withContext cleanedResult
            } else {
                return@withContext GeminiExtractionResult(
                    language = "English",
                    extractedText = "Error: Gemini returned an empty response. Let's try analyzing the page again."
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiRepository", "API Exception occurred", e)
            return@withContext GeminiExtractionResult(
                language = "English",
                extractedText = "Error analyzing document: ${e.localizedMessage ?: "Unknown network error. Please verify your internet connection."}"
            )
        }
    }

    fun cleanOcrResult(rawText: String, defaultLang: String = "English"): GeminiExtractionResult {
        var detectedLang = defaultLang
        var cleanText = rawText.trim()

        // 1. Check if the string is formatted as JSON
        if (cleanText.startsWith("{") && cleanText.endsWith("}")) {
            try {
                val jsonAdapter = RetrofitClient.moshiInstance.adapter(GeminiExtractionResult::class.java)
                val result = jsonAdapter.fromJson(cleanText)
                if (result != null) {
                    val contentCleaned = cleanOcrResult(result.extractedText, result.language)
                    return GeminiExtractionResult(
                        language = contentCleaned.language,
                        extractedText = contentCleaned.extractedText
                    )
                }
            } catch (e: Exception) {
                // Not valid JSON, proceed to text parsing
            }
        }

        // 2. Try parsing "PDF Language" or "Language" with key-value format
        val langPattern = Regex("(?i)(?:PDF\\s*Language|Language)\\s*:\\s*(English|Sinhala)", RegexOption.MULTILINE)
        val langMatch = langPattern.find(rawText)
        if (langMatch != null) {
            detectedLang = langMatch.groupValues[1]
        } else {
            // Check for Sinhala unicode presence as a fallback
            val hasSinhalaUnicode = rawText.any { it.code in 0x0D80..0x0DFF }
            if (hasSinhalaUnicode) {
                detectedLang = "Sinhala"
            }
        }

        // 3. Try parsing "Extracted" or "Extracted Text"
        val extractedPattern = Regex("(?i)(?:Extracted\\s*Text|Extracted)\\s*:\\s*([\\s\\S]+)")
        val extractedMatch = extractedPattern.find(rawText)
        if (extractedMatch != null) {
            cleanText = extractedMatch.groupValues[1].trim()
        } else {
            // Just strip out any lines starting with those labels to keep actual original text only!
            val lines = rawText.lines().filter { line ->
                val l = line.trim()
                !(l.startsWith("PDF Language", ignoreCase = true) || 
                  l.startsWith("Language:", ignoreCase = true) ||
                  l.startsWith("Extracted:", ignoreCase = true) ||
                  l.startsWith("Extracted Text:", ignoreCase = true))
            }
            cleanText = lines.joinToString("\n").trim()
        }

        return GeminiExtractionResult(
            language = detectedLang,
            extractedText = cleanText
        )
    }

    suspend fun askAiGeneralOrWithContext(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY_DEFAULT_VALUE") {
            return@withContext "Error: Gemini API key is not configured. Please add your key into the Secrets Panel in AI Studio."
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.7f
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = "You are Vihanga Madumal AI, a helpful, accessible reading voice assistant. Whenever the user asks you 'What is your name?', you MUST respond with 'I am Vihanga Madumal AI'."))
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from AI."
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Chat query failed", e)
            "Error: ${e.localizedMessage ?: "Failed to connect to AI engine."}"
        }
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
