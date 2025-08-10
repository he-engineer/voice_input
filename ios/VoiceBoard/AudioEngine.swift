import Foundation
import AVFoundation
import Combine

class AudioEngine: NSObject, ObservableObject {
    private var audioEngine = AVAudioEngine()
    private var inputNode: AVAudioInputNode?
    private var audioBuffer: [Float] = []
    private let sampleRate: Double = 16000.0
    
    @Published var isRecording = false
    @Published var audioLevel: Float = 0.0
    @Published var error: AudioEngineError?
    
    override init() {
        super.init()
        setupAudioSession()
    }
    
    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker, .allowBluetooth])
            try session.setActive(true)
        } catch {
            self.error = .sessionSetupFailed(error)
        }
    }
    
    func startRecording() async throws -> Bool {
        guard !isRecording else { return false }
        
        // Reset buffer
        audioBuffer.removeAll()
        
        // Configure audio engine
        let inputNode = audioEngine.inputNode
        self.inputNode = inputNode
        
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        let desiredFormat = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1)!
        
        // Create format converter if needed
        var converter: AVAudioConverter?
        if recordingFormat != desiredFormat {
            converter = AVAudioConverter(from: recordingFormat, to: desiredFormat)
        }
        
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { [weak self] buffer, _ in
            guard let self = self else { return }
            
            let convertedBuffer: AVAudioPCMBuffer
            
            if let converter = converter {
                // Convert to desired format
                guard let outputBuffer = AVAudioPCMBuffer(pcmFormat: desiredFormat, frameCapacity: buffer.frameCapacity) else { return }
                
                var error: NSError?
                converter.convert(to: outputBuffer, error: &error) { _, outStatus in
                    outStatus.pointee = .haveData
                    return buffer
                }
                
                if error != nil {
                    return
                }
                
                convertedBuffer = outputBuffer
            } else {
                convertedBuffer = buffer
            }
            
            // Extract audio samples
            if let channelData = convertedBuffer.floatChannelData?[0] {
                let frameLength = Int(convertedBuffer.frameLength)
                let samples = Array(UnsafeBufferPointer(start: channelData, count: frameLength))
                
                DispatchQueue.main.async {
                    self.audioBuffer.append(contentsOf: samples)
                    
                    // Update audio level for visualization
                    let rms = sqrt(samples.map { $0 * $0 }.reduce(0, +) / Float(samples.count))
                    self.audioLevel = min(rms * 10, 1.0) // Scale and clamp
                }
            }
        }
        
        do {
            try audioEngine.start()
            await MainActor.run {
                self.isRecording = true
            }
            return true
        } catch {
            await MainActor.run {
                self.error = .engineStartFailed(error)
            }
            throw error
        }
    }
    
    func stopRecording() async throws -> [Float] {
        guard isRecording else { return [] }
        
        audioEngine.stop()
        inputNode?.removeTap(onBus: 0)
        
        await MainActor.run {
            self.isRecording = false
            self.audioLevel = 0.0
        }
        
        return audioBuffer
    }
    
    func pauseRecording() {
        guard isRecording else { return }
        audioEngine.pause()
    }
    
    func resumeRecording() throws {
        guard isRecording else { return }
        try audioEngine.start()
    }
    
    deinit {
        if isRecording {
            Task {
                try? await stopRecording()
            }
        }
    }
}

// MARK: - Error Types
enum AudioEngineError: LocalizedError {
    case sessionSetupFailed(Error)
    case engineStartFailed(Error)
    case microphoneAccessDenied
    case audioUnavailable
    
    var errorDescription: String? {
        switch self {
        case .sessionSetupFailed(let error):
            return "Failed to setup audio session: \(error.localizedDescription)"
        case .engineStartFailed(let error):
            return "Failed to start audio engine: \(error.localizedDescription)"
        case .microphoneAccessDenied:
            return "Microphone access denied. Please enable in Settings."
        case .audioUnavailable:
            return "Audio input is not available on this device."
        }
    }
}