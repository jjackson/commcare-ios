# Xcode Project Setup Guide

**Date:** 2026-03-15
**Status:** Documentation only (requires macOS)

## Overview

The CommCare iOS app uses Kotlin Multiplatform + Compose Multiplatform. The Kotlin code compiles to a static framework (`CommCareApp.framework`) via Gradle. A thin SwiftUI shell wraps the Compose UI and provides the iOS app entry point.

## What Already Exists

### Swift Entry Points (`app/iosApp/iosApp/`)
- **iOSApp.swift** — `@main` SwiftUI app struct
- **ContentView.swift** — `UIViewControllerRepresentable` bridge that calls `MainViewControllerKt.MainViewController()` from the Kotlin framework
- **Info.plist** — Basic app configuration (portrait only, single scene)

### Kotlin Framework Output
- **Framework name:** `CommCareApp` (static)
- **Targets:** `iosArm64` (device), `iosSimulatorArm64` (simulator)
- **Build command:** `./gradlew linkDebugFrameworkIosSimulatorArm64` (from `app/`)
- **Output path:** `app/build/bin/iosSimulatorArm64/debugFramework/CommCareApp.framework`

### CI (`ios-build.yml`)
Already builds and tests both the commcare-core and app frameworks on macOS-14.

## Steps to Create Xcode Project

### 1. Generate the Framework

```bash
cd commcare-ios-jj/app
./gradlew linkDebugFrameworkIosSimulatorArm64
```

This produces `CommCareApp.framework` in `build/bin/iosSimulatorArm64/debugFramework/`.

### 2. Create Xcode Project

1. Open Xcode, File > New > Project
2. Choose **App** template (iOS)
3. Product Name: **CommCare**
4. Organization: `org.commcare`
5. Interface: **SwiftUI**
6. Language: **Swift**
7. Save to `app/iosApp/` directory

### 3. Add Swift Source Files

Replace the generated ContentView.swift with the existing files:
- Copy `iosApp/iOSApp.swift` into the Xcode project
- Copy `iosApp/ContentView.swift` into the Xcode project
- Copy `iosApp/Info.plist` into the project

### 4. Embed the Kotlin Framework

1. Select the project target > **General** tab
2. Under "Frameworks, Libraries, and Embedded Content", click **+**
3. Click "Add Other" > "Add Files" and select `CommCareApp.framework` from `build/bin/iosSimulatorArm64/debugFramework/`
4. Set embed mode to **"Do Not Embed"** (it's a static framework)

### 5. Configure Framework Search Paths

1. Target > **Build Settings** > search for "Framework Search Paths"
2. Add: `$(SRCROOT)/../../build/bin/iosSimulatorArm64/debugFramework`
3. For debug on device, also add: `$(SRCROOT)/../../build/bin/iosArm64/debugFramework`

### 6. Add Build Phase Script (Optional)

To auto-build the framework before each Xcode build:

1. Target > **Build Phases** > click **+** > "New Run Script Phase"
2. Move it before "Compile Sources"
3. Script:
```bash
cd "$SRCROOT/../.."
./gradlew :app:linkDebugFrameworkIosSimulatorArm64
```

Note: This adds ~30-60s to each build. For faster iteration, build the framework manually and skip this step.

### 7. Set Bundle Identifier

1. Target > **Signing & Capabilities**
2. Bundle Identifier: `org.commcare.ios`
3. Team: Select your development team

### 8. Run on Simulator

1. Select an iOS simulator (iPhone 15 Pro recommended)
2. Build and run (Cmd+R)

## Build Configurations

| Configuration | Framework Path | Gradle Task |
|---|---|---|
| Debug (Simulator) | `build/bin/iosSimulatorArm64/debugFramework/` | `linkDebugFrameworkIosSimulatorArm64` |
| Release (Simulator) | `build/bin/iosSimulatorArm64/releaseFramework/` | `linkReleaseFrameworkIosSimulatorArm64` |
| Debug (Device) | `build/bin/iosArm64/debugFramework/` | `linkDebugFrameworkIosArm64` |
| Release (Device) | `build/bin/iosArm64/releaseFramework/` | `linkReleaseFrameworkIosArm64` |

## Troubleshooting

### "No such module 'CommCareApp'"
- Ensure the framework was built: `./gradlew linkDebugFrameworkIosSimulatorArm64`
- Check Framework Search Paths point to the correct build output directory

### Linker errors about missing symbols
- The framework is static (`isStatic = true`). Make sure embed mode is "Do Not Embed"
- Ensure both `CommCareCore.framework` (engine) and `CommCareApp.framework` (app) are linked

### "Cannot find 'MainViewControllerKt' in scope"
- The function `MainViewController()` in Kotlin generates `MainViewControllerKt.MainViewController()` in Swift
- Verify the import: `import CommCareApp`

## Architecture

```
Xcode Project
├── iOSApp.swift          # @main SwiftUI entry point
├── ContentView.swift     # ComposeView bridge (UIViewControllerRepresentable)
├── Info.plist
└── Linked Frameworks
    └── CommCareApp.framework (static, from Gradle build)
        └── MainViewControllerKt.MainViewController()
            └── Compose UI (App → LoginScreen → HomeScreen → ...)
```
