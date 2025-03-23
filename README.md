# Android Speech Recognizer

A simple Android application that uses Android's built-in speech recognition service to transcribe speech in real-time.

## Features

- Real-time speech recognition with text display
- Support for both English and Chinese languages
- Automatic detection of speech pauses (stops after 5 seconds of silence)
- Clean, modern UI using Jetpack Compose
- Scrollable text area to view full transcription
- Clear text functionality

## Requirements

- Android 6.0 (API level 23) or higher
- Microphone permission

## Screenshots

[Screenshots to be added]

## How to Use

1. Launch the app
2. Select your preferred language (English or Chinese)
3. Tap "Start Listening" to begin speech recognition
4. Speak into your device's microphone
5. The recognized text will appear in the text area
6. Recognition will automatically stop after 5 seconds of silence
7. Tap "Stop Listening" to manually stop recognition
8. Use the "Clear Text" button to reset the transcription

## Technical Implementation

- Uses Android's `SpeechRecognizer` API
- Implements proper permission handling
- Sets silence timeout to 5 seconds
- Uses Jetpack Compose for the UI

## Development Environment

- Android Studio Iguana
- Kotlin 1.9+
- Gradle 8.0+

## License

[Your License Here]

## Author

[Your Name or Username] 