#include <jni.h>
#include <string>
#include <vector>
#include "../../../../../../../whisper-cpp/whisper.h"
#include <android/log.h>

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static whisper_context* g_context = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_voiceboard_android_WhisperNative_loadModel(JNIEnv *env, jobject thiz, jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    
    if (g_context) {
        whisper_free(g_context);
        g_context = nullptr;
    }
    
    g_context = whisper_init_from_file(path);
    
    env->ReleaseStringUTFChars(model_path, path);
    
    if (g_context == nullptr) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }
    
    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_voiceboard_android_WhisperNative_transcribe(JNIEnv *env, jobject thiz, jfloatArray audio_data) {
    if (g_context == nullptr) {
        LOGE("Model not loaded");
        return nullptr;
    }
    
    jsize length = env->GetArrayLength(audio_data);
    jfloat* samples = env->GetFloatArrayElements(audio_data, nullptr);
    
    // Convert to vector
    std::vector<float> audio(samples, samples + length);
    
    // Set up parameters
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = 4;
    params.offset_ms = 0;
    params.duration_ms = 0;
    params.translate = false;
    params.no_context = false;
    params.single_segment = false;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    
    // Run transcription
    int result = whisper_full(g_context, params, audio.data(), length);
    
    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    
    if (result != 0) {
        LOGE("Transcription failed: %d", result);
        return nullptr;
    }
    
    // Get results
    std::string transcription;
    const int n_segments = whisper_full_n_segments(g_context);
    
    for (int i = 0; i < n_segments; ++i) {
        const char* text = whisper_full_get_segment_text(g_context, i);
        transcription += text;
    }
    
    return env->NewStringUTF(transcription.c_str());
}

JNIEXPORT void JNICALL
Java_com_voiceboard_android_WhisperNative_cleanup(JNIEnv *env, jobject thiz) {
    if (g_context) {
        whisper_free(g_context);
        g_context = nullptr;
        LOGI("Model cleanup complete");
    }
}

}