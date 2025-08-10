package com.voiceboard.android

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class WhisperManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WhisperManager"
        
        // Model information
        private val MODELS = mapOf(
            "tiny.en" to ModelInfo(
                "ggml-tiny.en-q5_1.bin",
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q5_1.bin",
                39 * 1024 * 1024L // ~39MB
            ),
            "base.en" to ModelInfo(
                "ggml-base.en-q5_1.bin", 
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q5_1.bin",
                148 * 1024 * 1024L // ~148MB
            ),
            "small.en" to ModelInfo(
                "ggml-small.en-q5_1.bin",
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en-q5_1.bin", 
                244 * 1024 * 1024L // ~244MB
            )
        )
    }
    
    data class ModelInfo(
        val fileName: String,
        val downloadUrl: String,
        val sizeBytes: Long
    )
    
    private var whisperNative: WhisperNative? = null
    private val modelsDir: File
    private var currentModel: String = "base.en" // Default model
    
    var isModelReady = false
        private set
    
    var modelLoadCallback: ((Boolean, String?) -> Unit)? = null
    var downloadProgressCallback: ((Int) -> Unit)? = null
    
    init {
        // Create models directory
        modelsDir = File(context.filesDir, "whisper_models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        
        // Initialize native library
        try {
            System.loadLibrary("whisper-engine")
            whisperNative = WhisperNative()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }
    
    fun loadModel(modelName: String = currentModel) {
        currentModel = modelName
        val modelInfo = MODELS[modelName] ?: return
        val modelFile = File(modelsDir, modelInfo.fileName)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Download model if not exists
                if (!modelFile.exists()) {
                    downloadModel(modelInfo, modelFile)
                }
                
                // Load model
                val success = whisperNative?.loadModel(modelFile.absolutePath) == true
                
                withContext(Dispatchers.Main) {
                    isModelReady = success
                    modelLoadCallback?.invoke(success, if (success) null else "Failed to load model")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model", e)
                withContext(Dispatchers.Main) {
                    isModelReady = false
                    modelLoadCallback?.invoke(false, e.message)
                }
            }
        }
    }
    
    private suspend fun downloadModel(modelInfo: ModelInfo, targetFile: File) {
        try {
            Log.i(TAG, "Downloading model: ${modelInfo.fileName}")
            
            val url = URL(modelInfo.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()
            
            val contentLength = connection.contentLength
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(targetFile)
            
            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                // Update progress
                val progress = if (contentLength > 0) {
                    ((totalBytesRead * 100) / contentLength).toInt()
                } else {
                    -1 // Indeterminate
                }
                
                withContext(Dispatchers.Main) {
                    downloadProgressCallback?.invoke(progress)
                }
            }
            
            outputStream.close()
            inputStream.close()
            
            Log.i(TAG, "Model downloaded successfully: ${targetFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model", e)
            // Clean up partial download
            if (targetFile.exists()) {
                targetFile.delete()
            }
            throw e
        }
    }
    
    suspend fun transcribe(audioData: FloatArray): String = withContext(Dispatchers.IO) {
        if (!isModelReady || whisperNative == null) {
            Log.w(TAG, "Model not ready or native library not loaded")
            return@withContext ""
        }
        
        try {
            // Preprocess audio if needed
            val processedAudio = preprocessAudio(audioData)
            
            // Call native transcription
            val result = whisperNative!!.transcribe(processedAudio)
            
            Log.d(TAG, "Transcription result: $result")
            return@withContext result ?: ""
            
        } catch (e: Exception) {
            Log.e(TAG, "Transcription error", e)
            return@withContext ""
        }
    }
    
    private fun preprocessAudio(audioData: FloatArray): FloatArray {
        // Basic audio preprocessing
        var processed = audioData
        
        // Apply pre-emphasis filter
        processed = applyPreEmphasis(processed)
        
        // Normalize
        processed = normalizeAudio(processed)
        
        // Ensure minimum length (Whisper typically needs at least 1 second)
        if (processed.size < 16000) { // 1 second at 16kHz
            processed = processed + FloatArray(16000 - processed.size) // Pad with zeros
        }
        
        return processed
    }
    
    private fun applyPreEmphasis(input: FloatArray, alpha: Float = 0.97f): FloatArray {
        if (input.isEmpty()) return input
        
        val output = FloatArray(input.size)
        output[0] = input[0]
        
        for (i in 1 until input.size) {
            output[i] = input[i] - alpha * input[i - 1]
        }
        
        return output
    }
    
    private fun normalizeAudio(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input
        
        val max = input.maxOfOrNull { kotlin.math.abs(it) } ?: 1.0f
        if (max == 0.0f) return input
        
        return input.map { it / max }.toFloatArray()
    }
    
    fun benchmarkDevice(): DeviceBenchmark {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        val processors = runtime.availableProcessors()
        
        // Simple heuristic for model recommendation
        val recommendedModel = when {
            maxMemory >= 2048 && processors >= 6 -> "small.en"
            maxMemory >= 1024 && processors >= 4 -> "base.en"
            else -> "tiny.en"
        }
        
        return DeviceBenchmark(
            maxMemoryMB = maxMemory,
            processorCount = processors,
            recommendedModel = recommendedModel,
            deviceModel = android.os.Build.MODEL
        )
    }
    
    fun getAvailableModels(): List<String> = MODELS.keys.toList()
    
    fun getModelInfo(modelName: String): ModelInfo? = MODELS[modelName]
    
    fun isModelDownloaded(modelName: String): Boolean {
        val modelInfo = MODELS[modelName] ?: return false
        val modelFile = File(modelsDir, modelInfo.fileName)
        return modelFile.exists() && modelFile.length() > 0
    }
    
    fun cleanup() {
        whisperNative?.cleanup()
        whisperNative = null
        isModelReady = false
    }
    
    data class DeviceBenchmark(
        val maxMemoryMB: Long,
        val processorCount: Int,
        val recommendedModel: String,
        val deviceModel: String
    )
}

// Native interface - implementation will be in C++
private class WhisperNative {
    external fun loadModel(modelPath: String): Boolean
    external fun transcribe(audioData: FloatArray): String?
    external fun cleanup()
    
    companion object {
        init {
            try {
                System.loadLibrary("whisper-engine")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("WhisperNative", "Failed to load native library", e)
            }
        }
    }
}