import SwiftUI

@main
struct VoiceBoardApp: App {
    @StateObject private var whisperManager = WhisperManager()
    @StateObject private var audioEngine = AudioEngine()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(whisperManager)
                .environmentObject(audioEngine)
                .onAppear {
                    handleAppLaunch()
                }
        }
    }
    
    private func handleAppLaunch() {
        // Handle URL scheme launches from keyboard extension
        NotificationCenter.default.addObserver(
            forName: Notification.Name("ProcessDictationRequest"),
            object: nil,
            queue: .main
        ) { notification in
            if let userInfo = notification.userInfo,
               let requestId = userInfo["requestId"] as? String {
                processDictationRequest(requestId: requestId)
            }
        }
    }
    
    private func processDictationRequest(requestId: String) {
        guard !audioEngine.isRecording else { return }
        
        Task {
            do {
                try await audioEngine.startRecording()
                
                // Wait for user to finish speaking (simplified for demo)
                try await Task.sleep(nanoseconds: 5_000_000_000) // 5 seconds
                
                let audioData = try await audioEngine.stopRecording()
                let transcription = await whisperManager.transcribe(audioData)
                
                // Send result back to keyboard via App Groups
                await sendResultToKeyboard(transcription: transcription, requestId: requestId)
                
            } catch {
                print("Dictation error: \(error)")
            }
        }
    }
    
    private func sendResultToKeyboard(transcription: String, requestId: String) async {
        let sharedDefaults = UserDefaults(suiteName: "group.com.voiceboard.shared")
        sharedDefaults?.set(transcription, forKey: "transcription_\(requestId)")
        sharedDefaults?.set(Date(), forKey: "timestamp_\(requestId)")
        
        // Notify keyboard extension
        let notification = Notification.Name("TranscriptionComplete")
        NotificationCenter.default.post(name: notification, object: nil, userInfo: ["requestId": requestId])
    }
}