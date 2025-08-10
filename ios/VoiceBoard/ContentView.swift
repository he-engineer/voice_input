import SwiftUI
import AVFoundation

struct ContentView: View {
    @EnvironmentObject private var whisperManager: WhisperManager
    @EnvironmentObject private var audioEngine: AudioEngine
    @State private var isSetupComplete = false
    @State private var showingSetup = false
    @State private var microphonePermissionGranted = false
    @State private var keyboardInstalled = false
    
    var body: some View {
        NavigationView {
            VStack(spacing: 30) {
                headerView
                setupStatusView
                actionButtonsView
                Spacer()
            }
            .padding()
            .navigationTitle("VoiceBoard")
            .onAppear {
                checkSetupStatus()
            }
            .sheet(isPresented: $showingSetup) {
                SetupView(isSetupComplete: $isSetupComplete)
            }
        }
    }
    
    private var headerView: some View {
        VStack(spacing: 16) {
            Image(systemName: "mic.circle.fill")
                .font(.system(size: 80))
                .foregroundColor(.blue)
            
            Text("VoiceBoard")
                .font(.largeTitle)
                .fontWeight(.bold)
            
            Text("Offline Voice Dictation")
                .font(.subtitle)
                .foregroundColor(.secondary)
            
            HStack {
                Image(systemName: "wifi.slash")
                    .foregroundColor(.green)
                Text("100% Offline")
                    .font(.caption)
                    .foregroundColor(.green)
                    .fontWeight(.semibold)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
            .background(Color.green.opacity(0.1))
            .cornerRadius(12)
        }
    }
    
    private var setupStatusView: some View {
        VStack(spacing: 16) {
            setupStatusRow(
                title: "Microphone Access",
                isComplete: microphonePermissionGranted,
                action: requestMicrophonePermission
            )
            
            setupStatusRow(
                title: "Keyboard Installed",
                isComplete: keyboardInstalled,
                action: openKeyboardSettings
            )
            
            setupStatusRow(
                title: "Whisper Model Ready",
                isComplete: whisperManager.isModelReady,
                action: { showingSetup = true }
            )
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
    
    private var actionButtonsView: some View {
        VStack(spacing: 16) {
            if isSetupComplete {
                Button("Test Dictation") {
                    testDictation()
                }
                .buttonStyle(PrimaryButtonStyle())
            } else {
                Button("Complete Setup") {
                    showingSetup = true
                }
                .buttonStyle(PrimaryButtonStyle())
            }
            
            Button("Open Keyboard Settings") {
                openKeyboardSettings()
            }
            .buttonStyle(SecondaryButtonStyle())
        }
    }
    
    private func setupStatusRow(title: String, isComplete: Bool, action: @escaping () -> Void) -> some View {
        HStack {
            Image(systemName: isComplete ? "checkmark.circle.fill" : "circle")
                .foregroundColor(isComplete ? .green : .gray)
            
            Text(title)
                .foregroundColor(isComplete ? .primary : .secondary)
            
            Spacer()
            
            if !isComplete {
                Button("Setup") {
                    action()
                }
                .font(.caption)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(8)
            }
        }
    }
    
    private func checkSetupStatus() {
        // Check microphone permission
        microphonePermissionGranted = AVAudioSession.sharedInstance().recordPermission == .granted
        
        // Check if keyboard is installed (simplified check)
        keyboardInstalled = UserDefaults.standard.bool(forKey: "keyboardInstalled")
        
        isSetupComplete = microphonePermissionGranted && keyboardInstalled && whisperManager.isModelReady
    }
    
    private func requestMicrophonePermission() {
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            DispatchQueue.main.async {
                microphonePermissionGranted = granted
                checkSetupStatus()
            }
        }
    }
    
    private func openKeyboardSettings() {
        if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(settingsUrl)
        }
    }
    
    private func testDictation() {
        // Test dictation functionality
        print("Testing dictation...")
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.blue)
            .cornerRadius(12)
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundColor(.blue)
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.blue.opacity(0.1))
            .cornerRadius(12)
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
    }
}

struct SetupView: View {
    @Binding var isSetupComplete: Bool
    @Environment(\.presentationMode) var presentationMode
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Initial Setup")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                
                Text("VoiceBoard will download and configure the optimal Whisper model for your device.")
                    .multilineTextAlignment(.center)
                    .padding()
                
                Button("Start Setup") {
                    // TODO: Implement setup wizard
                    isSetupComplete = true
                    presentationMode.wrappedValue.dismiss()
                }
                .buttonStyle(PrimaryButtonStyle())
                .padding()
                
                Spacer()
            }
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarItems(trailing: Button("Cancel") {
                presentationMode.wrappedValue.dismiss()
            })
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(WhisperManager())
        .environmentObject(AudioEngine())
}