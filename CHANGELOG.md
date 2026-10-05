# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Release tags follow the `vX.Y` pattern used for the APK file names.

## [Unreleased]

## [1.0] - 2026-10-05

First public release.

### Added

- Fullscreen app for [beta.cyberspace.online](https://beta.cyberspace.online).
- Cyberspace links (`beta.cyberspace.online`) open directly in the app.
- Support for Android 5.0 (API 21) and newer.

### Notes

- The internal build number differs from the release tag: this APK reports `versionName` 1.3 and `versionCode` 4. Android compares `versionCode` when installing an update, so any future APK must use a `versionCode` higher than 4.

[Unreleased]: https://github.com/IamAndelib/CyberSpace/compare/v1.0...HEAD
[1.0]: https://github.com/IamAndelib/CyberSpace/releases/tag/v1.0
