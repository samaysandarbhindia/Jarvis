# JARVIS — Native Android AI Assistant

JARVIS is a native Android personal AI assistant built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room**, **DataStore**, **Firebase AI Logic (Gemini)**, **Android SpeechRecognizer / TextToSpeech**, **Google AI Edge LiteRT (openWakeWord compatible)**, and native Android system integrations.

## SDK & Build Configuration

- **Application ID**: `com.aistudio.jarvis.kxfvpm`
- **Minimum SDK**: `24` (Android 7.0)
- **Target SDK**: `36` (Android 16)
- **Compile SDK**: `36`
- **UI**: Jetpack Compose + Material 3 + Adaptive `WindowSizeClass` (`NavigationBar` / `NavigationRail`)
- **Database**: Room (`2.7.0`) via KSP
- **Preferences & Secrets**: Jetpack DataStore + Android Keystore AES-256-GCM

## Architecture

- `com.example.jarvis.ai`: Modular `AiProvider` interface (`FirebaseAiProvider` with Gemini function calling & `OpenAiCompatibleProvider`), `ToolRegistry` (24 registered tools), and permission-aware `ToolDispatcher`.
- `com.example.jarvis.voice`: Native Android `SpeechRecognizer` (supporting `createOnDeviceSpeechRecognizer` on Android 12+ and `EXTRA_PREFER_OFFLINE` for offline recognition when installed; English, Hindi, Hinglish `en-IN` / `hi-IN` / `en-US`) and `TextToSpeech` with voice selection, rate/pitch tuning, speech interruption, and real RMS dB waveform telemetry.
- `com.example.jarvis.wakeword`: Modular, account-free voice activation system with zero proprietary API keys:
  - `WakeWordEngine`: Clean interface for swappable local wake-word engines + `WakeWordPhraseMatcher` (default phrase `"JARVIS"`).
  - `OnDeviceSpeechWakeEngine`: Local `AudioRecord` RMS voice-activity gate + Android 12+ on-device `SpeechRecognizer` trigger (no continuous cloud audio streaming).
  - `OpenWakeWordTfliteEngine`: Apache-2.0 **Google AI Edge LiteRT** 3-stage `.tflite` pipeline (`melspectrogram.tflite` → `embedding_model.tflite` → `hey_jarvis.tflite`), using local model files imported via Android's Storage Access Framework (`OpenWakeWordModelStore`).
  - `VoiceActivationForegroundService`: Android 14/15/16 compliant `foregroundServiceType="microphone"` service requiring explicit user activation and displaying a persistent notification.
- `com.example.jarvis.apps`: `InstalledAppsManager` querying real launchable applications via `PackageManager` and Android 11+ `<queries>` declarations.
- `com.example.jarvis.device`: `DeviceControlManager` using `BatteryManager`, `AudioManager`, `CameraManager` (`setTorchMode`), `ConnectivityManager`, `ActivityManager`, `StatFs`, `AlarmClock`, and system `Settings` intents.
- `com.example.jarvis.accessibility` & `automation`: `JarvisAccessibilityService` + `AutomationEngine` supporting multi-step UI inspection, clicking, text entry, scrolling, retries, timeouts, and Room execution logs.
- `com.example.jarvis.memory` & `conversations`: Room persistence with JSON export/import via Storage Access Framework (`CreateDocument` / `OpenDocument`).
- `com.example.jarvis.reminders`: `ReminderScheduler` + `ReminderBroadcastReceiver` + `BootReceiver` using `AlarmManager` and Android notifications.
- `com.example.jarvis.notifications`: Opt-in `JarvisNotificationListenerService` with package exclusions and sensitive alert filtering.

## API Key & Service Setup

1. **Google Gemini (Firebase AI Logic)**:
   - Uses **Firebase AI Logic** (`Firebase.ai`) and **Firebase App Check** (`configureAppCheck(this, intent)` in `MainActivity.onCreate()`).
   - No client-side `GEMINI_API_KEY` is required.
2. **Voice & Optional Offline Wake Word (Zero Keys / Zero Accounts)**:
   - **Manual Microphone**: Works immediately via the large **Start Listening** button on the Dashboard or Chat screen.
   - **Offline Speech Recognition**: Toggle **Prefer Offline Speech Recognition** in **Config → Voice & Activation** (uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` on Android 12+ when system offline language packs are installed).
   - **Optional openWakeWord LiteRT**: Obtain Apache-2.0 `melspectrogram.tflite`, `embedding_model.tflite`, and `hey_jarvis_v0.1.tflite` models from `dscripka/openWakeWord` and import them under **Config → Voice & Activation**, then switch Activation Mode to **openWakeWord (LiteRT)** and enable the Background Microphone Service.

## Building & Testing

- **Run local JVM & Robolectric tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Build Debug APK** (in Android Studio or CLI):
  ```bash
  gradle :app:assembleDebug
  ```
  Output: `app/build/outputs/apk/debug/app-debug.apk`
- **Build Release APK**:
  ```bash
  gradle :app:assembleRelease
  ```
  Output: `app/build/outputs/apk/release/app-release.apk`

## Hardware-Dependent Testing Notes

On physical Android devices (not emulated in headless JVM tests), manually verify:
- **Manual Mic & Offline SpeechRecognizer**: Grant `RECORD_AUDIO`, tap **Start Listening**, speak in English/Hindi/Hinglish, and verify on-device recognition when offline language packs are downloaded.
- **Background Voice Activation Service**: Select *On-Device Trigger* (Android 12+) or import the 3 openWakeWord `.tflite` models, enable *Background Microphone Service*, verify the persistent notification appears, and say `"Hey JARVIS"`.
- **Accessibility Automation**: Enable *JARVIS Automation Engine* in Android Settings → Accessibility, then command `"Open YouTube and search for Iron Man"`.
- **Notification Reader**: Enable *JARVIS Notification Reader* in Android Settings → Notification Access and toggle reading on in JARVIS Config.
- **Hardware Flashlight**: Verify `CameraManager` torch toggle on physical hardware.
