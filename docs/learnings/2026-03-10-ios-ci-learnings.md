# iOS CI and Build Learnings

## String(CharArray, Int, Int) is deprecated on iOS/Native

**Problem:** `PlatformDataInputStream.readUTF()` used `String(chars, 0, charCount)` which compiled fine on JVM but failed on iOS Native with a deprecation error (treated as error in framework linking).

**Fix:** Use `chars.concatToString(0, charCount)` — the Kotlin common stdlib replacement.

**Lesson:** `String(CharArray)` and `String(CharArray, Int, Int)` constructors are JVM-specific. In commonMain or iosMain, use `CharArray.concatToString()`.

## App commonMain can only see commcare-core commonMain

**Problem:** `app/src/commonMain/` imported `XPathParseTool` from commcare-core, but XPathParseTool is in `src/main/java/` (jvmMain), not commonMain.

**Root cause:** KMP dependency resolution for commonMain only exposes the dependency's commonMain source set. The app's commonMain cannot see any jvmMain or iosMain code from commcare-core.

**Fix:** The app's commonMain can only reference types defined in commcare-core's commonMain (e.g., `PlatformIOException`, `ListMultimap`, `PlatformDataInputStream`). For now, the app shell uses a static screen — the real verification is that `linkDebugFrameworkIosSimulatorArm64` succeeds, confirming the iOS framework links correctly.

**Lesson:** When adding engine integration to the app, either move the needed types to commonMain or create expect/actual wrappers.

## iOS CI requires macOS runners

**Problem:** Linux CI agents cannot build iOS frameworks — Kotlin/Native iOS compilation requires Xcode toolchain.

**Solution:** Use `runs-on: macos-14` in GitHub Actions for iOS builds. The workflow runs `linkDebugFrameworkIosSimulatorArm64` which compiles all commonMain + iosMain Kotlin code into a native iOS framework.

**CI strategy:**
- `kotlin-tests.yml` — runs on Linux, tests JVM compilation + unit tests (fast, ~1.5min)
- `ios-build.yml` — runs on macOS-14, builds iOS framework (slower, ~6min)
- Both trigger on PRs touching `commcare-core/`

**Lesson:** Keep iOS CI enabled on PRs touching commcare-core so regressions in commonMain/iosMain are caught early. The macOS runner cost is justified by catching iOS-specific issues that JVM tests miss.
