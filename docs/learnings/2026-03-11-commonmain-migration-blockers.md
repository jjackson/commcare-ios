# CommonMain Migration Blockers

**Date:** 2026-03-11
**Context:** Phase 3 Wave 6 — Attempting to move XPath engine and utility files to commonMain

## Key Finding

Moving files from `src/main/java` to `src/commonMain/kotlin` is fundamentally harder than removing `java.*` imports. Even after Waves 1-5 removed JVM collection types, Date, regex, and stream dependencies, **zero files** were ready for immediate commonMain migration without modification.

## Blockers by Category

### 1. KMP Metadata Compilation Is Stricter Than JVM

| Pattern | JVM | CommonMain | Fix |
|---------|-----|-----------|-----|
| `@JvmStatic` | Implicit | Must `import kotlin.jvm.JvmStatic` | Add explicit import |
| `String.format()` | Available | Not available | String templates |
| `StringBuffer` | Available | Not available | `StringBuilder` |
| `String(CharArray)` | Available | Deprecated | `charArray.concatToString()` |
| `HashSet.clone()` | Available | Not available | `HashSet(original)` |
| `LinkedHashMap` | Extensible | **Final** | Cannot subclass in common |
| `Class<*>`, `.javaClass` | Available | Not available | expect/actual |
| `Thread`, `synchronized` | Available | Not available | expect/actual |
| `WeakReference` | Available | Not available | expect/actual |
| `System.currentTimeMillis()` | Available | Not available | expect/actual |

### 2. Circular Dependencies Between XPath and Model

```
XPath engine → EvaluationContext → XPath engine
```

- `XPathExpression` depends on `EvaluationContext`, `DataInstance`, `TreeReference`
- `EvaluationContext` depends on `XPathExpression`, `FunctionUtils`, `ExpressionCacher`
- Cannot move either side independently

### 3. Reflection Everywhere in Serialization

`ExtUtil`, `ExtWrapList`, `ExtWrapTagged`, `ExtWrapMap` all use:
- `Class<*>` parameters
- `Class.forName()` for deserialization
- `.newInstance()` for object creation
- `PrototypeFactory` (already abstracted via expect/actual, but callers still use `Class<*>`)

### 4. No Low-Hanging Fruit

Analysis of all 535+ .kt files in `src/main/java`:
- ~443 have no `java.*` imports
- ~26 avoid JVM-specific patterns
- **0** have all dependencies already in commonMain

Every file depends on other `src/main/java` files that also can't move independently.

## Recommended Strategy

The original Phase 3 wave plan assumed files could move independently. The reality is that **files must move in large cohesive batches** with their entire dependency chains.

### Batch 1: Pure Utility Objects
Adapt and move together: `DataUtil`, `CompressingIdGenerator`, `MathUtils`, `PropertyUtils`
- Requires: expect/actual for `Random`/`SecureRandom`

### Batch 2: Serialization Wrappers
Move together: `ExtWrapList`, `ExtWrapNullable`, `ExtWrapTagged`, `ExtWrapBase`, `ExtWrapMap`, `ExtWrapListPoly`, `ExtUtil`
- Requires: abstract away `Class<*>` parameters (use `KClass<*>` or string-based type registry)
- This is the hardest batch due to deep reflection usage

### Batch 3: Core Instance Types
Move together: `TreeReference`, `AbstractTreeElement`, `DataInstance`, `TreeElement`
- Requires: Batch 2 complete, plus `CacheTable` expect/actual (WeakReference)

### Batch 4: Evaluation Context + XPath Engine
Move together: `EvaluationContext` + all XPath expression/function files (~100 files)
- Requires: Batches 1-3 complete
- Breaks the circular dependency by moving both sides at once

### Batch 5: Remaining Model/Cases/Session
Everything else follows once the core types are in commonMain.

## Impact on Plan

The Phase 3 wave plan needs revision:
- Waves 6 and 7 cannot be done independently as planned
- The migration is really one large batch operation (Batch 4 above)
- Prep work (KMP-compat fixes) can proceed incrementally
- Actual moves must happen as large, coordinated batches
