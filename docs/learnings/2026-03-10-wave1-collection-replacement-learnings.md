# Phase 3 Wave 1: Collection Replacement Learnings

## Hashtable → HashMap introduces nullable get()

**Problem:** `Hashtable.get(key)` in Java throws on null key/value and never returns null for missing keys in practice. `HashMap.get(key)` returns `T?` (nullable). After bulk replacement, dozens of call sites got type mismatches — methods expecting `String` received `String?`.

**Fix:** Add `!!` where the value is known to be present, or `?: ""` / `?: default` where a fallback makes sense. Each case needs context-specific judgment.

**Lesson:** This is the highest-effort part of the Hashtable→HashMap migration. Budget ~30% of the effort for nullable cascades.

## OrderedHashtable must use LinkedHashMap, not HashMap

**Problem:** `OrderedHashtable` extended `Hashtable` but maintained insertion order via an internal `ArrayList<K>`. After replacing `Hashtable` with `HashMap`, iteration order became non-deterministic, breaking serialization tests where XML output order matters.

**Fix:** Use `LinkedHashMap` as the base class, which preserves insertion order natively. Also changed `Case.data` to `LinkedHashMap` for serialization determinism.

**Lesson:** Any `Hashtable` subclass that maintains custom ordering needs `LinkedHashMap`, not `HashMap`. Check for order-dependent serialization tests.

## setElementAt and insertElementAt have reversed argument order

**Problem:** `Vector.setElementAt(value, index)` and `Vector.insertElementAt(value, index)` put the value first. `ArrayList.set(index, value)` and `ArrayList.add(index, value)` put the index first. A naive find-and-replace produces silently wrong code if types happen to match.

**Fix:** Custom Python script with argument extraction and swap. Always verify with compilation — type mismatches catch most cases.

## .keys() on Hashtable vs .keys on HashMap

**Problem:** `Hashtable.keys()` is a method returning `Enumeration<K>`. After replacement, `HashMap.keys()` doesn't exist — `.keys` is a property returning `Set<K>`. Code doing `val e = map.keys()` followed by `e.hasNext()`/`e.next()` breaks doubly.

**Fix:** Replace `.keys()` with `.keys.iterator()`. In Java files, use `.keySet().iterator()`.

**Lesson:** The `Enumeration`→`Iterator` migration and `Hashtable`→`HashMap` migration interact. Both `.elements()` and `.keys()` on Hashtable returned `Enumeration`, but their HashMap equivalents are structurally different.

## ArrayList throws different exception subclass

**Problem:** `Vector.get(index)` throws `ArrayIndexOutOfBoundsException`. `ArrayList.get(index)` throws `IndexOutOfBoundsException`. Code catching the specific subclass misses the new exception.

**Fix:** Broaden catch to `IndexOutOfBoundsException` (parent class).

## Test resource XMLs may need order updates

**Problem:** Switching from `Hashtable`/`HashMap` to `LinkedHashMap` changes property serialization order. Test XML fixtures that assert specific element ordering fail.

**Fix:** Update expected XML to match `LinkedHashMap` insertion order. Only 2 test resource files needed changes in this wave.

## Bulk scripting approach works but needs multiple passes

**Approach:** Python script for mechanical replacements (imports, types, simple method renames), followed by compilation-driven manual fixes. Three passes were needed:
1. Types + simple methods (addElement, removeElementAt, etc.)
2. Missed patterns (removeElement, keys(), firstElement, lastElement, push, pop)
3. Complex cases (nullable cascades, OrderedHashtable rework, exception types)

**Lesson:** Don't try to handle every case in one script. Do the 80% mechanically, compile, fix the remaining 20% by hand.
