# Performance Testing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a comprehensive benchmark suite that measures CommCare engine performance across Java/JVM (upstream baseline), Kotlin/JVM, and Kotlin/Native, using kotlinx-benchmark.

**Architecture:** JVM-only benchmarks initially (engine code is still in `src/main/java/`). Benchmarks use the existing MockApp/FormParseInit test infrastructure. A separate clone of `dimagi/commcare-core` provides the upstream Java baseline via JMH. A Python comparison script produces delta reports. macOS native target added later when Phase 3 moves engine code to commonMain.

**Tech Stack:** kotlinx-benchmark 0.4.16 (JMH on JVM), Kotlin 2.0.21, JMH for upstream Java baseline, Python 3 for comparison script.

**Design doc:** `docs/plans/2026-03-11-performance-testing-design.md`

---

### Task 1: Add kotlinx-benchmark Gradle Infrastructure

**Files:**
- Modify: `commcare-core/build.gradle.kts`
- Create: `commcare-core/src/jvmBenchmarks/kotlin/.gitkeep` (source set marker)

This is the most complex task — getting the Gradle configuration right with the existing KMP build.

**Step 1: Add the benchmark plugin and allopen plugin to `build.gradle.kts`**

At the top of the `plugins {}` block, add:

```kotlin
plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    kotlin("plugin.allopen") version "2.0.21"  // NEW
    id("org.jetbrains.kotlinx.benchmark") version "0.4.16"  // NEW
}
```

**Step 2: Add the allopen configuration**

After the `repositories {}` block, add:

```kotlin
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}
```

**Step 3: Add a custom benchmark compilation to the JVM target**

Inside the `kotlin {}` block, modify the `jvm {}` target to add a benchmark compilation:

```kotlin
jvm {
    withJava()

    compilations {
        val main by getting
        val test by getting

        // Custom benchmark compilation
        val benchmarks by compilations.creating {
            defaultSourceSet {
                dependencies {
                    // Depend on main compilation output + its dependencies
                    implementation(main.compileDependencyFiles + main.output.classesDirs)
                    // Depend on test compilation output (for MockApp, FormParseInit, etc.)
                    implementation(test.compileDependencyFiles + test.output.classesDirs)
                    // Benchmark runtime
                    implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.16")
                }
            }
        }
    }
}
```

**Step 4: Register the benchmark target**

After the `kotlin {}` block (before the `sourceSets["main"]` lines), add:

```kotlin
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
        register("jvmBenchmarks")
    }
}
```

**Step 5: Create the benchmark source directory**

```bash
mkdir -p commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks
```

**Step 6: Verify the Gradle configuration compiles**

```bash
cd commcare-core
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew compileKotlin compileJava
```

Expected: BUILD SUCCESSFUL (no benchmark source to compile yet, but Gradle configuration is valid).

**Step 7: Commit**

```bash
git add commcare-core/build.gradle.kts commcare-core/src/jvmBenchmarks/
git commit -m "build: Add kotlinx-benchmark infrastructure for performance testing"
```

---

### Task 2: Create Form Loading Benchmark

**Files:**
- Create: `commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/FormLoadingBenchmark.kt`

This ports the existing `LoadAndInitLargeForm.java` JMH benchmark to kotlinx-benchmark. It's the easiest starting point since we have a working reference.

**Step 1: Write the benchmark**

```kotlin
package org.commcare.benchmarks

import org.commcare.modern.session.SessionWrapper
import org.commcare.session.SessionNavigator
import org.commcare.test.utilities.MockApp
import org.commcare.test.utilities.MockSessionNavigationResponder
import org.javarosa.core.test.FormParseInit
import org.javarosa.form.api.FormEntryController
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class FormLoadingBenchmark {

    /**
     * Full form load with session navigation — the real-world path.
     * Uses the 328KB large TDH form with ~1,859 fields.
     */
    @Benchmark
    fun loadLargeFormWithSession(): Int {
        val mockApp = MockApp("/app_performance/")
        val responder = MockSessionNavigationResponder(mockApp.session)
        val navigator = SessionNavigator(responder)
        val session: SessionWrapper = mockApp.session

        navigator.startNextSessionStep()
        session.setCommand("m1")
        navigator.startNextSessionStep()
        session.setEntityDatum("case_id", "3b6bff05-b9c3-42d8-9b12-9b27a834d330")
        navigator.startNextSessionStep()

        session.setCommand("m1-f2")
        navigator.startNextSessionStep()
        session.setEntityDatum(
            "case_id_new_imci_visit_0",
            "593ef28a-34ff-421d-a29c-6a0fd975df95"
        )
        navigator.startNextSessionStep()

        val fec: FormEntryController = mockApp.loadAndInitForm("large_tdh_form.xml")
        return fec.model.getEvent()
    }

    /**
     * Just XForm XML parsing — isolates parser overhead from model initialization.
     */
    @Benchmark
    fun parseLargeFormXml(): Int {
        val fpi = FormParseInit("/app_performance/large_tdh_form.xml")
        return fpi.formDef.children.size
    }
}
```

**Step 2: Verify compilation**

```bash
cd commcare-core
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew compileJvmBenchmarksKotlin
```

Expected: BUILD SUCCESSFUL

**Step 3: Run the benchmark (short iteration for verification)**

```bash
cd commcare-core
./gradlew jvmBenchmarksBenchmark
```

Expected: Benchmark runs and produces JSON output in `build/reports/benchmarks/`.

**Troubleshooting:** If `FormParseInit` can't find the resource, the classpath may not include test resources. Add this to the benchmark compilation dependencies if needed:

```kotlin
implementation(files("src/test/resources"))
```

**Step 4: Commit**

```bash
git add commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/FormLoadingBenchmark.kt
git commit -m "perf: Add form loading benchmark (port of LoadAndInitLargeForm)"
```

---

### Task 3: Create XPath Evaluation Benchmark

**Files:**
- Create: `commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/XPathBenchmark.kt`

XPath evaluation is the hottest code path — thousands of evaluations per form session.

**Step 1: Write the benchmark**

```kotlin
package org.commcare.benchmarks

import org.commcare.test.utilities.MockApp
import org.commcare.test.utilities.MockSessionNavigationResponder
import org.commcare.session.SessionNavigator
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryModel
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class XPathBenchmark {

    private lateinit var model: FormEntryModel
    private lateinit var evalContext: EvaluationContext

    /**
     * Set up a loaded form with evaluation context.
     * Uses the large TDH form so XPath expressions have realistic data to traverse.
     */
    @Setup(Level.Trial)
    fun setUp() {
        val mockApp = MockApp("/app_performance/")
        val responder = MockSessionNavigationResponder(mockApp.session)
        val navigator = SessionNavigator(responder)
        val session = mockApp.session

        navigator.startNextSessionStep()
        session.setCommand("m1")
        navigator.startNextSessionStep()
        session.setEntityDatum("case_id", "3b6bff05-b9c3-42d8-9b12-9b27a834d330")
        navigator.startNextSessionStep()
        session.setCommand("m1-f2")
        navigator.startNextSessionStep()
        session.setEntityDatum(
            "case_id_new_imci_visit_0",
            "593ef28a-34ff-421d-a29c-6a0fd975df95"
        )
        navigator.startNextSessionStep()

        val fec: FormEntryController = mockApp.loadAndInitForm("large_tdh_form.xml")
        model = fec.model
        evalContext = model.form.evaluationContext
    }

    /**
     * Parse a simple XPath reference. Baseline for parser overhead.
     */
    @Benchmark
    fun parseSimpleReference(): Any {
        return XPathParseTool.parseXPath("/data/question1")
    }

    /**
     * Parse a complex XPath expression with predicates and functions.
     */
    @Benchmark
    fun parseComplexExpression(): Any {
        return XPathParseTool.parseXPath(
            "if(count(instance('casedb')/casedb/case[@case_type='patient' and @status='open']) > 0, 'yes', 'no')"
        )
    }

    /**
     * Evaluate a simple path expression against the loaded form model.
     */
    @Benchmark
    fun evalSimpleReference(): Any? {
        val expr = XPathParseTool.parseXPath("/data/patient_name")
        return FunctionUtils.unpack(expr.eval(model.form.mainInstance, evalContext))
    }

    /**
     * Evaluate an arithmetic expression (e.g., BMI calculation).
     */
    @Benchmark
    fun evalArithmetic(): Any? {
        val expr = XPathParseTool.parseXPath("1 + 2 * 3 div 4")
        return FunctionUtils.unpack(expr.eval(model.form.mainInstance, evalContext))
    }

    /**
     * Evaluate a string concatenation expression.
     */
    @Benchmark
    fun evalStringFunction(): Any? {
        val expr = XPathParseTool.parseXPath("concat('hello', ' ', 'world')")
        return FunctionUtils.unpack(expr.eval(model.form.mainInstance, evalContext))
    }

    /**
     * Evaluate a conditional expression (relevance/skip logic pattern).
     */
    @Benchmark
    fun evalConditional(): Any? {
        val expr = XPathParseTool.parseXPath("if(true() and not(false()), 'show', 'hide')")
        return FunctionUtils.unpack(expr.eval(model.form.mainInstance, evalContext))
    }

    /**
     * Batch evaluation — 20 mixed expressions, simulating form entry.
     * This is the most realistic benchmark: during form entry, each question
     * triggers multiple XPath evaluations for relevance, constraints, and calculations.
     */
    @Benchmark
    fun evalBatch20(): Int {
        val expressions = listOf(
            "true()",
            "false()",
            "1 + 1",
            "2 * 3",
            "concat('a', 'b')",
            "string-length('hello')",
            "not(false())",
            "if(true(), 1, 0)",
            "number('42')",
            "string(42)",
            "true() and true()",
            "true() or false()",
            "1 = 1",
            "1 != 2",
            "1 < 2",
            "2 > 1",
            "1 + 2 + 3",
            "concat('a', 'b', 'c')",
            "if(1 > 0, 'pos', 'neg')",
            "not(1 = 2)"
        )
        var count = 0
        for (exprStr in expressions) {
            val expr = XPathParseTool.parseXPath(exprStr)
            FunctionUtils.unpack(expr.eval(model.form.mainInstance, evalContext))
            count++
        }
        return count
    }
}
```

**Step 2: Verify compilation**

```bash
cd commcare-core && ./gradlew compileJvmBenchmarksKotlin
```

Expected: BUILD SUCCESSFUL

**Step 3: Run and verify output**

```bash
cd commcare-core && ./gradlew jvmBenchmarksBenchmark
```

Expected: All XPath benchmarks produce results in JSON.

**Step 4: Commit**

```bash
git add commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/XPathBenchmark.kt
git commit -m "perf: Add XPath evaluation benchmark suite"
```

---

### Task 4: Create Case Query Benchmark

**Files:**
- Create: `commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/CaseQueryBenchmark.kt`

Case queries run when displaying case lists. Performance depends on case database size and filter complexity.

**Step 1: Write the benchmark**

This benchmark uses MockApp to load the app_performance test app, which includes case data in `user_restore.xml`. For larger datasets, it synthetically creates additional cases.

```kotlin
package org.commcare.benchmarks

import org.commcare.test.utilities.MockApp
import org.commcare.test.utilities.CaseTestUtils
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class CaseQueryBenchmark {

    private lateinit var evalContext: EvaluationContext

    @Setup(Level.Trial)
    fun setUp() {
        val mockApp = MockApp("/app_performance/")
        val session = mockApp.session
        evalContext = session.evaluationContext
    }

    /**
     * Query all cases — baseline for case database traversal.
     */
    @Benchmark
    fun queryAllCases(): Any? {
        val expr = XPathParseTool.parseXPath(
            "count(instance('casedb')/casedb/case)"
        )
        return FunctionUtils.unpack(expr.eval(evalContext))
    }

    /**
     * Query cases with a simple type filter.
     */
    @Benchmark
    fun queryCasesByType(): Any? {
        val expr = XPathParseTool.parseXPath(
            "count(instance('casedb')/casedb/case[@case_type='case'])"
        )
        return FunctionUtils.unpack(expr.eval(evalContext))
    }

    /**
     * Query cases with compound filter (type + status).
     */
    @Benchmark
    fun queryCasesCompoundFilter(): Any? {
        val expr = XPathParseTool.parseXPath(
            "count(instance('casedb')/casedb/case[@case_type='case' and @status='open'])"
        )
        return FunctionUtils.unpack(expr.eval(evalContext))
    }

    /**
     * Query a specific case by ID.
     */
    @Benchmark
    fun queryCaseById(): Any? {
        val expr = XPathParseTool.parseXPath(
            "instance('casedb')/casedb/case[@case_id='3b6bff05-b9c3-42d8-9b12-9b27a834d330']/case_name"
        )
        return FunctionUtils.unpack(expr.eval(evalContext))
    }
}
```

**Step 2: Verify compilation**

```bash
cd commcare-core && ./gradlew compileJvmBenchmarksKotlin
```

**Step 3: Run and verify**

```bash
cd commcare-core && ./gradlew jvmBenchmarksBenchmark
```

**Step 4: Commit**

```bash
git add commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/CaseQueryBenchmark.kt
git commit -m "perf: Add case query benchmark suite"
```

---

### Task 5: Create Serialization Benchmark

**Files:**
- Create: `commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/SerializationBenchmark.kt`

Serialization is how the engine persists data. This measures the `Externalizable` round-trip performance.

**Step 1: Write the benchmark**

```kotlin
package org.commcare.benchmarks

import org.commcare.cases.model.Case
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.test.FormParseInit
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.LivePrototypeFactory
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.openjdk.jmh.annotations.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

@State(Scope.Benchmark)
open class SerializationBenchmark {

    private lateinit var formInstance: FormInstance
    private lateinit var serializedFormBytes: ByteArray
    private lateinit var prototypeFactory: PrototypeFactory

    @Setup(Level.Trial)
    fun setUp() {
        prototypeFactory = LivePrototypeFactory()

        // Load a form and get its instance for serialization benchmarking
        val fpi = FormParseInit("/app_performance/large_tdh_form.xml")
        formInstance = fpi.formDef.mainInstance

        // Pre-serialize for deserialization benchmark
        val baos = ByteArrayOutputStream()
        formInstance.writeExternal(DataOutputStream(baos))
        serializedFormBytes = baos.toByteArray()
    }

    /**
     * Serialize a form instance to bytes.
     */
    @Benchmark
    fun serializeFormInstance(): ByteArray {
        val baos = ByteArrayOutputStream()
        formInstance.writeExternal(DataOutputStream(baos))
        return baos.toByteArray()
    }

    /**
     * Deserialize a form instance from bytes.
     */
    @Benchmark
    fun deserializeFormInstance(): FormInstance {
        val instance = FormInstance()
        instance.readExternal(
            DataInputStream(ByteArrayInputStream(serializedFormBytes)),
            prototypeFactory
        )
        return instance
    }

    /**
     * Full round-trip: serialize then deserialize.
     */
    @Benchmark
    fun roundTripFormInstance(): FormInstance {
        val baos = ByteArrayOutputStream()
        formInstance.writeExternal(DataOutputStream(baos))
        val bytes = baos.toByteArray()

        val result = FormInstance()
        result.readExternal(
            DataInputStream(ByteArrayInputStream(bytes)),
            prototypeFactory
        )
        return result
    }
}
```

**Step 2: Verify compilation**

```bash
cd commcare-core && ./gradlew compileJvmBenchmarksKotlin
```

**Step 3: Run and verify**

```bash
cd commcare-core && ./gradlew jvmBenchmarksBenchmark
```

**Step 4: Commit**

```bash
git add commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/SerializationBenchmark.kt
git commit -m "perf: Add serialization round-trip benchmark suite"
```

---

### Task 6: Create Session Navigation Benchmark

**Files:**
- Create: `commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/SessionNavigationBenchmark.kt`

Session navigation orchestrates the full user workflow: menu → case → form.

**Step 1: Write the benchmark**

```kotlin
package org.commcare.benchmarks

import org.commcare.modern.session.SessionWrapper
import org.commcare.session.SessionNavigator
import org.commcare.test.utilities.MockApp
import org.commcare.test.utilities.MockSessionNavigationResponder
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class SessionNavigationBenchmark {

    /**
     * Full session navigation from app entry to form ready.
     * This is the end-to-end latency users experience when opening a form.
     */
    @Benchmark
    fun navigateToForm(): Int {
        val mockApp = MockApp("/app_performance/")
        val responder = MockSessionNavigationResponder(mockApp.session)
        val navigator = SessionNavigator(responder)
        val session: SessionWrapper = mockApp.session

        // Navigate: root → module → case select → form → case select → ready
        navigator.startNextSessionStep()
        session.setCommand("m1")
        navigator.startNextSessionStep()
        session.setEntityDatum("case_id", "3b6bff05-b9c3-42d8-9b12-9b27a834d330")
        navigator.startNextSessionStep()
        session.setCommand("m1-f2")
        navigator.startNextSessionStep()
        session.setEntityDatum(
            "case_id_new_imci_visit_0",
            "593ef28a-34ff-421d-a29c-6a0fd975df95"
        )
        navigator.startNextSessionStep()

        return responder.lastResultCode
    }

    /**
     * Just app initialization — how long to set up MockApp (profile install + restore).
     * This is the "app launch" cost.
     */
    @Benchmark
    fun initializeApp(): Int {
        val mockApp = MockApp("/app_performance/")
        return mockApp.session.hashCode()
    }
}
```

**Step 2: Verify compilation**

```bash
cd commcare-core && ./gradlew compileJvmBenchmarksKotlin
```

**Step 3: Run and verify**

```bash
cd commcare-core && ./gradlew jvmBenchmarksBenchmark
```

**Step 4: Commit**

```bash
git add commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/SessionNavigationBenchmark.kt
git commit -m "perf: Add session navigation benchmark suite"
```

---

### Task 7: Create Sync & Restore Benchmark

**Files:**
- Create: `commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/SyncRestoreBenchmark.kt`

User restore parsing is the longest user-facing operation. The test fixture `user_restore.xml` is 3.6MB.

**Step 1: Write the benchmark**

```kotlin
package org.commcare.benchmarks

import org.commcare.test.utilities.MockApp
import org.javarosa.xml.util.InvalidStructureException
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class SyncRestoreBenchmark {

    /**
     * Full app initialization including user restore parsing.
     * MockApp constructor parses the user_restore.xml and populates the sandbox.
     * This is the closest proxy to real sync/restore performance.
     */
    @Benchmark
    fun fullRestoreAndInit(): Int {
        val mockApp = MockApp("/app_performance/")
        val session = mockApp.session
        // Force evaluation of the session to ensure all data is loaded
        return session.evaluationContext.hashCode()
    }
}
```

**Note:** The sync/restore benchmark overlaps with `initializeApp` in Task 6, but this one focuses on the evaluation context initialization which forces case database population. We may refine these benchmarks after the first run to better isolate restore parsing from other initialization. The first run tells us what's worth measuring more precisely.

**Step 2: Verify compilation**

```bash
cd commcare-core && ./gradlew compileJvmBenchmarksKotlin
```

**Step 3: Run and verify**

```bash
cd commcare-core && ./gradlew jvmBenchmarksBenchmark
```

**Step 4: Commit**

```bash
git add commcare-core/src/jvmBenchmarks/kotlin/org/commcare/benchmarks/SyncRestoreBenchmark.kt
git commit -m "perf: Add sync and restore benchmark suite"
```

---

### Task 8: Create Comparison Tooling

**Files:**
- Create: `commcare-core/benchmarks/compare.py`
- Create: `commcare-core/benchmarks/baselines/.gitkeep`
- Create: `commcare-core/benchmarks/README.md`

**Step 1: Create the directory structure**

```bash
mkdir -p commcare-core/benchmarks/baselines
```

**Step 2: Write the comparison script**

`commcare-core/benchmarks/compare.py`:

```python
#!/usr/bin/env python3
"""
Compare benchmark results across Java/JVM, Kotlin/JVM, and Kotlin/Native.

Usage:
    python compare.py [--java baselines/java-jvm.json] [--kt-jvm path/to/jvm.json] [--kt-native path/to/native.json]

If no arguments given, looks for results in default locations:
    - Java baseline: benchmarks/baselines/java-jvm.json
    - Kotlin/JVM: build/reports/benchmarks/jvmBenchmarks/main/
    - Kotlin/Native: build/reports/benchmarks/macosArm64Benchmarks/main/
"""

import json
import sys
import os
import glob
import argparse


def load_jmh_results(path):
    """Load JMH JSON results (used by both JMH and kotlinx-benchmark on JVM)."""
    with open(path, 'r') as f:
        data = json.load(f)

    results = {}
    for entry in data:
        name = entry.get('benchmark', '')
        # Strip package prefix, keep ClassName.methodName
        short_name = '.'.join(name.split('.')[-2:])
        score = entry.get('primaryMetric', {}).get('score', 0)
        unit = entry.get('primaryMetric', {}).get('scoreUnit', 'ms/op')
        error = entry.get('primaryMetric', {}).get('scoreError', 0)
        results[short_name] = {
            'score': score,
            'unit': unit,
            'error': error,
        }
    return results


def find_latest_json(directory):
    """Find the most recent JSON file in a directory."""
    pattern = os.path.join(directory, '*.json')
    files = glob.glob(pattern)
    if not files:
        return None
    return max(files, key=os.path.getmtime)


def format_score(score, error=0):
    """Format a benchmark score with error margin."""
    if score < 1:
        return f"{score*1000:.1f}us"
    elif score < 1000:
        return f"{score:.2f}ms"
    else:
        return f"{score/1000:.2f}s"


def format_delta(baseline, current):
    """Format the delta between two scores."""
    if baseline == 0:
        return "N/A"
    ratio = current / baseline
    if ratio > 1.05:
        return f"{ratio:.1f}x slower"
    elif ratio < 0.95:
        return f"{1/ratio:.1f}x faster"
    else:
        return "~same"


def main():
    parser = argparse.ArgumentParser(description='Compare benchmark results')
    parser.add_argument('--java', help='Java/JVM baseline JSON path')
    parser.add_argument('--kt-jvm', help='Kotlin/JVM results JSON path')
    parser.add_argument('--kt-native', help='Kotlin/Native results JSON path')
    args = parser.parse_args()

    # Resolve paths
    script_dir = os.path.dirname(os.path.abspath(__file__))
    base_dir = os.path.dirname(script_dir)  # commcare-core/

    java_path = args.java or os.path.join(script_dir, 'baselines', 'java-jvm.json')
    kt_jvm_path = args.kt_jvm or find_latest_json(
        os.path.join(base_dir, 'build', 'reports', 'benchmarks', 'jvmBenchmarks', 'main')
    )
    kt_native_path = args.kt_native or find_latest_json(
        os.path.join(base_dir, 'build', 'reports', 'benchmarks', 'macosArm64Benchmarks', 'main')
    )

    # Load available results
    java_results = load_jmh_results(java_path) if java_path and os.path.exists(java_path) else {}
    kt_jvm_results = load_jmh_results(kt_jvm_path) if kt_jvm_path and os.path.exists(kt_jvm_path) else {}
    kt_native_results = load_jmh_results(kt_native_path) if kt_native_path and os.path.exists(kt_native_path) else {}

    if not java_results and not kt_jvm_results and not kt_native_results:
        print("No benchmark results found. Run benchmarks first.")
        print(f"  Kotlin/JVM:    cd commcare-core && ./gradlew jvmBenchmarksBenchmark")
        print(f"  Kotlin/Native: cd commcare-core && ./gradlew macosArm64BenchmarksBenchmark")
        sys.exit(1)

    # Collect all benchmark names
    all_names = sorted(set(
        list(java_results.keys()) +
        list(kt_jvm_results.keys()) +
        list(kt_native_results.keys())
    ))

    # Print header
    has_java = bool(java_results)
    has_kt_jvm = bool(kt_jvm_results)
    has_kt_native = bool(kt_native_results)

    header = f"{'Benchmark':<45}"
    if has_java:
        header += f"{'Java/JVM':>12}"
    if has_kt_jvm:
        header += f"{'Kt/JVM':>12}"
    if has_kt_native:
        header += f"{'Kt/Native':>12}"
    if has_java and has_kt_jvm:
        header += f"{'Kt/JVM vs Java':>18}"
    if has_java and has_kt_native:
        header += f"{'Native vs Java':>18}"
    if has_kt_jvm and has_kt_native:
        header += f"{'Native vs Kt/JVM':>18}"

    print()
    print(header)
    print("=" * len(header))

    for name in all_names:
        java = java_results.get(name, {})
        kt_jvm = kt_jvm_results.get(name, {})
        kt_native = kt_native_results.get(name, {})

        line = f"{name:<45}"
        if has_java:
            line += f"{format_score(java.get('score', 0)):>12}" if java else f"{'—':>12}"
        if has_kt_jvm:
            line += f"{format_score(kt_jvm.get('score', 0)):>12}" if kt_jvm else f"{'—':>12}"
        if has_kt_native:
            line += f"{format_score(kt_native.get('score', 0)):>12}" if kt_native else f"{'—':>12}"
        if has_java and has_kt_jvm:
            if java and kt_jvm:
                line += f"{format_delta(java['score'], kt_jvm['score']):>18}"
            else:
                line += f"{'—':>18}"
        if has_java and has_kt_native:
            if java and kt_native:
                line += f"{format_delta(java['score'], kt_native['score']):>18}"
            else:
                line += f"{'—':>18}"
        if has_kt_jvm and has_kt_native:
            if kt_jvm and kt_native:
                line += f"{format_delta(kt_jvm['score'], kt_native['score']):>18}"
            else:
                line += f"{'—':>18}"

        print(line)

    print()

    # Summary
    if has_java and has_kt_jvm:
        deltas = []
        for name in all_names:
            java = java_results.get(name, {})
            kt_jvm = kt_jvm_results.get(name, {})
            if java and kt_jvm and java.get('score', 0) > 0:
                deltas.append(kt_jvm['score'] / java['score'])
        if deltas:
            avg = sum(deltas) / len(deltas)
            print(f"Average Kotlin/JVM vs Java/JVM: {avg:.2f}x")

    if has_java and has_kt_native:
        deltas = []
        for name in all_names:
            java = java_results.get(name, {})
            kt_native = kt_native_results.get(name, {})
            if java and kt_native and java.get('score', 0) > 0:
                deltas.append(kt_native['score'] / java['score'])
        if deltas:
            avg = sum(deltas) / len(deltas)
            print(f"Average Kotlin/Native vs Java/JVM: {avg:.2f}x")

    print()


if __name__ == '__main__':
    main()
```

**Step 3: Write the README**

`commcare-core/benchmarks/README.md`:

```markdown
# CommCare Engine Benchmarks

Performance benchmarks comparing the CommCare engine across Java/JVM (upstream baseline),
Kotlin/JVM (our port), and Kotlin/Native (iOS proxy via macOS).

## Quick Start

### Run Kotlin/JVM Benchmarks

```bash
cd commcare-core
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew jvmBenchmarksBenchmark
```

Results: `build/reports/benchmarks/jvmBenchmarks/main/*.json`

### Run Kotlin/Native Benchmarks (macOS only)

```bash
cd commcare-core
./gradlew macosArm64BenchmarksBenchmark
```

Results: `build/reports/benchmarks/macosArm64Benchmarks/main/*.json`

### Compare Results

```bash
python benchmarks/compare.py
```

To compare specific files:

```bash
python benchmarks/compare.py \
  --java benchmarks/baselines/java-jvm.json \
  --kt-jvm build/reports/benchmarks/jvmBenchmarks/main/output.json \
  --kt-native build/reports/benchmarks/macosArm64Benchmarks/main/output.json
```

## Benchmark Suites

| Suite | File | What it measures |
|-------|------|-----------------|
| Form Loading | `FormLoadingBenchmark.kt` | XForm parsing + model initialization |
| XPath Evaluation | `XPathBenchmark.kt` | Expression parsing and evaluation |
| Case Query | `CaseQueryBenchmark.kt` | Case database filtering and lookup |
| Serialization | `SerializationBenchmark.kt` | Externalizable serialize/deserialize |
| Session Navigation | `SessionNavigationBenchmark.kt` | Full menu→case→form workflow |
| Sync & Restore | `SyncRestoreBenchmark.kt` | User restore parsing and initialization |

## Java Baseline

The Java baseline comes from running JMH benchmarks against an unmodified
clone of `dimagi/commcare-core`. See `baselines/java-jvm.json`.

To regenerate the baseline:

1. Clone `dimagi/commcare-core` separately
2. Add JMH benchmarks (see design doc)
3. Run: `java -jar build/libs/commcare-core-jmh.jar -rf json -rff results.json`
4. Copy `results.json` to `baselines/java-jvm.json`

## Interpreting Results

- **Kt/JVM vs Java**: Measures Kotlin conversion overhead. Ideally < 1.2x.
- **Native vs Java**: Measures iOS platform overhead. Expected 3-5x for Kotlin/Native.
- **Native vs Kt/JVM**: Isolates the JVM-vs-Native delta from conversion overhead.

Numbers vary by machine. Relative comparisons (deltas) are more meaningful than absolutes.
Run all benchmarks on the same machine for valid comparison.
```

**Step 4: Create the baselines placeholder**

```bash
touch commcare-core/benchmarks/baselines/.gitkeep
```

**Step 5: Commit**

```bash
git add commcare-core/benchmarks/
git commit -m "perf: Add benchmark comparison tooling and documentation"
```

---

### Task 9: Verify Full Benchmark Suite Runs End-to-End

**Files:** None new — this is a validation task.

Before setting up the upstream baseline, verify that all 6 benchmark suites run successfully as a complete suite.

**Step 1: Run the full benchmark suite**

```bash
cd commcare-core
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew jvmBenchmarksBenchmark
```

Expected: All benchmarks run. JSON output written to `build/reports/benchmarks/jvmBenchmarks/main/`.

**Step 2: Verify the comparison script works with Kotlin/JVM data only**

```bash
cd commcare-core
python benchmarks/compare.py
```

Expected: Table showing Kotlin/JVM results (no Java or Native columns since baselines don't exist yet).

**Step 3: Review results for reasonableness**

Check that:
- Form loading benchmarks take 10-500ms (not 0ms or >10s)
- XPath parse benchmarks take <1ms each
- XPath eval benchmarks take <10ms each
- Serialization benchmarks take 1-100ms
- No benchmark threw an exception

**Step 4: Fix any issues found and re-run**

If a benchmark fails, common issues:
- Resource not found: check classpath includes test resources
- MockApp initialization failure: check that test fixtures are accessible from benchmark source set
- OutOfMemory: add JVM args to benchmark configuration

**Step 5: Commit any fixes**

```bash
git add -A
git commit -m "perf: Fix benchmark runtime issues discovered during validation"
```

---

### Task 10: Document Results and Create PR

**Files:**
- No new files — commit history from Tasks 1-9 forms the PR

**Step 1: Run the full suite one final time and capture output**

```bash
cd commcare-core
./gradlew jvmBenchmarksBenchmark 2>&1 | tee benchmark-output.txt
```

**Step 2: Run comparison to get the Kotlin/JVM baseline numbers**

```bash
python benchmarks/compare.py
```

**Step 3: Create the PR**

Target: `main` branch. Include:
- Summary of what was built (kotlinx-benchmark infrastructure + 6 suites + comparison tooling)
- Kotlin/JVM baseline numbers from the first run
- Next steps (upstream Java baseline, macOS native benchmarks)

```bash
cd ..  # back to repo root
git push origin HEAD
gh pr create --title "perf: Add comprehensive benchmark suite with kotlinx-benchmark" --body "$(cat <<'EOF'
## Summary

- Added kotlinx-benchmark infrastructure to commcare-core with JVM target
- Implemented 6 benchmark suites: Form Loading, XPath Evaluation, Case Query,
  Serialization, Session Navigation, and Sync & Restore
- Created Python comparison script for cross-platform delta analysis
- Established Kotlin/JVM baseline numbers

## Benchmark Suites

| Suite | Benchmarks | What it measures |
|-------|-----------|-----------------|
| Form Loading | 2 | XForm parsing + session initialization |
| XPath Evaluation | 7 | Parse + eval across expression types |
| Case Query | 4 | Case database filtering and lookup |
| Serialization | 3 | Externalizable round-trip |
| Session Navigation | 2 | Full workflow latency |
| Sync & Restore | 1 | User restore + initialization |

## How to run

```bash
cd commcare-core
./gradlew jvmBenchmarksBenchmark    # Run all benchmarks
python benchmarks/compare.py        # View results
```

## Next steps

1. Clone dimagi/commcare-core, add JMH benchmarks, establish Java/JVM baseline
2. Add macOS native target when Phase 3 moves engine code to commonMain
3. Three-way comparison report after Phase 3

## Design doc

See `docs/plans/2026-03-11-performance-testing-design.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Future Tasks (Not in This PR)

These are documented here for reference but will be separate PRs:

### Future Task A: Set Up Upstream Java Baseline

1. Clone `dimagi/commcare-core` to a separate directory
2. Add the JMH Gradle plugin to its `build.gradle`
3. Write 6 JMH benchmark classes mirroring our kotlinx-benchmark suites
4. Run benchmarks, export as JSON
5. Copy results to `commcare-core/benchmarks/baselines/java-jvm.json` in our repo
6. Re-run `compare.py` for first cross-platform comparison

### Future Task B: Add macOS Native Target

After Phase 3 Waves 6-7 move engine code to commonMain:

1. Add `macosArm64()` target to build.gradle.kts
2. Register `macosArm64Benchmarks` target in benchmark config
3. Migrate benchmarks that can run on native (those not dependent on JVM test utilities)
4. Run on macOS, compare against JVM numbers

### Future Task C: CI Integration

1. Add GitHub Action that runs `./gradlew jvmBenchmarksBenchmark` on PRs
2. Compare output against committed baselines
3. Post delta table as PR comment
4. Configure failure threshold (e.g., >15% regression)
