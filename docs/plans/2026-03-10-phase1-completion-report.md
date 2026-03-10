# Phase 1 Completion Report: Core Port

**Date:** 2026-03-10
**Status:** Complete

## Summary

Phase 1 converted commcare-core from Java to Kotlin and added KMP multiplatform targets. The engine is now ready for iOS compilation via Kotlin/Native.

## Conversion Statistics

| Metric | Value |
|--------|-------|
| Kotlin files in src/main | 611 |
| Remaining Java files in src/main | 32 |
| Total test count | 710 |
| Test pass rate | 100% |
| JAR outputs verified | 5 (commcare-libraries, commcare-cli, commcare-api, form_translate, tests) |

## Wave History

| Wave | Group | Files | PR | Merged |
|------|-------|-------|-----|--------|
| 0 | Build setup | — | commcare-core #2 | 2026-03-08 |
| 1 | javarosa-utilities | 115 | commcare-core #3 | 2026-03-08 |
| 2 | javarosa-model | 82 | commcare-core #4 | 2026-03-08 |
| 3 | xpath-engine | 134 | #13 | 2026-03-09 |
| 4 | xform-parser | 27 | #21 | 2026-03-09 |
| 5 | case-management | 60 | #24 | 2026-03-09 |
| 6 | suite-and-session | 93 | #26 | 2026-03-10 |
| 7 | resources | 28 | #28 | 2026-03-10 |
| 8 | commcare-core-services | 71 | #29 | 2026-03-10 |
| 9 | KMP targets | — | #31 | 2026-03-10 |

## Remaining Java Files (32)

These were intentionally left as Java — they are simple interfaces, exceptions, and XML parsing base classes that interop cleanly with Kotlin:

- **data/xml/** (6): DataModelPullParser, SimpleNode, TransactionParser, TransactionParserFactory, TreeBuilder, VirtualInstances
- **core/api/** (3): ClassNameHasher, ILogger, IModule
- **core/data/** (1): IDataPointer
- **core/log/** (5): FatalException, IFullLogSerializer, LogEntry, StreamLogSerializer, WrappedException
- **core/reference/** (10): InvalidReferenceException, PrefixedRootFactory, Reference, ReferenceDataSource, ReferenceFactory, ReferenceHandler, ReferenceManager, ReleasedOnTimeSupportedReference, ResourceReference, ResourceReferenceFactory, RootTranslator
- **xml/** (3): ElementParser, TreeElementParser
- **xml/util/** (4): ActionableInvalidStructureException, InvalidCasePropertyLengthException, InvalidStructureException, UnfullfilledRequirementsException

## KMP Build Structure

```
commcare-core/
├── build.gradle.kts          # KMP multiplatform plugin
├── settings.gradle.kts       # Project name
├── gradle.properties          # iOS target skip on Linux
├── src/
│   ├── main/java/            # jvmMain (611 .kt + 32 .java)
│   ├── commonMain/kotlin/    # Shared code (placeholder)
│   ├── iosMain/kotlin/       # iOS actuals (placeholder)
│   ├── test/java/            # JVM tests (710 tests)
│   ├── cli/java/             # CLI source set (Java)
│   ├── ccapi/                # CCAPI source set (shared with cli)
│   └── translate/java/       # Translate source set (Java)
└── lib/                       # Bundled JARs (kxml2, json-simple)
```

## Key Learnings (21-item checklist)

The Kotlin Conversion Checklist in CLAUDE.md grew to 21 items through iterative discoveries. Key patterns:

1. **JVM signature clashes** (items 16-17): Constructor `val` vs interface `fun`, field vs getter
2. **`internal` visibility** (item 20): Hides from Java in other Gradle source sets
3. **Kotlin-to-Kotlin calls** (item 19): `fun getFoo()` must be `getFoo()`, not `foo`
4. **`@JvmField protected`**: Needed for Java subclasses in other source sets accessing fields
5. **`const val` auto-inlines** (item 15): No `@JvmField` needed for String/Int constants

## Phase 2 Readiness

**What's ready:**
- `commonMain` source set for shared code
- `iosMain` source set for iOS-specific implementations
- iOS framework target configured (`CommCareCore.framework`, static)
- All JVM backward compatibility verified

**What's needed for Phase 2:**
- Move pure-Kotlin code from jvmMain to commonMain (incremental)
- Create expect/actual for: HTTP (OkHttp → URLSession), crypto (javax.crypto → CryptoKit), file I/O (java.io → FileManager), XML (kxml2 → Foundation)
- Replace JVM-only dependencies: Guava → stdlib, joda-time → kotlinx.datetime, org.json → kotlinx.serialization
- iOS app shell (Compose Multiplatform or SwiftUI)
