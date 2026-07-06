<p align="center">
  <img src="core/design/src/main/res/drawable/hermes_logo.jpg" width="132" alt="Hermroid logo">
</p>

<h1 align="center">Hermroid</h1>

<p align="center">
  A native Android control surface for your self-hosted Hermes agent.
</p>

<p align="center">
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Status" src="https://img.shields.io/badge/status-developer%20preview-C58B12">
</p>

Hermroid brings Hermes sessions, streaming responses, models, and approved Android actions into one touch-first app for phones and tablets. It connects directly to infrastructure you control—there is no Hermroid cloud or proxy.

## What works

- Streaming chat with live reasoning and tool activity
- Session history, new chats, resume, and interruption
- In-composer model switcher grouped by provider
- Official Hermes Desktop and `hermes-webui` connections
- Password authentication and encrypted on-device connection storage
- Approval and clarification dialogs during agent runs
- Voice input and copyable fenced code blocks
- Phone and tablet layouts with a permanent session pane on larger screens
- Optional Hermroid home screen with app search and adjustable grid columns
- Approved Android actions: open apps, back/home/recents, notifications, tap labelled controls, and enter text
- WhatsApp message preparation through an official Android intent
- Multi-file ZIP creation and Android share sheet
- Trusted mode for users who explicitly choose to skip local action confirmations

## Install a development build

Hermroid currently ships from source. Clone the repository and build the debug APK:

```bash
git clone https://github.com/princejain756/Hermdroid---Hermes-App-For-Android.git
cd Hermdroid---Hermes-App-For-Android
./gradlew assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`, or open the project in Android Studio and run the `app` configuration.

Requirements:

- Android 8.0 (API 26) or newer
- A reachable Hermes Desktop or `hermes-webui` server
- Java 17 and Android SDK 35 when building locally

## Connect

1. Make the Hermes server reachable from your phone through HTTPS or a private Tailscale address.
2. Open Hermroid and enter the complete server URL, including its port.
3. Keep protocol detection on **Auto**, or choose **Official Desktop** / **hermes-webui** manually.
4. Enter the server credentials and connect.
5. Choose a session or start a new conversation.

The Desktop app's private `127.0.0.1` endpoint is only reachable from the computer itself. A phone needs an authenticated network endpoint; do not expose an unauthenticated Hermes server to the public internet.

## Android control

Hermroid follows Android's security model. Enable **Hermroid device control** in Android Accessibility settings only if you want screen actions. Every local action asks for confirmation by default.

Examples understood directly by the Android client:

```text
open WhatsApp
go back
tap Send
type hello from Hermroid
message +14155550123 on WhatsApp saying I am on my way
compress files
set the home screen to 5 columns
```

WhatsApp messages are prepared in WhatsApp for review. Hermroid does not silently send external messages in normal mode.

## Security boundaries

On a non-rooted phone, Hermroid can operate visible UI through Android Accessibility, launch supported intents, and work with files the user selects. It cannot silently read another app's private data, alter protected system files, bypass lock-screen security, or grant itself permissions.

Trusted mode removes Hermroid's local confirmation dialog; it does not bypass Android or server-side approval controls. Use it only on a device and server you trust.

## Development

Run the complete local verification suite:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Project layout:

```text
app/                  Application entry point
core/automation/      Accessibility, intents, and ZIP operations
core/design/          Theme and visual assets
core/model/           Chat, session, model, and server models
core/network/         Desktop JSON-RPC and WebUI REST/SSE clients
core/security/        Android encrypted credential storage
feature/chat/         Adaptive chat, sessions, models, and approvals
feature/launcher/     Optional Android home screen
feature/onboarding/   Detection, authentication, and connection setup
```

## Current limitations

- Passkey-only server login is not implemented.
- Hermes tools run on the connected server; Android actions currently use Hermroid's local command grammar rather than a remotely registered mobile-tool protocol.
- Physical-device compatibility still needs broader testing across Android vendors.
- A signed public release and Play Store distribution are not available yet.

## Contributing

Issues and focused pull requests are welcome. Include the Android version, device vendor, server type, and sanitized error details. Never post passwords, session cookies, private server URLs, or API keys.

Hermroid is an independent community project and is not an official Hermes or Nous Research application. Names and trademarks belong to their respective owners.

_README last reviewed: 2026-07-06._
