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

    script_dir = os.path.dirname(os.path.abspath(__file__))
    base_dir = os.path.dirname(script_dir)

    java_path = args.java or os.path.join(script_dir, 'baselines', 'java-jvm.json')
    kt_jvm_path = args.kt_jvm or find_latest_json(
        os.path.join(base_dir, 'build', 'reports', 'benchmarks', 'jvmBenchmarks', 'main')
    )
    kt_native_path = args.kt_native or find_latest_json(
        os.path.join(base_dir, 'build', 'reports', 'benchmarks', 'macosArm64Benchmarks', 'main')
    )

    java_results = load_jmh_results(java_path) if java_path and os.path.exists(java_path) else {}
    kt_jvm_results = load_jmh_results(kt_jvm_path) if kt_jvm_path and os.path.exists(kt_jvm_path) else {}
    kt_native_results = load_jmh_results(kt_native_path) if kt_native_path and os.path.exists(kt_native_path) else {}

    if not java_results and not kt_jvm_results and not kt_native_results:
        print("No benchmark results found. Run benchmarks first.")
        print(f"  Kotlin/JVM:    cd commcare-core && ./gradlew jvmBenchmarksBenchmark")
        print(f"  Kotlin/Native: cd commcare-core && ./gradlew macosArm64BenchmarksBenchmark")
        sys.exit(1)

    all_names = sorted(set(
        list(java_results.keys()) +
        list(kt_jvm_results.keys()) +
        list(kt_native_results.keys())
    ))

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
            line += f"{format_score(java.get('score', 0)):>12}" if java else f"{'---':>12}"
        if has_kt_jvm:
            line += f"{format_score(kt_jvm.get('score', 0)):>12}" if kt_jvm else f"{'---':>12}"
        if has_kt_native:
            line += f"{format_score(kt_native.get('score', 0)):>12}" if kt_native else f"{'---':>12}"
        if has_java and has_kt_jvm:
            if java and kt_jvm:
                line += f"{format_delta(java['score'], kt_jvm['score']):>18}"
            else:
                line += f"{'---':>18}"
        if has_java and has_kt_native:
            if java and kt_native:
                line += f"{format_delta(java['score'], kt_native['score']):>18}"
            else:
                line += f"{'---':>18}"
        if has_kt_jvm and has_kt_native:
            if kt_jvm and kt_native:
                line += f"{format_delta(kt_jvm['score'], kt_native['score']):>18}"
            else:
                line += f"{'---':>18}"

        print(line)

    print()

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
