# Cipher Virtual

**Cipher Virtual** is an Android application built with Kotlin and Jetpack Compose for organizing installed apps in a unified virtual workspace, customizing device hardware profiles, monitoring system resources, and checking Play Integrity status.

---

## Features

- **Unified Virtual Workspace:** Import installed apps and games into a single workspace, manage multiple app instances with custom labels and notes, track launch counts, and launch via standard or work profiles when available.
- **Hardware Profile Studio:** Switch between built-in device presets or create custom hardware profiles with configurable model details, SDK level, and virtual DPI.
- **Resource Monitoring:** Real-time tracking for device RAM, JVM/native heap usage, CPU load, battery state, and thermal status while the app is active in the foreground.
- **Play Integrity Check:** Supports standard Play Integrity token requests, periodic background checks, and optional server-side token verification.
- **Automatic Permission Prompting:** Checks for missing required device and system permissions on launch and resume, prompting the user directly and closing the app if declined.
- **Adaptive Material 3 UI:** Supports phones, foldables, and tablets with dark/light theme toggling.

---

## Tech Stack

- **Language & UI:** Kotlin, Jetpack Compose, Material 3, Navigation Compose
- **Architecture:** MVVM + Repository pattern with Kotlin Coroutines and `StateFlow`
- **Persistence:** Room (SQLite), Jetpack DataStore, and `SharedPreferences`
- **Security & Optimization:** Google Play Integrity API, R8 code & resource shrinking
- **Testing:** JUnit 4, Robolectric, and Compose UI Test

---

## Getting Started

### Prerequisites

- Android Studio (with JDK 21)
- Android SDK (API 36)

### Optional Configuration

To enable Google Play Integrity token requests and backend verification, provide these Gradle properties or environment variables:

```bash
gradle :app:assembleDebug \
  -PPLAY_INTEGRITY_CLOUD_PROJECT_NUMBER=123456789012 \
  -PPLAY_INTEGRITY_VERIFICATION_URL=https://api.example.com/integrity/verify
```

---

## Common Commands

```bash
# Build Debug APK
gradle :app:assembleDebug

# Run unit & Robolectric tests
gradle :app:testDebugUnitTest

# Run Android Lint
gradle :app:lintDebug

# Verify R8 release shrinking
gradle :app:minifyReleaseWithR8
```

---

## CI

GitHub Actions (`.github/workflows/build.yml`) runs unit tests, Android Lint, R8 minification verification, and builds the debug APK on every push.
