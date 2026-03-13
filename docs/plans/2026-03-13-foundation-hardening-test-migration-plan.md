# Foundation Hardening + Test Migration Plan

**Date:** 2026-03-13
**Issues:** #199, #200, #201, #202, #203
**Goal:** Fix critical foundation bugs, prove the iOS approach works via cross-platform engine tests, migrate all Java test files to Kotlin, and convert safe getter/setter methods to properties.

## Context

A skeptical supervisor review of the project found: (1) PrototypeFactory hash computation produces different results on JVM vs iOS, making cross-platform serialization broken; (2) iOS `writeTagged` throws at runtime; (3) zero cross-platform engine tests exist; (4) 103 Java test files force retention of Java-style getter/setter patterns; (5) `InMemoryStorage.isEmpty()` has inverted logic. This plan addresses all of these before continuing Tier 2 feature work.

## Wave Structure (6 waves, 6 PRs)

### Wave 0: Foundation Bug Fixes
**Branch:** `foundation/bug-fixes`
**Size:** ~4 files modified, ~1 new test file

**Tasks:**

1. **Fix InMemoryStorage.isEmpty()** — `app/src/commonMain/kotlin/org/commcare/app/storage/InMemoryStorage.kt` line 119: change `data.size > 0` to `data.isEmpty()`

2. **Fix iOS PrototypeFactory hash divergence** — `commcare-core/src/iosMain/kotlin/org/javarosa/core/util/externalizable/PrototypeFactory.kt`:
   - Change `DEFAULT_HASH_SIZE` from `4` to `32`
   - Replace both `computeHash()` (instance, line 70-77) and `computeHashStatic()` (companion, line 108-116) to: reverse class name, encode to UTF-8, copy first 32 bytes (pad with zeroes if shorter). This matches JVM `ClassNameHasher.getHashByName()` + `Hasher.getClassHashValue()` behavior.
   - Fix the internal inconsistency: instance method was NOT reversing the name, static method WAS. Both must reverse.

3. **Implement iOS writeTagged** — `commcare-core/src/iosMain/kotlin/org/javarosa/core/util/externalizable/SerializationHelpers.kt` lines 98-103:
   - Replace `throw UnsupportedOperationException` with real implementation
   - Implementation: delegate to `ExtWrapTagged.writeTag(out, obj)` then `ExtUtil.write(out, obj)` if ExtUtil is accessible, OR replicate the protocol: compute hash via `PrototypeFactory.getClassHashForType(obj::class)`, write hash bytes, then `(obj as Externalizable).writeExternal(out)`
   - Also fix `readTagged` wrapper tag handling (line 86-88) — implement wrapper code dispatch matching `ExtWrapTagged.readTag()` pattern

4. **Fix iOS classNameToKClass** — `commcare-core/src/iosMain/kotlin/org/javarosa/core/util/externalizable/ClassNameToKClass.kt`: Currently throws `UnsupportedOperationException`. This is called from `ExtWrapTagged.readTag()` (commonMain line 90). Must implement using the PrototypeFactory registry — look up the class name in the factory's `factories` map and return the KClass. Or alternatively, maintain a reverse mapping from class name to KClass in the iOS PrototypeFactory.

5. **Add cross-platform hash compatibility test** — New file `commcare-core/src/commonTest/kotlin/org/javarosa/core/util/externalizable/PrototypeFactoryHashTest.kt`:
   - Verify `getClassHashSize()` returns 32
   - Verify `getClassHashByName("org.javarosa.core.model.data.UncastData")` returns known expected bytes
   - Verify `compareHash()` works correctly
   - Verify `getWrapperTag()` is all 0xFF bytes of length 32

**Verification:**
- `./gradlew compileCommonMainKotlinMetadata` succeeds
- `./gradlew test` — all JVM tests pass
- `./gradlew iosSimulatorArm64Test` — iOS tests pass including new hash test
- New hash test passes on BOTH platforms with identical results

---

### Wave 1: Cross-Platform Engine Tests
**Branch:** `foundation/cross-platform-engine-tests`
**Size:** ~5-8 new test files
**Depends on:** Wave 0 (writeTagged must work for serialization tests)

**New files in `commcare-core/src/commonTest/kotlin/`:**

1. `org/javarosa/core/util/externalizable/TaggedSerializationTest.kt` — Write tagged `Externalizable` objects, read them back, verify equality. Tests the full writeTagged→readTagged round-trip on both platforms.

2. `org/javarosa/core/model/instance/TreeElementCrossPlatformTest.kt` — Create TreeElement, add children, test getChild/getChildAt, test attribute access, test serialization round-trip via readExternal/writeExternal.

3. `org/javarosa/core/model/instance/TreeReferenceCrossPlatformTest.kt` — Create TreeReferences, test equality, contextualization, intersection, clone, serialization round-trip.

4. `org/javarosa/xpath/XPathParseCrossPlatformTest.kt` — Parse XPath expressions via `XPathParseTool.parseXPath()`, verify structure. (If XPathParseTool is in commonMain; verify before implementing.)

5. `org/commcare/cases/CaseCrossPlatformTest.kt` — Create Case objects, set properties, serialization round-trip. (If Case is fully in commonMain.)

**Target:** At least 20 new cross-platform test methods covering engine operations.

**Verification:**
- `./gradlew test` — JVM tests pass (existing + new)
- `./gradlew iosSimulatorArm64Test` — new engine tests pass on iOS

---

### Wave 2: Test Migration — Tier 1 (Pure Unit Tests)
**Branch:** `foundation/test-migration-tier1`
**Size:** ~35-40 files converted (.java → .kt in place)
**Depends on:** None (can run in parallel with Waves 0-1)

Convert pure unit tests with no resource loading, no MockApp, minimal JVM-specific APIs. Files stay in `src/test/java/` as `.kt` files (Gradle compiles them as jvmTest).

**Files to convert:**

Test utilities (no resource loading):
- `DummyInstanceInitializationFactory.java`, `DummyFormEntryPrompt.java`, `FormInstanceWithFailures.java`, `MockTimezoneProvider.java`, `MockSessionNavigationResponder.java`, `ExprEvalUtils.java`, `MockFormSendCalloutHandler.java`

Pure unit tests:
- Data tests: `IntegerDataTests`, `StringDataTests`, `GeoPointDataTests`, `SelectMultiDataTests`, `SelectOneDataTests`, `DateDataTests`, `TimeDataTests`
- Model tests: `TreeElementTests`, `QuestionDataElementTests`, `QuestionDataGroupTests`, `FormIndexTests`, `TreeReferenceTest`, `DataInstanceTest`
- Util tests: `CompressingIdTest`, `NumericEncodingTest`, `DataUtilTest`, `CollectionUtilsTest`, `LocalizationTests`, `LocalizerTest`
- XPath tests: `XPathBinaryOpExprTest`, `StaticAnalysisTest`, `TreeReferenceAccumulatorTest`, `XPathJsonPropertyFuncTest`
- Platform tests: `PlatformStreamRoundTripTest`, `PlatformXmlRoundTripTest`, `PlatformAbstractionsTest`, `BufferedInputStreamTests`
- Other: `ModelSetTests`, `IndexedStorageUtilityTests`, `MockStorageUtilityTests`, `DateRangeUtilsTest`, `FuzzySearchTest`, `JsonUtilsTest`

**Conversion approach:**
- Convert `.java` → `.kt` in same directory, delete `.java`
- Keep JUnit 4 annotations (`@Test`, `@Before`, `@BeforeClass`)
- Replace `java.util.Date` → keep as-is (available in jvmTest)
- Replace Guava `ImmutableList.of()` → `listOf()`, `ImmutableMap.of()` → `mapOf()`
- Anonymous inner classes → `object : Interface { }` expressions
- Apply Kotlin Conversion Checklist (nullable params, open keyword)

**Verification:**
- `./gradlew compileKotlin compileJava` succeeds
- `./gradlew test` — same test count, all pass

---

### Wave 3: Test Migration — Tier 2+3 (Integration Tests + Remaining Utilities)
**Branch:** `foundation/test-migration-tier2-3`
**Size:** ~60-70 files converted
**Depends on:** Wave 2 (utility files migrated first)

**Convert test utilities first (depended on by integration tests):**
- `FormParseInit.java`, `MockApp.java`, `TestHelpers.java`, `TestInstanceInitializer.java`, `TestProfileConfiguration.java`, `CasePurgeTest.java` (base class), `CaseTestUtils.java`, `XmlComparator.java`, `TestInstances.java`, `ParserTestUtils.java`, `MockSessionUtils.java`, `ClassLoadUtils.java`

**Then convert integration tests:**
- Case tests: `BadCaseXMLTests`, `CaseExternalIdTest`, `CaseParseAndReadTest`, `CaseTemplateTest`, `CaseXPathQueryTest`, `BulkCaseInstanceXmlParserTests`, `CaseParseReindexTests`, `CasePurgeFilterTests`, `CasePurgeIntegrationTest`, `CasePurgeRegressions`
- Ledger tests: `LedgerEmptyReferenceTests`, `LedgerParseAndQueryTest`, `LedgerAndCaseQueryTest`
- Fixture tests: `FixtureQueryTest`, `IndexedFixtureTests`
- Session tests: `BasicSessionNavigationTests`, `ChildModuleNavigationTests`, `EvalContextTests`, `InstanceEvaluationTests`, `MarkRewindSessionTests`, `MenuTests`, `SessionNavigatorTests`, `SessionStackTests`, `StackObserverTest`, `StackRegressionTests`
- Suite tests: `AppStructureTests`, `CaseClaimModelTests`, `EmptyAppElementsTests`, `ProfileTests`, `QueryModelTests`, `StackFrameStepTests`
- Backend tests: `EntitySubnodeDetailTest`, `TemplateStructureTest`, `FormDataUtilTest`, `AppConfiguredTextTests`, `TextTests`
- XML parser tests: `EntryParserTest`, `QueryDataParserTest`, `RemoteRequestEntryParserTest`, `SessionDatumParserTest`, `TreeBuilderTest`, `VirtualInstancesTest`, `QueryDataModelTest`
- Form tests: `TextFormTests`, `FormEntryControllerTest`, `FormEntrySessionTest`, `ConstraintTest`, `CustomFuncTest`, `CyclicReferenceTests`, `ErrorHandlingTests`, `FormDefTest`, `GeoPointTests`, `InFormRequestTest`, `QuestionDefTest`, `UploadExtensionTest`
- XPath tests: `XPathFuncExprTest`, `XPathPathExprTest`, `XPathEvalTest`, `XPathParseTest`
- XForm tests: `SendParseTests`, `SubmissionParseTests`, `TextEncodingTests`, `CalendarTests`, `XFormAnswerDataSerializerTest`, `XmlSerializerTests`, `FormatDateTest`
- Util tests: `DateUtilsTests`, `ExternalizableTest`, `XmlUtilTest`, `JavaResourceReferenceTest`
- CLI tests: `CliTests`, `MockSessionUtils`

**Key conversion patterns:**
- `this.getClass().getResourceAsStream(path)` → `this.javaClass.getResourceAsStream(path)`
- `@RunWith(Parameterized.class)` → `@RunWith(Parameterized::class)`
- Java streams `.stream().map().collect()` → `.map { }.toList()`
- `assertEquals(expected, actual)` — keep as-is (JUnit 4 works in Kotlin)

**Files that stay Java (~5, too reflection-heavy):**
- `ReflectionUtils.java` — heavy `java.lang.reflect` APIs
- `ComposedRunner.java` — custom JUnit runner internals
- `RunnerCoupler.java` — JUnit runner coupling via reflection
- `RunWithResource.java` — custom annotation

**Verification:**
- `./gradlew compileKotlin compileJava` succeeds
- `./gradlew test` — same test count, all pass
- Verify ≤5 `.java` files remain in `src/test/java/`

---

### Wave 4: Getter/Setter → Property Conversion
**Branch:** `foundation/property-conversion`
**Size:** ~50+ files modified in commonMain, call site updates across test files
**Depends on:** Waves 2+3 (test files must be Kotlin so call sites can be updated)

**Convert only safe classes** (not `open`, not implementing interfaces for these methods, not subclassed):

Suite model classes in `commcare-core/src/commonMain/kotlin/org/commcare/suite/model/`:
- `DetailField.kt` (~48 getter/setters) — Note: has Builder pattern, use `internal var` with `internal set` for properties the Builder writes
- `QueryPrompt.kt` (~14)
- `Detail.kt` (~22)
- `Style.kt` (~11)
- `QueryPromptCondition.kt` (~2)
- `GridCoordinate.kt`, `GridStyle.kt`, `DisplayUnit.kt`
- `EndpointAction.kt`, `ValueQueryData.kt`, `ListQueryData.kt`
- `Action.kt`, `Callout.kt`, `Credential.kt`, `EndpointArgument.kt`
- `GeoOverlay.kt`, `PostRequest.kt`, `PropertySetter.kt`, `StackOperation.kt`

**DO NOT convert (high risk):**
- `TreeElement`, `ConcreteTreeElement` — `@JvmField` + `open`
- `Case` — `@JvmField` + `open`
- `FormDef` — `IFormElement` interface
- `EvaluationContext` — complex
- `FormEntryCaption`, `FormEntryPrompt` — `open` + `@JvmField`
- `CommCareSession` — `open`, subclassed by `SessionWrapper`
- `EvaluationTrace` — `open` methods
- Any class implementing `AbstractTreeElement` interface

**Conversion pattern:**
```kotlin
// Before:
private var headerWidthHint: String? = null
fun getHeaderWidthHint(): String? = headerWidthHint

// After:
var headerWidthHint: String? = null
    private set  // or internal set if Builder needs access
```

**Call site updates:** Change `.getHeader()` → `.header` in all Kotlin callers. Java callers (the ~5 remaining files) automatically use generated `getHeader()` bytecode.

**Verification:**
- `./gradlew compileKotlin compileJava` succeeds
- `./gradlew compileCommonMainKotlinMetadata` succeeds
- `./gradlew test` — all tests pass
- `./gradlew iosSimulatorArm64Test` — iOS tests pass

---

### Wave 5: CLAUDE.md + Documentation
**Branch:** `docs/foundation-hardening-completion`
**Size:** ~3 files
**Depends on:** All previous waves

**Tasks:**
1. Update CLAUDE.md status table to reflect actual project state (Phases 3-8 complete, Tier 1 complete, foundation hardening complete)
2. Update file counts, Key Docs links, Current Status section
3. Write `docs/learnings/2026-03-13-foundation-hardening-learnings.md` with findings from this work
4. Add Foundation Hardening phase to status tables

**Verification:** CI passes. Self-merge per Doc PR Rules.

---

## Dependency Graph

```
Wave 0 (Bug Fixes) ───────→ Wave 1 (Engine Tests)
                                    │
Wave 2 (Test Tier 1) ──────────────┤
         │                          │
         ↓                          ↓
Wave 3 (Test Tier 2+3) ──→ Wave 4 (Property Conversion)
                                    │
                                    ↓
                            Wave 5 (Docs)
```

- Wave 0 and Wave 2 can run in **parallel** (different files)
- Wave 1 starts after Wave 0 merges
- Wave 3 starts after Wave 2 merges
- Wave 4 starts after Waves 2+3 merge (need Kotlin tests for call site updates)
- Wave 5 after everything

## Verification (End-to-End)

After all waves:
- `./gradlew compileCommonMainKotlinMetadata` — commonMain compiles cleanly
- `./gradlew test` — all 710+ JVM tests pass (same count, no regressions)
- `./gradlew iosSimulatorArm64Test` — all cross-platform tests pass (77 existing + ~20+ new engine tests)
- ≤5 `.java` files remain in `src/test/java/` (reflection-heavy utilities only)
- Zero `UnsupportedOperationException` stubs in serialization code on iOS
- `PrototypeFactory.getClassHashSize()` returns 32 on both platforms
- `InMemoryStorage.isEmpty()` returns correct values
