import UIKit
import SwiftUI

class KeyboardViewController: UIInputViewController {
    private var keyboardView: UIView!
    private var micButton: UIButton!
    private var waveformView: WaveformView!
    private var statusLabel: UILabel!
    
    private var isRecording = false
    private var currentRequestId: String?
    
    override func updateViewConstraints() {
        super.updateViewConstraints()
        
        // Set keyboard height
        let keyboardHeight: CGFloat = 280
        view.heightAnchor.constraint(equalToConstant: keyboardHeight).isActive = true
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupKeyboardView()
        setupNotifications()
    }
    
    private func setupKeyboardView() {
        // Main container
        keyboardView = UIView()
        keyboardView.backgroundColor = UIColor.systemBackground
        keyboardView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(keyboardView)
        
        // Setup constraints
        NSLayoutConstraint.activate([
            keyboardView.topAnchor.constraint(equalTo: view.topAnchor),
            keyboardView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            keyboardView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            keyboardView.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
        
        setupUI()
    }
    
    private func setupUI() {
        // Header with offline indicator
        let headerStack = UIStackView()
        headerStack.axis = .horizontal
        headerStack.alignment = .center
        headerStack.spacing = 8
        headerStack.translatesAutoresizingMaskIntoConstraints = false
        
        let offlineIcon = UIImageView(image: UIImage(systemName: "wifi.slash"))
        offlineIcon.tintColor = .systemGreen
        offlineIcon.translatesAutoresizingMaskIntoConstraints = false
        
        let offlineLabel = UILabel()
        offlineLabel.text = "Offline"
        offlineLabel.font = UIFont.systemFont(ofSize: 12, weight: .semibold)
        offlineLabel.textColor = .systemGreen
        
        let titleLabel = UILabel()
        titleLabel.text = "VoiceBoard"
        titleLabel.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        titleLabel.textColor = .label
        
        headerStack.addArrangedSubview(offlineIcon)
        headerStack.addArrangedSubview(offlineLabel)
        headerStack.addArrangedSubview(UIView()) // Spacer
        headerStack.addArrangedSubview(titleLabel)
        
        keyboardView.addSubview(headerStack)
        
        // Status label
        statusLabel = UILabel()
        statusLabel.text = "Tap and hold microphone to dictate"
        statusLabel.font = UIFont.systemFont(ofSize: 14)
        statusLabel.textColor = .secondaryLabel
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 2
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        keyboardView.addSubview(statusLabel)
        
        // Waveform visualization
        waveformView = WaveformView()
        waveformView.translatesAutoresizingMaskIntoConstraints = false
        waveformView.isHidden = true
        keyboardView.addSubview(waveformView)
        
        // Large microphone button
        micButton = UIButton(type: .system)
        micButton.setImage(UIImage(systemName: "mic.circle.fill", withConfiguration: UIImage.SymbolConfiguration(pointSize: 80)), for: .normal)
        micButton.tintColor = .systemBlue
        micButton.translatesAutoresizingMaskIntoConstraints = false
        
        // Add touch handlers for push-to-talk
        micButton.addTarget(self, action: #selector(micButtonPressed), for: .touchDown)
        micButton.addTarget(self, action: #selector(micButtonReleased), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        
        keyboardView.addSubview(micButton)
        
        // Utility buttons
        let utilityStack = UIStackView()
        utilityStack.axis = .horizontal
        utilityStack.distribution = .fillEqually
        utilityStack.spacing = 20
        utilityStack.translatesAutoresizingMaskIntoConstraints = false
        
        let deleteButton = createUtilityButton(
            title: "Delete",
            systemImage: "delete.backward",
            action: #selector(deleteButtonTapped)
        )
        
        let spaceButton = createUtilityButton(
            title: "Space",
            systemImage: "space",
            action: #selector(spaceButtonTapped)
        )
        
        let returnButton = createUtilityButton(
            title: "Return",
            systemImage: "return",
            action: #selector(returnButtonTapped)
        )
        
        let dismissButton = createUtilityButton(
            title: "Dismiss",
            systemImage: "keyboard.chevron.compact.down",
            action: #selector(dismissButtonTapped)
        )
        
        utilityStack.addArrangedSubview(deleteButton)
        utilityStack.addArrangedSubview(spaceButton)
        utilityStack.addArrangedSubview(returnButton)
        utilityStack.addArrangedSubview(dismissButton)
        
        keyboardView.addSubview(utilityStack)
        
        // Layout constraints
        NSLayoutConstraint.activate([
            // Header
            headerStack.topAnchor.constraint(equalTo: keyboardView.topAnchor, constant: 12),
            headerStack.leadingAnchor.constraint(equalTo: keyboardView.leadingAnchor, constant: 16),
            headerStack.trailingAnchor.constraint(equalTo: keyboardView.trailingAnchor, constant: -16),
            
            // Status label
            statusLabel.topAnchor.constraint(equalTo: headerStack.bottomAnchor, constant: 16),
            statusLabel.leadingAnchor.constraint(equalTo: keyboardView.leadingAnchor, constant: 16),
            statusLabel.trailingAnchor.constraint(equalTo: keyboardView.trailingAnchor, constant: -16),
            
            // Waveform
            waveformView.centerYAnchor.constraint(equalTo: micButton.centerYAnchor),
            waveformView.leadingAnchor.constraint(equalTo: keyboardView.leadingAnchor, constant: 16),
            waveformView.trailingAnchor.constraint(equalTo: keyboardView.trailingAnchor, constant: -16),
            waveformView.heightAnchor.constraint(equalToConstant: 60),
            
            // Microphone button
            micButton.centerXAnchor.constraint(equalTo: keyboardView.centerXAnchor),
            micButton.centerYAnchor.constraint(equalTo: keyboardView.centerYAnchor, constant: 10),
            micButton.widthAnchor.constraint(equalToConstant: 100),
            micButton.heightAnchor.constraint(equalToConstant: 100),
            
            // Utility buttons
            utilityStack.bottomAnchor.constraint(equalTo: keyboardView.bottomAnchor, constant: -16),
            utilityStack.leadingAnchor.constraint(equalTo: keyboardView.leadingAnchor, constant: 16),
            utilityStack.trailingAnchor.constraint(equalTo: keyboardView.trailingAnchor, constant: -16),
            utilityStack.heightAnchor.constraint(equalToConstant: 40)
        ])
    }
    
    private func createUtilityButton(title: String, systemImage: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.setImage(UIImage(systemName: systemImage), for: .normal)
        button.titleLabel?.font = UIFont.systemFont(ofSize: 12)
        button.tintColor = .systemBlue
        button.backgroundColor = UIColor.systemGray5
        button.layer.cornerRadius = 8
        button.addTarget(self, action: action, for: .touchUpInside)
        
        // Configure image and title positioning
        button.imageEdgeInsets = UIEdgeInsets(top: -15, left: 0, bottom: 0, right: 0)
        button.titleEdgeInsets = UIEdgeInsets(top: 15, left: -button.imageView!.frame.width, bottom: -15, right: 0)
        
        return button
    }
    
    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(transcriptionComplete),
            name: Notification.Name("TranscriptionComplete"),
            object: nil
        )
    }
    
    // MARK: - Button Actions
    @objc private func micButtonPressed() {
        startDictation()
    }
    
    @objc private func micButtonReleased() {
        stopDictation()
    }
    
    @objc private func deleteButtonTapped() {
        textDocumentProxy.deleteBackward()
    }
    
    @objc private func spaceButtonTapped() {
        textDocumentProxy.insertText(" ")
    }
    
    @objc private func returnButtonTapped() {
        textDocumentProxy.insertText("\\n")
    }
    
    @objc private func dismissButtonTapped() {
        dismissKeyboard()
    }
    
    // MARK: - Dictation Logic
    private func startDictation() {
        guard !isRecording else { return }
        
        isRecording = true
        currentRequestId = UUID().uuidString
        
        // Update UI
        micButton.tintColor = .systemRed
        statusLabel.text = "Listening... Release to stop"
        waveformView.isHidden = false
        waveformView.startAnimating()
        
        // Trigger main app for actual recording
        triggerMainAppDictation()
        
        // Provide haptic feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
    }
    
    private func stopDictation() {
        guard isRecording else { return }
        
        isRecording = false
        
        // Update UI
        micButton.tintColor = .systemBlue
        statusLabel.text = "Processing..."
        waveformView.stopAnimating()
        waveformView.isHidden = true
        
        // Provide haptic feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .light)
        impactFeedback.impactOccurred()
    }
    
    private func triggerMainAppDictation() {
        guard let requestId = currentRequestId else { return }
        
        // Try to open main app via URL scheme
        if let url = URL(string: "voiceboard://dictate?requestId=\\(requestId)") {
            _ = openURL(url)
        }
        
        // Also use App Groups as backup communication method
        let sharedDefaults = UserDefaults(suiteName: "group.com.voiceboard.shared")
        sharedDefaults?.set(requestId, forKey: "current_request_id")
        sharedDefaults?.set(Date(), forKey: "request_timestamp")
        
        // Post notification
        NotificationCenter.default.post(
            name: Notification.Name("ProcessDictationRequest"),
            object: nil,
            userInfo: ["requestId": requestId]
        )
    }
    
    @objc private func transcriptionComplete(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let requestId = userInfo["requestId"] as? String,
              requestId == currentRequestId else { return }
        
        // Get transcription result from App Groups
        let sharedDefaults = UserDefaults(suiteName: "group.com.voiceboard.shared")
        let transcription = sharedDefaults?.string(forKey: "transcription_\\(requestId)") ?? ""
        
        // Insert transcribed text
        if !transcription.isEmpty {
            textDocumentProxy.insertText(transcription)
        }
        
        // Clean up
        currentRequestId = nil
        statusLabel.text = "Tap and hold microphone to dictate"
        
        // Clean up shared data
        sharedDefaults?.removeObject(forKey: "transcription_\\(requestId)")
        sharedDefaults?.removeObject(forKey: "timestamp_\\(requestId)")
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
}

// MARK: - WaveformView
class WaveformView: UIView {
    private var displayLink: CADisplayLink?
    private var waveformLayers: [CAShapeLayer] = []
    private let numberOfBars = 20
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupWaveform()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupWaveform()
    }
    
    private func setupWaveform() {
        backgroundColor = .clear
        
        for _ in 0..<numberOfBars {
            let layer = CAShapeLayer()
            layer.fillColor = UIColor.systemBlue.cgColor
            layer.cornerRadius = 2
            waveformLayers.append(layer)
            self.layer.addSublayer(layer)
        }
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        updateWaveformLayout()
    }
    
    private func updateWaveformLayout() {
        let barWidth: CGFloat = 4
        let spacing: CGFloat = 4
        let totalWidth = CGFloat(numberOfBars) * barWidth + CGFloat(numberOfBars - 1) * spacing
        let startX = (bounds.width - totalWidth) / 2
        
        for (index, layer) in waveformLayers.enumerated() {
            let x = startX + CGFloat(index) * (barWidth + spacing)
            let height = CGFloat.random(in: 4...40)
            let y = (bounds.height - height) / 2
            
            layer.frame = CGRect(x: x, y: y, width: barWidth, height: height)
        }
    }
    
    func startAnimating() {
        displayLink = CADisplayLink(target: self, selector: #selector(updateWaveform))
        displayLink?.add(to: .main, forMode: .common)
    }
    
    func stopAnimating() {
        displayLink?.invalidate()
        displayLink = nil
    }
    
    @objc private func updateWaveform() {
        updateWaveformLayout()
    }
}