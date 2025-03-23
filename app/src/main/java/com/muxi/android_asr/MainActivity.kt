package com.muxi.android_asr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var isListening = mutableStateOf(false)
    private var selectedLanguage = mutableStateOf("en-US") // Default to English
    private val handler = Handler(Looper.getMainLooper())
    private var restartRunnable: Runnable? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
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
                        recognizedText = recognizedText.value,
                        isListening = isListening.value,
                        selectedLanguage = selectedLanguage.value,
                        onLanguageSelected = { 
                            selectedLanguage.value = it
                        },
                        onStartListening = { checkPermission() },
                        onStopListening = { stopListening() },
                        onClearText = { recognizedText.value = "" },
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
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // Intentionally empty
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // Intentionally empty
            }
            
            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech")
                
                // If we're still in listening mode, schedule a restart
                if (isListening.value) {
                    scheduleRestart()
                }
            }
            
            override fun onError(error: Int) {
                Log.e(TAG, "onError: $error")
                
                // For errors, if we're still supposed to be listening, restart
                if (isListening.value) {
                    scheduleRestart()
                }
            }
            
            override fun onResults(results: Bundle?) {
                Log.d(TAG, "onResults")
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                
                if (!matches.isNullOrEmpty()) {
                    val result = matches[0]
                    // Append to recognized text with a space
                    if (recognizedText.value.isEmpty()) {
                        recognizedText.value = result
                    } else {
                        recognizedText.value += " $result"
                    }
                }
                
                // If we're still in listening mode, restart immediately
                if (isListening.value) {
                    scheduleRestart()
                }
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val newPartial = matches[0]
                    
                    // Optionally update UI with partial results
                    // Uncomment to show partial results in real-time
                    // Note: This might make the text jumpy as it updates frequently
                    // recognizedText.value = if (recognizedText.value.isEmpty()) newPartial else "${recognizedText.value} $newPartial"
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                // Intentionally empty
            }
        })
    }
    
    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    private fun scheduleRestart() {
        // Cancel any pending restarts
        cancelPendingRestarts()
        
        // Schedule a new restart with a small delay
        restartRunnable = Runnable {
            if (isListening.value) {
                startSpeechRecognizer()
            }
        }
        handler.postDelayed(restartRunnable!!, 100)
    }
    
    private fun cancelPendingRestarts() {
        restartRunnable?.let { handler.removeCallbacks(it) }
        restartRunnable = null
    }
    
    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            
            // Set language explicitly
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage.value)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguage.value)
            
            // Only specify "prefer offline" - let the system handle timeouts
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            
            // Multiple results for better accuracy
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }
    
    private fun startListening() {
        // Only reset text when starting fresh (not restarting)
        if (!isListening.value) {
            recognizedText.value = ""
        }
        
        isListening.value = true
        startSpeechRecognizer()
    }
    
    private fun startSpeechRecognizer() {
        try {
            speechRecognizer.startListening(createRecognizerIntent())
            Log.d(TAG, "Started listening with language: ${selectedLanguage.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recognition: ${e.message}")
            scheduleRestart() // Try again if there was an error
        }
    }
    
    private fun stopListening() {
        isListening.value = false
        cancelPendingRestarts()
        speechRecognizer.stopListening()
        Log.d(TAG, "Stopped listening")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cancelPendingRestarts()
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