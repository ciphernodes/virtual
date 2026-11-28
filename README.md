# Cipher Virtual

**Cipher Virtual** (`com.cipher.virtual`) is a multi-instance Android virtualization workspace, hardware profile studio, and system telemetry host built with **Kotlin**, **Jetpack Compose**, and a custom **Neumorphic (Soft UI) Material 3** design system. Targeting **Android 8.0 (API 26) through Android 16 (API 36 / compileSdk 36.1)** with **64-bit ABI filtering (`arm64-v8a`, `x86_64`)**, it isolates per-instance app storage (`data`, `cache`, `databases`, `shared_prefs`), routes instances across worker process slots (`:w0`–`:w7`), monitors live host CPU/RAM/battery/thermal telemetry, enforces hardware/special-access permission bridging (including a foreground host sync service and Doze exemption rationale), and integrates Google Play Integrity Standard API verification.

---

## Table of Contents

1. [Honest Implementation Status](#honest-implementation-status)
2. [Core Capabilities](#core-capabilities)
3. [Architecture & Dependency Graph](#architecture--dependency-graph)
4. [Multi-Instance Virtual Workspace Engine](#multi-instance-virtual-workspace-engine)
5. [Virtual Execution Engine & Worker Container](#virtual-execution-engine--worker-container)
6. [Persistence, Database Migrations & Backup Rules](#persistence-database-migrations--backup-rules)
7. [Hardware Profile Studio](#hardware-profile-studio)
8. [App Discovery, Cloning & Launch Routing](#app-discovery-cloning--launch-routing)
9. [Live Resource Telemetry & Play Integrity Security](#live-resource-telemetry--play-integrity-security)
10. [Virtual Vault & Storage Access Framework (SAF)](#virtual-vault--storage-access-framework-saf)
11. [Device Permission Bridge & Background Execution](#device-permission-bridge--background-execution)
12. [Neumorphic (Soft UI) Design System & Adaptive UI](#neumorphic-soft-ui-design-system--adaptive-ui)
13. [Project Structure](#project-structure)
14. [Build Configuration, Secrets & ProGuard/R8](#build-configuration-secrets--proguardr8)
15. [Testing & Verification Commands](#testing--verification-commands)
16. [Continuous Integration (CI)](#continuous-integration-ci)

---

## Honest Implementation Status

| Component | Current Verified Status |
|---|---|
| **Namespace & 64-bit ABI Config** | Unified under `com.cipher.virtual` (including JNI symbols); `ndk.abiFilters` restricted to `arm64-v8a` & `x86_64`. |
| **Background Execution & Doze Flow** | Implemented & Robolectric-tested: `CipherHostService` (`foregroundServiceType="dataSync"`) calls `startForeground()` immediately in `onCreate()`, `BootCompletedReceiver` re-arms on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`, and `PermissionPromptHost` displays a dedicated `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` Doze rationale dialog. |
| **Per-Instance Storage & Context Routing** | Implemented & tested: `WorkspaceStorageManager`, `WorkspaceManager`, `WorkspaceProcessRouter` (`:w0`–`:w7`), `WorkspaceContextWrapper`, and atomic `<map>` XML `WorkspaceSharedPreferences`. |
| **Android Work Profile Isolation** | Implemented via `CrossProfileApps.startMainActivity` when a managed work profile exists on-device. |
| **Unified Room Schema (v1 → v7)** | Implemented & verified via `MigrationTestHelper` (`MIGRATION_1_2` through `MIGRATION_6_7` merging legacy cloned rows into `virtual_apps`). |
| **Native `.so` Syscall Interception** | **Not yet compiled/active:** CMake/NDK `externalNativeBuild` is not wired yet, and `libciphervirtual.so` has no inline libc hooks (`Dobby`/`ShadowHook`). `NativeVirtualEngine` honestly reports unavailability (`isNativeEngineAvailable() == false`) instead of faking path redirections. |
| **Standalone APK Container Execution** | **Not yet implemented:** `VirtualAppContainerActivity` runs on `:w0`–`:w7` and demonstrates isolated `WorkspaceContextWrapper` I/O, while launching a target package delegates to Work Profile or Host Launcher (`getLaunchIntentForPackage`). |
| **Device Profile Spoofing on Target Apps** | Stored & inspected inside Cipher Virtual metadata (`DeviceProfileManager` + Room), not yet injected into external target app processes. |

---

## Core Capabilities

- **Isolated Multi-Instance Storage (`WorkspaceStorageManager`):** Dedicated internal and optional external storage subtrees (`files/workspaces/<instanceId>/{data,cache,databases,shared_prefs}`) per app instance with strict canonical path-traversal sanitization, inspection, wipe/reset, and deletion APIs.
- **Persistent Instance Registry (`WorkspaceManager` + `WorkspaceRegistryJson`):** Room-backed `WorkspaceInstance` lifecycle management (`createInstance`, `switchActiveInstance`, `clearInstanceData`, `removeInstance`, `updateWorkerSlot`) protected by coroutine `Mutex` locks with atomic `.tmp` JSON registry fallback (`workspaces/registry.json`).
- **Worker Process & Context Routing (`WorkspaceProcessRouter` + `WorkspaceSharedPreferences`):** Least-loaded assignment across isolated worker process slots (`:w0`–`:w7`), restart hydration, and `WorkspaceContextWrapper` routing `filesDir`, `cacheDir`, `dataDir`, SQLite paths, and isolated `<map>` XML `SharedPreferences`.
- **Foreground Host Sync & Boot Persistence (`CipherHostService` + `BootCompletedReceiver`):** `dataSync` foreground service promoting immediately in `onCreate()`, started from `MainActivity.onStart()` and re-armed across `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`.
- **Virtual Container & Native JNI Bridge (`VirtualAppExecutionEngine`, `NativeVirtualEngine`, `VirtualProcessHostService`, `VirtualAppContainerActivity`):** Manages `:w0`–`:w7` worker process sessions, binds virtual UID/slot metadata, probes root binaries (`RootDetectionHelper`), and transparently reports native `libciphervirtual.so` load status without faking syscall hooks.
- **Hardware Profile Studio (`DeviceProfile` + `DeviceProfileManager`):** 7 built-in device profiles (`ROG Phone 8 Pro`, `Pixel 9 Pro`, `Pixel 8`, `Galaxy S24 Ultra`, `Galaxy S24`, `RedMagic 9S Pro`, `Native Hardware`) plus a `CustomProfileBuilder` supporting up to 12 persisted user-defined profiles, SDK targeting (26–36), and virtual DPI scaling (240–640 dpi).
- **App Scanner & Launch Router (`DeviceAppScanner` + `AppManifestCloningHelper`):** Fast package query with synchronized `LruCache<String, ImageBitmap>(256)`, bounded-concurrency (`Semaphore(4)`) icon decoding, manifest inspection (`activities`, `services`, `providers`, `requestedPermissions`), multi-instance cloning (`Instance #1`, `Instance #2`, ...), and Work Profile (`CrossProfileApps`) vs. Host Launcher routing.
- **Live Telemetry & Security (`ResourceMonitorService` & `IntegrityManager`):** Real-time CPU/RAM/heap/storage/battery/thermal sampling with 24-point sparkline history, per-space resource allocation breakdowns, and Google Play Integrity token + optional HTTPS backend verdict verification.
- **Virtual Vault (`VirtualVaultCard`):** Zero-permission Android Photo Picker (`PickVisualMedia`) attachments persisted in DataStore alongside Storage Access Framework (`OpenDocumentTree`) persistable folder grants.
- **Neumorphic (Soft UI) Adaptive Interface:** Custom `Modifier.neuExtruded` and `Modifier.neuPressed` primitives across Obsidian Slate (Dark) and Soft Alabaster (Light) palettes, with `NavigationBar`/`NavigationRail` and 1/2/3-column responsive grid breakpoints.

---

## Architecture & Dependency Graph

Cipher Virtual follows **MVVM + Repository** with manual **Constructor Injection** anchored in an `Application`-scoped container (`CipherVirtualApp.kt`):

- **`CipherVirtualApp` (`Application`):** Lazily instantiates singletons shared across `MainActivity`, `VirtualAppContainerActivity`, ViewModels, and bound services:
  - `OctoDatabase` (`cipher_virtual.db`, v7) &rarr; `octoDao()`, `workspaceInstanceDao()`
  - `WorkspaceStorageManager` &rarr; `WorkspaceManager` &rarr; `WorkspaceProcessRouter`
  - `DeviceAppScanner`, `DeviceBridgeManager`, `PreferencesStore`, `DeviceProfileManager`
  - `ResourceMonitorSampler`, `IntegrityManager`
  - `VirtualAppExecutionEngine`
  - `OctoRepository`
- **`MainActivity` (`ComponentActivity`):**
  - Configures edge-to-edge rendering (`enableEdgeToEdge()` + reactive `SystemBarStyle` synced to `uiState.darkThemeEnabled`).
  - Starts `CipherHostService` (`dataSync`) and binds `IntegrityCheckService` and `ResourceMonitorService` in `onStart()`, unbinding cleanly in `onStop()`.
  - Toggles foreground telemetry activity (`setForegroundActive(true/false)`) and re-evaluates device permissions in `onResume()`.
- **`OctoViewModel`, `OctoUiState`, `DeviceProfileStateController`, & `ServiceTelemetryBridge`:**
  - Combines Room `Flow`s, DataStore preferences, scanner results, icon cache, bridge snapshot, Play Integrity status, resource telemetry, active device profile, native engine availability/warning state, and inspected workspace stats into a single `StateFlow<OctoUiState>` (`SharingStarted.WhileSubscribed(5000)`).
  - Delegates hardware profile switching/persistence to `DeviceProfileStateController` and service/fallback telemetry polling to `ServiceTelemetryBridge` (3-second foreground fallback on `Dispatchers.Default` when unbound).

---

## Multi-Instance Virtual Workspace Engine

### 1. Isolated Storage Layout (`WorkspaceStorageManager.kt`)

Every registered instance receives a sanitized root folder under internal storage (`<filesDir>/workspaces/<instanceId>/`) and optional app-specific external storage (`<externalFilesDir>/workspaces/<instanceId>/`):

```text
workspaces/
├── registry.json                    # Atomic JSON mirror/fallback registry
├── <instanceId>/
│   ├── data/                        # Isolated internal files & app payloads
│   ├── cache/                       # Isolated internal cache
│   ├── databases/                   # Isolated SQLite DB directory
│   └── shared_prefs/                # Isolated <name>.xml SharedPreferences files
└── space_<spaceId>/                 # Legacy session metadata snapshots
    └── instance_<appId>/
        └── session_config.json
```

**Safety & Lifecycle Helpers:**
- `sanitizeInstanceId(instanceId)` enforces non-blank `[A-Za-z0-9._-]+` identifiers, rejects `/`, `\`, null bytes, `.`, and `..`, and verifies canonical parent containment inside `workspacesRoot` to prevent path-traversal attacks.
- `initializeInstanceStorage(instanceId)` / `initializeInstanceStorageSync(instanceId)` creates all internal and external directories (`WorkspaceInstancePaths`).
- `inspectInstanceStorage(instanceId)` computes `InstanceWorkspaceStats` (`directoryPath`, `sizeBytes`, `fileCount`, `lastSessionRecordedAt`).
- `resetInstanceStorage(instanceId)` wipes internal & external trees and recreates clean empty directories.
- `deleteInstanceStorage(instanceId)` permanently removes all internal and external workspace directories.
- `recordSessionMetadata(space, app, profile)` captures `session_config.json` snapshots for isolated-storage launch auditing.

### 2. Instance Registry & Lifecycle (`WorkspaceManager.kt` + `WorkspaceRegistryJson.kt`)

- **`WorkspaceInstance` (`@Entity(tableName = "workspace_instances")`):**
  - `instanceId: String` (`@PrimaryKey`)
  - `label: String` (e.g., `"WhatsApp (Instance #1)"`)
  - `targetPackageName: String` (indexed)
  - `createdAt: Long`
  - `isActive: Boolean` (indexed)
  - `workerSlot: String?` (e.g., `":w0"`–`":w7"`)
- **Thread-Safe Operations (Coroutine `Mutex`):**
  - `initialize()`: Loads from `WorkspaceInstanceDao` (or migrates/imports from `registry.json` if DB is empty), ensures at most one active instance, initializes directories on disk, and emits `instances` / `activeInstance` `StateFlow`s.
  - `createInstance(...)`: Registers the entity, initializes isolated directories, optionally deactivates siblings atomically via `switchActiveAtomically()`, and persists state.
  - `switchActiveInstance(instanceId)`: Atomically flips `isActive` across Room and in-memory state.
  - `clearInstanceData(instanceId)`: Clears the instance's isolated directory tree while preserving registry membership.
  - `removeInstance(instanceId)`: Deletes registry metadata and wipes storage; automatically promotes the oldest remaining instance if the removed instance was active.
  - `updateWorkerSlot(instanceId, workerSlot)`: Persists worker process affinity (`:w0`–`:w7`).
  - `WorkspaceRegistryJson`: Pure-JVM JSON serializer & recursive-descent array parser for standalone fallback registry persistence.

### 3. Process & Context Routing (`WorkspaceProcessRouter.kt`)

- **Worker Pool (`:w0` through `:w7`, configurable):**
  - `assignWorkerSlot(instanceId)`: Selects the least-loaded worker slot (breaking ties by lowest slot index), cleanly filling `:w0`–`:w7` before reusing slots.
  - `assignAndPersistWorkerSlot(instanceId)`: Assigns a slot and writes it back to `WorkspaceManager`.
  - `hydrateFromManager()`: Restores persisted `workerSlot` affinities across cold app restarts.
  - `releaseWorkerSlot(instanceId)` / `pruneUnregisteredSlots()`: Cleans up removed or orphaned instance mappings.
- **`WorkspaceContextWrapper` (`ContextWrapper`):**
  - Creates a sandboxed Android `Context` bound to an instance's `WorkspaceInstancePaths` and assigned `workerSlot`:
    - Overrides `getDataDir()`, `getFilesDir()`, `getCacheDir()`, `getNoBackupFilesDir()`, `getExternalFilesDir()`, `getExternalCacheDir()`.
    - Routes `openFileInput`, `openFileOutput`, `deleteFile`, `fileList()`, `getDir()`.
    - Routes `getDatabasePath`, `databaseList()`, `deleteDatabase()` (including `-wal`, `-shm`, `-journal` sidecars).
    - Routes `getSharedPreferences` and `deleteSharedPreferences` to `WorkspaceSharedPreferences`.
  - `resolveActiveContext(baseContext)` and `createInstanceContext(baseContext, instanceId)` automatically ensure directories exist and hydrate worker slots.

### 4. Isolated XML Preferences (`WorkspaceSharedPreferences.kt`)

- Implements `android.content.SharedPreferences` directly inside `workspaces/<instanceId>/shared_prefs/<name>.xml`.
- Supports `String`, `Int`, `Long`, `Float`, `Boolean`, and `Set<String>` using Android's standard `<map>` XML schema.
- XXE-hardened `DocumentBuilderFactory` parsing (`disallow-doctype-decl`, disabled external entities).
- Atomic `.tmp` file writes using `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)` with safe copy fallback.
- Dispatches `OnSharedPreferenceChangeListener` callbacks on the main `Looper`.

### 5. Repository Synchronization (`OctoRepository.kt`)

- Consolidates into a single primary `VirtualSpaceEntity` (`"Cipher Workspace"`, tag `"MAIN"`), migrating any apps from secondary spaces and deleting legacy secondary directories.
- Deterministic multi-instance ID mapping: `OctoRepository.multiInstanceId(spaceId, appId)` &rarr; `space_<spaceId>_app_<appId>`.
- Automatically backfills existing `virtual_apps` rows into `WorkspaceManager` and `WorkspaceProcessRouter` on startup, prunes orphaned `space_*_app_*` entries, updates `isActive`/`workerSlot`/`virtualUid`/`storageSizeBytes`/`fileCount` in Room, and combines legacy + multi-instance workspace stats during inspection/reset.

---

## Virtual Execution Engine & Worker Container

- **`NativeVirtualEngine.kt`:** JNI bridge (`libciphervirtual.so`, `Java_com_cipher_virtual_virtualization_NativeVirtualEngine_*`) that reports honest load availability (`isNativeEngineAvailable()`, `getLoadFailureReason()`) without faking path redirects when the native library is absent or not yet compiled.
- **`RootDetectionHelper.kt`:** Probes common `su` binary paths, Magisk/KernelSU/APatch packages, and `ro.debuggable` / `test-keys` build tags, exposing `RootStatus` (`isRooted`, `rootType`, `suBinaryPath`, `selinuxEnforcing`).
- **`VirtualAppExecutionEngine.kt`:** Provisions isolated directories, registers path redirection metadata when `libciphervirtual.so` is loaded, activates the virtual UID/session on `VirtualAppEntity`, and launches `VirtualAppContainerActivity` (`VirtualWorkerContainers` `:w0`–`:w7`).
- **`VirtualAppContainerActivity.kt`:** Edge-to-edge **Neumorphic** container screen (synced to `PreferencesStore.darkThemeFlow`) displaying worker slot badge, virtual UID, OS PID, honest native library load status, isolated path mappings, live `WorkspaceContextWrapper` I/O write/file verification buttons (`>= 48.dp` touch targets), and host APK launch action.
- **`VirtualProcessHostService.kt`:** `START_STICKY` foreground service (`specialUse`) with `VirtualProcessBinder` allocating/releasing worker slots (`:w0`–`:w7`).
- **`CipherHostService.kt` & `BootCompletedReceiver.kt`:** `START_STICKY` foreground service (`foregroundServiceType="dataSync"`) promoting via `ServiceCompat.startForeground()` immediately in `onCreate()`, re-armed on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
- **`AppManifestCloningHelper.kt`:** Inspects target APK activities, services, providers, and declared permissions to validate compatibility.

---

## Persistence, Database Migrations & Backup Rules

### Room Database (`OctoDatabase.kt` — `cipher_virtual.db`, Version 7)

Room schema exports (`exportSchema = true`) are tracked under `app/schemas/com.cipher.virtual.data.local.OctoDatabase/` (`1.json`–`7.json`).

| Entity / Table | Columns & Constraints |
|---|---|
| `virtual_spaces` (`VirtualSpaceEntity`) | `id` (PK), `name`, `tag`, `accentIndex`, `deviceModelPreset`, `isolatedStorage`, `frameRateHud`, `permissionBridgeEnabled`, `virtualDpi`, `createdAt` |
| `virtual_apps` (`VirtualAppEntity`) | `id` (PK), `spaceId` (FK &rarr; `virtual_spaces.id` `ON DELETE CASCADE`, indexed), `packageName`, `displayName`, `instanceLabel`, `performanceMode`, `launchCount`, `lastLaunchedAt`, `addedAt`, `notes`, `lastProfileId`, `workerSlot`, `virtualUid`, `isActive`, `storageSizeBytes`, `fileCount` |
| `workspace_instances` (`WorkspaceInstance`) | `instanceId` (PK), `label`, `targetPackageName` (indexed), `createdAt`, `isActive` (indexed), `workerSlot` (nullable `TEXT`) |

**Migration Path:**
- **`MIGRATION_1_2`:** Adds `accentIndex`, `deviceModelPreset`, `isolatedStorage`, `frameRateHud`, `permissionBridgeEnabled`, `virtualDpi` to `virtual_spaces`.
- **`MIGRATION_2_3`:** Adds `notes` and `lastProfileId` to `virtual_apps`.
- **`MIGRATION_3_4`:** Creates `workspace_instances` table + `targetPackageName` & `isActive` indices.
- **`MIGRATION_4_5`:** Adds nullable `workerSlot` column to `workspace_instances`.
- **`MIGRATION_5_6`:** Creates legacy `cloned_app_instances` table + indices.
- **`MIGRATION_6_7`:** Consolidates the duplicate `cloned_app_instances` model into `virtual_apps` (`workerSlot`, `virtualUid`, `isActive`, `storageSizeBytes`, `fileCount`), migrates matched & orphaned clone rows, and drops `cloned_app_instances`.

### Jetpack DataStore (`PreferencesStore.kt`)

- Stored in `cipher_virtual_datastore` with automatic `SharedPreferencesMigration("cipher_virtual_prefs")`.
- Keys: `dark_theme_enabled` (default `true`), `periodic_integrity_enabled` (default `true`), `attached_vault_uris` (JSON array of SAF/Photo Picker URIs).

### Backup & Data Extraction Exclusions

Both `res/xml/data_extraction_rules.xml` (`cloud-backup` & `device-transfer`) and `res/xml/backup_rules.xml` exclude sensitive local sandbox state from device backups:
- `sharedpref`: `cipher_device_profiles.xml`, `cipher_virtual_prefs.xml`
- `database`: `cipher_virtual.db`, `cipher_virtual.db-shm`, `cipher_virtual.db-wal`
- `file`: `datastore`, `workspaces`

---

## Hardware Profile Studio

Managed by `DeviceProfile.kt` + `DeviceProfileManager.kt` and persisted in `cipher_device_profiles` SharedPreferences (both globally and scoped per `spaceId`):

### Built-in Presets

| Preset Key | Model | Manufacturer / Brand | Device / Board / Hardware | Android / SDK | Default DPI |
|---|---|---|---|---|---|
| `ROG Phone 8 Pro` | `ASUS_AI2401` | `asus` | `AI2401` / `pineapple` / `qcom` | Android 14 (34) | `440` |
| `Pixel 9 Pro` | `Pixel 9 Pro` | `Google` / `google` | `caiman` / `caiman` / `caiman` | Android 15 (35) | `480` |
| `Pixel 8` | `Pixel 8` | `Google` / `google` | `shiba` / `shiba` / `shiba` | Android 14 (34) | `420` |
| `Galaxy S24 Ultra` | `SM-S928B` | `samsung` | `e3q` / `pineapple` / `qcom` | Android 15 (35) | `480` |
| `Galaxy S24` | `SM-S921B` | `samsung` | `e1s` / `s5e9945` / `s5e9945` | Android 14 (34) | `420` |
| `RedMagic 9S Pro` | `NX769J` | `nubia` | `NX769J` / `pineapple` / `qcom` | Android 14 (34) | `440` |
| `Native Hardware` | Host `Build.MODEL` | Host `Build.MANUFACTURER` | Host device/board/hardware | Host Release / SDK | `440` |

### Custom Profiles & Sandbox Controls
- **`CustomProfileBuilder` + `CustomDeviceProfileDialog`:** Create, edit, and delete up to **12** custom hardware profiles (`profileId`, `displayName`, `model`, `manufacturer`, `brand`, `device`, `product`, `board`, `hardware`, `androidVersion`, `sdkInt` 26–36, `densityDpi` 160–640) serialized with safe JSON escaping/parsing.
- **Sandbox Configuration (`SandboxConfigScreen`):**
  - Virtual Display DPI slider (`240–640 dpi`) using local draft state committed to Room on `onValueChangeFinished` to prevent jank during drag.
  - Per-space toggles for **Isolated Storage**, **Frame Rate & Telemetry HUD**, and **Permission Bridge**.
  - One-tap **Reset Defaults** restoring `ROG Phone 8 Pro` (`440 dpi`) and default flags.

---

## App Discovery, Cloning & Launch Routing

- **App Discovery (`DeviceAppScanner.kt`):**
  - Queries `ACTION_MAIN` / `CATEGORY_LAUNCHER` via API 33+ `PackageManager.ResolveInfoFlags` (with pre-Tiramisu fallback).
  - Classifies games (`ApplicationInfo.CATEGORY_GAME` / `FLAG_IS_GAME`), identifies system apps, resolves `targetSdkVersion`, and measures APK size in MB.
  - Decodes icons on `Dispatchers.IO` bounded by `Semaphore(4)` into a synchronized `LruCache<String, ImageBitmap>(256)`, converting `Bitmap.Config.HARDWARE` bitmaps to `ARGB_8888` and preserving aspect ratios up to `84 px`.
- **Import & Multi-Instance Cloning (`SpacesDialogs.kt`, `SpacesScreen.kt`):**
  - Filterable import modal sheet (`ImportInstalledAppsSheet`) with search query, category/sort chips, and incremental icon loading.
  - Clone existing virtual app entries (`Duplicate Instance`) creating sequential labels (`Instance #1`, `Instance #2`, ...) with independent `WorkspaceStorageManager` roots and worker slots.
  - Search, performance-mode filter (`Balanced`, `Turbo`, `Battery`), and sort (`Recent`, `Most Used`, `A–Z`) inside `SpacesScreen`.
- **Instance Inspector (`AppDetailScreen.kt` + `AppDetailCards.kt`) & Launch Session (`VirtualLaunchSessionDialog`):**
  - Displays assigned worker slot (`:w0`–`:w7`), isolated directory path, byte size, file count, launch count, last launch timestamp, and active hardware profile.
  - Debounces `instanceLabel` and `notes` text edits by **350 ms** before writing to Room (flushing immediately if launched).
  - Supports **Host Launcher** (`FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`), **Work Profile** (`CrossProfileApps.startMainActivity` on Android 9+ API 28+ when managed profiles exist), or **Virtual Sandbox Container** (`VirtualAppExecutionEngine.launchInstance`).

---

## Live Resource Telemetry & Play Integrity Security

### Resource Monitor (`ResourceMonitorService.kt`)
- **Sampler Metrics (`ResourceMonitorSampler`):**
  - **System & Process Memory:** `ActivityManager.MemoryInfo` (`systemUsedRamMb`, `systemTotalRamMb`, `systemRamUsageRatio`, `systemLowMemory`), JVM heap (`appHeapUsedMb`, `appHeapMaxMb`), native heap (`Debug.getNativeHeapAllocatedSize()`).
  - **CPU & Rolling History:** Process CPU % delta via `Process.getElapsedCpuTime()` normalized across `availableProcessors()`, stored in 24-sample `ArrayDeque<Float>` ring buffers (`cpuHistory`, `heapHistory`) rendered in `LiveSparklineChart` and `MemoryAllocationBar`.
  - **Storage, Battery & Thermals:** `StatFs` internal data partition GB usage, `ACTION_BATTERY_CHANGED` / `BatteryManager` capacity & charging status, and `PowerManager.currentThermalStatus` (API 29+: `Nominal`, `Warm`, `Moderate`, `Throttled`, `Critical`).
  - **Per-Space Allocation & Stability:** Estimates RAM/CPU share per virtual space and classifies system health into `StabilityLevel.OPTIMAL`, `MODERATE` (`RAM >= 72%` or `CPU >= 50%`), or `HIGH_LOAD` (`lowMemory`, `RAM >= 88%`, or `CPU >= 80%`).
- **Service Execution:** Bound `ResourceMonitorService` runs on `Dispatchers.Default` with a `2500 ms` sampling interval (pauses automatically in background; `ServiceTelemetryBridge` maintains a `3000 ms` foreground fallback when unbound).

### Google Play Integrity (`IntegrityManager.kt` & `IntegrityCheckService.kt`)
- Integrates `com.google.android.play:integrity:1.6.0` (`IntegrityManagerFactory.createStandard`).
- Caches `StandardIntegrityTokenProvider` per Cloud Project Number (`PrepareIntegrityTokenRequest`) under a coroutine `Mutex`.
- Generates SHA-256 request hashes (`cipher-virtual:<trigger>:<timestamp>`), tracks Play Store availability, and transitions through explicit states: `IDLE`, `VERIFYING`, `TOKEN_ISSUED`, `VERIFIED`, `VERDICT_FAILED`, `CONFIGURATION_REQUIRED`, and `ERROR`.
- Supports **periodic background verification** every **15 minutes** (toggled via DataStore) and optional HTTPS backend token validation (`POST` JSON `{ "integrityToken", "packageName", "requestHash" }` to `BuildConfig.PLAY_INTEGRITY_VERIFICATION_URL`).

---

## Virtual Vault & Storage Access Framework (SAF)

Implemented in `VirtualVaultCard.kt` + `OctoViewModel`:
- **Media Attachment (`PickVisualMedia`):** Zero-permission Android Photo Picker imports images/videos into the Virtual Vault URI list without requiring broad media permissions (`READ_MEDIA_IMAGES`/`VIDEO` in picker flows).
- **Persistent Export Folder Grant (`OpenDocumentTree`):** Requests a Storage Access Framework directory URI and persists read/write permissions via `ContentResolver.takePersistableUriPermission()`, surfacing live grant badges in the Security Dashboard.

---

## Device Permission Bridge & Background Execution

Implemented in `PermissionManager.kt`, `PermissionPromptHost.kt`, `CipherHostService.kt`, `BootCompletedReceiver.kt`, and `DeviceBridgeManager.kt`:
- **All 54 Manifest Permissions Retained & Extended:** Retains all 53 existing `<uses-permission>` declarations plus `FOREGROUND_SERVICE_DATA_SYNC`.
- **Runtime Permissions Checked (`PermissionManager`):**
  - Location (`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`)
  - Contacts (`READ_CONTACTS`, `WRITE_CONTACTS`)
  - Phone & Calls (`READ_PHONE_STATE`, `CALL_PHONE`, `ANSWER_PHONE_CALLS`)
  - Call Log (`READ_CALL_LOG`, `WRITE_CALL_LOG`)
  - SMS (`READ_SMS`, `SEND_SMS`)
  - Camera (`CAMERA`), Microphone (`RECORD_AUDIO`), Calendar (`READ_CALENDAR`)
  - Nearby Bluetooth on Android 12+ API 31+ (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`)
  - Notifications on Android 13+ API 33+ (`POST_NOTIFICATIONS`)
  - Legacy Storage on Android 10 and below (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`)
- **Special-Access Permissions Checked:**
  - All Files Access on Android 11+ API 30+ (`MANAGE_EXTERNAL_STORAGE` via `Environment.isExternalStorageManager()`)
  - Display Over Other Apps (`SYSTEM_ALERT_WINDOW` via `Settings.canDrawOverlays()`)
  - Modify System Settings (`WRITE_SETTINGS` via `Settings.System.canWrite()`)
  - App Usage Access (`PACKAGE_USAGE_STATS` via `AppOpsManager.OPSTR_GET_USAGE_STATS`)
  - Install Unknown Apps (`REQUEST_INSTALL_PACKAGES` via `packageManager.canRequestPackageInstalls()`)
  - Unrestricted Battery (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` via `PowerManager.isIgnoringBatteryOptimizations()`)
- **Prompt Gate & Dedicated Doze Rationale (`PermissionPromptHost`):** Evaluates missing permissions on launch and `ON_RESUME`, launches multi-permission runtime dialogs with Settings fallback on permanent denial, displays a dedicated `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` Doze rationale dialog explaining `CipherHostService` + `BOOT_COMPLETED` behavior (`battery_rationale_dialog`), steps through other special-access intents, and finishes cleanly on decline.
- **Host Bridge Telemetry (`DeviceBridgeManager`):** Inspects active contacts count, synced calendars count, `ConnectivityManager` transport (`Wi-Fi`/`Cellular`/`Ethernet`/`VPN`) + validation + metered state, `DisplayManager` physical resolution/refresh rate/DPI, and `LocationManager` last known fix.

---

## Neumorphic (Soft UI) Design System & Adaptive UI

Cipher Virtual renders a cohesive **Neumorphic (Soft UI)** Material 3 interface (`Color.kt`, `Theme.kt`, `Neumorphism.kt`, `Type.kt`):

- **Monochromatic Dual-Tone Palettes (`NeuPalette`):**
  - **Obsidian Slate (Dark Mode):** `#1C212B` base canvas, `#202632` raised surface, `#161A22` recessed well, `#2C3545` top-left glow, `#0E1117` bottom-right drop shadow, `#394458` / `#0B0E13` bevel rim.
  - **Soft Alabaster (Light Mode):** `#E6ECF4` base canvas, `#EAF0F7` raised surface, `#DCE3EE` recessed well, `#FFFFFF` top-left highlight, `#B9C4D6` bottom-right shadow, `#FFFFFF` / `#B4C0D2` bevel rim.
  - **5 Space Accent Palettes (`spaceAccentColors`):** Cyan (`#22D3EE` / `#0284C7`), Coral (`#FF4D8D` / `#E11D48`), Mint (`#34D399` / `#059669`), Amber (`#FBBF24` / `#D97706`), and Violet (`#A78BFA` / `#7C3AED`).
- **Soft UI Primitives (`Neumorphism.kt`):**
  - `Modifier.neuExtruded`: Cached `drawWithCache` + `BlurMaskFilter` rendering top-left light bloom, bottom-right drop shadow, filled rounded surface, and diagonal 1 dp bevel stroke.
  - `Modifier.neuPressed`: Concave debossed inner well with top-left shading gradient and bottom-right specular rim.
  - Reusable components: `NeuCard`, `NeuWell`, `NeuButton` (spring-animated press depth, `>= 48.dp` touch target), `NeuTonalButton`, `NeuIconButton` (`48.dp` circle), `NeuChip` (raised unselected vs. recessed selected with accent border), and `neuTextFieldColors`.
- **Bundled Typography (`Type.kt`):** Local `.ttf` font resources in `res/font/`:
  - **Space Grotesk** (`displaySmall`–`titleLarge`)
  - **Plus Jakarta Sans** (`titleMedium`–`labelLarge`)
  - **JetBrains Mono** (`labelMedium`, `labelSmall` with `fontFeatureSettings = "tnum"` for tabular telemetry figures)
- **Adaptive Breakpoints & Accessibility:**
  - `OctoApp.kt`: `NavigationBar` below `600.dp` width; `NavigationRail` at `>= 600.dp`, inside `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`.
  - `SpacesScreen.kt`: Responsive `LazyVerticalGrid` switching between **1 column** (`< 640.dp`), **2 columns** (`>= 640.dp`), and **3 columns** (`>= 960.dp`), capped at `1160.dp`.
  - All primary buttons and icon controls (including `VirtualAppContainerActivity` actions) enforce `>= 48.dp` layout and touch dimensions, full `contentDescription` semantics, `Modifier.testTag(...)` instrumentation hooks, and localized strings in `res/values/strings.xml`.

---

## Project Structure

```text
.
├── .github/workflows/build.yml                 # CI workflow (Unit Tests, Lint, R8, Debug APK)
├── .env.example                                # Template secrets file (Secrets Gradle Plugin)
├── build.gradle.kts / settings.gradle.kts      # Root Gradle setup (Kotlin DSL)
├── gradle/libs.versions.toml                   # Version Catalog (AGP 9.1.1, Kotlin 2.4.21, Compose BOM)
└── app/
    ├── build.gradle.kts                        # App config (applicationId, SDK 26..36, 64-bit abiFilters, BuildConfig, R8)
    ├── proguard-rules.pro                      # R8 keep rules for JNI, Room, virtualization, services, Play Integrity
    ├── schemas/com.cipher.virtual.data.local.OctoDatabase/
    │   └── {1,2,3,4,5,6,7}.json                # Exported Room database schemas
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml             # 54 permissions, :w0-:w7 workers, CipherHostService, BootCompletedReceiver
        │   ├── cpp/                            # C++17 native engine source (ciphervirtual_jni.cpp, syscall_interceptor.cpp, virtual_sandbox.cpp)
        │   ├── res/
        │   │   ├── font/                       # space_grotesk.ttf, plus_jakarta_sans.ttf, jetbrains_mono.ttf
        │   │   ├── mipmap-*/                   # Adaptive launcher icons
        │   │   ├── values/strings.xml          # Localized UI, Doze rationale & service notification strings
        │   │   └── xml/{backup_rules,data_extraction_rules}.xml
        │   └── java/com/cipher/virtual/
        │       ├── CipherVirtualApp.kt         # Application dependency container
        │       ├── MainActivity.kt             # Edge-to-edge host, CipherHostService start & ServiceConnection bindings
        │       ├── data/
        │       │   ├── hardware/
        │       │   │   ├── AppManifestCloningHelper.kt # Target APK component/permission inspector
        │       │   │   ├── DeviceAppScanner.kt      # Package discovery, LRU icon cache, Work Profile routing
        │       │   │   ├── DeviceBridgeManager.kt   # Host network, display, contacts, calendar, GPS summary
        │       │   │   ├── DeviceProfile.kt         # DeviceProfile model & flat/org.json codec
        │       │   │   └── DeviceProfileManager.kt  # 7 presets + CustomProfileBuilder + SharedPreferences store
        │       │   ├── local/
        │       │   │   ├── OctoDatabase.kt          # Room DB v7 & MIGRATION_1_2..MIGRATION_6_7
        │       │   │   ├── OctoDao.kt               # VirtualSpace & unified VirtualApp SQLite queries
        │       │   │   ├── PreferencesStore.kt      # DataStore preferences & SharedPreferences migration
        │       │   │   ├── WorkspaceStorageManager.kt     # Isolated per-instance filesystem manager
        │       │   │   ├── WorkspaceManager.kt            # WorkspaceInstance entity, DAO, Mutex registry
        │       │   │   ├── WorkspaceRegistryJson.kt       # Pure-JVM JSON fallback parser & serializer
        │       │   │   ├── WorkspaceProcessRouter.kt      # :w0–:w7 routing & WorkspaceContextWrapper
        │       │   │   └── WorkspaceSharedPreferences.kt  # Thread-safe isolated XML SharedPreferences
        │       │   ├── model/
        │       │   │   ├── InstalledAppInfo.kt        # App scan & DeviceBridgeSnapshot models
        │       │   │   └── VirtualEntities.kt         # VirtualSpaceEntity & unified VirtualAppEntity
        │       │   └── repository/
        │       │       └── OctoRepository.kt        # Unified space, backfill, launch & virtual sandbox repo
        │       ├── monitoring/
        │       │   └── ResourceMonitorService.kt    # Bound telemetry Service & ResourceMonitorSampler
        │       ├── security/
        │       │   ├── IntegrityManager.kt          # Play Integrity Standard API & backend HTTP verifier
        │       │   ├── IntegrityCheckService.kt     # Bound periodic integrity verification Service
        │       │   └── PermissionManager.kt         # Runtime + Special-Access permission inspector & intents
        │       ├── service/
        │       │   ├── BootCompletedReceiver.kt     # BOOT_COMPLETED & MY_PACKAGE_REPLACED service re-arm
        │       │   ├── CipherHostService.kt         # Foreground dataSync host service (immediate startForeground)
        │       │   └── VirtualProcessHostService.kt # Foreground specialUse worker slot (:w0–:w7) service
        │       ├── virtualization/
        │       │   ├── NativeVirtualEngine.kt       # JNI bridge reporting honest load status & failure diagnostics
        │       │   ├── RootDetectionHelper.kt       # Root binary/framework detection & su helper
        │       │   ├── VirtualAppExecutionEngine.kt # Redirect registration & worker launcher
        │       │   ├── VirtualWorkerContainers.kt   # :w0–:w7 isolated process Activity declarations
        │       │   └── VirtualAppContainerActivity.kt # Neumorphic container & I/O verification UI
        │       └── ui/
        │           ├── OctoViewModel.kt             # Primary UI ViewModel coordinator
        │           ├── OctoUiState.kt               # OctoUiState, combine holders, OctoViewModelFactory
        │           ├── DeviceProfileStateController.kt # Hardware preset & space reset state helper
        │           ├── ServiceTelemetryBridge.kt    # Bound service & fallback telemetry polling bridge
        │           ├── navigation/OctoApp.kt        # NavHost, responsive NavBar/Rail, PermissionPromptHost
        │           ├── components/
        │           │   ├── OctoComponents.kt        # AppIconBadge, TelemetryPill, VirtualLaunchSessionDialog
        │           │   ├── PermissionPromptHost.kt  # Runtime, special-access & Doze battery rationale flow
        │           │   ├── DashboardMetricRow.kt    # Shared metric row & badge primitives
        │           │   ├── PlayIntegrityCard.kt     # Play Integrity status & periodic toggle card
        │           │   ├── ResourceMonitorCard.kt   # CPU sparkline, heap/RAM/storage/thermal card
        │           │   ├── HostProfileCard.kt       # Host hardware, native status & active profile card
        │           │   ├── VirtualVaultCard.kt      # Photo Picker + OpenDocumentTree vault card
        │           │   ├── SecurityDashboard.kt     # Vertical dashboard orchestrator
        │           │   └── TelemetryCharts.kt       # LiveSparklineChart & MemoryAllocationBar
        │           ├── screens/
        │           │   ├── SpacesScreen.kt          # Virtual app grid, search, filter, sort, HUD sparkline
        │           │   ├── SpacesCards.kt           # HeroWorkspaceBanner, VirtualAppCard, EmptySpaceCard
        │           │   ├── SpacesDialogs.kt         # ImportInstalledAppsSheet modal
        │           │   ├── SandboxConfigScreen.kt   # Hardware presets, DPI, toggles & SecurityDashboard
        │           │   ├── CustomDeviceProfileDialog.kt # Custom profile authoring/editing dialog
        │           │   ├── AppDetailCards.kt        # Header, environment profile & storage inspection cards
        │           │   └── AppDetailScreen.kt       # Instance inspector, notes, workspace wipe & launch
        │           └── theme/
        │               ├── Color.kt                 # Obsidian Slate & Soft Alabaster Neumorphic tokens
        │               ├── Theme.kt                 # NeuPalette CompositionLocal & M3 ColorSchemes/Shapes
        │               ├── Neumorphism.kt           # neuExtruded, neuPressed, NeuCard/Well/Button/Chip
        │               └── Type.kt                  # Bundled Space Grotesk, Plus Jakarta Sans, JetBrains Mono
        └── test/java/com/cipher/virtual/
            ├── ExampleUnitTest.kt               # Baseline JUnit sanity test
            ├── ExampleRobolectricTest.kt        # Robolectric app resource test (SDK 36)
            ├── CipherVirtualUnitTests.kt        # Pure JVM tests (storage, manager, router, profiles, engine honesty)
            └── CipherVirtualRobolectricTests.kt # Room v1->v7 migrations, ContextWrapper prefs, Service/Receiver, Compose UI tests
```

---

## Build Configuration, Secrets & ProGuard/R8

### Build Parameters (`app/build.gradle.kts`)
- **Application ID & Namespace:** `com.cipher.virtual` (`applicationId` and resource `namespace` unified)
- **ABI Filters:** `ndk { abiFilters += listOf("arm64-v8a", "x86_64") }` (64-bit only; all 32-bit ABIs excluded)
- **SDK Levels:** `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36.1`
- **Java / Kotlin:** Java 11 bytecode target, Kotlin `2.4.21`, KSP `2.3.6`
- **Signing & Release Optimization:**
  - `release` enables `isMinifyEnabled = true`, `isShrinkResources = true`, and `app/proguard-rules.pro` (preserving JNI native methods, Room entities/DAOs, Play Integrity classes, `@Keep` members, `DeviceProfile`, `com.cipher.virtual.data.model.**`, `com.cipher.virtual.virtualization.**`, `com.cipher.virtual.service.**`, `com.cipher.virtual.security.**`, and `com.cipher.virtual.monitoring.**`).
  - `debug` uses `debug.keystore` (`debug.keystore.base64` fallback in CI).

### Play Integrity Configuration (Optional)
Provide these via Gradle properties (`-P...`) or environment variables to enable live Play Integrity token issuance and optional backend verdict verification (`BuildConfig`):

```bash
gradle :app:assembleDebug \
  -PPLAY_INTEGRITY_CLOUD_PROJECT_NUMBER=123456789012 \
  -PPLAY_INTEGRITY_VERIFICATION_URL=https://api.example.com/integrity/verify
```

- Uses the **Secrets Gradle Plugin** (`com.google.android.libraries.mapsplatform.secrets-gradle-plugin`) reading `.env` with `.env.example` defaults (`android:usesCleartextTraffic="false"` enforces HTTPS for network endpoints).

---

## Testing & Verification Commands

> **Note:** Use `gradle` directly (`./gradlew` is not used in this build container).

```bash
# Build Debug APK
gradle :app:assembleDebug

# Run all pure JUnit + Robolectric + Compose UI tests
gradle :app:testDebugUnitTest

# Run fast pure JVM unit tests (storage, manager, router, profiles, telemetry, virtual engine)
gradle :app:testDebugUnitTest --tests "com.cipher.virtual.CipherVirtualUnitTests" --tests "com.cipher.virtual.ExampleUnitTest"

# Run Android Lint checks
gradle :app:lintDebug

# Verify R8 release minification and resource shrinking
gradle :app:minifyReleaseWithR8
```

### Test Suite Coverage
- **`CipherVirtualUnitTests`:** Telemetry calculations & stability states, `OctoUiState` fallback logic, unified `VirtualAppEntity` defaults, `DeviceProfile` JSON round-trips & `CustomProfileBuilder`, `WorkspaceStorageManager` directory isolation/inspection/reset/traversal-rejection, `WorkspaceManager` persistence & lifecycle, `WorkspaceProcessRouter` `:w0`–`:w7` load-balanced slot assignment & restart hydration, `NativeVirtualEngine` honest unavailable reporting, `RootDetectionHelper`, `DeviceAppScanner` aspect-ratio scaling, `resolveWorkerContainerClass`, typed navigation routes, and permission models.
- **`CipherVirtualRobolectricTests`:** In-memory Room CRUD + cascade deletion (`OctoDao`), `OctoRepository` space consolidation/backfill/orphan pruning, `MigrationTestHelper` v1 &rarr; v4 &rarr; v6 &rarr; v7 preservation (including `cloned_app_instances` consolidation & drop), `DeviceProfileManager` persistence, `SandboxConfigScreen` & `VirtualAppContainerContent` Compose UI (`>= 48.dp` touch-target assertions), `PermissionManager` & `PermissionPromptHost` dialog flows (including dedicated `battery_rationale_dialog`), `CipherHostService` immediate foreground promotion, `BootCompletedReceiver` service dispatch, and `WorkspaceContextWrapper` + `WorkspaceSharedPreferences` XML isolation tests.

---

## Continuous Integration (CI)

`.github/workflows/build.yml` runs on `push` and manual `workflow_dispatch` using `ubuntu-latest`, **JDK 21 (Temurin)**, and **Gradle 9.3.1**:
1. Restores `debug.keystore` (from `debug.keystore.base64` or via `keytool`).
2. Executes `gradle :app:testDebugUnitTest`.
3. Executes `gradle :app:lintDebug`.
4. Verifies release R8 shrinking via `gradle :app:minifyReleaseWithR8`.
5. Builds and uploads `app/build/outputs/apk/debug/*.apk` as the `CipherVirtual-debug-apk` workflow artifact (30-day retention).
