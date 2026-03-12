# Phase 6: Deep Platform Abstraction — Implementation Plan

**Date:** 2026-03-12
**Status:** Draft
**Prerequisite:** Phase 5 complete (227 commonMain files, serialization framework in commonMain). 430 .kt files remain in main/java, blocked by 16 files with direct JVM dependencies.

---

## Goal

Remove JVM dependencies from the 16 direct blocker files (or move them to jvmMain) to unblock bulk migration of 430 remaining files to commonMain.

**Exit criteria:** 500+ total files in commonMain. Core engine types (FormDef, TreeElement, EvaluationContext, TreeReference) in commonMain. All 800+ JVM tests pass. `compileCommonMainKotlinMetadata` succeeds.

---

## Current State

| Source Set | Files |
|-----------|-------|
| commonMain | 227 .kt |
| jvmMain | 65 .kt |
| iosMain | 36 .kt |
| src/main/java | 430 .kt |

### The 16 Direct Blocker Files

| # | File | JVM Dependency | Direct Importers (in main/java) |
|---|------|---------------|-------------------------------|
| 1 | FormDef.kt | `System.getProperty()` | 17 files |
| 2 | PrototypeManager.kt | `ThreadLocal` | 1 file (CommCareSession) |
| 3 | ReferenceHandler.kt | `ThreadLocal` | ReferenceManager |
| 4 | LocalizerManager.kt | `ThreadLocal` | Localization |
| 5 | LocalizationUtils.kt | `java.io.BufferedReader`, `InputStreamReader` | 2 files |
| 6 | ResourceTable.kt | `java.io.FileNotFoundException` | 12 files |
| 7 | XFormParser.kt | `java.io.InputStreamReader`, `java.io.Reader` | ~2 files (same-package) |
| 8 | XFormParserFactory.kt | `java.io.Reader` | ~2 files |
| 9 | XFormParserReporter.kt | `java.io.PrintStream` | ~1 file |
| 10 | XFormSerializer.kt | `java.io.DataOutputStream`, `OutputStreamWriter` | ~3 files |
| 11 | XFormUtils.kt | `java.io.InputStreamReader` | ~4 files |
| 12 | XFormInstaller.kt | `java.io.InputStreamReader` | ~1 file |
| 13 | GeoPointUtils.kt | `org.gavaghan.geodesy.GlobalCoordinates` | ~5 files |
| 14 | PolygonUtils.kt | `org.gavaghan.geodesy.*` | ~3 files |
| 15 | XPathClosestPointOnPolygonFunc.kt | `org.gavaghan.geodesy.GlobalCoordinates` | 0 (registered in factory) |
| 16 | XPathIsPointInsidePolygonFunc.kt | `org.gavaghan.geodesy.GlobalCoordinates` | 0 (registered in factory) |

### Key Dependency Chains

The 430 files form one connected component. Critical chains:

```
FormDef → FormEntryModel → FormEntryPrompt → ...20+ files
FormDef → Triggerable → Condition/Recalculate → ...
FormDef → ActionController → Action → ...

LocalizerManager → Localization → Text → ...50+ files
ReferenceHandler → ReferenceManager → ReferenceDataSource → Localization → ...

ResourceTable → ResourceManager → CommCarePlatform → ...
ResourceTable → *Installer → ...

XFormParser → XFormUtils → ... (but these are parser-specific, less cascade)
```

### Existing Abstractions Available

- `PlatformInputStream` / `PlatformOutputStream` — commonMain stream abstractions
- `PlatformDataInputStream` / `PlatformDataOutputStream` — for serialization
- `platformSynchronized()` — replaces `synchronized` keyword
- `PlatformIOException` — replaces `IOException`
- `PlatformXmlParser` — replaces kxml2 XmlPullParser
- `PlatformDate` / `PlatformCalendar` / `PlatformTimeZone` — date abstractions

---

## Wave Plan

### Wave 1: FormDef — System.getProperty removal (highest ROI)

**Files:** 1 (FormDef.kt)
**JVM dep:** `System.getProperty("src.main.java.org.javarosa.enableOpenTracing")` — one line
**What:**
- Create `expect fun platformGetSystemProperty(key: String): String?` in commonMain
- JVM actual: `System.getProperty(key)`
- iOS actual: returns `null` (no system properties on iOS)
- Replace the single call in FormDef.kt
- Move FormDef.kt to commonMain

**Impact:** Unblocks 17 direct importers + their transitive dependents. FormDef is referenced by FormEntryModel, Triggerable, ActionController, and many more.

**Acceptance criteria:**
- [ ] FormDef.kt compiles in commonMain
- [ ] `compileCommonMainKotlinMetadata` succeeds
- [ ] All 800+ JVM tests pass

### Wave 2: ThreadLocal abstraction (3 files)

**Files:** 3 (PrototypeManager.kt, ReferenceHandler.kt, LocalizerManager.kt)
**JVM dep:** `ThreadLocal<T>` for multi-tenant thread isolation
**What:**
- Create `expect class PlatformThreadLocal<T>(initialValue: () -> T)` in commonMain with `get()` and `set()` methods
- JVM actual: wraps `java.lang.ThreadLocal<T>`
- iOS actual: simple property (single-threaded on iOS, no thread isolation needed)
- Replace `ThreadLocal<T>` usage in all 3 files
- All 3 files already have `useThreadLocal` strategy flags — keep these

**Impact:** PrototypeManager → CommCareSession; ReferenceHandler → ReferenceManager → Localization; LocalizerManager → Localization. These are critical singletons.

**Acceptance criteria:**
- [ ] All 3 files compile in commonMain
- [ ] Thread-local strategy still works on JVM
- [ ] All 800+ JVM tests pass

### Wave 3: java.io Reader/Writer abstraction (6 files)

**Files:** 6 (LocalizationUtils.kt, XFormParser.kt, XFormParserFactory.kt, XFormUtils.kt, XFormInstaller.kt, XFormSerializer.kt)
**JVM deps:** `java.io.Reader`, `InputStreamReader`, `BufferedReader`, `DataOutputStream`, `OutputStreamWriter`, `PrintStream`
**What:**
- Create `expect class PlatformReader` in commonMain with `readLine(): String?`, `close()`
- Create `expect fun createInputStreamReader(input: PlatformInputStream, charset: String = "UTF-8"): PlatformReader`
- Create `expect fun createBufferedReader(reader: PlatformReader): PlatformReader`
- JVM actual: wraps `java.io.Reader` / `InputStreamReader` / `BufferedReader`
- iOS actual: NSString-based reading from NSData
- For XFormSerializer: use existing `PlatformOutputStream` + `expect fun createOutputStreamWriter(output: PlatformOutputStream, charset: String): PlatformWriter`
- Replace java.io usages in all 6 files

**Strategy note:** XFormParser and XFormParserFactory are complex files (1500+ lines). Consider:
- Option A: Move to commonMain by abstracting Reader (preferred if feasible)
- Option B: Move to jvmMain if too many JVM deps (XFormParser uses kxml2 via PlatformXmlParser already)

**Impact:** LocalizationUtils is the highest-value target here — it unblocks the Localization → Text chain.

**Acceptance criteria:**
- [ ] LocalizationUtils.kt compiles in commonMain (minimum)
- [ ] `compileCommonMainKotlinMetadata` succeeds
- [ ] All 800+ JVM tests pass

### Wave 4: ResourceTable — FileNotFoundException (1 file)

**Files:** 1 (ResourceTable.kt)
**JVM dep:** `java.io.FileNotFoundException`
**What:**
- Replace `catch (e: FileNotFoundException)` with `catch (e: PlatformIOException)` or create a specific `expect class PlatformFileNotFoundException : PlatformIOException`
- ResourceTable already uses platform abstractions for most operations
- Move ResourceTable.kt to commonMain

**Impact:** Unblocks 12 installer/resource files.

**Acceptance criteria:**
- [ ] ResourceTable.kt compiles in commonMain
- [ ] All 800+ JVM tests pass

### Wave 5: XFormParserReporter — PrintStream (1 file)

**Files:** 1 (XFormParserReporter.kt)
**JVM dep:** `java.io.PrintStream`
**What:**
- Replace `PrintStream` with a simple `(String) -> Unit` callback or `expect class PlatformPrintStream`
- This is a logging/reporting class — the abstraction is straightforward

**Acceptance criteria:**
- [ ] XFormParserReporter.kt compiles in commonMain
- [ ] All 800+ JVM tests pass

### Wave 6: Gavaghan geodesy replacement (4 files)

**Files:** 4 (GeoPointUtils.kt, PolygonUtils.kt, XPathClosestPointOnPolygonFunc.kt, XPathIsPointInsidePolygonFunc.kt)
**JVM dep:** `org.gavaghan.geodesy.*` (GlobalCoordinates, Ellipsoid, GeodeticCalculator)
**What:**
- Option A (preferred): Create a simple `GeoCoordinate` data class in commonMain, implement Vincenty's formula in pure Kotlin to replace GeodeticCalculator
- Option B: Move gavaghan-dependent code to jvmMain, create expect/actual for geo calculations
- GeoPointUtils already has haversine math in pure Kotlin — extend this

**Impact:** Low (5 downstream files), but removes last external library dep.

**Acceptance criteria:**
- [ ] All 4 files compile in commonMain (option A) or jvmMain (option B)
- [ ] Geo calculations produce same results
- [ ] All 800+ JVM tests pass

### Wave 7: Bulk migration sweep

**Files:** remaining ~380+
**What:**
- With all 16 blockers resolved, re-run iterative compiler-validated migration
- Move all candidates → compile → rollback failures → repeat
- Move any remaining JVM-only files to jvmMain
- Identify any new blockers not visible before

**Acceptance criteria:**
- [ ] 500+ total files in commonMain
- [ ] Core engine types (FormDef, TreeElement, EvaluationContext, TreeReference) in commonMain
- [ ] `compileCommonMainKotlinMetadata` succeeds
- [ ] All 800+ JVM tests pass

### Wave 8: Validation and cleanup

**Files:** ~5 new
**What:**
- Add cross-platform tests in commonTest for moved engine types
- Verify iOS builds with real engine types available
- Clean up any unused JVM backward-compat shims
- Update jvmMain/iosMain expect/actual inventory

**Acceptance criteria:**
- [ ] Cross-platform tests pass on both JVM and iOS
- [ ] iOS build succeeds
- [ ] All 800+ JVM tests pass

---

## Dependency Graph

```
Wave 1 (FormDef) ──────────────┐
Wave 2 (ThreadLocal) ──────────┤
Wave 3 (java.io Reader/Writer) ┼→ Wave 7 (bulk migration) → Wave 8 (validation)
Wave 4 (ResourceTable) ────────┤
Wave 5 (PrintStream) ──────────┤
Wave 6 (gavaghan) ─────────────┘
```

Waves 1-6 are independent and can be done in any order. Waves 1 and 2 have the highest cascade impact and should be prioritized.

---

## Risk Analysis

| Risk | Mitigation |
|------|-----------|
| FormDef has hidden JVM deps beyond System.getProperty | Read file carefully first. It's 1200+ lines. Check for java.util.Collections, ArrayIndexOutOfBoundsException, etc. |
| PlatformThreadLocal performance on JVM | Wrap actual java.lang.ThreadLocal — zero overhead |
| Reader abstraction too leaky | Keep it minimal: readLine(), read(buf), close(). XFormParser may need more complex reading. |
| XFormParser too complex to move | Fall back to jvmMain. XFormParser is a parser implementation — it's okay for it to be platform-specific. |
| Bulk migration still blocked by same-package references | Use iterative compiler-validated approach (proven in Phase 3/4). |
| New blockers emerge during bulk migration | Handle them ad-hoc in Wave 7 as they appear. |

---

## Acceptance Criteria (Phase-level)

- [ ] All 16 direct JVM blocker files resolved (moved to commonMain or jvmMain)
- [ ] 500+ total files in commonMain
- [ ] Core engine types (FormDef, TreeElement, EvaluationContext, TreeReference) in commonMain
- [ ] All 800+ JVM tests pass
- [ ] `compileCommonMainKotlinMetadata` succeeds
- [ ] Cross-platform tests for engine types pass on JVM and iOS
- [ ] Zero `java.*` imports in commonMain files (excluding kotlin.jvm)
