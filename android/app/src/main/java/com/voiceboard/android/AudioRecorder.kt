package com.voiceboard.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.math.sqrt

class AudioRecorder(private val context: Context) {
    
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }
    
    private var audioRecord: AudioRecord? = null
    private var bufferSize = 0
    private var isRecording = false
    private var recordingData = mutableListOf<Float>()
    
    var audioLevelCallback: ((Float) -> Unit)? = null
    
    init {
        calculateBufferSize()
    }
    
    private fun calculateBufferSize() {
        bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR
    }
    
    suspend fun startRecording(): FloatArray? = withContext(Dispatchers.IO) {
        if (!checkPermissions()) {
            return@withContext null
        }
        
        try {
            recordingData.clear()
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord not initialized")
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            val buffer = ShortArray(bufferSize / 2) // 16-bit samples
            
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // Convert to float and normalize
                    val floatBuffer = FloatArray(bytesRead)
                    for (i in 0 until bytesRead) {
                        floatBuffer[i] = buffer[i] / 32768.0f // Normalize 16-bit to [-1, 1]
                    }
                    
                    recordingData.addAll(floatBuffer.toList())
                    
                    // Calculate audio level for visualization
                    val rms = calculateRMS(floatBuffer)
                    withContext(Dispatchers.Main) {
                        audioLevelCallback?.invoke(rms)
                    }
                }
                
                // Yield to prevent blocking
                yield()
            }
            
            return@withContext recordingData.toFloatArray()
            
        } catch (e: SecurityException) {
            throw IllegalStateException("Microphone permission not granted", e)
        } catch (e: IllegalStateException) {
            throw IllegalStateException("Failed to initialize AudioRecord", e)
        } catch (e: Exception) {
            throw RuntimeException("Recording failed", e)
        }
    }
    
    fun stopRecording() {
        isRecording = false
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Log error but don't crash
            e.printStackTrace()
        } finally {
            audioRecord = null
        }
    }
    
    private fun calculateRMS(buffer: FloatArray): Float {
        if (buffer.isEmpty()) return 0f
        
        var sum = 0.0
        for (sample in buffer) {
            sum += sample * sample
        }
        
        return sqrt(sum / buffer.size).toFloat()
    }
    
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun cleanup() {
        stopRecording()
    }
    
    // Utility functions for audio processing
    fun convertSampleRate(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return input
        
        val ratio = fromRate.toDouble() / toRate
        val outputLength = (input.size / ratio).toInt()
        val output = FloatArray(outputLength)
        
        for (i in output.indices) {
            val sourceIndex = (i * ratio).toInt()
            if (sourceIndex < input.size) {
                output[i] = input[sourceIndex]
            }
        }
        
        return output
    }
    
    fun applyPreEmphasis(input: FloatArray, alpha: Float = 0.97f): FloatArray {
        if (input.isEmpty()) return input
        
        val output = FloatArray(input.size)
        output[0] = input[0]
        
        for (i in 1 until input.size) {
            output[i] = input[i] - alpha * input[i - 1]
        }
        
        return output
    }
    
    fun normalizeAudio(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input
        
        val max = input.maxOfOrNull { kotlin.math.abs(it) } ?: 1.0f
        if (max == 0.0f) return input
        
        return input.map { it / max }.toFloatArray()
    }
}