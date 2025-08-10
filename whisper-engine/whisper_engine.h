#ifndef WHISPER_ENGINE_H
#define WHISPER_ENGINE_H

#include "../shared/types.h"

#ifdef __cplusplus
extern "C" {
#endif

// Engine lifecycle
vb_status_t vb_engine_init(const vb_engine_config_t* config);
vb_status_t vb_engine_load_model(vb_model_type_t model_type, const char* model_path);
vb_status_t vb_engine_unload_model(void);
vb_status_t vb_engine_cleanup(void);

// Audio processing
vb_status_t vb_engine_start_transcription(vb_transcription_callback_t callback, 
                                          vb_error_callback_t error_callback,
                                          void* user_data);
vb_status_t vb_engine_process_audio(const vb_audio_buffer_t* audio_buffer);
vb_status_t vb_engine_stop_transcription(void);

// Utility functions
vb_device_capabilities_t vb_engine_benchmark_device(void);
bool vb_engine_is_model_loaded(void);
const char* vb_engine_get_version(void);
const char* vb_engine_status_to_string(vb_status_t status);

// Model management
vb_status_t vb_engine_download_model(vb_model_type_t model_type, const char* download_path);
bool vb_engine_is_model_available(vb_model_type_t model_type, const char* models_dir);
int64_t vb_engine_get_model_size(vb_model_type_t model_type);

#ifdef __cplusplus
}
#endif

#endif // WHISPER_ENGINE_H