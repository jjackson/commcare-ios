# Phase 1: Core Port — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Port all 642 Java files in commcare-core to idiomatic Kotlin while maintaining 100% backward compatibility with existing JVM consumers (FormPlayer, commcare-android).

**Architecture:** Three-stage approach — (1) add Kotlin toolchain to existing Gradle build, (2) port each functional group from Java to Kotlin with tests passing after each group, (3) add KMP multiplatform targets for iOS Native compilation.

**Tech Stack:** Kotlin 2.0+, Gradle with kotlin("jvm") then kotlin("multiplatform"), JUnit 4 (existing tests), Kotlin/Native for iOS target.

**Repo:** Fork of `dimagi/commcare-core` at `jjackson/commcare-core` (or branch on existing repo)

---

## Context for AI Agents

commcare-core is a Java library used by:
- **commcare-android** (Android app) — imports the library as a Gradle dependency
- **FormPlayer** (server-side web app renderer) — imports the JAR
- **commcare-ios** (this project) — will import via KMP

The library contains the CommCare engine: XForm parsing, XPath evaluation, case management, sync protocol, session engine, and resource management. It has ~130 test files using JUnit 4, all in `src/test/java/`.

**Build system:** Single-module Gradle project with custom sourceSets (main, test, cli, translate, ccapi, jmh). Build file: `build.gradle`. Java 17.

**Critical constraint:** After every change, `gradle test` must pass. The JVM JAR output must remain backward-compatible.

---

## Task 1: Add Kotlin Toolchain to commcare-core

**Goal:** Enable Kotlin compilation alongside existing Java code without changing any source files.

**Files:**
- Modify: `build.gradle`
- Create: `gradle.properties` (if not exists)

**Step 1: Add Kotlin JVM plugin**

In `build.gradle`, add the Kotlin JVM plugin:

```groovy
plugins {
    id 'java-library'
    id 'org.jetbrains.kotlin.jvm' version '2.0.21'
    id "me.champeau.jmh" version "0.7.0"
}
```

Add Kotlin stdlib dependency:

```groovy
dependencies {
    implementation 'org.jetbrains.kotlin:kotlin-stdlib'
    // ... existing dependencies unchanged
}
```

**Step 2: Configure Kotlin source directories**

Kotlin files will live alongside Java files in the same `src/main/java` directory (Gradle's Kotlin plugin supports this). No separate source directory needed initially.

**Step 3: Add Gradle wrapper**

```bash
gradle wrapper --gradle-version 8.10
```

This creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/` files so builds are reproducible.

**Step 4: Verify build**

```bash
./gradlew test
```

All existing tests must pass. The Kotlin plugin should not affect Java compilation.

**Step 5: Create a smoke-test Kotlin file**

Create `src/main/java/org/commcare/modern/KotlinSmokeTest.kt`:

```kotlin
package org.commcare.modern

/**
 * Temporary file to verify Kotlin compilation works alongside Java.
 * Delete after first real Kotlin file is committed.
 */
object KotlinSmokeTest {
    fun isKotlinWorking(): Boolean = true
}
```

Run `./gradlew test` again. Verify the Kotlin file compiles.

**Step 6: Commit**

```bash
git add build.gradle gradlew gradlew.bat gradle/ src/main/java/org/commcare/modern/KotlinSmokeTest.kt
git commit -m "build: add Kotlin JVM plugin to commcare-core"
```

**Exit criteria:** `./gradlew test` passes. Both Java and Kotlin files compile.

---

## Tasks 2-9: Port Functional Groups to Kotlin

Each of the 8 functional groups follows the same process. They MUST be ported in order (each group depends on prior groups being complete). Use `pipeline/src/pipeline/generate_tasks.py --grouped` to generate the GitHub Issues with full file lists.

### Group Overview

| Wave | Group ID | Files | Key Contents |
|------|----------|-------|-------------|
| 1 | javarosa-utilities | 115 | ArrayUtils, StreamReader, locale, storage interfaces |
| 2 | javarosa-model | 108 | FormDef, TreeElement, bindings, model instances |
| 3 | xpath-engine | 134 | XPath parser, evaluator, functions |
| 4 | xform-parser | 27 | XForm XML parsing, form building |
| 5 | case-management | 66 | Case model, case operations, ledger |
| 6 | suite-and-session | 93 | Suite model, session engine, menus |
| 7 | resources | 28 | Resource management, installation, updates |
| 8 | commcare-core-services | 71 | Network, parsing, encryption, process |

### Porting Process (same for each group)

For each group, the AI agent should:

**Step 1: Read all Java files in the group**

Read every file listed in the task's `files_to_read`. Understand the package structure, class hierarchy, and patterns used.

**Step 2: Convert Java files to Kotlin**

For each `.java` file in the group:
1. Create the corresponding `.kt` file in the same directory
2. Convert the Java code to Kotlin, applying these transformations:
   - Java classes → Kotlin classes (use `data class` where appropriate)
   - Nullable annotations → Kotlin null safety (`?` types)
   - Getters/setters → Kotlin properties
   - Static methods → companion object or top-level functions
   - `instanceof` → `is` checks with smart casts
   - Java streams → Kotlin collection operations
   - `StringBuilder` patterns → string templates
   - Try-with-resources → `use {}` extension
   - Anonymous inner classes → lambdas where possible
3. Delete the original `.java` file after the `.kt` file is complete
4. Maintain the same package name and public API

**Step 3: Fix compilation issues**

After converting all files in the group:
```bash
./gradlew compileKotlin
```

Fix any compilation errors. Common issues:
- Java interop: classes in other (still-Java) groups reference these classes — Kotlin/Java interop handles this automatically, but watch for:
  - `@JvmStatic` annotations needed on companion object methods called from Java
  - `@JvmField` annotations on properties accessed as fields from Java
  - `@JvmOverloads` for functions with default parameters called from Java
  - `@Throws` annotations for checked exceptions expected by Java callers
- Visibility: Kotlin's default is `public`, but `internal` has no Java equivalent

**Step 4: Run tests**

```bash
./gradlew test
```

All existing tests must pass. Tests remain in Java for now — Kotlin/Java interop means Java test code can call Kotlin code seamlessly.

**Step 5: Verify JAR output**

```bash
./gradlew jar
```

The JAR should contain compiled `.class` files from both Java (remaining groups) and Kotlin (ported groups). The binary API must be compatible with existing consumers.

**Step 6: Commit and Create PR**

Squash all fix commits into a single clean commit, then create a PR per the PR Strategy section:

```bash
git add -A
git commit -m "port: convert <group-name> to Kotlin (<N> files)"
```

Create a branch named `kotlin-port/wave-N-<group-name>`, push, and open a PR targeting the previous wave's branch. See **PR Strategy** above for naming, targets, and description template.

### PR Strategy

Each wave must produce a reviewable PR before the next wave begins.

**One PR per wave.** Each wave's conversion gets its own PR for human review. Do not batch multiple waves into a single PR.

**Branch naming:** `kotlin-port/wave-N-<group-name>` (e.g., `kotlin-port/wave-1-utilities`).

**Stacked PR targets:** Each PR targets the previous wave's branch:
- Wave 0 (build setup) → `master`
- Wave 1 → `kotlin-port/wave-0-build-setup`
- Wave 2 → `kotlin-port/wave-1-utilities`
- Wave N → `kotlin-port/wave-(N-1)-<name>`

**Squash fix commits.** All compilation/interop fix commits must be squashed into the wave's port commit before creating the PR. Each wave branch should have exactly one commit on top of its parent.

**PR size guidelines.** Each PR should be reviewable in one sitting (~100-150 files changed max). If a wave exceeds this, split it into sub-PRs within the wave.

**CI gate.** Each PR must pass `./gradlew test` before the next wave starts. Do not begin Wave N+1 until Wave N's PR is green.

**PR description template:**
```
## Summary
- Convert <group-name> to Kotlin (<N> files)
- Key changes: <notable conversions, interop fixes, etc.>

## Test plan
- [ ] `./gradlew compileKotlin compileJava` passes
- [ ] `./gradlew test` passes (all existing tests)
- [ ] Java code in unconverted groups correctly calls Kotlin code
```

### Issue Closure

When a wave's PR is merged, close the corresponding GitHub issue on `jjackson/commcare-ios`.

The closure comment **MUST** include:

- **Summary of what was done** — files converted, packages affected, notable changes
- **Verification of each item** in the issue's "Tests That Must Pass" checklist, with evidence (test output, CI link, compilation proof)
- **Technical decisions** made during the wave — interop fixes, design choices, deviations from plan
- **Link to the merged PR**

**Closure comment template:**

```
## Completion Summary

### What was done
- <summary of changes>

### Acceptance criteria verification
- [x] <criteria from issue> — <evidence>
- [x] <criteria from issue> — <evidence>

### Notable technical decisions
- <any non-obvious decisions>

### PR
Merged: <PR URL>
```

Do not close an issue with a one-line comment. The closure comment is part of the project's evidence trail and must demonstrate that acceptance criteria were verified.

### Group-Specific Notes

**Wave 1 — javarosa-utilities (115 files):**
- Foundation layer, no dependencies on other groups
- Includes storage interfaces (`IStorageUtility`, `Persistable`) that are implemented by many other packages — maintain exact same API
- `org.javarosa.core.util` has many utility classes that are good candidates for Kotlin extension functions, but keep the same class structure for backward compat
- `org.commcare.modern` package is already designed for modernization

**Wave 2 — javarosa-model (108 files):**
- Core data model: `FormDef`, `FormInstance`, `TreeElement`, `TreeReference`
- Heavy use of `Serializable`/`Externalizable` — keep serialization compatible
- `TreeElement` is a large class (~1000 lines) with complex null handling — Kotlin null safety will improve this significantly
- Depends on Wave 1 utilities

**Wave 3 — xpath-engine (134 files):**
- XPath parser and evaluator — performance-critical code
- Large switch statements in evaluator → Kotlin `when` expressions
- Many nested classes → consider sealed classes for AST nodes
- This is the largest group by file count — may need to be split into sub-tasks

**Wave 4 — xform-parser (27 files):**
- XForm XML parsing, relatively self-contained
- Heavy use of XML pull parser (kxml2)
- Smallest group, good candidate for quick conversion

**Wave 5 — case-management (66 files):**
- Case model, case operations, ledger management
- Important for CommCare's core functionality
- Watch for `CaseIndex` and case relationship handling

**Wave 6 — suite-and-session (93 files):**
- Suite model defines app structure (menus, forms, case lists)
- Session engine manages navigation state
- Complex state machine logic — maintain exact behavior

**Wave 7 — resources (28 files):**
- Resource installation, updates, recovery
- File I/O heavy — will need `expect/actual` later for KMP
- Relatively isolated from other groups

**Wave 8 — commcare-core-services (71 files):**
- Catch-all for remaining packages
- Includes network, parsing, encryption
- Most platform-specific code lives here — these will get `expect/actual` in Task 10

---

## Task 10: Add KMP Multiplatform Targets

**Goal:** Convert from `kotlin("jvm")` to `kotlin("multiplatform")` with JVM and iOS Native targets.

**Prerequisites:** All 8 groups ported to Kotlin (Tasks 2-9 complete).

**Files:**
- Modify: `build.gradle` → `build.gradle.kts` (convert to Kotlin DSL)
- Create: `src/commonMain/kotlin/` (move shared code here)
- Create: `src/jvmMain/kotlin/` (JVM-specific implementations)
- Create: `src/iosMain/kotlin/` (iOS-specific stubs)

**Step 1: Convert build file to Kotlin DSL**

Rename `build.gradle` to `build.gradle.kts` and convert syntax. Switch plugin to:

```kotlin
plugins {
    kotlin("multiplatform") version "2.0.21"
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "CommCareCore"
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Pure Kotlin dependencies only
        }
        jvmMain.dependencies {
            // JVM dependencies (kxml2, retrofit, okhttp, etc.)
            api("com.github.stefanhaustein:kxml2:2.4.1")
            implementation("com.squareup.retrofit2:retrofit:2.9.0")
            // ... etc
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("junit:junit:4.13.2")
            implementation("org.xerial:sqlite-jdbc:3.40.0.0")
        }
    }
}
```

**Step 2: Reorganize source directories**

```
src/
├── commonMain/kotlin/     ← Most ported Kotlin code goes here
├── commonTest/kotlin/     ← Tests that don't need JVM
├── jvmMain/kotlin/        ← JVM-specific actual implementations
├── jvmTest/kotlin/        ← JVM-specific tests (most existing tests)
├── iosMain/kotlin/        ← iOS actual implementations (stubs initially)
└── iosTest/kotlin/        ← iOS-specific tests
```

**Step 3: Identify platform-specific code**

Scan all ported Kotlin files for JVM-specific usage:
- `java.io.File` → expect/actual with `Path` abstraction
- `java.net.*` → expect/actual for networking
- `javax.crypto.*` → expect/actual for encryption
- `java.sql.*` → expect/actual for database
- `java.util.concurrent.*` → kotlinx.coroutines
- `Serializable`/`Externalizable` → custom serialization
- Reflection → avoid or use expect/actual

For each, create:
```kotlin
// In commonMain
expect class PlatformFile(path: String) {
    fun readBytes(): ByteArray
    fun writeBytes(data: ByteArray)
    fun exists(): Boolean
}

// In jvmMain
actual class PlatformFile actual constructor(path: String) {
    private val file = java.io.File(path)
    actual fun readBytes(): ByteArray = file.readBytes()
    actual fun writeBytes(data: ByteArray) = file.writeBytes(data)
    actual fun exists(): Boolean = file.exists()
}

// In iosMain (stub)
actual class PlatformFile actual constructor(path: String) {
    actual fun readBytes(): ByteArray = TODO("iOS implementation")
    actual fun writeBytes(data: ByteArray) = TODO("iOS implementation")
    actual fun exists(): Boolean = TODO("iOS implementation")
}
```

**Step 4: Verify JVM backward compatibility**

```bash
./gradlew jvmTest
./gradlew jvmJar
```

All existing tests must pass on JVM. The JAR output must be compatible with FormPlayer and commcare-android.

**Step 5: Verify iOS compilation**

```bash
./gradlew iosSimulatorArm64MainKotlinNativeCompile
```

iOS target must compile (stubs with `TODO()` are acceptable at this stage — they'll be implemented in Phase 2).

**Step 6: Commit**

```bash
git add -A
git commit -m "build: add KMP multiplatform targets (JVM + iOS Native)"
```

**Exit criteria:** `./gradlew jvmTest` passes (100% of existing tests). iOS target compiles without errors.

---

## Task 11: Final Integration Verification

**Goal:** Verify the complete port is correct and backward-compatible.

**Step 1: Run full test suite**

```bash
./gradlew test  # or jvmTest if KMP
```

Verify 100% pass rate. Document any test modifications that were needed.

**Step 2: Build all artifacts**

```bash
./gradlew jvmJar
./gradlew iosSimulatorArm64MainKotlinNativeCompile
```

**Step 3: Compare JAR API surface**

Use `javap` or a binary compatibility tool to verify the JVM JAR exposes the same public API as the original Java version.

**Step 4: Generate Phase 1 completion report**

Document:
- Total files ported
- Test pass rate
- Any behavioral changes or known issues
- Platform-specific code identified for future `expect/actual` implementation
- Performance observations

**Step 5: Commit and create PR**

```bash
git commit -m "verify: Phase 1 integration verification complete"
```

Create PR against the main commcare-core repo (or fork).

---

## Pipeline Execution

The pipeline orchestrator (`pipeline/src/pipeline/orchestrator.py`) manages these tasks:

1. **Task 1** runs first (build setup)
2. **Tasks 2-9** run sequentially by wave (each depends on prior wave)
3. **Task 10** runs after all groups are ported
4. **Task 11** runs last as verification

Each task is a GitHub Issue created by:
```bash
cd pipeline
python -m pipeline.generate_tasks "/path/to/commcare-core/src/main/java" \
    --repo jjackson/commcare-ios --grouped --create
```

Tasks 1, 10, and 11 are created manually (they're infrastructure tasks, not generated from package analysis).

---

## Risk Mitigations

| Risk | Mitigation |
|------|-----------|
| Large group too big for one AI session | Split into sub-tasks by package within the group |
| Java interop issues after conversion | `@JvmStatic`, `@JvmField`, `@Throws` annotations |
| Serialization incompatibility | Keep same `serialVersionUID`, test deserialization of old format |
| XPath evaluator performance regression | Benchmark before/after on representative forms |
| Test modifications needed | Document every test change, justify why |
| iOS compilation issues with JVM dependencies | Stub with `TODO()` initially, implement in Phase 2 |
