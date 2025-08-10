#!/bin/bash

# VoiceBoard Whisper Setup Script
# Downloads and builds whisper.cpp for iOS and Android

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WHISPER_DIR="$PROJECT_ROOT/whisper-cpp"
MODELS_DIR="$PROJECT_ROOT/models"

echo "🎙️  Setting up Whisper.cpp for VoiceBoard..."

# Create directories
mkdir -p "$MODELS_DIR"

# Clone whisper.cpp if not exists
if [ ! -d "$WHISPER_DIR" ]; then
    echo "📥 Cloning whisper.cpp..."
    git clone https://github.com/ggerganov/whisper.cpp.git "$WHISPER_DIR"
    cd "$WHISPER_DIR"
    git checkout v1.5.4  # Use stable version
else
    echo "📁 Using existing whisper.cpp..."
    cd "$WHISPER_DIR"
fi

# Build for macOS/iOS (if on macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "🍎 Building whisper.cpp for iOS..."
    
    # Build iOS library
    mkdir -p build-ios
    cd build-ios
    
    cmake .. \
        -DCMAKE_TOOLCHAIN_FILE=../cmake/ios.toolchain.cmake \
        -DPLATFORM=OS64 \
        -DDEPLOYMENT_TARGET=15.0 \
        -DCMAKE_BUILD_TYPE=Release \
        -DWHISPER_NO_AVX=ON \
        -DWHISPER_NO_AVX2=ON \
        -DWHISPER_NO_FMA=ON \
        -DWHISPER_NO_F16C=ON \
        -DWHISPER_METAL=ON
        
    make -j$(sysctl -n hw.ncpu)
    
    # Copy library to iOS project
    cp libwhisper.a "$PROJECT_ROOT/ios/VoiceBoard/libwhisper.a"
    
    cd ..
    
    echo "✅ iOS whisper.cpp build complete"
fi

# Build for Android
echo "🤖 Building whisper.cpp for Android..."

# Set Android NDK path (adjust as needed)
if [ -z "$ANDROID_NDK_ROOT" ]; then
    ANDROID_NDK_ROOT="$HOME/Library/Android/sdk/ndk/25.1.8937393"
fi

if [ ! -d "$ANDROID_NDK_ROOT" ]; then
    echo "❌ Android NDK not found at $ANDROID_NDK_ROOT"
    echo "Please set ANDROID_NDK_ROOT environment variable"
    exit 1
fi

# Build for multiple architectures
for ARCH in arm64-v8a armeabi-v7a x86_64; do
    echo "🔨 Building for $ARCH..."
    
    BUILD_DIR="build-android-$ARCH"
    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"
    
    case $ARCH in
        arm64-v8a)
            ANDROID_ABI="arm64-v8a"
            CMAKE_ANDROID_ARCH_ABI="arm64-v8a"
            ;;
        armeabi-v7a)
            ANDROID_ABI="armeabi-v7a"
            CMAKE_ANDROID_ARCH_ABI="armeabi-v7a"
            ;;
        x86_64)
            ANDROID_ABI="x86_64"
            CMAKE_ANDROID_ARCH_ABI="x86_64"
            ;;
    esac
    
    cmake .. \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ANDROID_ABI" \
        -DANDROID_PLATFORM=android-24 \
        -DCMAKE_BUILD_TYPE=Release \
        -DWHISPER_NO_AVX=ON \
        -DWHISPER_NO_AVX2=ON \
        -DWHISPER_NO_FMA=ON \
        -DWHISPER_NO_F16C=ON \
        -DBUILD_SHARED_LIBS=ON
        
    make -j$(nproc 2>/dev/null || echo 4)
    
    # Copy library to Android project
    mkdir -p "$PROJECT_ROOT/android/app/src/main/jniLibs/$ARCH"
    cp libwhisper.so "$PROJECT_ROOT/android/app/src/main/jniLibs/$ARCH/"
    
    cd ..
done

echo "✅ Android whisper.cpp build complete"

# Download sample models (quantized versions)
echo "📦 Downloading sample models..."

cd "$MODELS_DIR"

# Download tiny model (for testing)
if [ ! -f "ggml-tiny.en-q5_1.bin" ]; then
    echo "⬇️  Downloading tiny.en model..."
    curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q5_1.bin" \
         -o "ggml-tiny.en-q5_1.bin"
fi

# Download base model
if [ ! -f "ggml-base.en-q5_1.bin" ]; then
    echo "⬇️  Downloading base.en model..."
    curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q5_1.bin" \
         -o "ggml-base.en-q5_1.bin"
fi

echo "✅ Model download complete"

# Create Android JNI wrapper
echo "🔧 Creating JNI wrapper..."

mkdir -p "$PROJECT_ROOT/android/app/src/main/cpp"
cat > "$PROJECT_ROOT/android/app/src/main/cpp/whisper_jni.cpp" << 'EOF'
#include <jni.h>
#include <string>
#include <vector>
#include "whisper.h"
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
EOF

# Create CMakeLists.txt for Android
cat > "$PROJECT_ROOT/android/app/src/main/cpp/CMakeLists.txt" << 'EOF'
cmake_minimum_required(VERSION 3.22.1)

project("whisper-engine")

# Include whisper.cpp
set(WHISPER_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../whisper-cpp")

# Add whisper library
add_subdirectory(${WHISPER_ROOT} whisper)

# Create JNI wrapper library
add_library(${CMAKE_PROJECT_NAME} SHARED whisper_jni.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}
    whisper
    android
    log
)
EOF

echo "🎉 Whisper.cpp setup complete!"
echo ""
echo "Next steps:"
echo "1. Build iOS project: Open ios/VoiceBoard.xcodeproj in Xcode"
echo "2. Build Android project: cd android && ./gradlew build"
echo "3. Test with sample models in models/ directory"
echo ""
echo "Note: Make sure to add libwhisper.a to your iOS project's Link Binary With Libraries"