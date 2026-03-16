# Kotlin Conversion Checklist

When converting Java files to Kotlin in commcare-core, check for these **before pushing**:

1. **Nullable parameters**: Scan Java call sites. If any passes `null`, the Kotlin parameter must be `?` type.
2. **Generic raw types**: Java raw types don't exist in Kotlin. If a generic type parameter is consistently bypassed, consider removing it.
3. **Return type invariance**: `Vector<SubType>` is NOT `Vector<SuperType>` in Kotlin. Use `out` variance on interface declarations.
4. **`open` keyword**: Kotlin classes are `final` by default. Check if the class is subclassed anywhere (including commcare-android, FormPlayer) and mark `open`.
5. **`@JvmField` / `@JvmStatic`**: Java subclasses accessing `super.field` need `@JvmField`. Java callers of companion methods need `@JvmStatic`.
6. **Local build first**: Run `./gradlew compileKotlin compileJava` locally before pushing. Run `./gradlew test` for final verification.
7. **KDoc `*/` hazard**: Grep for `*/` inside `/** ... */` block comments — XPath wildcards like `/data/*/to` prematurely close the comment. Escape as `` `*` ``.
8. **Preserve `abstract`**: If the Java class is `abstract`, the Kotlin class must be `abstract` too (not `open`). Reflection-based tests depend on this.
9. **Nullable parameter threading**: Don't add `!!` on nullable params just to call a child method — make the child accept nullable too. Java silently passes null through call chains.
10. **`protected` → `internal`**: Java `protected` = package + subclass access. Kotlin `protected` = subclass only. Use `internal` for same-package non-subclass callers.
11. **Companion method inheritance**: Kotlin companion methods are NOT inherited by subclasses. Call them on the defining class (`DataInstance.unpackReference`), not a subclass (`FormInstance.unpackReference`).
12. **`@JvmField` vs `open`**: `@JvmField` cannot be used on `open` properties. Drop `open` — subclasses access the inherited field directly.
13. **Companion `protected`**: Companion object members cannot be `protected`. Use `internal const val` for constants that subclasses need within the same module.
14. **Smart cast on `var`**: Kotlin won't smart-cast mutable properties after null checks. Capture to a local `val` first: `val el = element; if (el != null) ...`
15. **`const val` auto-inlines**: `const val` in companion objects compiles to `public static final` in Java bytecode automatically. No `@JvmField` needed for String/Int/Long/Boolean constants.
16. **JVM signature clash: `val` vs `fun`**: A constructor `val foo` generates `getFoo()`, which clashes with `override fun getFoo()` from an interface. Fix: rename the constructor param (e.g., `_foo`) and delegate from the override.
17. **JVM signature clash: field vs getter**: A `var foo` field generates `getFoo()`, which clashes with an explicit `fun getFoo()`. Fix: rename the backing field to `_foo`.
18. **Java boxed types in generics**: `Pair<Integer, Integer>` must be `Pair<Int, Int>` in Kotlin. Never use Java boxed types in Kotlin generic type arguments.
19. **Kotlin-to-Kotlin `fun` calls**: When calling Kotlin code that defines `fun getFoo()`, use `getFoo()` not `foo`. Kotlin only synthesizes property access for *Java* getters, not Kotlin `fun` declarations.
20. **`internal` hides from other source sets**: Kotlin `internal` mangles names in bytecode. Java code in separate Gradle source sets (ccapi, cli, test) can't access `internal` properties. Add explicit public getter methods.
21. **Property getter/setter clash**: A `var foo` auto-generates `getFoo()`/`setFoo()`. Don't also define explicit `fun setFoo()` — remove it and let callers use property syntax.
22. **`@Throws` must match exactly in commonMain**: Kotlin/Native (iOS) requires override methods to have **exactly** matching `@Throws` annotations as their parent declarations. On JVM, subsets are allowed, but in commonMain (compiled for both targets), every override must list the same exceptions. Check all levels of the hierarchy (interface → abstract class → concrete class).
