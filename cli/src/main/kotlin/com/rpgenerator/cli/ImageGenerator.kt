package com.rpgenerator.cli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Interface for generating images from text prompts.
 */
interface ImageGenerator {
    /**
     * Generate an image from a text prompt.
     * @param prompt The text description of the image
     * @param outputPath Where to save the generated image
     * @return True if successful, false otherwise
     */
    suspend fun generateImage(prompt: String, outputPath: File): Boolean

    /**
     * Check if the image generation service is available and healthy.
     */
    suspend fun isAvailable(): Boolean
}

/**
 * Stable Diffusion image generator that connects to a local Python service.
 */
class StableDiffusionGenerator(
    private val serviceUrl: String = "http://127.0.0.1:5050"
) : ImageGenerator {

    @Serializable
    data class GenerateRequest(
        val prompt: String,
        val output_path: String,
        val steps: Int = 20,
        val width: Int = 512,
        val height: Int = 512,
        val guidance_scale: Double = 7.5,
        val negative_prompt: String = "blurry, low quality, distorted, ugly, text, watermark"
    )

    @Serializable
    data class GenerateResponse(
        val path: String,
        val prompt: String
    )

    @Serializable
    data class ErrorResponse(
        val error: String
    )

    override suspend fun generateImage(prompt: String, outputPath: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Enhance prompt for fantasy/RPG scenes
                val enhancedPrompt = enhancePrompt(prompt)

                // Create output directory if needed
                outputPath.parentFile?.mkdirs()

                val url = URL("$serviceUrl/generate_file")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 60000  // 60 seconds
                    readTimeout = 120000     // 2 minutes (generation time)
                }

                val json = Json.encodeToString(
                    GenerateRequest(
                        prompt = enhancedPrompt,
                        output_path = outputPath.absolutePath
                    )
                )

                connection.outputStream.use { os ->
                    os.write(json.toByteArray())
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val result = Json.decodeFromString<GenerateResponse>(response)
                    File(result.path).exists()
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    System.err.println("Image generation failed: $error")
                    false
                }
            } catch (e: Exception) {
                System.err.println("Error generating image: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    override suspend fun isAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$serviceUrl/health")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 2000
                    readTimeout = 2000
                }

                connection.responseCode == 200
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Enhance prompts with style guidance for better fantasy/RPG scenes.
     */
    private fun enhancePrompt(prompt: String): String {
        // Add style keywords if not already present
        val lowerPrompt = prompt.lowercase()

        val styleKeywords = mutableListOf<String>()

        if (!lowerPrompt.contains("fantasy") && !lowerPrompt.contains("digital art")) {
            styleKeywords.add("fantasy art")
        }

        if (!lowerPrompt.contains("detailed") && !lowerPrompt.contains("intricate")) {
            styleKeywords.add("detailed")
        }

        if (!lowerPrompt.contains("atmospheric")) {
            styleKeywords.add("atmospheric lighting")
        }

        return if (styleKeywords.isNotEmpty()) {
            "$prompt, ${styleKeywords.joinToString(", ")}"
        } else {
            prompt
        }
    }
}

/**
 * Null/no-op image generator for when the service is disabled.
 */
class NoOpImageGenerator : ImageGenerator {
    override suspend fun generateImage(prompt: String, outputPath: File): Boolean {
        return false
    }

    override suspend fun isAvailable(): Boolean {
        return false
    }
}
