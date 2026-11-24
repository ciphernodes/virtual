# Cipher Virtual

**Cipher Virtual** is an Android Jetpack Compose virtual workspace organizer, multi-instance application launcher, hardware device profile studio, Play Integrity monitor, and live container resource telemetry dashboard targeting Android 8.0 through Android 16 (`minSdk 26`, `targetSdk 36`, `compileSdk 36.1`).

---

## Table of Contents

1. [Overview & Key Capabilities](#1-overview--key-capabilities)
2. [Tech Stack & Dependency Versions](#2-tech-stack--dependency-versions)
3. [Project Structure & Complete File Inventory](#3-project-structure--complete-file-inventory)
4. [Architecture & Reactive State Flow](#4-architecture--reactive-state-flow)
5. [Navigation, Screens & UI Components](#5-navigation-screens--ui-components)
6. [Data Persistence Layer](#6-data-persistence-layer)
   - [Room Database (`OctoDatabase` v3)](#room-database-octodatabase-v3)
   - [Jetpack DataStore (`PreferencesStore`)](#jetpack-datastore-preferencesstore)
   - [Isolated Workspace Storage (`WorkspaceStorageManager`)](#isolated-workspace-storage-workspacestoragemanager)
7. [Hardware, App Scanning & Device Profile Layer](#7-hardware-app-scanning--device-profile-layer)
   - [`DeviceProfileManager` & Presets](#deviceprofilemanager--presets)
   - [`DeviceAppScanner` & Work Profile Launching](#deviceappscanner--work-profile-launching)
   - [`DeviceBridgeManager`](#devicebridgemanager)
8. [Security, Permissions & Play Integrity](#8-security-permissions--play-integrity)
   - [Startup Permission Gate (`PermissionManager` & `PermissionGateScreen`)](#startup-permission-gate-permissionmanager--permissiongatescreen)
   - [Manifest Permissions & Optional Hardware Features](#manifest-permissions--optional-hardware-features)
   - [Google Play Integrity (`IntegrityManager` & `IntegrityCheckService`)](#google-play-integrity-integritymanager--integritycheckservice)
   - [Backup, Data Extraction & Network Security](#backup-data-extraction--network-security)
9. [Resource Monitoring & Telemetry](#9-resource-monitoring--telemetry)
10. [Design System & Adaptive UI](#10-design-system--adaptive-ui)
11. [Build Configuration, R8 Optimization & Secrets](#11-build-configuration-r8-optimization--secrets)
12. [Testing Suite](#12-testing-suite)
13. [CI/CD Workflow (GitHub Actions)](#13-cicd-workflow-github-actions)

---

## 1. Overview & Key Capabilities

Cipher Virtual organizes installed Android apps and games into isolated named **Virtual Spaces** (`virtual_spaces`) with per-instance configurations (`virtual_apps`), selectable or custom-built hardware emulation profiles, live system telemetry, and Play Integrity health monitoring:

- **Multi-Space Organization:** Create, rename, color-tag (5 space accent themes), switch, and delete virtual spaces (`Cipher Primary`, `Dual Account`, or custom spaces).
- **Multi-Instance App Management:** Import launcher apps from the host device, clone multiple logical instances (`Instance #1`, `Instance #2`, …) with independent labels, notes, performance modes (`Balanced`, `Turbo`, `Battery`), launch counters, and per-instance workspace JSON storage.
- **Metadata-First App Discovery & On-Demand Icons:** `DeviceAppScanner` scans `ACTION_MAIN` / `CATEGORY_LAUNCHER` packages without eagerly decoding every bitmap; icons load on demand into a `256`-entry `LruCache`.
- **Android Work Profile Support:** When a managed/work profile is present on API 28+, users can launch sessions either via standard host launcher intents or `CrossProfileApps.startMainActivity(...)`.
- **Hardware Device Profile Studio:** `DeviceProfileManager` provides 7 built-in reference presets (`ROG Phone 8 Pro`, `Pixel 9 Pro`, `Pixel 8`, `Galaxy S24 Ultra`, `Galaxy S24`, `RedMagic 9S Pro`, `Native Hardware`) plus a persistent `CustomProfileBuilder` (up to 12 custom profiles stored via JSON in `SharedPreferences`).
- **Live Resource Telemetry Service:** Bound `ResourceMonitorService` and `ResourceMonitorSampler` track system RAM, Dalvik/JVM heap, native heap, process CPU %, thermal status, battery level/charging state, internal storage usage, and per-space weighted RAM/CPU shares—automatically pausing periodic sampling when the app moves to the background.
- **Play Integrity Pipeline:** Bound `IntegrityCheckService` and `IntegrityManager` prepare Google Play Integrity Standard tokens with automatic provider invalidation recovery, SHA-256 request hashes, optional HTTPS server-side token verification, and periodic background-safe scheduling.
- **Mandatory Permission Gate & Device Bridge:** Enforces required runtime permissions on startup via `PermissionGateScreen` and exposes live Location, Contacts, Calendar, Network, Storage Access Framework (`OpenDocumentTree`), Photo Picker (`PickVisualMedia`), Document Picker (`OpenDocument`), and Battery Optimization telemetry.

> **Transparency Note:** Cipher Virtual manages logical instances, session metadata, hardware profile presets, and isolated workspace records on top of standard Android launcher / `CrossProfileApps` intents; it does not inject code into third-party APKs or bypass OS process sandboxing.

---

## 2. Tech Stack & Dependency Versions

Defined in `gradle/libs.versions.toml` and `app/build.gradle.kts`:

| Component | Library / Tool | Version |
| --- | --- | --- |
| Android Gradle Plugin | `com.android.application` | `9.1.1` |
| Kotlin & Compose Compiler | `org.jetbrains.kotlin.plugin.compose` | `2.4.21` |
| KSP | `com.google.devtools.ksp` | `2.3.6` |
| Compile / Target / Min SDK | Android SDK | `compileSdk 36` (`minorApiLevel = 1`), `targetSdk 36`, `minSdk 26` |
| Java Compatibility | `JavaVersion.VERSION_11` | `11` |
| Compose BOM | `androidx.compose:compose-bom` | `2024.09.00` |
| Material 3 & Icons | `material3`, `material-icons-core`, `material-icons-extended` | BOM-managed |
| Activity Compose | `androidx.activity:activity-compose` | `1.10.1` |
| Lifecycle & ViewModel | `lifecycle-runtime-ktx`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | `2.8.7` |
| Navigation Compose | `androidx.navigation:navigation-compose` | `2.8.9` |
| Room & KSP Compiler | `androidx.room:room-runtime`, `room-ktx`, `room-compiler` | `2.7.0` |
| Jetpack DataStore | `androidx.datastore:datastore-preferences` | `1.1.7` |
| Kotlin Coroutines | `kotlinx-coroutines-core`, `kotlinx-coroutines-android`, `kotlinx-coroutines-test` | `1.10.2` |
| Google Play Integrity | `com.google.android.play:integrity` | `1.6.0` |
| Secrets Gradle Plugin | `com.google.android.libraries.mapsplatform.secrets-gradle-plugin` | `2.0.1` |
| Google Services Plugin | `com.google.gms.google-services` (`missing.passthrough=true`) | `4.5.0` |
| Testing | `junit` (`4.13.2`), `androidx.test:core` (`1.6.1`), `androidx.test.ext:junit` (`1.3.0`), `robolectric` (`4.16.1`), `androidx.compose.ui:ui-test-junit4` | — |

Unused catalog libraries (Firebase BOM/AI/Firestore/Auth/AppCheck, Retrofit, OkHttp, Moshi, Coil, CameraX, Play Services Location, Accompanist Permissions) are kept commented out in `app/build.gradle.kts` to minimize APK size while retaining version catalog definitions.

---

## 3. Project Structure & Complete File Inventory

```text
.
├── .env.example
├── .github/workflows/build.yml
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── metadata.json
├── settings.gradle.kts
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    ├── schemas/com.example.data.local.OctoDatabase/3.json
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/
        │   │   ├── com/ciphernodes/ciphervirtual/
        │   │   │   ├── monitoring/ResourceMonitorService.kt
        │   │   │   └── security/
        │   │   │       ├── IntegrityCheckService.kt
        │   │   │       ├── IntegrityManager.kt
        │   │   │       └── PermissionManager.kt
        │   │   └── com/example/
        │   │       ├── MainActivity.kt
        │   │       ├── data/
        │   │       │   ├── hardware/
        │   │       │   │   ├── DeviceAppScanner.kt
        │   │       │   │   ├── DeviceBridgeManager.kt
        │   │       │   │   └── DeviceProfileManager.kt
        │   │       │   ├── local/
        │   │       │   │   ├── OctoDao.kt
        │   │       │   │   ├── OctoDatabase.kt
        │   │       │   │   ├── PreferencesStore.kt
        │   │       │   │   └── WorkspaceStorageManager.kt
        │   │       │   ├── model/
        │   │       │   │   ├── InstalledAppInfo.kt
        │   │       │   │   └── VirtualEntities.kt
        │   │       │   └── repository/OctoRepository.kt
        │   │       └── ui/
        │   │           ├── OctoViewModel.kt
        │   │           ├── components/
        │   │           │   ├── OctoComponents.kt
        │   │           │   ├── SecurityDashboard.kt
        │   │           │   ├── TelemetryCharts.kt
        │   │           │   └── VirtualInstanceLauncher.kt
        │   │           ├── navigation/OctoApp.kt
        │   │           ├── screens/
        │   │           │   ├── AppDetailScreen.kt
        │   │           │   ├── PermissionGateScreen.kt
        │   │           │   ├── PermissionsScreen.kt
        │   │           │   ├── SandboxConfigScreen.kt
        │   │           │   ├── SpacesDialogs.kt
        │   │           │   └── SpacesScreen.kt
        │   │           └── theme/
        │   │               ├── Color.kt
        │   │               ├── Theme.kt
        │   │               └── Type.kt
        │   └── res/
        │       ├── drawable/{ic_launcher_background.xml,ic_launcher_foreground.xml,img_empty_space.jpg,img_hero_banner.jpg,octo_icon_fg.jpg}
        │       ├── font/{jetbrains_mono.ttf,plus_jakarta_sans.ttf,space_grotesk.ttf}
        │       ├── mipmap-{anydpi-v26,hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/
        │       ├── values/{colors.xml,strings.xml,themes.xml}
        │       └── xml/{backup_rules.xml,data_extraction_rules.xml}
        └── test/java/com/example/
            ├── CipherVirtualRobolectricTests.kt
            ├── CipherVirtualUnitTests.kt
            ├── ExampleRobolectricTest.kt
            └── ExampleUnitTest.kt
```

---

## 4. Architecture & Reactive State Flow

Cipher Virtual follows an **MVVM + Repository** architecture with explicit constructor injection in `MainActivity.kt` (`OctoViewModelFactory`):

1. **Activity & Bound Services (`MainActivity`):**
   - Calls `enableEdgeToEdge()` and dynamically updates `SystemBarStyle.dark` / `SystemBarStyle.light` when `uiState.darkThemeEnabled` changes.
   - Constructs `OctoDatabase`, `DeviceAppScanner`, `DeviceBridgeManager`, `WorkspaceStorageManager`, `IntegrityManager`, `ResourceMonitorSampler`, `PreferencesStore`, and `DeviceProfileManager` (plus a localized `stringLookup` lambda).
   - Binds `IntegrityCheckService` and `ResourceMonitorService` in `onStart()` and unbinds them cleanly in `onStop()`, calling `viewModel.setForegroundActive(true/false)` to pause telemetry polling while backgrounded.
   - Refreshes `DeviceBridgeSnapshot` in `onResume()` and via `LocalLifecycleOwner` (`ON_RESUME`) so Settings/permission changes are immediately reflected.

2. **ViewModel (`OctoViewModel`):**
   - Exposes a single hot `StateFlow<OctoUiState>` (`SharingStarted.WhileSubscribed(5000)`) built from chained `combine(...)` + `flatMapLatest(...)` flows (`CoreData`, `ScannerData`, `BridgeData`, `UiFlagsData`, active/available `DeviceProfile`).
   - Automatically triggers on-demand icon loading whenever `allVirtualApps` emits packages missing from `appIconsFlow`.
   - Syncs `activeDeviceProfile` and `ResourceMonitorService.updateTrackedContainers(...)` whenever `spaces` or `allVirtualApps` change.
   - Falls back to `ResourceMonitorSampler` / `IntegrityManager` directly when services are unbound (for example during unit/UI tests) and pauses fallback polling when `isForegroundActive == false` or a bound service is active.

3. **Repository (`OctoRepository`):**
   - Mediates Room DAO queries/mutations, `DeviceAppScanner` package discovery & launching, `DeviceBridgeManager` inspections, and `WorkspaceStorageManager` per-instance file operations.

---

## 5. Navigation, Screens & UI Components

### Navigation (`OctoApp.kt` & `OctoRoute`)
Type-safe sealed route hierarchy rendered inside a responsive `Scaffold` (`WindowInsets.safeDrawing`):
- `OctoRoute.Spaces` (`"spaces"`) — Primary workspace & virtual app grid.
- `OctoRoute.Permissions` (`"permissions"`) — Runtime permission manager, bridge telemetry, SAF & Media Vault.
- `OctoRoute.Sandbox` (`"sandbox"`) — Hardware device profile studio, space toggles, Play Integrity & Resource Monitor dashboards.
- `OctoRoute.AppDetail(appId)` (`"app_detail/{appId}"` with `NavType.LongType`) — Full instance inspector screen.

On viewports `< 600.dp`, bottom `NavigationBar` is shown; on viewports `>= 600.dp` (tablets/foldables/desktop), the layout automatically switches to a side `NavigationRail`.

### Screens & Dialogs
- **`PermissionGateScreen.kt`:** Shown on launch whenever `!uiState.deviceBridge.allRequiredGranted`. Automatically requests missing runtime permissions once, allows toggling Settings / SAF Storage / Battery Optimization, and calls `PermissionManager.handlePermissionDenialAndExit(...)` (`finishAffinity()`) if required permissions are denied.
- **`SpacesScreen.kt`:**
  - `HeroWorkspaceBanner` (`img_hero_banner.jpg` + gradient scrim, space/app/permission pills, Add App CTA).
  - `VirtualInstanceLauncher` card embedded cleanly in the grid.
  - Optional live telemetry HUD card (`live_telemetry_hud_row`) with animated pulse dot, CPU/Heap/Stability pills, and `LiveSparklineChart` when `activeSpace.frameRateHud == true`.
  - Search field, performance mode filters (`All`, `Turbo`, `Balanced`, `Battery`), sort chips (`Recent`, `Most Used`, `A–Z`), and responsive 1/2/3-column `LazyVerticalGrid` of `VirtualAppCard`s (with Inspect, Clone, Remove, and Launch actions).
  - `EmptySpaceCard` (`img_empty_space.jpg`) when a space has no imported apps yet.
- **`SpacesDialogs.kt`:**
  - `ImportInstalledAppsSheet`: Bottom sheet with filter chips (`User Apps`, `Games`, `All Apps`), sort chips (`Recommended`, `A–Z`, `Size`, `SDK`), live search, lazy per-visible-item icon loading, APK size + target SDK badges, and `+1` / `Clone` triggers.
  - `CreateEditSpaceDialog`: Create or edit space name, up to 6-character uppercase tag badge, and 5-color accent swatch picker.
- **`AppDetailScreen.kt` (`Instance Inspector`):**
  - Displays package metadata (`versionName`, `targetSdk`, `apkSizeMb`, `addedAt`, `lastLaunchedAt`, `lastProfileId`), assigned `VirtualSpaceEntity` and `DeviceProfile` hardware fields, editable `instanceLabel` and `notes`, performance mode chips, optional Work Profile switch, isolated workspace file count/byte telemetry + `Reset Instance Workspace` button, and Clone / Remove / Launch actions.
- **`PermissionsScreen.kt`:**
  - `Device Permissions Bridge` card with progress indicator, `Grant All Access`, and App Settings shortcut.
  - `Live Device Bridge Telemetry` card (Location, Contacts, Calendar, Network, Battery Mode).
  - Responsive 1/2-column grid of per-permission cards (`location`, `contacts`, `camera`, `microphone`, `calendar`, `bluetooth`, `notifications`).
  - `Storage & Battery Configuration` & `Virtual Vault` (`OpenDocumentTree`, `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `PickVisualMedia(ImageAndVideo)`, `OpenDocument`, and removable attached URI list resolving `OpenableColumns.DISPLAY_NAME`).
- **`SandboxConfigScreen.kt`:**
  - `Display & Hardware Preset` selector (`LazyRow` of built-in + custom `DeviceProfile` chips with inline edit/delete icons for custom profiles) + snapped `240..640 dpi` slider.
  - `CustomDeviceProfileDialog`: Create or edit custom profiles (preserving `profileId` on edits) with `displayName`, `manufacturer`, `model`, `brand`, `device`, `product`, `board`, `hardware`, `androidVersion`, `sdkInt` (`26..36` slider), and `densityDpi` (`160..640` slider).
  - Space behavior toggles (`Isolated Storage Profile`, `Frame-Rate Telemetry HUD`, `Device Permission Bridge`) and `Reset Defaults`.
  - Embedded `SecurityDashboard`.

### Reusable Components (`ui/components/`)
- `VirtualInstanceLauncher.kt`: Lists all virtual spaces with animated border/fill (`animateColorAsState`), active pulse dot, app count, estimated RAM pill, DPI/preset summary, edit/delete actions, active `Switch`, and `New Environment` / `Configure` CTAs.
- `SecurityDashboard.kt`: Three-card telemetry suite:
  1. **Play Integrity Verification** (state badge, verdict pills for `appRecognitionVerdict` & `deviceRecognitionVerdict`, token preview, timestamp, Play Store detection, periodic toggle, `Verify Now` button).
  2. **Container Resource Monitor** (`PAUSED` / `OPTIMAL` / `MODERATE` / `HIGH LOAD` badge, `LiveSparklineChart`, `MemoryAllocationBar`, internal storage bar, battery/charging & thermal pills, per-space RAM & CPU breakdown, manual sample trigger).
  3. **Host & Workspace Profile** (host `Build` info, display panel metrics, network link, active space preset, and active `DeviceProfile` details).
- `TelemetryCharts.kt`: `AnimatedPulseDot`, `LiveSparklineChart` (cubic Bézier smoothed curve + gradient fill + peak/now labels), and `MemoryAllocationBar` (animated segmented OS / App Heap / Native Heap bar).
- `OctoComponents.kt`: `AppIconBadge`, `TelemetryPill`, and `VirtualLaunchSessionDialog` (pre-launch session sheet with active hardware profile details, Work Profile toggle, inline rename, performance mode chips, Clone, Inspect, transparent launch disclaimer, and Launch Now).

---

## 6. Data Persistence Layer

### Room Database (`OctoDatabase` v3)
- Database file: `cipher_virtual.db` (`version = 3`, `exportSchema = true`, schemas exported to `app/schemas/com.example.data.local.OctoDatabase/3.json`).
- **No destructive migration fallback:** Explicit migrations `MIGRATION_1_2` and `MIGRATION_2_3` preserve existing user spaces and apps across upgrades.

**`virtual_spaces` (`VirtualSpaceEntity`):**
| Column | Type | Default / Notes |
| --- | --- | --- |
| `id` | `Long` | Primary key (`autoGenerate = true`) |
| `name` | `String` | Workspace display name |
| `tag` | `String` | Short badge tag (normalized uppercase, max 6 chars) |
| `accentIndex` | `Int` | `0` (5-color cyclic accent index `0..4`) |
| `deviceModelPreset` | `String` | `"ROG Phone 8 Pro"` |
| `isolatedStorage` | `Boolean` | `true` |
| `frameRateHud` | `Boolean` | `true` |
| `permissionBridgeEnabled` | `Boolean` | `true` |
| `virtualDpi` | `Int` | `440` |
| `createdAt` | `Long` | `System.currentTimeMillis()` |

Default spaces seeded once via `ensureDefaultSpaces()` when table is empty:
1. `Cipher Primary` (`MAIN`, accent `0`, `ROG Phone 8 Pro`, `440 dpi`, `frameRateHud = true`)
2. `Dual Account` (`DUAL`, accent `1`, `Pixel 9 Pro`, `420 dpi`, `frameRateHud = false`)

**`virtual_apps` (`VirtualAppEntity`):**
- Foreign key `spaceId -> virtual_spaces(id)` with `onDelete = ForeignKey.CASCADE` and `Index("spaceId")`.

| Column | Type | Default / Notes |
| --- | --- | --- |
| `id` | `Long` | Primary key (`autoGenerate = true`) |
| `spaceId` | `Long` | FK -> `virtual_spaces.id` |
| `packageName` | `String` | Android package name |
| `displayName` | `String` | Resolved app label |
| `instanceLabel` | `String` | `"Instance #1"`, `"Instance #2"`, … |
| `performanceMode` | `String` | `"Balanced"`, `"Turbo"`, or `"Battery"` |
| `launchCount` | `Int` | `0` |
| `lastLaunchedAt` | `Long?` | `null` |
| `addedAt` | `Long` | `System.currentTimeMillis()` |
| `notes` | `String` | `""` (added in v3) |
| `lastProfileId` | `String?` | `null` (added in v3) |

### Jetpack DataStore (`PreferencesStore`)
- Backed by `preferencesDataStore(name = "cipher_virtual_datastore")` with automatic `SharedPreferencesMigration` from legacy `"cipher_virtual_prefs"`.
- Reactive `Flow`s & `suspend` setters for:
  - `dark_theme_enabled` (`Boolean`, default `true`)
  - `selected_space_id` (`Long`, sentinel `-1L` when unset)
  - `periodic_integrity_enabled` (`Boolean`, default `true`)
  - `attached_vault_uris` (`String` JSON array of SAF/Media URIs, max 20 recent URIs)

### Isolated Workspace Storage (`WorkspaceStorageManager`)
- Root directory: `<filesDir>/workspaces/`
- Per-space directory: `workspaces/space_<spaceId>/`
- Per-instance directory: `workspaces/space_<spaceId>/instance_<appId>/`
- Records `session_config.json` on launch (when `space.isolatedStorage == true`) containing `spaceId`, `spaceName`, `appId`, `packageName`, `instanceLabel`, `performanceMode`, `virtualDpi`, `isolatedStorage`, `recordedAt`, and active `DeviceProfile` metadata (`profileId`, `profileModel`, `profileManufacturer`, `sdkInt`).
- Supports `inspectInstanceWorkspace(...)` (file count, byte total, last recorded timestamp), `resetInstanceWorkspace(...)`, and cascading `deleteSpaceWorkspaces(...)` on space deletion.

---

## 7. Hardware, App Scanning & Device Profile Layer

### `DeviceProfileManager` & Presets
`DeviceProfile` holds `profileId` (UUID), `presetKey`, `displayName`, `model`, `manufacturer`, `brand`, `device`, `product`, `board`, `hardware`, `androidVersion`, `sdkInt`, `densityDpi`, and `isCustom`.
- Includes pure-Kotlin JSON serialization (`toJsonString()` / `fromJsonString()`) alongside `org.json.JSONObject` helpers, plus per-space and global persistence in `cipher_device_profiles` `SharedPreferences`.
- Built-in reference presets:

| Preset | Model | Manufacturer / Brand | Device / Product | Board / Hardware | Android / API | DPI |
| --- | --- | --- | --- | --- | --- | --- |
| `ROG Phone 8 Pro` | `ASUS_AI2401` | `asus` / `asus` | `AI2401` / `WW_AI2401` | `pineapple` / `qcom` | `14` / `34` | `440` |
| `Pixel 9 Pro` | `Pixel 9 Pro` | `Google` / `google` | `caiman` / `caiman` | `caiman` / `caiman` | `15` / `35` | `480` |
| `Pixel 8` | `Pixel 8` | `Google` / `google` | `shiba` / `shiba` | `shiba` / `shiba` | `14` / `34` | `420` |
| `Galaxy S24 Ultra` | `SM-S928B` | `samsung` / `samsung` | `e3q` / `e3qx` | `pineapple` / `qcom` | `15` / `35` | `480` |
| `Galaxy S24` | `SM-S921B` | `samsung` / `samsung` | `e1s` / `e1sx` | `s5e9945` / `s5e9945` | `14` / `34` | `420` |
| `RedMagic 9S Pro` | `NX769J` | `nubia` / `nubia` | `NX769J` / `NX769J` | `pineapple` / `qcom` | `14` / `34` | `440` |
| `Native Hardware` | Host `Build.MODEL` | Host `Build.*` | Host `Build.*` | Host `Build.*` | Host `RELEASE` / `SDK_INT` | `440` |

### `DeviceAppScanner` & Work Profile Launching
- Queries `Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)` using API 33+ `ResolveInfoFlags` / `PackageInfoFlags` with backwards-compatible fallbacks.
- Collects `label`, `versionName`, `isGame` (`CATEGORY_GAME` or legacy `FLAG_IS_GAME`), `isSystemApp`, `targetSdk`, and `apkSizeMb` from `publicSourceDir ?: sourceDir`.
- Maintains a `256`-entry `LruCache<String, ImageBitmap>` scaled to `84×84 px` (`ARGB_8888`, converting API 26+ `HARDWARE` bitmaps safely) and loads icons on demand via `loadAppIcons(packageNames)`.
- `launchInstalledApp(packageName, preferWorkProfile)` supports `CrossProfileApps.startMainActivity` (API 28+) when a target profile exists (`LaunchTargetMode.WORK_PROFILE`) with fallback to `Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` (`LaunchTargetMode.HOST_LAUNCHER`).

### `DeviceBridgeManager`
Builds `DeviceBridgeSnapshot` asynchronously on `Dispatchers.IO`:
- Runtime permission audit via `PermissionManager`.
- `ContactsContract.Contacts` count summary (when `READ_CONTACTS` granted).
- `CalendarContract.Calendars` count summary (when `READ_CALENDAR` granted).
- `LocationManager` active providers & last known fix coordinates (when Fine/Coarse location granted).
- `ConnectivityManager` active transport (`Wi-Fi`, `Ethernet`, `Cellular`, `VPN`), validation state, and metered state.
- `DisplayManager` physical resolution, refresh rate (Hz), and density DPI.
- Battery exemption (`PowerManager.isIgnoringBatteryOptimizations`) and persisted SAF URI presence.

---

## 8. Security, Permissions & Play Integrity

### Startup Permission Gate (`PermissionManager` & `PermissionGateScreen`)
- Evaluates 7 permission groups across Android 8–16:
  - `location` (`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` — `hasAny`)
  - `contacts` (`READ_CONTACTS`, `WRITE_CONTACTS` — `hasAll`)
  - `camera` (`CAMERA`)
  - `microphone` (`RECORD_AUDIO`)
  - `calendar` (`READ_CALENDAR`)
  - `bluetooth` (`BLUETOOTH_CONNECT`, `minApi = 31` / Android 12+)
  - `notifications` (`POST_NOTIFICATIONS`, `minApi = 33` / Android 13+)
- On startup denial, `PermissionManager.handlePermissionDenialAndExit(...)` shows a toast and exits cleanly via `activity?.finishAffinity()`.

### Manifest Permissions & Optional Hardware Features
Declared in `app/src/main/AndroidManifest.xml`:
- **Normal permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `VIBRATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **Runtime permissions:** `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `READ_CONTACTS`, `WRITE_CONTACTS`, `CAMERA`, `RECORD_AUDIO`, `READ_CALENDAR`, `BLUETOOTH` & `BLUETOOTH_ADMIN` (`maxSdkVersion="30"`), `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`.
- **Optional hardware (`android:required="false"`):** `camera`, `microphone`, `location.gps`.
- **`<queries>`:** Declares launcher intent visibility (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) for Android 11+ package visibility.

### Google Play Integrity (`IntegrityManager` & `IntegrityCheckService`)
- `IntegrityManager` wraps `IntegrityManagerFactory.createStandard(appContext)` with `Mutex` request serialization.
- Builds deterministic SHA-256 URL-safe Base64 `requestHash` (`"<packageName>|<trigger>|<timestampMillis>"`).
- Automatically refreshes expired `StandardIntegrityTokenProvider` on `INTEGRITY_TOKEN_PROVIDER_INVALID`.
- Maps Google Play Integrity errors (`API_NOT_AVAILABLE`, `PLAY_STORE_NOT_FOUND`, `NETWORK_ERROR`, `PLAY_STORE_VERSION_OUTDATED`, `PLAY_SERVICES_NOT_FOUND`, `CLOUD_PROJECT_NUMBER_IS_INVALID`, etc.) into user-readable summaries.
- State machine (`IntegrityVerificationState`): `IDLE`, `VERIFYING`, `TOKEN_ISSUED`, `VERIFIED`, `VERDICT_FAILED`, `CONFIGURATION_REQUIRED`, `ERROR`.
- Optional HTTPS backend verification (`verifyTokenWithServer`): `POST`s JSON (`integrityToken`, `requestHash`, `packageName`) with `useCaches = false`, `X-Request-Hash`, and `X-App-Package` headers; parses `verified`, `appRecognitionVerdict`, `deviceRecognitionVerdict`, or server `"error"` messages.
- `IntegrityCheckService` (`exported = false`) exposes a `LocalBinder` (`statusFlow`, `startPeriodicVerification`, `stopPeriodicVerification`, `verifyNow`) and `onStartCommand` actions (`START_PERIODIC_INTEGRITY`, `VERIFY_INTEGRITY_NOW`, `STOP_PERIODIC_INTEGRITY`). Default interval: `15 minutes`.

### Backup, Data Extraction & Network Security
- `android:usesCleartextTraffic="false"` in `AndroidManifest.xml` blocks insecure HTTP traffic app-wide.
- `xml/backup_rules.xml` (`fullBackupContent`) and `xml/data_extraction_rules.xml` (`cloud-backup` & `device-transfer`) exclude local hardware profiles (`cipher_device_profiles.xml`), legacy prefs (`cipher_virtual_prefs.xml`), Room database files (`cipher_virtual.db*`), `datastore`, and `workspaces`.

---

## 9. Resource Monitoring & Telemetry

Implemented in `com.ciphernodes.ciphervirtual.monitoring.ResourceMonitorService.kt`:
- **`ResourceMonitorSampler`:** Thread-safe (`@Synchronized`) snapshot collector computing:
  - System RAM (`ActivityManager.MemoryInfo.totalMem`, `availMem`, `lowMemory`).
  - App JVM heap (`Runtime.getRuntime()`) & Native heap (`Debug.getNativeHeapAllocatedSize()`).
  - Process CPU % via `Process.getElapsedCpuTime()` delta divided by `(elapsedRealtime delta * availableProcessors)`.
  - Internal `/data` storage (`StatFs(Environment.getDataDirectory().path)`).
  - Battery percentage & charging status (`Intent.ACTION_BATTERY_CHANGED` / `BatteryManager`).
  - Thermal label (`PowerManager.currentThermalStatus` on API 29+: `Nominal`, `Warm`, `Moderate`, `Throttled`, `Critical`).
  - Rolling 24-sample `cpuHistory` and `heapHistory` deques.
  - Per-space weighted `SpaceResourceUsage` (`estimatedRamMb` and `cpuSharePercent` proportional to registered app distribution).
  - Stability classification (`StabilityLevel`):
    - `HIGH_LOAD`: `lowMemory || ramRatio >= 0.88f || cpuPercent >= 80f`
    - `MODERATE`: `ramRatio >= 0.72f || cpuPercent >= 50f`
    - `OPTIMAL`: otherwise
- **`ResourceMonitorService` (`exported = false`):** Bound service sampling every `2500ms` while the app is in foreground (`ACTION_START_MONITORING`, `ACTION_STOP_MONITORING`, `ACTION_SAMPLE_NOW`).

---

## 10. Design System & Adaptive UI

- **Colors (`Color.kt` / `Theme.kt`):**
  - **Abyssal Cyber-Dark (Default):** Background `#080C14`, Surface `#101726`, SurfaceVariant `#182235`, SurfaceHighest `#223049`, Cyan Primary `#22D3EE`, Coral Secondary `#FF4D8D`, Mint Tertiary `#34D399`, Amber `#FBBF24`, Violet `#A78BFA`, Error `#F87171`.
  - **High-Contrast Light Fallback:** Background `#F4F7FB`, Surface `#FFFFFF`, SurfaceVariant `#E6EDF6`, Cyan `#0891B2`, Coral `#E11D48`, Mint `#059669`.
  - **5 Cyclic Space Accent Palettes (`spaceAccentColors`):** Cyan (`0`), Coral (`1`), Mint (`2`), Amber (`3`), Violet (`4`).
- **Typography (`Type.kt`):** Bundled local `.ttf` fonts in `res/font/`:
  - Display / Headlines: `Space Grotesk` (`space_grotesk.ttf`)
  - Body / UI Labels: `Plus Jakarta Sans` (`plus_jakarta_sans.ttf`)
  - Monospace Telemetry (`tnum` tabular numerals): `JetBrains Mono` (`jetbrains_mono.ttf`)
- **Shapes (`Theme.kt`):** Rounded corners `small = 10.dp`, `medium = 16.dp`, `large = 22.dp`, `extraLarge = 28.dp`.
- **Adaptive Breakpoints:**
  - `OctoApp`: Bottom `NavigationBar` (`< 600.dp`) vs. side `NavigationRail` (`>= 600.dp`).
  - `SpacesScreen`: 1 column (`< 640.dp`), 2 columns (`>= 640.dp`), 3 columns (`>= 960.dp`), max content width `1160.dp`.
  - `PermissionsScreen`: 1 column (`< 700.dp`) vs. 2 columns (`>= 700.dp`).
  - `SandboxConfigScreen` & `AppDetailScreen`: Centered column constrained to `920.dp` max width.
- **Launcher Icon:** Adaptive foreground/background XML + density-bucket mipmap icons (`mipmap-mdpi` through `mipmap-xxxhdpi` and `mipmap-anydpi-v26`).

---

## 11. Build Configuration, R8 Optimization & Secrets

- **Application ID & Namespace:**
  - `applicationId = "com.cipher.virtual"`
  - `namespace = "com.example"`
- **Release Optimization (`app/build.gradle.kts` & `app/proguard-rules.pro`):**
  - `release` enables `isMinifyEnabled = true`, `isShrinkResources = true`, `isCrunchPngs = false` using `proguard-android-optimize.txt` + `proguard-rules.pro`.
  - Keep rules preserve `SourceFile`/`LineNumberTable`, `kotlin.Metadata`, `$WhenMappings`, `@Keep` members, `Parcelable.CREATOR`, `Serializable`, enum `values()`/`valueOf()`, JNI `native` methods, Room `RoomDatabase`/`@Entity`/`@Dao`, app models/profiles/security/telemetry packages, and Google Play Integrity classes.
- **Play Integrity Configuration (`BuildConfig`):**
  Configured via Gradle properties or environment variables (defaults cleanly to `0L` / `""` when unconfigured):
  - `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER`: Google Cloud project number linked to Google Play Integrity API.
  - `PLAY_INTEGRITY_VERIFICATION_URL`: HTTPS endpoint (`https://...`) accepting JSON `{ integrityToken, requestHash, packageName }` and returning `{ verified, appRecognitionVerdict, deviceRecognitionVerdict, error? }`.
  - Example:
    ```bash
    gradle :app:assembleDebug \
      -PPLAY_INTEGRITY_CLOUD_PROJECT_NUMBER=123456789012 \
      -PPLAY_INTEGRITY_VERIFICATION_URL=https://api.example.com/integrity/verify
    ```
- **Secrets Plugin:** Uses `.env` (gitignored) with `.env.example` template; `GEMINI_API_KEY` remains commented out unless needed.

---

## 12. Testing Suite

Located in `app/src/test/java/com/example/`:

- **`CipherVirtualUnitTests.kt`** (Pure JVM unit tests):
  - `ResourceTelemetrySnapshot` RAM/storage ratio clamping & zero/overflow safety
  - `SpaceResourceUsage` & `StabilityLevel` metrics
  - `OctoUiState.activeSpace` selection and fallback behavior
  - `VirtualSpaceEntity`, `VirtualAppEntity`, `InstalledAppInfo`, and `DeviceBridgeSnapshot` defaults
  - `IntegrityStatusSnapshot` & `IntegrityVerdictResult` invariants
  - `DeviceProfile` presets, `CustomProfileBuilder`, and pure-Kotlin JSON round-trip serialization
  - `WorkspaceStorageManager` metadata write, inspection, instance reset, and space cascade deletion
  - `OctoRoute` destination pattern verification
- **`CipherVirtualRobolectricTests.kt`** (Robolectric + Compose UI tests at SDK 36):
  - `OctoDao` in-memory Room CRUD, `recordAppLaunch`, and foreign-key cascade deletion
  - `DeviceProfileManager` `SharedPreferences` custom profile persistence across reads
  - `SandboxConfigScreen` Compose tree verification (`new_custom_profile_button`, `security_dashboard_integrity_card`)
- **`ExampleUnitTest.kt`** & **`ExampleRobolectricTest.kt`**: Baseline JVM and Android resource (`R.string.app_name == "Cipher Virtual"`) checks.

Run commands (use `gradle`, not `./gradlew` when running inside containers without a wrapper):
```bash
# Run pure JVM tests
gradle :app:testDebugUnitTest --tests com.example.ExampleUnitTest --tests com.example.CipherVirtualUnitTests

# Run full unit + Robolectric suite
gradle :app:testDebugUnitTest

# Run Android Lint
gradle :app:lintDebug

# Verify R8 release shrinking
gradle :app:minifyReleaseWithR8

# Build Debug APK
gradle :app:assembleDebug
```

---

## 13. CI/CD Workflow (GitHub Actions)

Defined in `.github/workflows/build.yml` (`Build Android APK`, triggers on `push` and `workflow_dispatch`):

1. Checks out repository (`actions/checkout@v4`).
2. Installs Temurin JDK 21 (`actions/setup-java@v4`).
3. Accepts Android SDK licenses via `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses`.
4. Configures Gradle `9.3.1` (`gradle/actions/setup-gradle@v4`).
5. Restores `debug.keystore` from `debug.keystore.base64` (or generates one via `keytool` if absent).
6. Runs `gradle :app:testDebugUnitTest`.
7. Runs `gradle :app:lintDebug`.
8. Verifies release shrinking via `gradle :app:minifyReleaseWithR8`.
9. Builds `gradle :app:assembleDebug` and uploads `app/build/outputs/apk/debug/*.apk` as artifact **`CipherVirtual-debug-apk`** (30-day retention).
