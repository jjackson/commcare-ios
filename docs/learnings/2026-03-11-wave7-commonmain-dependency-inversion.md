# Wave 7 Learning: CommonMain Migration Requires Dependency Inversion

**Date**: 2026-03-11
**Context**: Phase 3 Wave 7 — attempting to move XPath engine and model files to commonMain

## Problem

Attempted to move ~120 XPath files to commonMain, but discovered that ~107 of them depend on `EvaluationContext` and `DataInstance` via `eval(DataInstance, EvaluationContext)` method signatures. These classes can't move to commonMain because they have deep transitive dependencies:

- **EvaluationContext** imports: QueryContext, QuerySensitiveTreeElementWrapper, CurrentModelQuerySet, QueryUtils, FormInstance, TreeElement, ExternalDataInstance, ConcreteInstanceRoot, TreeUtilities
- **DataInstance** imports: QuerySensitiveTreeElementWrapper, QueryUtils, Persistable, LocalCacheTable, XPathReference

Additionally:
- **TreeReference** implements `XPathAnalyzable` and stores `XPathExpression` predicates — both depend on EvaluationContext transitively
- **Parser/AST files** create XPath expression objects, creating transitive dependencies
- **Analysis files** reference `XPathAnalyzer` which needs EvaluationContext

## Attempted Approach (Failed)

1. Moved ~120 files to commonMain
2. Applied bulk JVM API replacements (Math→kotlin.math, synchronized removal, etc.)
3. Got 829 commonMain errors
4. Fixed many, but EvaluationContext/DataInstance references remained
5. Had to move back ~100 files that transitively depended on EvaluationContext

## Key Finding: Synchronized Removal Causes Silent Failures

When bulk-removing `synchronized` blocks for KMP compatibility, some files ended up back in src/main/java with their `synchronized` removed. This caused `CaseXPathQueryTest` to fail with `ArrayIndexOutOfBoundsException` — a race condition exposed by the missing synchronization.

**Rule**: Only remove `synchronized` for files that actually move to commonMain. Files staying in src/main/java MUST keep their `synchronized` blocks.

## Solution: Dependency Inversion Interfaces

Create interfaces in commonMain that capture what the XPath engine needs:

### XPathEvalContext (11 methods needed)

From analysis of all commonMain XPath code:
1. `contextRef: TreeReference?` — TreeReference is in commonMain (once moved)
2. `getMainInstance(): Any?` — only passed through, never directly accessed
3. `getVariable(name: String?): Any?`
4. `getFunctionHandlers(): HashMap<String, IFunctionHandler>` — IFunctionHandler in commonMain
5. `getContextPosition(): Int`
6. `getOriginalContext(): TreeReference?`
7. `expressionCachingEnabled(): Boolean`
8. `expressionCacher(): ExpressionCacher?` — ExpressionCacher in commonMain
9. `openTrace(XPathExpression)` — XPathExpression in commonMain
10. `closeTrace()`
11. `reportTraceValue(Any?, Boolean)`

### XPathDataModel (minimal)

DataInstance is mostly passed through in XPath expressions without direct method calls. Only `getReference()` and `getInstanceId()` are accessed in a few places.

### Implementation Strategy

1. Create `XPathEvalContext` interface in commonMain
2. Create `XPathDataModel` interface in commonMain (or use `Any?` for model parameter)
3. Change all XPath expression `eval(DataInstance, EvaluationContext)` to use interfaces
4. Have `EvaluationContext` implement `XPathEvalContext` in src/main/java
5. Have `DataInstance` implement `XPathDataModel` in src/main/java
6. Move XPath expression files to commonMain

## What Was Successfully Moved

Despite the blocker, this wave moved:
- 5 data types: DecimalData, GeoPointData, IntegerData, LongData, StringData
- PlatformNanoTime expect/actual (common/jvm/ios)
- PlatformDateUtils expect/actual (common/jvm/ios)
- (Previous commit) Platform abstractions: PlatformThread, PlatformStdErr, CacheTable
- (Previous commit) Logger chain: 7 files
- Total commonMain: ~153 files

## Lesson

Large-scale file moves to commonMain must be preceded by dependency analysis. The "move and fix" approach wastes effort when fundamental architectural changes (dependency inversion) are needed. Better to:
1. Map the dependency graph first
2. Identify blocker types (EvaluationContext, DataInstance)
3. Create abstraction interfaces BEFORE moving files
4. Then do the bulk move with interfaces already in place
