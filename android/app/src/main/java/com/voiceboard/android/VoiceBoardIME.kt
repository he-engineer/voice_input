package com.voiceboard.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class VoiceBoardIME : InputMethodService() {
    
    private lateinit var keyboardView: View
    private lateinit var micButton: ImageView
    private lateinit var statusText: TextView
    private lateinit var waveformView: WaveformView
    private lateinit var offlineIndicator: LinearLayout
    
    private var whisperManager: WhisperManager? = null
    private var audioRecorder: AudioRecorder? = null
    private var vibrator: Vibrator? = null
    
    private var isRecording = false
    private var recordingJob: Job? = null
    
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize components
        whisperManager = WhisperManager(this)
        audioRecorder = AudioRecorder(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        // Load Whisper model
        whisperManager?.loadModel()
    }
    
    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)
        setupViews()
        setupClickListeners()
        return keyboardView
    }
    
    private fun setupViews() {
        micButton = keyboardView.findViewById(R.id.mic_button)
        statusText = keyboardView.findViewById(R.id.status_text)
        waveformView = keyboardView.findViewById(R.id.waveform_view)
        offlineIndicator = keyboardView.findViewById(R.id.offline_indicator)
        
        // Initially hide waveform
        waveformView.visibility = View.GONE
        
        // Show offline indicator
        offlineIndicator.visibility = View.VISIBLE
        
        // Set initial status
        statusText.text = getString(R.string.tap_hold_to_dictate)
        
        // Check if model is ready
        updateModelStatus()
    }
    
    private fun setupClickListeners() {
        // Microphone button - push to talk
        micButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startDictation()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDictation()
                    true
                }
                else -> false
            }
        }
        
        // Utility buttons
        keyboardView.findViewById<ImageView>(R.id.delete_button).setOnClickListener {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
        
        keyboardView.findViewById<ImageView>(R.id.space_button).setOnClickListener {
            currentInputConnection?.commitText(" ", 1)
        }
        
        keyboardView.findViewById<ImageView>(R.id.enter_button).setOnClickListener {
            currentInputConnection?.commitText("\\n", 1)
        }
        
        keyboardView.findViewById<ImageView>(R.id.settings_button).setOnClickListener {
            // Open IME settings
            startActivity(android.content.Intent(this, IMESettingsActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
        
        keyboardView.findViewById<ImageView>(R.id.hide_button).setOnClickListener {
            requestHideSelf(0)
        }
    }
    
    private fun startDictation() {
        if (isRecording) return
        
        // Check permissions
        if (!hasRecordAudioPermission()) {
            showToast("Microphone permission required")
            return
        }
        
        // Check if model is ready
        if (whisperManager?.isModelReady != true) {
            showToast("Model not ready. Please wait...")
            return
        }
        
        isRecording = true
        
        // Update UI
        micButton.setImageResource(R.drawable.ic_mic_recording)
        micButton.setColorFilter(Color.RED)
        statusText.text = getString(R.string.listening_release_to_stop)
        waveformView.visibility = View.VISIBLE
        waveformView.startAnimation()
        
        // Haptic feedback
        vibrate(50)
        
        // Start recording
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val audioData = audioRecorder?.startRecording()
                
                // Process with Whisper on background thread
                audioData?.let { data ->
                    val transcription = whisperManager?.transcribe(data) ?: ""
                    
                    withContext(Dispatchers.Main) {
                        if (transcription.isNotEmpty()) {
                            insertTranscription(transcription)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Recording failed: ${e.message}")
                }
            }
        }
    }
    
    private fun stopDictation() {
        if (!isRecording) return
        
        isRecording = false
        recordingJob?.cancel()
        
        // Stop recording
        audioRecorder?.stopRecording()
        
        // Update UI
        micButton.setImageResource(R.drawable.ic_mic_normal)
        micButton.clearColorFilter()
        statusText.text = getString(R.string.processing)
        waveformView.stopAnimation()
        
        // Haptic feedback
        vibrate(30)
        
        // Hide waveform after delay
        handler.postDelayed({
            waveformView.visibility = View.GONE
            statusText.text = getString(R.string.tap_hold_to_dictate)
        }, 2000)
    }
    
    private fun insertTranscription(text: String) {
        currentInputConnection?.let { ic ->
            // Clean and format text
            val cleanText = text.trim()
                .replace("\\s+".toRegex(), " ") // Normalize whitespace
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            
            if (cleanText.isNotEmpty()) {
                ic.commitText(cleanText, 1)
                
                // Add space after if needed
                val currentText = ic.getTextBeforeCursor(1, 0)
                if (currentText?.lastOrNull()?.let { !it.isWhitespace() } == true) {
                    ic.commitText(" ", 1)
                }
            }
        }
    }
    
    private fun updateModelStatus() {
        val isReady = whisperManager?.isModelReady == true
        statusText.text = if (isReady) {
            getString(R.string.tap_hold_to_dictate)
        } else {
            getString(R.string.loading_model)
        }
        
        micButton.isEnabled = isReady
        micButton.alpha = if (isReady) 1.0f else 0.5f
    }
    
    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun vibrate(duration: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }
    
    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showError(message: String) {
        statusText.text = message
        statusText.setTextColor(Color.RED)
        
        handler.postDelayed({
            statusText.setTextColor(Color.BLACK)
            statusText.text = getString(R.string.tap_hold_to_dictate)
        }, 3000)
    }
    
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateModelStatus()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        recordingJob?.cancel()
        audioRecorder?.cleanup()
        whisperManager?.cleanup()
    }
}