<p align="center">
  <img src="core/design/src/main/res/drawable/hermes_logo.jpg" width="128" alt="Hermroid logo">
</p>

<h1 align="center">Hermroid</h1>

<p align="center">
  A native Android client for connecting to a self-hosted Hermes agent.
</p>

<p align="center">
  <img alt="Android API 26+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Project status" src="https://img.shields.io/badge/status-early%20alpha-F5A623">
</p>

Hermroid brings Hermes to Android with a touch-first interface built for phones and tablets. It connects directly to infrastructure you control and supports both the official Hermes Desktop server protocol and `hermes-webui`.

> [!IMPORTANT]
> Hermroid is an early alpha. The current build implements server discovery, authentication, and connection onboarding. Chat, model switching, sessions, and Android device actions remain under development.

## Current capabilities

- Native Kotlin and Jetpack Compose application
- Adaptive onboarding layout for phones and tablets
- Automatic detection of official Hermes Desktop and `hermes-webui`
- Manual protocol selection when server detection is ambiguous
- Password authentication for supported Hermes Desktop providers
- Password authentication for `hermes-webui`
- JSON-RPC WebSocket connection to the official Desktop backend
- HTTPS enforcement for public servers, with cleartext exceptions for local development and Tailscale
- Unit tests for protocol detection, authentication, and server models

## Planned capabilities

- Streaming chat with Markdown, code blocks, and tool activity
- In-chat model and reasoning selection
- Session, project, profile, task, skill, and memory management
- File selection, compression, and sharing
- Android app launching and intent-based actions
- Optional accessibility-driven device actions with explicit permission and confirmation controls
- Encrypted on-device storage for server credentials

The roadmap describes intended work, not shipped functionality. Android security boundaries will prevent Hermroid from silently changing protected system or application files on non-rooted devices.

## Requirements

- Android 8.0 (API 26) or newer
- A reachable official Hermes Desktop server or `hermes-webui` instance
- Java 17 for local builds
- Android SDK 35

The desktop application’s private `127.0.0.1` server cannot be reached from a phone. Bind an authenticated Hermes server to a network interface you trust, or expose it through a private network such as Tailscale.

## Build from source

Clone the repository and build the debug APK:

```bash
git clone https://github.com/princejain756/Hermdroid---Hermes-App-For-Android.git
cd Hermdroid---Hermes-App-For-Android
./gradlew assembleDebug
```

The APK will be written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

You can also open the repository in Android Studio and run the `app` configuration on a device or emulator.

## Connect to Hermes

1. Start a Hermes server that your Android device can reach.
2. Open Hermroid and enter the server URL, including its port when required.
3. Leave the protocol set to **Auto**, or select the server type manually.
4. Enter credentials when the server requests them.
5. Tap **Detect and connect**.

Use HTTPS for any server exposed beyond localhost or a private Tailscale address.

## Project structure

```text
app/                 Android application entry point
core/design/         Theme and shared visual assets
core/model/          Server addresses and protocol models
core/network/        Detection, REST authentication, and WebSocket transport
core/security/       Credential storage contract
feature/onboarding/  Adaptive connection and sign-in interface
```

## Development

Run unit tests, Android lint, and a debug build before opening a pull request:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The application uses a modular Gradle layout with Kotlin 2.0, Jetpack Compose, Material 3, OkHttp, Moshi, coroutines, Hilt, and AndroidX.

## Contributing

Issues and focused pull requests are welcome. Please describe the server type and Android version when reporting connection problems. Do not include passwords, cookies, server tokens, or private URLs in logs or screenshots.

## Project status

Hermroid is an independent community project. It is not an official Hermes or Nous Research application. Names and trademarks belong to their respective owners.
