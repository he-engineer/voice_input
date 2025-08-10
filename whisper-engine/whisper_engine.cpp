#include "whisper_engine.h"
#include "whisper.h"
#include <iostream>
#include <memory>
#include <thread>
#include <mutex>
#include <atomic>
#include <queue>

// Internal state
struct WhisperEngineState {
    whisper_context* ctx = nullptr;
    vb_engine_config_t config = {};
    vb_transcription_callback_t transcription_callback = nullptr;
    vb_error_callback_t error_callback = nullptr;
    void* user_data = nullptr;
    
    std::atomic<bool> is_processing{false};
    std::mutex audio_mutex;
    std::queue<vb_audio_buffer_t> audio_queue;
    std::unique_ptr<std::thread> processing_thread;
};

static WhisperEngineState g_engine_state;

// Helper functions
const char* model_type_to_filename(vb_model_type_t model_type) {
    switch (model_type) {
        case VB_MODEL_TINY_EN: return "ggml-tiny.en-q5_1.bin";
        case VB_MODEL_BASE_EN: return "ggml-base.en-q5_1.bin";
        case VB_MODEL_DISTIL_SMALL_EN: return "ggml-distil-small.en-q5_1.bin";
        default: return nullptr;
    }
}

void processing_thread_func() {
    while (g_engine_state.is_processing) {
        vb_audio_buffer_t audio_buffer = {};
        
        // Get audio from queue
        {
            std::lock_guard<std::mutex> lock(g_engine_state.audio_mutex);
            if (g_engine_state.audio_queue.empty()) {
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
                continue;
            }
            audio_buffer = g_engine_state.audio_queue.front();
            g_engine_state.audio_queue.pop();
        }
        
        if (!g_engine_state.ctx) {
            if (g_engine_state.error_callback) {
                g_engine_state.error_callback(VB_STATUS_MODEL_NOT_LOADED, 
                    "Model not loaded", g_engine_state.user_data);
            }
            continue;
        }
        
        // Process audio with Whisper
        whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        wparams.n_threads = g_engine_state.config.n_threads;
        wparams.offset_ms = 0;
        wparams.duration_ms = 0;
        wparams.translate = false;
        wparams.no_context = false;
        wparams.single_segment = false;
        wparams.print_realtime = false;
        wparams.print_progress = false;
        wparams.print_timestamps = false;
        wparams.print_special = false;
        
        int result = whisper_full(g_engine_state.ctx, wparams, 
                                  audio_buffer.samples, audio_buffer.n_samples);
        
        if (result != 0) {
            if (g_engine_state.error_callback) {
                g_engine_state.error_callback(VB_STATUS_ERROR, 
                    "Whisper processing failed", g_engine_state.user_data);
            }
            continue;
        }
        
        // Extract results
        const int n_segments = whisper_full_n_segments(g_engine_state.ctx);
        for (int i = 0; i < n_segments; ++i) {
            const char* text = whisper_full_get_segment_text(g_engine_state.ctx, i);
            const int64_t t0 = whisper_full_get_segment_t0(g_engine_state.ctx, i);
            const int64_t t1 = whisper_full_get_segment_t1(g_engine_state.ctx, i);
            
            vb_transcription_result_t result = {};
            result.text = const_cast<char*>(text);
            result.confidence = 1.0f; // Whisper doesn't provide confidence scores
            result.timestamp_ms = t0 * 10; // Convert from centiseconds to milliseconds
            result.is_final = (i == n_segments - 1);
            
            if (g_engine_state.transcription_callback) {
                g_engine_state.transcription_callback(&result, g_engine_state.user_data);
            }
        }
        
        // Cleanup audio buffer
        delete[] audio_buffer.samples;
    }
}

// Public API implementation
vb_status_t vb_engine_init(const vb_engine_config_t* config) {
    if (!config) {
        return VB_STATUS_ERROR;
    }
    
    g_engine_state.config = *config;
    
    // Initialize whisper
    whisper_log_set([](ggml_log_level level, const char* text, void* user_data) {
        // Suppress whisper logs for cleaner output
    }, nullptr);
    
    return VB_STATUS_SUCCESS;
}

vb_status_t vb_engine_load_model(vb_model_type_t model_type, const char* model_path) {
    if (g_engine_state.ctx) {
        whisper_free(g_engine_state.ctx);
        g_engine_state.ctx = nullptr;
    }
    
    g_engine_state.ctx = whisper_init_from_file(model_path);
    if (!g_engine_state.ctx) {
        return VB_STATUS_ERROR;
    }
    
    return VB_STATUS_SUCCESS;
}

vb_status_t vb_engine_unload_model(void) {
    if (g_engine_state.ctx) {
        whisper_free(g_engine_state.ctx);
        g_engine_state.ctx = nullptr;
    }
    return VB_STATUS_SUCCESS;
}

vb_status_t vb_engine_cleanup(void) {
    vb_engine_stop_transcription();
    vb_engine_unload_model();
    return VB_STATUS_SUCCESS;
}

vb_status_t vb_engine_start_transcription(vb_transcription_callback_t callback, 
                                          vb_error_callback_t error_callback,
                                          void* user_data) {
    if (g_engine_state.is_processing) {
        return VB_STATUS_ERROR;
    }
    
    g_engine_state.transcription_callback = callback;
    g_engine_state.error_callback = error_callback;
    g_engine_state.user_data = user_data;
    g_engine_state.is_processing = true;
    
    g_engine_state.processing_thread = std::make_unique<std::thread>(processing_thread_func);
    
    return VB_STATUS_SUCCESS;
}

vb_status_t vb_engine_process_audio(const vb_audio_buffer_t* audio_buffer) {
    if (!g_engine_state.is_processing) {
        return VB_STATUS_ERROR;
    }
    
    // Copy audio buffer for thread safety
    vb_audio_buffer_t buffer_copy = {};
    buffer_copy.n_samples = audio_buffer->n_samples;
    buffer_copy.sample_rate = audio_buffer->sample_rate;
    buffer_copy.samples = new float[audio_buffer->n_samples];
    memcpy(buffer_copy.samples, audio_buffer->samples, 
           audio_buffer->n_samples * sizeof(float));
    
    {
        std::lock_guard<std::mutex> lock(g_engine_state.audio_mutex);
        g_engine_state.audio_queue.push(buffer_copy);
    }
    
    return VB_STATUS_SUCCESS;
}

vb_status_t vb_engine_stop_transcription(void) {
    g_engine_state.is_processing = false;
    
    if (g_engine_state.processing_thread && g_engine_state.processing_thread->joinable()) {
        g_engine_state.processing_thread->join();
    }
    g_engine_state.processing_thread.reset();
    
    // Clear audio queue
    {
        std::lock_guard<std::mutex> lock(g_engine_state.audio_mutex);
        while (!g_engine_state.audio_queue.empty()) {
            auto& buffer = g_engine_state.audio_queue.front();
            delete[] buffer.samples;
            g_engine_state.audio_queue.pop();
        }
    }
    
    return VB_STATUS_SUCCESS;
}

bool vb_engine_is_model_loaded(void) {
    return g_engine_state.ctx != nullptr;
}

const char* vb_engine_get_version(void) {
    return "VoiceBoard Engine 1.0.0";
}

const char* vb_engine_status_to_string(vb_status_t status) {
    switch (status) {
        case VB_STATUS_SUCCESS: return "Success";
        case VB_STATUS_ERROR: return "Error";
        case VB_STATUS_MODEL_NOT_LOADED: return "Model not loaded";
        case VB_STATUS_AUDIO_ERROR: return "Audio error";
        case VB_STATUS_INSUFFICIENT_MEMORY: return "Insufficient memory";
        default: return "Unknown status";
    }
}

// Placeholder implementations - will be expanded
vb_device_capabilities_t vb_engine_benchmark_device(void) {
    vb_device_capabilities_t caps = {};
    caps.cpu_score = 1000.0f; // Placeholder
    caps.memory_mb = 4096.0f;
    caps.has_neural_engine = false;
    caps.has_gpu_acceleration = false;
    caps.recommended_model = VB_MODEL_BASE_EN;
    return caps;
}

vb_status_t vb_engine_download_model(vb_model_type_t model_type, const char* download_path) {
    // Placeholder - in real implementation would download from server
    return VB_STATUS_SUCCESS;
}

bool vb_engine_is_model_available(vb_model_type_t model_type, const char* models_dir) {
    // Placeholder - would check if model file exists
    return false;
}

int64_t vb_engine_get_model_size(vb_model_type_t model_type) {
    switch (model_type) {
        case VB_MODEL_TINY_EN: return 39 * 1024 * 1024; // ~39MB
        case VB_MODEL_BASE_EN: return 148 * 1024 * 1024; // ~148MB  
        case VB_MODEL_DISTIL_SMALL_EN: return 244 * 1024 * 1024; // ~244MB
        default: return 0;
    }
}