# Phase 6 Deep Migration Learnings

## Kotlin Compiler ICE in jvmMain (IrFakeOverrideSymbol)

Moving Kotlin files from `main/java` to `jvmMain` (KMP source set) triggers an internal compiler error:
```
IrFakeOverrideSymbolBase.getOwner: should not be called
```
This happens in the IR backend lowering phase when jvmMain classes have fake overrides (inherited methods) that transitively reference expect/actual types from commonMain. The ICE is systemic — it moves between files as you remove one, another surfaces.

**Root cause**: The Kotlin compiler's IR fake override resolution doesn't handle the interaction between jvmMain classes and expect/actual declarations correctly.

**Workaround**: Files that can't go to commonMain must stay in `main/java`, NOT `jvmMain`. Reserve `jvmMain` exclusively for `actual` implementations of expect declarations.

## expect/actual Classes That Extend Platform Types Also Trigger ICE

Using `expect class` / `actual class` patterns where the actual extends a platform-specific type (e.g., `actual class OrderedHashtable : LinkedHashMap`) triggers the same ICE.

**Fix**: Use regular commonMain classes with composition instead of expect/actual inheritance.

## OrderedHashtable Composition Pattern

LinkedHashMap is `final` in Kotlin/Native, so OrderedHashtable can't extend it on iOS. The solution is composition:

```kotlin
class OrderedHashtable<K, V> : MutableMap<K, V> {
    private val backingMap: LinkedHashMap<K, V>
    private val orderedKeys: ArrayList<K>
    // Delegate MutableMap to backingMap, maintain orderedKeys for index-based access
}
```

This works in commonMain without expect/actual, avoiding both the LinkedHashMap `final` issue and the compiler ICE.

## HashMap→Map/MutableMap Widening for KMP

When a core type (like OrderedHashtable) no longer extends HashMap, all code passing it where HashMap is expected must be widened. The pattern:
- **Read-only usage**: `HashMap<K,V>` → `Map<K,V>`
- **Mutable usage**: `HashMap<K,V>` → `MutableMap<K,V>`
- **Java consumers**: Must also be updated (`HashMap` → `Map` in Java files)

This affected 25+ files across the serialization layer, locale system, model classes, and CLI code.

## Bulk Migration Cascade Problem

Even after fixing individual blocker files, attempting to move all 425 remaining `main/java` files to commonMain fails because of cascading dependencies:

1. 48 files have direct JVM dependencies (kxml2, gavagain geo, java.io/net, JVM reflection, Runtime/Thread)
2. These 48 files include core types that virtually everything depends on (TreeReference, EvaluationContext, FormDef, Detail, Text)
3. When you move the other 377 "clean" files, they fail compilation because they reference the 48 blockers

**Implication**: Progress requires resolving JVM deps in the core model classes themselves, not just in leaf files. The blocker categories:
- **kxml2/XML parsing** (~15 files)
- **gavagain geo library** (~5 files)
- **JVM reflection/Class<*>** (~5 files)
- **java.io/java.net** (~3 files)
- **JVM runtime** (~3 files: Runtime, Thread, ArrayList final)
- **Deep dependency chains** (~17 files depending on above)
