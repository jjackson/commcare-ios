# Phase 8 Wave 1: iOS Cinterop Learnings

**Date**: 2026-03-12
**PR**: #177
**Issue**: #168

## 1. platform.CommonCrypto Doesn't Exist

Kotlin/Native's default platform bindings don't include CommonCrypto. Unlike `platform.Foundation`, `platform.Security`, or `platform.posix`, CommonCrypto requires a custom `.def` file and cinterop configuration.

**Fix**: Create `src/nativeInterop/cinterop/CommonCrypto.def` and configure `cinterops { create("CommonCrypto") }` in build.gradle.kts for each iOS target.

## 2. Custom Cinterop Not Commonized with Manual Hierarchy

With `kotlin.mpp.applyDefaultHierarchyTemplate=false` and manual `iosMain` source set creation, custom cinterop bindings generated per-target (iosArm64, iosSimulatorArm64) are NOT visible in the shared `iosMain` source set. Setting `kotlin.mpp.enableCInteropCommonization=true` doesn't help with manual hierarchies.

**Fix**: Create a shared source directory (e.g., `src/iosNativeCrypto/kotlin/`) and add it as `srcDir` to both target-specific source sets. Each target compiles the same source with its own cinterop bindings.

```kotlin
val iosCryptoDir = file("src/iosNativeCrypto/kotlin")
iosArm64Main.kotlin.srcDir(iosCryptoDir)
iosSimulatorArm64Main.kotlin.srcDir(iosCryptoDir)
```

**Lesson**: `applyDefaultHierarchyTemplate=true` would solve this automatically but doesn't work on Linux (iOS targets disabled). The shared srcDir approach works everywhere.

## 3. C Wrapper Functions for Reliable Binding Generation

Direct references to `CC_SHA256`, `CC_MD5`, etc. from system headers may not resolve in cinterop Kotlin bindings. Wrapping them in `static inline` C functions in the `.def` file's `---` section guarantees binding generation.

```def
package = CommCareCrypto
---
#include <CommonCrypto/CommonDigest.h>

static inline int cc_sha256(const void *data, unsigned int len, void *md) {
    CC_SHA256(data, len, md);
    return 0;
}
```

## 4. CommonCrypto GCM APIs Are SPI

`kCCModeGCM`, `CCCryptorGCMOneshotEncrypt`, `CCCryptorGCMOneshotDecrypt`, `CCCryptorGCMAddIV`, and `CCCryptorGCMFinal` are System Programming Interface (SPI), not part of the public CommonCrypto API. They're declared in `CommonCryptorSPI.h`, not `CommonCryptor.h`.

**Implication**: AES-GCM on iOS requires either CryptoKit (Swift-only, needs interop bridge) or a pure Kotlin implementation. CommonCrypto only supports AES-CBC/ECB publicly.

## 5. applyDefaultHierarchyTemplate and Linux Dev

Setting `kotlin.mpp.applyDefaultHierarchyTemplate=true` creates source sets like `iosMain` automatically. But on Linux where iOS targets are disabled (`kotlin.native.ignoreDisabledTargets=true`), these source sets don't get created, causing `KotlinSourceSet with name 'iosMain' not found` errors.

**Lesson**: Keep `applyDefaultHierarchyTemplate=false` and use manual source set creation for projects that need to build on Linux (dev) and macOS (CI).
