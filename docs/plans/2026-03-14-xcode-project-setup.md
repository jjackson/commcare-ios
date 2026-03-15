# Xcode Project Setup for CommCare iOS

## Overview

The CommCare iOS app is built with Compose Multiplatform + Kotlin Multiplatform (KMP). The Kotlin/Native compiler produces an iOS framework (`CommCareApp.framework`) that is embedded in a native iOS app via Xcode.

## Prerequisites

- macOS with Xcode 15+ installed
- Android Studio (for Gradle/KMP toolchain)
- JDK 17 (bundled with Android Studio)

## Step 1: Build the iOS Framework

```bash
cd commcare-ios/app
../commcare-core/gradlew linkReleaseFrameworkIosSimulatorArm64
# Or for device:
../commcare-core/gradlew linkReleaseFrameworkIosArm64
```

The framework is output to:
```
app/build/bin/iosSimulatorArm64/releaseFramework/CommCareApp.framework
```

## Step 2: Create Xcode Project

1. Open Xcode → File → New → Project
2. Choose **App** template (iOS)
3. Product Name: `CommCare`
4. Organization: `org.commcare`
5. Interface: **SwiftUI**
6. Language: **Swift**
7. Save in `commcare-ios/iosApp/`

## Step 3: Add the Framework

1. In Xcode, select the project in the navigator
2. Select the `CommCare` target → General → Frameworks, Libraries, and Embedded Content
3. Click **+** → Add Other → Add Files → navigate to `app/build/bin/iosSimulatorArm64/releaseFramework/CommCareApp.framework`
4. Set embed mode to **Embed & Sign**

## Step 4: Configure Build Phase (Auto-Rebuild Framework)

1. Select the `CommCare` target → Build Phases
2. Click **+** → New Run Script Phase
3. Name it "Build KMP Framework"
4. Move it **before** "Compile Sources"
5. Script:

```bash
cd "$SRCROOT/../app"
../commcare-core/gradlew :linkDebugFrameworkIosSimulatorArm64 \
    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
    -Pkotlin.native.cocoapods.archs=$ARCHS
```

## Step 5: Configure Framework Search Paths

1. Select the `CommCare` target → Build Settings
2. Search for "Framework Search Paths"
3. Add: `$(SRCROOT)/../app/build/bin/iosSimulatorArm64/debugFramework`

## Step 6: Swift Entry Point

The existing Swift entry point files are at:
- `app/src/iosMain/kotlin/org/commcare/app/MainViewController.kt` — creates the Compose UI controller

In `iosApp/CommCare/ContentView.swift`:

```swift
import SwiftUI
import CommCareApp

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

## Step 7: Run

1. Select an iOS Simulator target (e.g., iPhone 15, arm64)
2. Build & Run (Cmd+R)

## Notes

- **Debug vs Release**: Use `linkDebugFramework*` for development (faster builds, includes debug symbols) and `linkReleaseFramework*` for App Store builds
- **CocoaPods alternative**: KMP supports CocoaPods integration via `cocoapods` plugin in `build.gradle.kts`, which automates framework embedding. Not currently configured.
- **Framework is static**: `isStatic = true` in `build.gradle.kts` — no dynamic library issues
- **Compose Multiplatform**: The UI is fully in Kotlin/Compose; Swift only provides the hosting UIViewController
