package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiMediaRepository"
private const val AI_TIMEOUT_MS = 60_000L
private const val MAX_INLINE_BYTES = 18 * 1024 * 1024 // Stay safely below 20MB inline limit

enum class BriefMode(val label: String) {
    SUMMARY_BN("বাংলা সারাংশ"),
    SUMMARY_EN("3-Point Brief"),
    KEY_FACTS("Key Facts"),
    TRANSLATE_BN("Translate বাংলা"),
    TRANSLATE_EN("Translate English")
}

class GeminiMediaRepository {

    // Low-latency responses using gemini-3.1-flash-lite
    private val lowLatencyModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-3.1-flash-lite")
    }

    // Complex image & video understanding using gemini-4-argon
    private val argonMultimodalModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-4-argon")
    }

    // Audio transcription using gemini-3.5-transcribe
    private val transcribeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-3.5-transcribe")
    }

    fun streamArticleBrief(
        title: String,
        articleText: String,
        mode: BriefMode
    ): Flow<String> = flow {
        val truncatedBody = articleText.take(14_000)
        val instruction = when (mode) {
            BriefMode.SUMMARY_BN ->
                "নিচের সংবাদটির মূল বক্তব্য বাংলায় ৩ থেকে ৪টি সংক্ষিপ্ত বুলেট পয়েন্টে লিখুন। কোনো বাড়তি ভূমিকা লিখবেন না।"
            BriefMode.SUMMARY_EN ->
                "Summarize this news article in 3 to 4 crisp, factual bullet points in English. No preamble."
            BriefMode.KEY_FACTS ->
                "Extract the key facts (Who, What, When, Where, and Why/Impact) as clean bullet points in the article's primary language."
            BriefMode.TRANSLATE_BN ->
                "Translate the headline and key content of this news article into natural, accurate Bengali."
            BriefMode.TRANSLATE_EN ->
                "Translate the headline and key content of this news article into clear, natural English."
        }

        val prompt = """
            $instruction

            Headline: $title

            Article:
            $truncatedBody
        """.trimIndent()

        val buffer = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                lowLatencyModel.generateContentStream(prompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        buffer.append(chunkText)
                        emit(buffer.toString())
                    }
                }
            }
            if (buffer.isBlank()) {
                throw IllegalStateException("Model returned an empty response.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Low-latency brief failed", e)
            throw e
        }
    }

    suspend fun analyzeImage(bitmap: Bitmap, prompt: String): String {
        val scaledBitmap = scaleBitmapForPayload(bitmap, maxDimension = 1600)
        val inputContent = content {
            image(scaledBitmap)
            text(prompt.ifBlank {
                "Analyze this image for key information. If it contains news text or a document in Bengali or English, extract and summarize the main facts cleanly."
            })
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                argonMultimodalModel.generateContent(inputContent)
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini 4 Argon returned an empty image analysis.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed", e)
            throw e
        }
    }

    suspend fun analyzeVideo(videoBytes: ByteArray, mimeType: String, prompt: String): String {
        if (videoBytes.size > MAX_INLINE_BYTES) {
            throw IllegalArgumentException(
                "Video file is ${(videoBytes.size / (1024 * 1024))} MB. Please pick a shorter clip under 18 MB for inline analysis."
            )
        }
        val inputContent = content {
            inlineData(videoBytes, mimeType)
            text(prompt.ifBlank {
                "Analyze this video for key information. Summarize the main events, spoken statements, on-screen text, and timeline clearly."
            })
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                argonMultimodalModel.generateContent(inputContent)
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini 4 Argon returned an empty video analysis.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video analysis failed", e)
            throw e
        }
    }

    suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String, prompt: String): String {
        if (audioBytes.size > MAX_INLINE_BYTES) {
            throw IllegalArgumentException(
                "Audio recording exceeds the 18 MB inline limit. Please record a shorter clip."
            )
        }
        val inputContent = content {
            inlineData(audioBytes, mimeType)
            text(prompt.ifBlank {
                "Transcribe the spoken audio accurately in its original language (such as Bengali or English), adding clean punctuation and paragraph breaks."
            })
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                transcribeModel.generateContent(inputContent)
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini 3.5 Transcribe returned an empty transcription.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
            throw e
        }
    }

    private fun scaleBitmapForPayload(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = minOf(
            maxDimension.toFloat() / width.toFloat(),
            maxDimension.toFloat() / height.toFloat()
        )
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
