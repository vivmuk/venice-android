# Venice for Android (`ai.venice.app`)

**Venice** is a native Kotlin + Jetpack Compose (Material 3) Android client offering a futuristic, ultra-fast ChatGPT-style interface powered by the full [Venice API](https://api.venice.ai/api/v1).

---

## ✨ Key Features

- **ChatGPT-Style Streaming Chat**:
  - Real-time Server-Sent Events (`POST /chat/completions` with `stream: true`) with a live **Stop** button, **Regenerate** last response, **Copy** message/code, and **New Chat**.
  - Custom Compose **Markdown Renderer** supporting headings, bold/italic, inline code, fenced code blocks with one-tap copy, bullet/numbered lists, quotes, links, and collapsible **Reasoning Traces** (`reasoning_content`).
  - Local conversation & message persistence powered by **Room SQLite**.
- **Full Venice Model Catalog & Parameter Controls**:
  - Dynamically fetches the live text model catalog via `GET /models?type=text`.
  - Default model **`deepseek-v4-1-flash`**, with **`deepseek-v4-pro`**, **`deepseek-v4-flash`**, **`deepseek-v3.2`**, and **`deepseek-v4-1-flash-uncensored`** pinned at the top of the model picker, followed by all other Venice text models.
  - Per-conversation & default controls for **Temperature** (`0.0`–`2.0`), **Reasoning Effort** (`none`, `low`, `medium`, `high`), and **Venice Web Search** (`venice_parameters.enable_web_search`).
- **Venice Image Studio**:
  - Dedicated **Image** tab powered by `POST /images/generations` and `GET /models?type=image`.
  - Select aspect ratio / resolution (`1024x1024`, `1024x768`, `768x1024`), preview generated artwork, and save directly to your Android photo gallery (`Pictures/Venice`).
- **Two-Way Voice (STT & TTS)**:
  - **Voice Input**: Tap the microphone button in the chat composer to record audio and transcribe via `POST /audio/transcriptions`.
  - **Voice Replies**: Tap **Speak** on any assistant message to synthesize natural speech via `POST /audio/speech` (`tts-kokoro`).
- **Zero Hardcoded Secrets**:
  - No API key is ever hardcoded in the source code or repository.
  - Your Venice API key is entered once in the **Settings** tab and encrypted on-device using Android Keystore (`EncryptedSharedPreferences` with `AES256_GCM`).

---

## 🔑 Where Your Venice API Key Goes

1. Generate an API key from your Venice account at **[https://venice.ai/settings/api](https://venice.ai/settings/api)**.
2. Open the **Venice** Android app and tap the **Settings** tab in the bottom navigation bar (or tap the setup banner at the top of the Chat screen).
3. Paste your key into the **Bearer API Key** field and tap **Save API Key**.
4. The key is encrypted and persisted locally via `EncryptedSharedPreferences` (`venice_secure_prefs`) and sent exclusively as `Authorization: Bearer <your_key>` to `https://api.venice.ai/api/v1`.

---

## 🛠️ How to Build

### Prerequisites
- **JDK 17** (`JAVA_HOME` pointing to OpenJDK/Temurin 17)
- **Android SDK** (`ANDROID_HOME` pointing to `~/Android/Sdk`) with:
  - `platform-tools`
  - `platforms;android-34`
  - `build-tools;34.0.0`

### Build the Debug APK
```bash
export JAVA_HOME="$HOME/.local/jdk17"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

./gradlew assembleDebug
```

The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 How to Install the APK

### Option 1: Install via ADB (USB or Wireless Debugging)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Option 2: Direct Download on Your Android Phone
1. Transfer or download `app/build/outputs/apk/debug/app-debug.apk` onto your Android device (Android 8.0 / API 26 or newer).
2. Tap the downloaded `app-debug.apk` file in your notification shade or Files app.
3. Allow installation from your browser/file manager if prompted, then tap **Install** and **Open**.
