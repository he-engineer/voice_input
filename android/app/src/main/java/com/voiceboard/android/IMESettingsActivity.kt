package com.voiceboard.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.voiceboard.android.ui.theme.VoiceBoardTheme

class IMESettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoiceBoardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IMESettingsScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IMESettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { Text("VoiceBoard Settings") }
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Model Selection",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Choose the speech recognition model",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        var selectedModel by remember { mutableStateOf("base.en") }
                        
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedModel == "tiny.en",
                                    onClick = { selectedModel = "tiny.en" }
                                )
                                Column {
                                    Text("Tiny (Fast)")
                                    Text(
                                        "~39MB - Lower accuracy, faster processing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedModel == "base.en",
                                    onClick = { selectedModel = "base.en" }
                                )
                                Column {
                                    Text("Base (Recommended)")
                                    Text(
                                        "~148MB - Balanced accuracy and speed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedModel == "small.en",
                                    onClick = { selectedModel = "small.en" }
                                )
                                Column {
                                    Text("Small (Accurate)")
                                    Text(
                                        "~244MB - Higher accuracy, slower processing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            item {
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Punctuation",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Automatically add periods and commas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        var autoPunctuation by remember { mutableStateOf(true) }
                        Switch(
                            checked = autoPunctuation,
                            onCheckedChange = { autoPunctuation = it }
                        )
                    }
                }
            }
            
            item {
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Haptic Feedback",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Vibrate when recording starts/stops",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        var hapticFeedback by remember { mutableStateOf(true) }
                        Switch(
                            checked = hapticFeedback,
                            onCheckedChange = { hapticFeedback = it }
                        )
                    }
                }
            }
            
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Privacy",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "VoiceBoard processes all voice data on your device. No audio or text is sent to external servers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IMESettingsScreenPreview() {
    VoiceBoardTheme {
        IMESettingsScreen()
    }
}