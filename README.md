# Cyberspace Android

[![Android CI](https://github.com/IamAndelib/CyberSpace/actions/workflows/android.yml/badge.svg)](https://github.com/IamAndelib/CyberSpace/actions/workflows/android.yml)

An unofficial Android wrapper for [beta.cyberspace.online](https://beta.cyberspace.online) — a social media platform reimagined.

This app delivers a native-like fullscreen experience using a WebView, with no browser UI, transparent status bar, and edge-to-edge layout.

## Download

Grab the latest APK from the [`releases/`](./releases/) folder and install it directly on your Android device.

> You may need to enable **"Install from unknown sources"** in your device settings.

## Features

- Fullscreen WebView — no address bar, no browser chrome
- Transparent status bar with edge-to-edge layout
- Back button navigates within the app
- JavaScript, DOM storage and caching enabled
- Works on Android 5.0 (API 21) and above

## Build from Source

### Prerequisites

- JDK 21
- Android SDK (API 34)
- Gradle 8.9+

### Steps

```bash
git clone https://github.com/IamAndelib/CyberSpace.git
cd CyberSpace
./gradlew assembleDebug
```

The output APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

For a minified release build (requires signing config):
```bash
./gradlew assembleRelease
```

## Tech Stack

- Kotlin 2.0.20
- AndroidX Core KTX & Browser libraries
- AGP 8.5.2 / Gradle 8.9
- Kotlin DSL (`*.gradle.kts`) + version catalog (`libs.versions.toml`)

## CI/CD

GitHub Actions builds the debug APK on every pull request and the signed release APK on every push to `main`. Artifacts are uploaded for 7 days (debug) / 30 days (release).

Required secrets for release signing:
- `KEYSTORE_FILE` — base64-encoded `.jks` keystore
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## License

MIT License
