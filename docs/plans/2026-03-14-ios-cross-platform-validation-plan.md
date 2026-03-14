# iOS Cross-Platform Validation Plan

**Goal**: 99% confidence that forms serialized on iOS produce identical XML to JVM, and that the full form entry pipeline (parse → navigate → answer → serialize) works correctly on iOS native.

**Status**: Today's oracle tests run entirely on JVM. The `commonMain` code is shared, but the iOS `actual` implementations (XML serializer, streams, data encoding) are never tested against real form output. This plan closes that gap.

## The Problem

Our oracle test pattern works like this:

```
Load XForm → Fill answers → Serialize (ours) → Serialize (oracle) → Compare
```

The oracle (`XFormSerializingVisitor`) uses kxml2 DOM and is permanently JVM-only. It cannot compile for iOS. So the comparison can never run on Kotlin/Native.

But that's not really the question we need to answer. The question is:

> **Does the iOS native binary, running our commonMain code with iOS `actual` implementations, produce the same XML as the JVM binary?**

The oracle comparison already proves our `DataModelSerializer` logic is correct. What's untested is whether the iOS platform layer (`IosXmlSerializer`, `IosByteArrayInputStream`, iOS data streams) introduces any divergence.

## Architecture of the Solution

### Golden File Pattern

1. **JVM generates golden files**: Run oracle tests on JVM, capture the serialized XML output, check it into the repo as `.expected.xml` files
2. **commonTest validates against golden files**: Tests in `commonTest` load forms, fill answers, serialize, and compare against the golden files
3. **Both platforms run the same tests**: `./gradlew jvmTest` and `./gradlew iosSimulatorArm64Test` both execute the golden file tests — any platform divergence fails the test

This gives us the three-way guarantee:
- JVM oracle proves our logic matches the reference implementation ✓
- Golden file proves JVM output is stable across runs ✓
- iOS golden file test proves iOS output matches JVM output ✓

### What Needs to Be Built

There are two JVM-only blockers that prevent form tests from running in `commonTest`:

1. **XForm parsing** — `XFormUtils.getFormFromInputStream()` uses Java's `InputStreamReader` and kxml2
2. **Resource loading** — `this::class.java.getResourceAsStream()` is Java reflection

Both need `expect`/`actual` implementations.

## Task Breakdown

### Wave 1: Infrastructure (3 tasks)

#### Task 1: Cross-platform XForm loader

Create `expect`/`actual` for loading XForm XML into a `FormDef`.

**Files:**
- Create: `commcare-core/src/commonMain/kotlin/org/javarosa/xform/util/XFormLoader.kt`
- Create: `commcare-core/src/jvmMain/kotlin/org/javarosa/xform/util/XFormLoader.kt`
- Create: `commcare-core/src/iosMain/kotlin/org/javarosa/xform/util/XFormLoader.kt`

**What to do:**
```kotlin
// commonMain
expect object XFormLoader {
    fun loadForm(xmlBytes: ByteArray): FormDef
}

// jvmMain — delegate to existing XFormUtils
actual object XFormLoader {
    actual fun loadForm(xmlBytes: ByteArray): FormDef {
        return XFormUtils.getFormFromInputStream(ByteArrayInputStream(xmlBytes))
    }
}

// iosMain — use PlatformXmlParser-based parsing
actual object XFormLoader {
    actual fun loadForm(xmlBytes: ByteArray): FormDef {
        val reader = XFormParser(createByteArrayInputStream(xmlBytes))
        return reader.parse()
    }
}
```

**Key risk**: The iOS XForm parser path (`XFormParser` using `PlatformXmlParser`) may not be fully exercised yet. This task will be the first real end-to-end test of iOS XForm parsing.

**Verification**: `./gradlew compileCommonMainKotlinMetadata` passes, basic test loads a form on both platforms.

#### Task 2: Cross-platform test resource loader

Create `expect`/`actual` for loading test resource files as `ByteArray`.

**Files:**
- Create: `commcare-core/src/commonTest/kotlin/org/commcare/test/TestResources.kt`
- Create: `commcare-core/src/jvmTest/kotlin/org/commcare/test/TestResources.kt` (or `src/test/java/`)
- Create: `commcare-core/src/iosTest/kotlin/org/commcare/test/TestResources.kt`

**What to do:**
```kotlin
// commonTest
expect object TestResources {
    fun loadResource(path: String): ByteArray
}

// jvmTest
actual object TestResources {
    actual fun loadResource(path: String): ByteArray {
        return TestResources::class.java.getResourceAsStream(path)!!.readBytes()
    }
}

// iosTest — use NSBundle or embed as Kotlin resources
actual object TestResources {
    actual fun loadResource(path: String): ByteArray {
        // Use Compose Resources or NSBundle to load test fixtures
    }
}
```

**Alternative**: Use Kotlin Multiplatform's resource system (`Res.readBytes()`) if available, or embed XML as string constants in the test files (simpler, no I/O needed).

**Verification**: A simple test loads `test_all_question_types.xml` on both platforms.

#### Task 3: Golden file generator (JVM-only tool)

Create a JVM test that generates golden files from the oracle serializer, so they can be checked into the repo.

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/oracle/GoldenFileGenerator.kt`
- Create: `commcare-core/src/commonTest/resources/golden/` directory

**What to do:**
- For each test form + answer set, run through the JVM oracle (`XFormSerializingVisitor`)
- Write the normalized output to `golden/<form_name>_<scenario>.expected.xml`
- Normalize: strip XML declaration, normalize whitespace, sort attributes (deterministic output)
- Check the golden files into the repo — they become the contract

**Golden file format:**
```
golden/
  all_question_types_text_only.expected.xml
  all_question_types_full.expected.xml
  calculations_basic.expected.xml
  geopoint_basic.expected.xml
  multi_select_basic.expected.xml
  repeat_with_children.expected.xml
  repeat_skip.expected.xml
  constraints_valid.expected.xml
  empty_form.expected.xml
```

**Verification**: Generator produces deterministic output. Running twice gives identical files.

### Wave 2: Cross-Platform Form Tests (3 tasks)

#### Task 4: Port form serialization tests to commonTest

Move the core serialization tests from `jvmTest` to `commonTest`, comparing against golden files instead of the oracle.

**Files:**
- Create: `commcare-core/src/commonTest/kotlin/org/commcare/app/FormSerializationCrossPlatformTest.kt`

**What to do:**
- For each golden file scenario:
  1. Load the test form XML via `XFormLoader.loadForm()`
  2. Initialize, step through, answer questions (all commonMain APIs)
  3. Serialize via `FormSerializer.serializeForm()`
  4. Normalize the output
  5. Compare against the golden file content
- Cover all question types: text, integer, decimal, date, time, select-one, select-multi, trigger, geopoint
- Cover structural features: empty form, calculations, repeat groups

**Test count target**: 15+ test methods

**Verification**: `./gradlew jvmTest` AND `./gradlew iosSimulatorArm64Test` both pass all 15+ tests.

#### Task 5: Port form navigation tests to commonTest

Move the form structure tests (repeat groups, field-lists, skip logic, constraints) to commonTest.

**Files:**
- Create: `commcare-core/src/commonTest/kotlin/org/commcare/app/FormNavigationCrossPlatformTest.kt`

**What to do:**
- Test repeat group navigation: `EVENT_PROMPT_NEW_REPEAT`, `newRepeat()`, skip repeat
- Test field-list groups: `getQuestionPrompts()` returns multiple prompts
- Test skip logic: changing one answer hides/shows another question
- Test constraints: `ANSWER_CONSTRAINT_VIOLATED`, `getConstraintText()`
- Test required fields: `ANSWER_REQUIRED_BUT_EMPTY`
- These tests don't need golden files — they validate navigation behavior, not serialized output

**Test count target**: 10+ test methods

**Verification**: Both platforms pass all navigation tests.

#### Task 6: Port ViewModel tests to commonTest

Move FormEntryViewModel tests to validate the app-layer logic works identically on iOS.

**Files:**
- Create: `app/src/commonTest/kotlin/org/commcare/app/viewmodel/FormEntryViewModelCrossPlatformTest.kt`

**What to do:**
- Test ViewModel question state mapping (`mapControlType`)
- Test answer submission flow (`answerQuestion`, `answerQuestionString`)
- Test constraint message state (`setConstraint`, `clearConstraint`)
- Test navigation state (`nextQuestion`, `previousQuestion`, `isComplete`)
- Test repeat prompt state (`isRepeatPrompt`, `addRepeat`, `skipRepeat`)
- Test multi-select toggle (`toggleMultiSelectChoice`)

**Note**: This requires the `app` module to have a `commonTest` source set configured in `build.gradle.kts`. Currently it only has `jvmTest`.

**Test count target**: 10+ test methods

**Verification**: Both platforms pass.

### Wave 3: CI and Confidence (2 tasks)

#### Task 7: iOS CI test pipeline

Update the CI workflow to run the new cross-platform tests and report results.

**Files:**
- Modify: `.github/workflows/ios-build.yml`
- Modify: `.github/workflows/kotlin-tests.yml`

**What to do:**
- `ios-build.yml`: Add `./gradlew :commcare-core:iosSimulatorArm64Test` (already exists) AND `./gradlew :app:iosSimulatorArm64Test` (new — requires app module iOS test target)
- `kotlin-tests.yml`: Ensure JVM tests still include oracle comparison tests
- Add test result reporting (JUnit XML → GitHub Actions summary)
- Fail the PR if any cross-platform test fails on either platform

**Verification**: A PR that breaks iOS serialization fails CI.

#### Task 8: Golden file staleness detection

Add a JVM test that regenerates golden files and fails if they differ from what's checked in.

**Files:**
- Create: `app/src/jvmTest/kotlin/org/commcare/app/oracle/GoldenFileStalenessTest.kt`

**What to do:**
- Regenerate all golden files in-memory
- Compare against checked-in golden files
- If any differ, fail with a diff showing exactly what changed
- This catches cases where engine changes alter serialization output — the developer must then:
  1. Verify the new output is correct (via oracle comparison)
  2. Update the golden files
  3. Re-run iOS tests to confirm iOS matches the new golden output

**Verification**: Intentionally changing a golden file causes the test to fail.

## Dependencies

```
Task 1 (XForm loader) ──┐
Task 2 (resource loader) ├──→ Task 4 (serialization tests)
Task 3 (golden generator) ┘   Task 5 (navigation tests)
                               Task 6 (ViewModel tests)
                                  │
                                  ├──→ Task 7 (CI pipeline)
                                  └──→ Task 8 (staleness detection)
```

Wave 1 tasks are independent of each other. Wave 2 depends on all of Wave 1. Wave 3 depends on Wave 2.

## Confidence Analysis

After this plan is complete:

| Layer | JVM Coverage | iOS Coverage | Confidence |
|-------|-------------|-------------|------------|
| XForm parsing | XFormUtils (battle-tested) | XFormParser + PlatformXmlParser | **New — first real test** |
| Form navigation | 35 tests (jvmTest) + 10 (commonTest) | 10 tests (commonTest) | **High** |
| Answer data types | All types tested | All types tested via golden files | **High** |
| Form serialization | Oracle + golden comparison | Golden file comparison | **High** — proves identical output |
| XML serializer impl | kxml2 (proven) | IosXmlSerializer (string-based) | **Directly tested** via golden files |
| Data stream encoding | PlatformDataStreamTest (13 tests) | PlatformDataStreamTest (13 tests) | **Already covered** |
| Binary serialization | SerializationRoundTripTest (7 tests) | SerializationRoundTripTest (7 tests) | **Already covered** |

**Remaining 1% risk**:
- Kotlin/Native memory model edge cases (shared mutable state)
- Unicode normalization differences between JVM and iOS
- Floating point representation differences (unlikely with IEEE 754)
- iOS-specific XML parser edge cases with malformed input

**Total test count after plan**: ~80 cross-platform tests (existing 16 commonTest + ~35 new) running on both JVM and iOS.

## Exit Criteria

- [ ] 15+ form serialization golden file tests pass on both JVM and iOS
- [ ] 10+ form navigation tests pass on both JVM and iOS
- [ ] 10+ ViewModel tests pass on both JVM and iOS
- [ ] Golden file staleness test catches serialization regressions
- [ ] CI runs iOS simulator tests on every PR
- [ ] Zero golden file mismatches between JVM and iOS output
- [ ] All 7 test form XMLs load and parse correctly on iOS native
