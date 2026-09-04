<p align="center">
  <img src="https://raw.githubusercontent.com/basebin/lilt/main/.github/assets/thumbnail.png" alt="lilt" width="100%">
</p>

<p align="center">
  <img src="https://raw.githubusercontent.com/bniladridas/lilt/main/docs/assets/hero-image.png" alt="Lilt hero image" width="720">
</p>

![Android](https://img.shields.io/badge/Android-native-6B7280?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-6B7280?style=flat-square&logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%202.0-6B7280?style=flat-square)

# Lilt

Lilt is a quiet Android messenger in progress. It is built in Kotlin with Jetpack Compose, Firebase Auth, Firestore chat sync, local message caching, and notification hooks.

The app currently opens with phone onboarding, Firebase OTP, profile setup, contact and notification prompts, then a simple chat list and message screen. Messages are cached locally and synced through Firestore, so a separate WebSocket backend is not needed for the current MVP.

The project is split into small Android modules so the app, shared UI, domain contracts, data layer, and features can grow without becoming tangled.

For build notes, Firebase setup, and payload examples, read [docs/android-setup.md](docs/android-setup.md).
