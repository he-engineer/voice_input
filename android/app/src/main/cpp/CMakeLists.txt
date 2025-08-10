cmake_minimum_required(VERSION 3.22.1)

project("whisper-engine")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find path to whisper-cpp
get_filename_component(WHISPER_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../whisper-cpp" ABSOLUTE)

message(STATUS "Whisper root: ${WHISPER_ROOT}")

# Add whisper source files
set(WHISPER_SOURCES
    ${WHISPER_ROOT}/whisper.cpp
    ${WHISPER_ROOT}/ggml.c
    ${WHISPER_ROOT}/ggml-alloc.c
    ${WHISPER_ROOT}/ggml-backend.c
    ${WHISPER_ROOT}/ggml-quants.c
)

# Include directories
include_directories(${WHISPER_ROOT})

# Compiler flags for optimization
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -DNDEBUG -DANDROID -fPIC")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG -DANDROID -fPIC")

# Add target-specific optimizations
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8-a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv7-a -mfloat-abi=softfp -mfpu=neon")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv7-a -mfloat-abi=softfp -mfpu=neon")
endif()

# Disable features not needed on Android
add_definitions(-DGGML_USE_ACCELERATE=0)
add_definitions(-DGGML_USE_METAL=0)

# Create whisper-engine library
add_library(${CMAKE_PROJECT_NAME} SHARED
    whisper_jni.cpp
    ${WHISPER_SOURCES}
)

# Link libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
    m
)

# Set library properties
set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES
    ANDROID_ARM_MODE arm
)

# Add compile definitions
target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
    ANDROID
    __ANDROID__
)