package com.muxi.android_asr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.muxi.android_asr.ui.theme.Android_asrTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private var recognizedText = mutableStateOf("")
    private var isListening = mutableStateOf(false)
    private var selectedLanguage = mutableStateOf("en-US") // Default to English

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, speech recognition can proceed
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize speech recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        // Check and request permission if needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        
        // Set up recognition listener
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                // Don't do anything here - just let the recognizer naturally end
                // It will call onResults() with the final results
            }
            override fun onError(error: Int) {
                if (isListening.value && (error == SpeechRecognizer.ERROR_NO_MATCH || 
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    // For these errors, restart listening if still in listening mode
                    startSpeechRecognition(keepText = true)
                    return
                }
                
                isListening.value = false
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No recognition result matched"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                recognizedText.value = "Error: $errorMessage"
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    if (recognizedText.value == "Listening...") {
                        recognizedText.value = matches[0]
                    } else {
                        recognizedText.value += " " + matches[0]
                    }
                }
                
                // Recognition has ended naturally after silence - stop completely
                isListening.value = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty() && recognizedText.value != "Listening...") {
                    val currentText = if (recognizedText.value == "Listening...") "" else recognizedText.value
                    val lastSpace = currentText.lastIndexOf(" ")
                    val baseText = if (lastSpace > 0) currentText.substring(0, lastSpace + 1) else ""
                    recognizedText.value = baseText + matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        enableEdgeToEdge()
        setContent {
            Android_asrTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpeechRecognizerScreen(
                        recognizedText = recognizedText.value,
                        isListening = isListening.value,
                        selectedLanguage = selectedLanguage.value,
                        onLanguageSelected = { selectedLanguage.value = it },
                        onStartListening = { startSpeechRecognition() },
                        onStopListening = { stopSpeechRecognition() },
                        onClearText = { recognizedText.value = "" },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
    
    private fun startSpeechRecognition(keepText: Boolean = false) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            isListening.value = true
            
            if (!keepText) {
                recognizedText.value = "Listening..."
            }
            
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage.value)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                
                // Set silence timeout to 5 seconds (5000 milliseconds)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            }
            
            try {
                speechRecognizer.startListening(recognizerIntent)
            } catch (e: Exception) {
                recognizedText.value = "Error starting recognition: ${e.message}"
                isListening.value = false
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    private fun stopSpeechRecognition() {
        speechRecognizer.stopListening()
        isListening.value = false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}

@Composable
fun SpeechRecognizerScreen(
    recognizedText: String,
    isListening: Boolean,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onClearText: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Speech Recognizer",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Text result area with scrolling
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = recognizedText.ifEmpty { "Tap the button and speak" },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Clear button
        Button(
            onClick = onClearText,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Clear Text")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Language selection
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Language:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // English option
                RadioButton(
                    selected = selectedLanguage == "en-US",
                    onClick = { onLanguageSelected("en-US") }
                )
                Text(
                    text = "English",
                    modifier = Modifier.padding(end = 16.dp)
                )
                
                // Chinese option
                RadioButton(
                    selected = selectedLanguage == "zh-CN",
                    onClick = { onLanguageSelected("zh-CN") }
                )
                Text(text = "Chinese")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = if (isListening) onStopListening else onStartListening,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(text = if (isListening) "Stop Listening" else "Start Listening")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpeechRecognizerPreview() {
    Android_asrTheme {
        SpeechRecognizerScreen(
            recognizedText = "Preview text for speech recognition",
            isListening = false,
            selectedLanguage = "en-US",
            onLanguageSelected = {},
            onStartListening = {},
            onStopListening = {},
            onClearText = {}
        )
    }
}