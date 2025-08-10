package com.voiceboard.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.voiceboard.android.ui.theme.VoiceBoardTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var whisperManager: WhisperManager
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, initialize Whisper
            initializeWhisper()
        } else {
            // Permission denied
            // Handle this case in UI
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        whisperManager = WhisperManager(this)
        
        setContent {
            VoiceBoardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onRequestPermission = { requestMicrophonePermission() },
                        onOpenSettings = { openInputMethodSettings() },
                        whisperManager = whisperManager
                    )
                }
            }
        }
        
        // Check permissions on startup
        checkPermissions()
    }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            initializeWhisper()
        }
    }
    
    private fun requestMicrophonePermission() {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    
    private fun openInputMethodSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }
    
    private fun initializeWhisper() {
        whisperManager.loadModel()
    }
}

@Composable
fun MainScreen(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    whisperManager: WhisperManager
) {
    val context = LocalContext.current
    val hasMicPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val isIMEEnabled = remember { mutableStateOf(false) }
    val isModelReady = remember { mutableStateOf(whisperManager.isModelReady) }
    
    // Setup tasks
    val setupTasks = listOf(
        SetupTask(
            title = "Microphone Permission",
            description = "Required for voice input",
            icon = Icons.Default.Mic,
            isComplete = hasMicPermission.value,
            action = onRequestPermission
        ),
        SetupTask(
            title = "Enable VoiceBoard Keyboard", 
            description = "Add VoiceBoard to your keyboards",
            icon = Icons.Default.Keyboard,
            isComplete = isIMEEnabled.value,
            action = onOpenSettings
        ),
        SetupTask(
            title = "Download AI Model",
            description = "Download speech recognition model",
            icon = Icons.Default.Download,
            isComplete = isModelReady.value,
            action = { /* Model will download automatically */ }
        )
    )
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeaderSection()
        }
        
        item {
            PrivacyBadge()
        }
        
        item {
            Text(
                text = "Setup",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        items(setupTasks) { task ->
            SetupTaskCard(task = task)
        }
        
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        if (setupTasks.all { it.isComplete }) {
            item {
                CompletionCard()
            }
        }
    }
}

@Composable
fun HeaderSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "VoiceBoard",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Offline Voice Dictation",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PrivacyBadge() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = Color(0xFF4CAF50)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "100% Offline - Your privacy is protected",
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SetupTaskCard(task: SetupTask) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isComplete) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (task.isComplete) Icons.Default.CheckCircle else task.icon,
                contentDescription = null,
                tint = if (task.isComplete) {
                    Color(0xFF4CAF50)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (!task.isComplete) {
                Button(
                    onClick = task.action,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Setup")
                }
            }
        }
    }
}

@Composable
fun CompletionCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Setup Complete!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "VoiceBoard is ready to use. Switch to VoiceBoard keyboard in any app to start dictating.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

data class SetupTask(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isComplete: Boolean,
    val action: () -> Unit
)

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    VoiceBoardTheme {
        MainScreen(
            onRequestPermission = {},
            onOpenSettings = {},
            whisperManager = WhisperManager(LocalContext.current)
        )
    }
}