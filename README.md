# Cyberspace Android

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

- JDK 17
- Android SDK (API 34)
- Gradle 8.10+

### Steps

```bash
git clone https://github.com/IamAndelib/CyberSpace.git
cd CyberSpace
./gradlew assembleRelease
```

The output APK will be at:
```
app/build/outputs/apk/release/app-release.apk
```

## Tech Stack

- Java
- AndroidX Browser & Core libraries
- Gradle 8.10 / AGP 8.3

## License

MIT License
