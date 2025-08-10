# VoiceBoard - Offline Voice Dictation

VoiceBoard is a system-wide, offline voice dictation keyboard for iOS and Android that provides private, on-device speech recognition using Whisper models.

## 🌟 Key Features

- **100% Offline**: No data sent to cloud, works in airplane mode
- **System-wide**: Works in all apps on iOS and Android  
- **Private**: All processing happens on your device
- **Fast**: Optimized Whisper models with <500ms latency
- **Cross-platform**: Native iOS keyboard extension + Android IME

## 🏗️ Architecture

### iOS Implementation
- **Host App**: Main app with microphone permissions and ASR processing
- **Keyboard Extension**: UI layer that communicates with host app via App Groups + URL schemes
- **Whisper Integration**: Metal-accelerated whisper.cpp for optimal performance

### Android Implementation  
- **IME Service**: Custom InputMethodService with direct microphone access
- **Direct Integration**: Real-time text insertion without external handoff
- **Whisper Integration**: CPU/NEON optimized whisper.cpp

### Shared Components
- Unified Whisper engine wrapper (C++)
- Cross-platform model management
- Device benchmarking utilities
- Audio processing pipeline

## 📱 Supported Models

| Model | Size | Performance | Use Case |
|-------|------|-------------|----------|
| `tiny.en` | ~39MB | Fastest, lower accuracy | Legacy devices, testing |
| `base.en` | ~148MB | Balanced | Default recommendation |
| `distil-small.en` | ~244MB | Slower, higher accuracy | High-end devices |

## 🚀 Quick Start

### Prerequisites

- **iOS**: Xcode 15+, iOS 15+ deployment target
- **Android**: Android Studio, NDK r25+, API 24+ (Android 7.0)
- **System**: macOS (for iOS builds), Python 3.8+

### Setup

1. **Clone and setup:**
   ```bash
   git clone <repository-url>
   cd voice_input
   
   # Install Python dependencies
   pip install requests tqdm
   
   # Download models
   python scripts/build_models.py
   
   # Build Whisper libraries  
   ./scripts/setup_whisper.sh
   ```

2. **iOS Build:**
   ```bash
   cd ios
   open VoiceBoard.xcodeproj
   # Build and run in Xcode
   ```

3. **Android Build:**
   ```bash
   cd android
   ./gradlew build
   ./gradlew installDebug
   ```

### Usage

#### iOS
1. Install the VoiceBoard app
2. Go to Settings → General → Keyboard → Keyboards → Add New Keyboard → VoiceBoard
3. Grant microphone permission when prompted
4. Switch to VoiceBoard keyboard in any app
5. Tap and hold microphone button to dictate

#### Android  
1. Install the VoiceBoard app
2. Go to Settings → Languages & input → Virtual keyboard → Manage keyboards
3. Enable VoiceBoard
4. Switch to VoiceBoard as your input method
5. Grant microphone permission when prompted
6. Tap and hold microphone button to dictate

## 🔧 Development

### Project Structure
```
voice_input/
├── shared/              # Cross-platform types and utilities
├── whisper-engine/      # Unified ASR engine wrapper
├── ios/                 # iOS app and keyboard extension
│   ├── VoiceBoard/      # Main iOS app
│   └── VoiceBoardKeyboard/  # Keyboard extension
├── android/             # Android IME implementation
│   └── app/             # Android app and service
├── models/              # Whisper model files
├── scripts/             # Build and setup scripts
└── docs/                # Documentation
```

### Key Components

- **WhisperManager**: Model loading and transcription
- **AudioEngine/AudioRecorder**: Audio capture and processing  
- **VoiceBoardIME**: Android input method service
- **KeyboardViewController**: iOS keyboard extension controller

### Building from Source

1. **Setup Whisper.cpp:**
   ```bash
   ./scripts/setup_whisper.sh
   ```

2. **Download Models:**
   ```bash  
   python scripts/build_models.py
   ```

3. **iOS Development:**
   - Open `ios/VoiceBoard.xcodeproj`
   - Configure signing and provisioning profiles
   - Build for device or simulator

4. **Android Development:**
   - Set `ANDROID_NDK_ROOT` environment variable
   - Build native libraries: `cd android && ./gradlew build`

### Testing

```bash
# iOS
cd ios && xcodebuild test -scheme VoiceBoard -destination 'platform=iOS Simulator,name=iPhone 14'

# Android
cd android && ./gradlew test

# Lint
npm run lint  # (if configured)
```

## 🔒 Privacy & Security

- **No Network Requests**: All processing happens locally
- **No Data Collection**: Audio and transcriptions never leave your device  
- **Offline Operation**: Works without internet connection
- **App Store Compliant**: Follows platform privacy guidelines

## 📊 Performance Targets

- **Latency**: <500ms start-to-first-character (p95)
- **Partial Results**: Updates every 300-500ms
- **Battery**: ≤10% overhead during 5min dictation
- **Accuracy**: Competitive with cloud services for English

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature-name`
3. Make your changes and test thoroughly
4. Submit a pull request with detailed description

### Development Setup

```bash
# Install development dependencies
pip install -r requirements-dev.txt

# Setup pre-commit hooks
pre-commit install

# Run tests
./scripts/run_tests.sh
```

## 📄 License

[MIT License](LICENSE)

## 🙏 Acknowledgments

- [Whisper.cpp](https://github.com/ggerganov/whisper.cpp) - Efficient Whisper implementation
- [OpenAI Whisper](https://github.com/openai/whisper) - Original speech recognition model
- The open source community for inspiration and tools

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/username/voice_input/issues)
- **Discussions**: [GitHub Discussions](https://github.com/username/voice_input/discussions)
- **Documentation**: [Wiki](https://github.com/username/voice_input/wiki)

---

**VoiceBoard** - Private, offline voice dictation for everyone. 🎙️