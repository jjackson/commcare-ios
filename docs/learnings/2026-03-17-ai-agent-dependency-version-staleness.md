# Learning: AI Agents Should Verify Dependency Versions at Runtime

**Date:** 2026-03-17
**Phase:** Phase 4 (Polish)
**Category:** AI agent process

## What Happened

The project was initialized with Kotlin 2.0.21 and Compose Multiplatform 1.7.3. These were the latest stable versions as of the AI model's training data cutoff (~early 2025). By the time the project actually started (March 2026), significantly newer versions were available:

- Kotlin 2.2.20 (recommended for iOS)
- Compose Multiplatform 1.10.2 (latest stable)

The older CMP 1.7.3 has a critical limitation: `Modifier.semantics {}` crashes at runtime on iOS, making both VoiceOver accessibility and XCTest UI automation impossible. CMP 1.8.0 (May 2025) was the first version with working iOS accessibility support.

## Impact

- Phase 4 Wave 4 (Accessibility) could not ship VoiceOver semantic descriptions — all `semantics` code had to be removed
- XCTest UI tests crash the app on launch because XCTest's accessibility framework conflicts with Compose's incomplete iOS accessibility implementation
- End-to-end iOS UI testing against real HQ is blocked until CMP is upgraded
- A major dependency upgrade (Kotlin 2.0 → 2.2 + CMP 1.7 → 1.10) is now needed mid-project, with risk of breaking 800+ source files in commcare-core

## Root Cause

The AI agent that initialized the project selected dependency versions from its training knowledge rather than checking what was actually available at the time of execution. LLM training data has an inherent staleness window — versions that were "latest" during training may be 6-12 months behind by the time the model is used.

## Recommendation

When an AI agent sets up a new project or selects dependency versions:

1. **Verify versions at runtime** — query Maven Central, GitHub releases, or the official docs (e.g. `https://kotlinlang.org/docs/releases.html`) to find the actual latest stable versions
2. **Don't trust training knowledge for version numbers** — treat any version number from the model's memory as a starting point to verify, not a final answer
3. **Check compatibility matrices** — for KMP specifically, the Kotlin ↔ Compose Multiplatform compatibility table should be consulted at project setup time
4. **Document version choices** — when pinning dependency versions, note why that version was chosen and what alternatives were considered

This applies broadly to any AI-driven project setup: npm packages, Python libraries, Gradle plugins, etc. The version the model "knows" is almost certainly not the latest.
