# Cipher Virtual

**Cipher Virtual** is an advanced Android application built with Kotlin and Jetpack Compose for organizing installed apps in unified virtual workspaces, managing fully isolated multi-instance application environments with dedicated storage and worker process slots, configuring custom hardware profiles, monitoring live system telemetry, and verifying Play Integrity status.

---

## Core Architecture & Features

- **Multi-Instance Virtual Workspace (`WorkspaceStorageManager` & `WorkspaceManager`):** Creates and manages isolated per-instance directories under internal/external storage (`files/workspaces/<instanceId>/{data,cache,databases,shared_prefs}`), ensuring independent local state without collisions. Persistence is backed by Room entities and atomic SharedPreferences wrappers.
- **Worker Process Routing (`WorkspaceProcessRouter`):** Maps instances to dedicated worker process slots (`:w0` through `:w7`) and resolves active instance contexts with strict path isolation and legacy session migration support.
- **Hardware Profile Studio:** Switch between built-in device presets (e.g., ROG Phone 8 Pro, Pixel 8, Galaxy S24 Ultra) or author custom hardware profiles with configurable model details, SDK levels, and virtual DPI sliders.
- **App Discovery & Cloning (`DeviceAppScanner`):** Fast, synchronized device app scanning with bounded-parallel icon decoding and aspect-ratio preservation.
- **Resource Monitoring (`ResourceMonitorService` & `ResourceMonitorSampler`):** Real-time tracking for device RAM, JVM/native heap usage, CPU load, battery state, and thermal status offloaded to `Dispatchers.Default`.
- **Play Integrity & Security (`IntegrityManager`):** Standard Play Integrity token requests, periodic background checks, and optional HTTPS backend token verification.
- **Virtual Vault & SAF Storage (`VirtualVaultCard`):** Zero-permission Photo Picker media attachment and Storage Access Framework (SAF) directory export permission grants.
- **Adaptive Material 3 UI:** Fluid support for phones, foldables, and tablets with responsive layouts, NavigationBar/NavigationRail breakpoints, debounced database writes, and accessibility touch targets (>= 48.dp).

---

## Tech Stack

- **Language & UI:** Kotlin, Jetpack Compose, Material 3, Navigation Compose
- **Architecture:** MVVM + Repository pattern with Kotlin Coroutines and `StateFlow`
- **Persistence:** Room (SQLite) with schema export v1–v5, Jetpack DataStore, and thread-safe XML `SharedPreferences`
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
