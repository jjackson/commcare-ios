# Learning: J2K Converter vs AI-Driven Conversion

**Date**: 2026-03-09
**Context**: Reviewing Wave 3 conversion approach — agent confirmed all 134 files were converted by AI from scratch, not using IntelliJ's J2K converter. Investigated whether we should switch approaches for remaining waves (4-8, ~285 files).
**Status**: Resolved (staying with AI conversion, documenting tradeoffs)

## Problem

After Wave 3 completed, we questioned whether hand-converting every Java file via AI was the right approach vs. using IntelliJ's official Java-to-Kotlin (J2K) converter. The concern: are we doing unnecessary work that a tool could automate?

## Investigation

### What IntelliJ's J2K does well
- Mechanical syntax transformation: getters→properties, `instanceof`→`is`, `switch`→`when`, string concatenation→templates
- Handles ~80-90% of straightforward syntax changes reliably

### What J2K does NOT handle
- `@JvmField`, `@JvmStatic`, `@Throws` annotations for Java interop
- Nullability analysis from Java call-site patterns (our checklist item #1)
- `open` keyword based on subclass analysis across commcare-android/FormPlayer
- Generic type fixes (raw types, variance — our checklist items #2, #3)
- `protected`→`internal` visibility mapping (our checklist item #10)
- KDoc comment hazards (our checklist item #7)

### J2K is not headless
The converter is tightly coupled to IntelliJ's IDE internals (PSI, type resolution, symbol indexing). There is no official CLI. It can only be run via Android Studio GUI (Ctrl+Alt+Shift+K) or by building a custom IntelliJ plugin with ApplicationStarter.

### How others handle this at scale

**Meta (40,000+ files, "Kotlinator"):**
- Built custom headless J2K wrapper embedded in a server-side process
- Added ~50 preprocessing steps and ~150 postprocessing steps
- Added build-error-fix loop (parse compiler errors → apply corrections)
- Collaborated directly with JetBrains to improve J2K
- Open-sourced AST utilities: https://github.com/fbsamples/kotlin_ast_tools
- Did NOT open-source the full Kotlinator (too coupled to Buck/internal frameworks)

**Google:**
- Has a converter tool: https://github.com/google/kotlin_convert

**Pandora:**
- Built an IntelliJ plugin for bulk conversion with git history preservation
- https://github.com/PandoraMedia/multi-file-kotlin-converter-plugin

### Our environment
- Android Studio Narwhal 3 (2025.1.3) installed with JDK 21 (JBR)
- `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` — needed for Gradle
- No standalone `kotlinc` or `java` on PATH (only via Android Studio)
- J2K available only through Android Studio GUI

## Decision

**Stay with AI-driven conversion** for remaining waves. Rationale:

1. **The converter alone isn't sufficient.** Meta needed 200+ custom pre/post-processing steps. Everyone doing this at scale wraps J2K in a fix loop — which is what our AI agents already do (Wave 3: 6 iterations, 262→0 errors).

2. **Our hard problems are J2K's blind spots.** Nullability analysis, interop annotations, visibility mapping, and generic fixes are the bulk of our fix iterations. The converter doesn't help with any of these.

3. **Switching mid-project adds risk.** Waves 0-3 are done, learnings are accumulated, checklist is growing. Changing approach for waves 4-8 means re-learning failure modes.

4. **No headless option.** Running J2K requires Android Studio GUI, which doesn't integrate into our autonomous AI pipeline.

### Hybrid approach (future consideration)
If starting a similar project from scratch, the ideal workflow would be:
1. J2K for mechanical syntax transformation (via Android Studio GUI or custom plugin)
2. AI agent for interop annotations, nullability, generics, visibility fixes
3. Build-error-fix loop (already what our agents do)

This could reduce the number of fix iterations by giving the AI a closer starting point.

## Key Takeaway

J2K handles syntax; AI handles semantics. At our scale (~285 remaining files), the semantic fixes dominate the work. The converter would save some mechanical effort but wouldn't eliminate the fix-iterate loop that accounts for most of the conversion time. For autonomous AI pipelines without IDE access, pure AI conversion is a viable approach — Meta's experience shows that even with J2K, extensive post-processing is required.
