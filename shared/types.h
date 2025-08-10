#ifndef VOICEBOARD_TYPES_H
#define VOICEBOARD_TYPES_H

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// ASR Engine Types
typedef enum {
    VB_MODEL_TINY_EN = 0,
    VB_MODEL_BASE_EN = 1,
    VB_MODEL_DISTIL_SMALL_EN = 2
} vb_model_type_t;

typedef enum {
    VB_STATUS_SUCCESS = 0,
    VB_STATUS_ERROR = -1,
    VB_STATUS_MODEL_NOT_LOADED = -2,
    VB_STATUS_AUDIO_ERROR = -3,
    VB_STATUS_INSUFFICIENT_MEMORY = -4
} vb_status_t;

typedef struct {
    float* samples;
    int32_t n_samples;
    int32_t sample_rate;
} vb_audio_buffer_t;

typedef struct {
    char* text;
    float confidence;
    int64_t timestamp_ms;
    bool is_final;
} vb_transcription_result_t;

typedef struct {
    vb_model_type_t model_type;
    bool use_gpu_acceleration;
    int32_t n_threads;
    bool enable_partial_results;
    int32_t partial_update_interval_ms;
} vb_engine_config_t;

// Device Benchmarking
typedef struct {
    float cpu_score;
    float memory_mb;
    bool has_neural_engine;  // iOS only
    bool has_gpu_acceleration;
    vb_model_type_t recommended_model;
} vb_device_capabilities_t;

// Callback function types
typedef void (*vb_transcription_callback_t)(vb_transcription_result_t* result, void* user_data);
typedef void (*vb_error_callback_t)(vb_status_t status, const char* message, void* user_data);

#ifdef __cplusplus
}
#endif

#endif // VOICEBOARD_TYPES_H