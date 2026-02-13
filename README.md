# Cyberspace Android

A high-performance, mobile-optimized Android client for the **Cyberspace** platform. This app is designed to provide a native-like experience for [cyberspace.online](https://cyberspace.online), featuring advanced dynamic theming, seamless navigation, and custom UI enhancements.

## 🚀 Key Features

*   **Seamless Refresh:** Uses a dual-WebView "double buffering" strategy to eliminate loading flickers. Content is swapped only when the page is fully rendered.
*   **Dynamic Native Theming:** Real-time synchronization of Android system bars and UI components with the website's CSS variables and theme state.
*   **Web-to-Native Font Bridging:** Automatically downloads, converts (WOFF to OTF), and applies the platform's custom typography to native app elements.
*   **Mobile-Optimized UI:** Injects custom SVGs and modifies DOM elements to transform desktop-centric web buttons into mobile-friendly icons.
*   **Edge-to-Edge Experience:** Full support for modern Android 15 (SDK 35) edge-to-edge layouts and gesture navigation.
*   **Deep Linking:** Seamlessly handles `cyberspace.online` links from other apps.

## 🛠 Technical Highlights

*   **WOFF Converter:** A custom binary implementation to handle web fonts which are not natively supported by Android's `Typeface` API.
*   **Mutation Observers:** Persistent JS bridges to monitor DOM and theme changes without manual polling.
*   **Smart Refresh:** A custom `SwipeRefreshLayout` implementation that handles complex WebView scroll states to prevent accidental triggers.

## 📥 Installation

To try out the app, you can download the latest APK from the repository:

1.  **Download:** [Download cyberspace-v1.0.apk](./bin/cyberspace-v1.0.apk?raw=true)
    *   *Note: If you are on GitHub, click the link then click "Download" on the next page, or right-click and "Save Link As...".*
2.  **Install:** Open the `.apk` file on your Android device.
3.  **Permissions:** You may need to enable "Install from Unknown Sources" in your device settings.

## 🏗 Development

### Prerequisites
*   Android Studio Ladybug or newer.
*   JDK 17.
*   Android SDK 35 (API 35).

### Building from source
```bash
./gradlew assembleDebug
```

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.
