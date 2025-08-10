# VoiceBoard - Offline Dictation Input Method

## Project Overview
VoiceBoard is a system-wide, offline dictation keyboard for iOS and Android that provides private, on-device speech recognition using Whisper models.

## Key Technical Requirements
- **Offline ASR**: 100% on-device processing using whisper.cpp
- **Cross-platform**: iOS keyboard extension + Android IME
- **Performance**: <500ms start-to-first-char, partial updates every 300-500ms
- **Privacy**: No network calls during dictation, all data stays on device
- **Models**: Quantized Whisper models (tiny.en, base.en, distil-small.en)

## Development Commands

### Testing
```bash
# iOS testing
cd ios && xcodebuild test -scheme VoiceBoard -destination 'platform=iOS Simulator,name=iPhone 14'

# Android testing  
cd android && ./gradlew test

# Run linting
npm run lint
```

### Build Commands
```bash
# iOS build
cd ios && xcodebuild -scheme VoiceBoard -configuration Release

# Android build
cd android && ./gradlew assembleRelease

# Model preparation
python scripts/prepare_models.py --quantize
```

## Architecture Notes

### iOS Implementation
- **Host App**: Main app with mic permissions, ASR processing
- **Keyboard Extension**: UI layer that triggers host app via URL schemes
- **Communication**: App Groups + URL schemes for data transfer
- **ASR**: whisper.cpp with Metal acceleration

### Android Implementation  
- **IME Service**: InputMethodService with direct mic access
- **Integration**: Direct text insertion into focused fields
- **ASR**: whisper.cpp with CPU/NEON optimization

### Shared Components
- Whisper engine wrapper
- Model management system
- Device benchmarking utilities
- Audio processing pipeline

## File Structure
```
voice_input/
├── shared/              # Cross-platform utilities
├── whisper-engine/      # ASR engine wrapper
├── ios/                 # iOS app + keyboard extension
├── android/             # Android IME implementation
├── models/              # Whisper model files
├── scripts/             # Build and utility scripts
└── docs/                # Technical documentation
```

## Privacy & Security
- All audio processing happens on-device
- No network requests during dictation
- User data never leaves the device
- Whisper models stored locally

## Performance Targets
- p95 latency: <500ms start-to-first-char
- Partial results: 300-500ms intervals  
- Battery overhead: ≤10% during 5min dictation
- Memory usage: Optimized for mobile constraints

## Current Status
- Project initialized
- Specifications documented
- Ready for implementation phase