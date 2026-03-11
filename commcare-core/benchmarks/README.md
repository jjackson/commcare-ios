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
| Session Navigation | `SessionNavigationBenchmark.kt` | Full menu->case->form workflow |
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
