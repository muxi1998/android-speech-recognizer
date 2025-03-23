package com.muxi.android_asr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.muxi.android_asr.ui.theme.Android_asrTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val TAG = "SpeechRecognizer"
    private lateinit var speechRecognizer: SpeechRecognizer
    private var recognizedText = mutableStateOf("")
    private var partialText = mutableStateOf("")
    private var isListening = mutableStateOf(false)
    private var selectedLanguage = mutableStateOf("en-US") // Default to English
    private var lastStartTime = 0L
    private var lastErrors = mutableListOf<Int>()
    private var waitingForSpeech = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition()
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize speech recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupRecognitionListener()
        
        enableEdgeToEdge()
        setContent {
            Android_asrTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpeechRecognizerScreen(
                        recognizedText = if (recognizedText.value.isEmpty()) {
                            partialText.value
                        } else if (partialText.value.isEmpty()) {
                            recognizedText.value
                        } else {
                            "${recognizedText.value} ${partialText.value}"
                        },
                        isListening = isListening.value,
                        selectedLanguage = selectedLanguage.value,
                        onLanguageSelected = { 
                            selectedLanguage.value = it
                            Log.d(TAG, "Language selected: $it") 
                        },
                        onStartListening = { checkPermissionAndStartRecognition() },
                        onStopListening = { stopSpeechRecognition() },
                        onClearText = { 
                            recognizedText.value = ""
                            partialText.value = ""
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
    
    private fun setupRecognitionListener() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "onReadyForSpeech")
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech")
                waitingForSpeech = false // User has started speaking
            }
            
            override fun onRmsChanged(rmsdB: Float) {}
            
            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d(TAG, "onBufferReceived")
            }
            
            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech")
                // Natural end of speech - will be followed by onResults
                // We do NOT stop listening here - user must press stop
            }
            
            override fun onError(error: Int) {
                Log.e(TAG, "onError: $error")
                
                // Special handling for ERROR_NO_MATCH when no speech has been detected yet
                if (error == SpeechRecognizer.ERROR_NO_MATCH && waitingForSpeech) {
                    Log.d(TAG, "Ignoring initial NO_MATCH and restarting recognition")
                    
                    // Keep track of consecutive errors to prevent infinite loops
                    lastErrors.add(error)
                    if (lastErrors.size > 3) {
                        lastErrors.removeAt(0)
                    }
                    
                    // Only if we're still actively listening and this isn't a recurring pattern
                    if (isListening.value && !lastErrors.all { it == SpeechRecognizer.ERROR_NO_MATCH }) {
                        try {
                            // Restart listening without changing UI state
                            speechRecognizer.startListening(createRecognizerIntent())
                            return
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to restart after NO_MATCH: ${e.message}")
                        }
                    }
                }
                
                // Save any partial results we have, regardless of error
                if (partialText.value.isNotEmpty()) {
                    if (recognizedText.value.isEmpty()) {
                        recognizedText.value = partialText.value
                    } else {
                        recognizedText.value += " " + partialText.value
                    }
                    partialText.value = ""
                }
                
                // Update UI to show recognition has stopped
                waitingForSpeech = false
                isListening.value = false
                
                // Only show toast for errors other than NO_MATCH (which is too common)
                if (error != SpeechRecognizer.ERROR_NO_MATCH) {
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
                    Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onResults(results: Bundle?) {
                Log.d(TAG, "onResults")
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                
                if (!matches.isNullOrEmpty()) {
                    val result = matches[0]
                    Log.d(TAG, "Result: $result")
                    
                    // Append to existing recognized text
                    if (recognizedText.value.isEmpty()) {
                        recognizedText.value = result
                    } else {
                        // Add a space between existing text and new result
                        recognizedText.value += " " + result
                    }
                    partialText.value = ""
                }
                
                // SpeechRecognizer stops after delivering results
                // Update UI to reflect this
                isListening.value = false
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    // Store only the current partial phrase
                    partialText.value = matches[0]
                    Log.d(TAG, "Partial: ${partialText.value}")
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "onEvent: $eventType")
            }
        })
    }
    
    private fun checkPermissionAndStartRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            startSpeechRecognition()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            
            // Set language explicitly
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage.value)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguage.value)
            
            // For English, also specify locale
            if (selectedLanguage.value == "en-US") {
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            }
            
            // Don't use any timeouts - let the user take as long as needed to start speaking
            // These are max values to prevent the recognizer from timing out
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000000) // Very long
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000000) // Very long
            
            // Multiple results for better accuracy
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
    }
    
    private fun startSpeechRecognition() {
        // Reset text when starting fresh
        recognizedText.value = ""
        partialText.value = ""
        isListening.value = true
        lastStartTime = System.currentTimeMillis()
        waitingForSpeech = true
        lastErrors.clear()
        
        val recognizerIntent = createRecognizerIntent()
        
        try {
            speechRecognizer.startListening(recognizerIntent)
            Log.d(TAG, "Started listening with language: ${selectedLanguage.value}")
        } catch (e: Exception) {
            isListening.value = false
            waitingForSpeech = false
            Log.e(TAG, "Error starting recognition: ${e.message}")
            Toast.makeText(this, "Error starting recognition: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopSpeechRecognition() {
        // When manually stopping, save any partial results
        if (partialText.value.isNotEmpty()) {
            if (recognizedText.value.isEmpty()) {
                recognizedText.value = partialText.value
            } else {
                recognizedText.value += " " + partialText.value
            }
            partialText.value = ""
        }
        
        speechRecognizer.stopListening()
        isListening.value = false
        Log.d(TAG, "Stopped listening")
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
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Speech Recognizer",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Text result area with scrolling
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                text = recognizedText.ifEmpty { "Tap the microphone button and speak" },
                style = MaterialTheme.typography.bodyLarge
            )
        }
        
        // Language selection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
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
        
        // Buttons
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = if (isListening) onStopListening else onStartListening,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(text = if (isListening) "Stop" else "Start Listening")
            }
            
            if (recognizedText.isNotEmpty()) {
                Button(
                    onClick = onClearText,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text("Clear Text")
                }
            }
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