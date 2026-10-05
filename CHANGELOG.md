# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Release tags follow the `vX.Y` pattern used for the APK file names.

## [Unreleased]

## [1.1] - 2026-10-05

A new app, built from source in this repository.

### Changed

- **All Cyberspace themes now work.** v1.0 changed only the font when you switched themes. v1.1 shows each theme's colours and styles exactly as a browser does, because the app no longer injects anything into the page.
- Opens [cyberspace.online](https://cyberspace.online). Links to `beta.cyberspace.online` still open in the app.
- The status and navigation bars follow the active theme's colour.
- New package name, `io.github.iamandelib.cyberspace`, signed with a new release key. It installs next to v1.0 instead of replacing it.
- Requires Android 7.0 (API 24) or newer. Targets Android 15.
- Permissions cut to `INTERNET` and `ACCESS_NETWORK_STATE`.

### Added

- New glowing-globe app icon, including an Android 13+ themed (monochrome) icon.
- Android 12-style splash screen, shown until the first page appears.
- File uploads, fullscreen video, and an offline screen with a Retry button.
- An emulator test in CI that checks theme switching against the live site.

## [1.0] - 2026-10-05

First public release.

### Added

- Fullscreen app for [beta.cyberspace.online](https://beta.cyberspace.online).
- Cyberspace links (`beta.cyberspace.online`) open directly in the app.
- Support for Android 5.0 (API 21) and newer.

### Notes

- The internal build number differs from the release tag: this APK reports `versionName` 1.3 and `versionCode` 4. Android compares `versionCode` when installing an update, so any future APK must use a `versionCode` higher than 4.

[Unreleased]: https://github.com/IamAndelib/CyberSpace/compare/v1.1...HEAD
[1.1]: https://github.com/IamAndelib/CyberSpace/releases/tag/v1.1
[1.0]: https://github.com/IamAndelib/CyberSpace/releases/tag/v1.0
