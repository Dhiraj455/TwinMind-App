# TwinMind2

An intelligent Android meeting recorder application powered by Google Gemini AI that provides real-time transcription, intelligent summaries, and comprehensive meeting insights.

## Features

### 🎙️ **Smart Recording**
- **Continuous Audio Recording**: High-quality audio capture with automatic chunking
- **Pause/Resume**: Control recording with pause and resume functionality
- **Silence Detection**: Automatically detects if microphone is working
- **Phone Call Integration**: Automatically pauses recording during incoming calls

### 📝 **Real-time Transcription**
- **Auto-Transcription**: Automatically transcribes audio chunks using Google Gemini 2.0 Flash
- **Progress Tracking**: Real-time status updates for transcription progress
- **Retry Logic**: Automatic retries with exponential backoff on failures
- **Separate Transcript View**: Dedicated screen for viewing complete transcripts

### 📊 **AI-Powered Summaries**
- **Intelligent Summarization**: Generate comprehensive meeting summaries using Gemini AI
- **Meeting Title**: Auto-generate concise meeting titles
- **Action Items**: Extract and list action items from discussions
- **Key Points**: Identify and highlight important decisions and topics
- **Separate Summary View**: Dedicated screen for viewing summaries
- **Progressive Generation**: Watch summaries generate section by section

### 🎯 **Session Management**
- **Audio Sessions**: Organize recordings by session
- **Complete Audio**: Access full audio recordings of entire sessions
- **Chunk Viewing**: View individual audio chunks
- **Status Tracking**: Track recording, transcription, and summary status

## ⚠️ IMPORTANT SETUP NOTE

**Before running the app, you MUST configure your Google Gemini API key!**

The app requires a valid Google Gemini API key to function. Follow the setup instructions below to add your key.

## Setup Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 11 or later
- Android SDK 24 (Android 7.0) or higher
- Google Gemini API key ([Get one here](https://aistudio.google.com/app/apikey))

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd TwinMind2
   ```

2. **⚠️ Configure API Key - REQUIRED STEP**

   **DO THIS BEFORE BUILDING**: You must add your Google Gemini API key to run the app!
   
   Edit the file `app/src/main/java/com/example/twinmind2/di/NetworkModule.kt` and replace `YOUR_GOOGLE_GEMINI_API_KEY_HERE` with your actual API key:
   
   ```kotlin
   fun getGeminiApiKey(context: android.content.Context): String {
       // Replace with your actual Google Gemini API key
       val hardcodedApiKey = "YOUR_GOOGLE_GEMINI_API_KEY_HERE"  // ⬅️ CHANGE THIS!
       
       // Try to get from SharedPreferences first, fallback to hardcoded
       val prefs = context.getSharedPreferences("twinmind_prefs", android.content.Context.MODE_PRIVATE)
       val savedKey = prefs.getString("gemini_api_key", "")?.takeIf { it.isNotEmpty() }
       
       return savedKey ?: hardcodedApiKey
   }
   ```
   
   **Get your API key from**: [Google AI Studio](https://aistudio.google.com/app/apikey)
   
   - Click "Create API Key"
   - Copy the generated key
   - Paste it in `NetworkModule.kt` where indicated above

3. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run the app**
   - Open the project in Android Studio
   - Connect an Android device or start an emulator
   - Click Run (Shift+F10)

## Architecture

### Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Hilt Dependency Injection
- **Database**: Room Database
- **Network**: Retrofit, OkHttp
- **AI**: Google Gemini 2.0 Flash
- **Navigation**: Jetpack Navigation Compose

### Key Components

- **MainActivity**: Main UI with recording controls and session navigation
- **RecordingService**: Foreground service handling audio recording and transcription
- **RecordingViewModel**: ViewModel managing recording state
- **TranscriptionRepository**: Handles Gemini API transcription calls
- **SummaryRepository**: Manages AI-powered summary generation
- **RecordingRepository**: Manages audio chunks and session data

### Database Schema

- **RecordingSession**: Stores session metadata (start/end times, status, complete audio path)
- **AudioChunk**: Individual 30-second audio segments with overlap for continuity
- **Transcript**: Transcribed text for each chunk
- **Summary**: AI-generated meeting summaries with title, summary, action items, and key points

## Usage

### Recording a Meeting

1. Launch the app
2. Tap **Record** to start recording
3. Use **Pause** during breaks or **Resume** to continue
4. Tap **Stop** when the meeting ends
5. View transcripts and summaries automatically

### Viewing Transcripts

1. Tap on any session to expand it
2. Click **View Transcript** button
3. See real-time transcription progress
4. Read the complete transcript

### Generating Summaries

1. Navigate to a session
2. Click **View Summary** button
3. If not generated, tap **Generate Summary**
4. Watch as sections generate:
   - Meeting Title
   - Summary
   - Action Items
   - Key Points

## Error Handling

### API Rate Limiting (429)

The app automatically handles Google Gemini API rate limits:
- **Automatic Retries**: Up to 5 retries with exponential backoff
- **Wait Times**: 2s, 4s, 8s, 16s, 32s between retries
- **User Feedback**: Clear error messages if retries fail

### Network Issues

- **Retry Logic**: Built-in retry mechanisms for failed API calls
- **Progress Tracking**: Real-time status updates during failures
- **Offline Capability**: Audio chunks saved locally for processing when online

### API Key Issues

If you encounter API key errors:
1. Verify your API key is valid at [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Check that API is enabled in Google Cloud Console
3. Ensure billing is enabled for your Google Cloud project
4. Update the key in `NetworkModule.kt`

## Permissions

The app requires the following permissions:
- `RECORD_AUDIO`: Capture audio from microphone
- `READ_PHONE_STATE`: Detect incoming calls to pause recording
- `FOREGROUND_SERVICE`: Run background recording service
- `FOREGROUND_SERVICE_MICROPHONE`: Foreground microphone access
- `POST_NOTIFICATIONS`: Show recording status notifications
- `INTERNET`: Connect to Gemini API
- `ACCESS_NETWORK_STATE`: Check network connectivity

## File Storage

Audio files are stored in the app's internal storage:
- Location: `/data/data/com.example.twinmind2/files/recordings/{sessionId}/`
- Format: WAV files (16-bit PCM, 16kHz, mono)
- Chunk files: `chunk_XXXX.wav` (30-second segments with 2-second overlap)
- Complete audio: `complete_audio.wav` (full session concatenated)

## Limitations

- **Recording Duration**: Limited by device storage
- **API Limits**: Subject to Google Gemini API quotas
- **Storage**: Audio files can consume significant storage space
- **Network**: Requires internet for transcription and summarization

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is licensed under the MIT License.

## Acknowledgments

- **Google Gemini AI**: For transcription and summarization capabilities
- **Android Architecture Components**: For modern app architecture
- **Jetpack Compose**: For declarative UI framework

## Version

Current Version: 1.0
