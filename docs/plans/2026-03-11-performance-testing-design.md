# Performance Testing Design

## Problem Statement

CommCare's engine has had significant investment in performance optimization over the years. As we port commcare-core from Java to Kotlin and move it to Kotlin/Native for iOS (Phase 3), we need to measure and track performance across three dimensions:

1. **Java JVM vs Kotlin JVM** — Does our Kotlin conversion introduce overhead vs the upstream Java engine?
2. **Kotlin JVM vs Kotlin/Native** — How much slower is the iOS (Kotlin/Native) engine compared to JVM?
3. **Regression prevention** — Do Phase 3 refactoring changes (abstraction layers, platform implementations) degrade performance?

The baseline is the **upstream production Java engine** (`dimagi/commcare-core`) that powers CommCare Android and FormPlayer today.

## Architecture

### Benchmark Framework: kotlinx-benchmark

We use JetBrains' [kotlinx-benchmark](https://github.com/Kotlin/kotlinx-benchmark) (v0.4.16), the official KMP benchmarking library:

- On JVM: delegates to JMH (Java Microbenchmark Harness) — the gold standard for JVM benchmarks
- On Native: uses Kotlin/Native's own measurement harness with proper warmup and iteration
- Single benchmark codebase runs on both platforms
- Gradle-integrated: `./gradlew jvmBenchmark`, `./gradlew macosArm64Benchmark`
- JSON output for automated comparison

### Three-Way Comparison

```
dimagi/commcare-core (separate clone)
├── JMH benchmarks (added by us)
├── Same 6 benchmark scenarios
└── → Java/JVM baseline JSON → stored in our repo

commcare-ios/commcare-core (this repo)
├── kotlinx-benchmark (0.4.16)
├── macosArm64 target (Kotlin/Native, iOS proxy)
├── Same 6 benchmark scenarios
├── → Kotlin/JVM JSON
├── → Kotlin/Native JSON
└── compare.py (reads all 3 JSONs, outputs delta table)
```

### macOS Native as iOS Proxy

kotlinx-benchmark's Native target only supports host execution (you can't run iOS simulator benchmarks directly). For **engine-level** benchmarks, macOS native is a valid proxy for iOS native because:

- Same Kotlin/Native compiler pipeline (LLVM backend)
- Same Apple Silicon architecture
- Same garbage collector (Kotlin/Native tracing GC)
- No JIT on either platform
- The engine code in commonMain compiles identically for both targets

The meaningful delta we're measuring is **JVM vs Kotlin/Native**, not macOS vs iOS.

For iOS-specific performance (UI rendering, memory constraints, app lifecycle), we'd use XCTest performance tests at the app layer — that's a future concern for Phase 4.

## Benchmark Suites

### Suite 1: XPath Evaluation (highest frequency)

XPath expressions run per-question during form entry — thousands of evaluations per form session. This is the single hottest code path in the engine.

| Benchmark | Expression | What it tests |
|-----------|-----------|---------------|
| `evalSimpleReference` | `/data/question1` | Baseline path lookup |
| `evalPredicateFilter` | `instance('casedb')/casedb/case[@case_type='patient']` | Case list filtering |
| `evalArithmetic` | `(/data/weight / (/data/height * /data/height)) * 10000` | Calculation expressions |
| `evalStringConcat` | `concat(/data/first, ' ', /data/last)` | Display expressions |
| `evalConditional` | `if(condition, trueVal, falseVal)` with nesting | Skip/relevance logic |
| `evalBatch100` | 100 mixed expressions on a loaded form model | Realistic form entry simulation |

### Suite 2: Form Loading (startup cost)

Happens once per form session but directly impacts perceived responsiveness.

| Benchmark | Input | What it tests |
|-----------|-------|---------------|
| `loadSmallForm` | Simple 10-question form | Baseline form load time |
| `loadLargeForm` | 328KB TDH form (1,859 fields) | Real-world worst case |
| `parseXFormXml` | Just XML parsing step | Isolate parser overhead |
| `initFormModel` | Just model init after parsing | Isolate initialization cost |

### Suite 3: Case Query (case list display)

Users scroll through case lists of hundreds to thousands of cases. Filters run XPath on every case.

| Benchmark | Dataset | What it tests |
|-----------|---------|---------------|
| `querySmallCaseDb` | 50 cases, simple filter | Baseline query cost |
| `queryMediumCaseDb` | 500 cases, compound filter | Typical deployment |
| `queryLargeCaseDb` | 5,000 cases, complex predicate | Large deployment stress test |
| `sortCaseList` | 1,000 cases, calculated sort field | Sort overhead |

### Suite 4: Serialization Round-trip (sync/persistence cost)

Serialization persists form data, case data, and app state. Phase 3 replaces the JVM reflection-based serializer with a registration-based approach.

| Benchmark | Data | What it tests |
|-----------|------|---------------|
| `serializeFormInstance` | Completed form → bytes | Write cost |
| `deserializeFormInstance` | Bytes → form instance | Read cost |
| `serializeCaseDb` | 500-case database → bytes | Bulk write |
| `deserializeCaseDb` | Bytes → case database | Bulk read |

### Suite 5: Session Navigation (end-to-end workflow)

The full orchestration path: menu → case list → case select → form.

| Benchmark | Workflow | What it tests |
|-----------|----------|---------------|
| `navigateToForm` | Full session from app entry to form ready | End-to-end latency |
| `switchBetweenMenus` | Menu traversal speed | Navigation responsiveness |

### Suite 6: Sync & Restore (longest user operation)

User restore parsing is the heaviest single operation — the 3.6MB test fixture is representative.

| Benchmark | Data | What it tests |
|-----------|------|---------------|
| `parseUserRestore` | 3.6MB user restore XML | Full payload parse time |
| `processCaseUpdates` | Case create/update/close transactions | Transaction processing |
| `fullSyncSimulation` | Restore parsing + case processing + indexing | End-to-end sync |

## File Layout

### In This Repo (commcare-ios)

```
commcare-core/
├── src/benchmarks/                    # kotlinx-benchmark source set
│   └── org/commcare/benchmarks/
│       ├── XPathBenchmark.kt          # Suite 1
│       ├── FormLoadingBenchmark.kt    # Suite 2
│       ├── CaseQueryBenchmark.kt      # Suite 3
│       ├── SerializationBenchmark.kt  # Suite 4
│       ├── SessionNavigationBenchmark.kt  # Suite 5
│       └── SyncRestoreBenchmark.kt    # Suite 6
├── benchmarks/                        # Results and tooling
│   ├── baselines/
│   │   └── java-jvm.json             # Upstream Java baseline (committed)
│   ├── compare.py                     # Comparison script
│   └── README.md                      # How to run and interpret
└── build.gradle.kts                   # Updated with benchmark plugin
```

### In Separate Clone (dimagi/commcare-core)

```
dimagi-commcare-core/
├── src/jmh/java/org/commcare/benchmarks/
│   ├── XPathBenchmark.java
│   ├── FormLoadingBenchmark.java
│   ├── CaseQueryBenchmark.java
│   ├── SerializationBenchmark.java
│   ├── SessionNavigationBenchmark.java
│   └── SyncRestoreBenchmark.java
└── build.gradle                       # Updated with JMH plugin
```

## Gradle Configuration

### Our Repo (kotlinx-benchmark)

```kotlin
plugins {
    id("org.jetbrains.kotlinx.benchmark") version "0.4.16"
    id("org.jetbrains.kotlin.plugin.allopen") version "<kotlin-version>"
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

kotlin {
    macosArm64()  // Native target for benchmarks

    sourceSets {
        val benchmarks by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.16")
            }
        }
    }
}

benchmark {
    configurations {
        named("main") {
            warmups = 5
            iterations = 10
            iterationTime = 1000
            iterationTimeUnit = "ms"
            outputTimeUnit = "ms"
            reportFormat = "json"
        }
    }
    targets {
        register("jvm")
        register("macosArm64")
    }
}
```

### Upstream Clone (JMH)

Standard JMH Gradle plugin setup — the existing `LoadAndInitLargeForm.java` already uses JMH annotations.

## Running Benchmarks

```bash
# Our Kotlin benchmarks (this repo)
cd commcare-core
./gradlew jvmBenchmark              # Kotlin on JVM
./gradlew macosArm64Benchmark       # Kotlin/Native (macOS only)

# Upstream Java baselines (separate clone, one-time)
cd ~/dimagi-commcare-core
java -jar build/libs/commcare-core-jmh.jar -wi 5 -i 10

# Compare all three
cd commcare-core
python benchmarks/compare.py
```

## Comparison Output

The comparison script reads JSON from all three sources and produces:

```
Benchmark                    Java/JVM   Kt/JVM   Kt/Native   Kt/JVM Δ   Native Δ
────────────────────────────────────────────────────────────────────────────────────
XPath.evalSimpleReference     0.010      0.012     0.045      1.2x       4.5x
XPath.evalBatch100            1.100      1.234     4.891      1.1x       4.4x
FormLoading.loadLargeForm    82.3       89.3      312.7       1.1x       3.8x
CaseQuery.queryLargeCaseDb   21.0       23.1       78.4       1.1x       3.7x
Serialization.roundTrip      15.2       16.8       52.1       1.1x       3.4x
Sync.parseUserRestore        42.0       45.2      167.8       1.1x       4.0x
```

## Rollout Plan

### Step 1: Set Up Infrastructure (pre-Phase 3 continuation)

- Add kotlinx-benchmark plugin and dependencies to `build.gradle.kts`
- Add macOS native target for benchmarks
- Create the `src/benchmarks/` source set
- Implement Form Loading benchmark (port of existing `LoadAndInitLargeForm.java`)
- Verify `./gradlew jvmBenchmark` works

### Step 2: Implement All Benchmark Suites

- XPath Evaluation, Case Query, Serialization, Session Navigation, Sync & Restore
- Use existing test fixtures (large_tdh_form.xml, user_restore.xml)
- Generate synthetic case databases for varying sizes

### Step 3: Establish Java Baseline

- Clone `dimagi/commcare-core`
- Add JMH benchmarks for the same 6 scenarios
- Run on JVM, export results as JSON
- Commit baseline to `commcare-core/benchmarks/baselines/java-jvm.json`

### Step 4: First Comparison (Kotlin JVM vs Java JVM)

- Run `./gradlew jvmBenchmark`
- Compare against Java baseline
- Document conversion overhead in a report

### Step 5: Native Comparison (as Phase 3 moves code to commonMain)

- After Waves 6-7 move engine code to commonMain
- Run `./gradlew macosArm64Benchmark` (on macOS)
- First JVM-vs-Native comparison
- Identify hot spots for Phase 4 optimization

### Step 6: Full Report (after Phase 3)

- Three-way comparison: Java JVM → Kotlin JVM → Kotlin/Native
- Per-suite analysis of where the biggest deltas are
- Prioritized optimization backlog for Phase 4

## Test Data Strategy

**Phase A (now):** Use existing test fixtures:
- `large_tdh_form.xml` (328KB, 1,859 fields) for form loading
- `user_restore.xml` (3.6MB) for sync/restore
- Synthetic case databases (generated in `@Setup`) for case queries
- Existing suite/profile resources for session navigation

**Phase B (later):** Add anonymized production data:
- Real forms from high-volume CommCare deployments
- Case databases with realistic field distributions
- User restores from large-scale projects

## Future: CI Integration

Not implemented now, but the design supports it:

1. GitHub Action runs `./gradlew jvmBenchmark` on PRs
2. Compares output JSON against committed baselines
3. Posts delta table as PR comment
4. Optionally fails PR if any benchmark regresses beyond threshold (e.g., >15%)

The JSON output format and baseline files make this straightforward to add when we're ready.

## Future: Real App Instrumentation

Phase B adds instrumentation to the real CommCare Android app and FormPlayer:
- Timing hooks in CommCare Android for form loading, case queries, sync
- FormPlayer server-side timing for the same operations
- Production-representative numbers (real devices, real network, real data)
- Compare our engine against actual production experience

## Dependencies & Risks

| Risk | Mitigation |
|------|------------|
| kotlinx-benchmark Native support is less mature | Start with JVM benchmarks; Native benchmarks are additive |
| macOS native may not perfectly represent iOS | Both use same Kotlin/Native compiler; delta is minimal for engine code |
| Upstream Java codebase may diverge | Pin to a specific commit for baseline; re-run if upstream changes significantly |
| Synthetic case data may not represent production | Phase B adds real anonymized data |
| Benchmark results vary by machine | Document hardware specs in results; relative deltas are more meaningful than absolutes |
