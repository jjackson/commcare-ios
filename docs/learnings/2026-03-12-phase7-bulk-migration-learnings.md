# Phase 7: Bulk Migration Learnings

## The Connected Component Problem

All 392 files in `main/java` formed a single connected component — no subset could move to `commonMain` independently. This was because core types (EvaluationContext, TreeReference, FormDef, etc.) were referenced by everything and had transitive JVM dependencies.

## Only 6 Files Had Direct JVM Imports

Despite 392 files being stuck, only 6 had actual JVM library imports:
- 5 files with `java.io.*` (Reader, InputStreamReader, OutputStreamWriter, DataOutputStream)
- 1 file with `com.carrotsearch.hppc.IntCollection`

Plus 8 files imported `org.kxml2.*` (XML parsing library). The rest were blocked purely by transitive references.

## Strategy: Extract JVM-Only Files to jvmMain, Then Bulk Migrate

The winning approach:
1. **Identify root blockers** — files with direct JVM library dependencies
2. **Move them to jvmMain** — these files are inherently JVM-only
3. **Create abstractions** at the boundary where commonMain references jvmMain types
4. **Bulk migrate** everything else to commonMain

## Key Abstractions That Unblocked Migration

### 1. InstallerFactory → JvmInstallerFactory
**Problem**: `InstallerFactory` referenced `XFormInstaller` (depends on kxml2).
**Fix**: Made base `InstallerFactory.getXFormInstaller()` throw `UnsupportedOperationException`. Created `JvmInstallerFactory` in jvmMain that overrides it.

### 2. DetailParser → GraphParser Factory
**Problem**: `DetailParser` directly constructed `GraphParser` (depends on graph rendering libraries in jvmMain).
**Fix**: Added `DetailParser.graphParserFactory` companion property. JVM init registers `{ parser -> GraphParser(parser) }`.

### 3. ItemSetParsingUtils → getAbsRef()
**Problem**: `ItemSetParsingUtils` called `XFormParser.getAbsRef()` (XFormParser is in jvmMain).
**Fix**: Extracted `getAbsRef()` as a top-level function in commonMain `XFormParserUtils.kt`. All its dependencies (TreeReference, XPathReference, XFormParseException) were already in commonMain.

### 4. StorageManager.registerStorage(Class<*>)
**Problem**: `Class<*>` doesn't exist in commonMain. Java callers need `Class<*>` overload.
**Fix**: Removed `Class<*>` overload from StorageManager. Created `StorageManagerCompat.java` in main/java with static method `registerStorage(manager, key, class)` that calls `.kotlin` conversion.

### 5. createXmlSerializer(output, encoding)
**Problem**: `DataModelSerializer` called `createXmlSerializer(stream, "UTF-8")` but the expect fun only had a no-arg version.
**Fix**: Added `expect fun createXmlSerializer(output: PlatformOutputStream, encoding: String)` to commonMain.

### 6. createAccessOrderedLinkedHashMap()
**Problem**: `LruCache` used `LinkedHashMap(0, 0.75f, true)` — JVM-specific constructor with `accessOrder` parameter.
**Fix**: Created `expect fun createAccessOrderedLinkedHashMap<K,V>()` with JVM actual using the 3-arg constructor.

### 7. PlatformFuncExprRegistry
**Problem**: `ASTNodeFunctionCall` directly referenced JVM-only XPath functions (distance, encrypt, decrypt, etc.).
**Fix**: Created `PlatformFuncExprRegistry` in commonMain using `Any` types. JVM registers builders at startup via `JvmXPathFunctions`.

## Iterative Compiler-Validated Migration is Essential

The bulk migration process:
1. Move ALL files to commonMain
2. Compile with `compileCommonMainKotlinMetadata`
3. Roll back error files to main/java
4. Repeat until clean

This took 7-8 iterations per attempt. Static import analysis misses same-package references (no import needed), so the compiler is the only reliable validator.

## Extension Functions Don't Work for Java Callers

Kotlin extension functions compile to static methods in a `*Kt` class (e.g., `StorageManagerJvmKt.registerStorage(manager, key, type)`). Java callers can't call them with instance syntax (`manager.registerStorage(key, type.class)`). For Java backward compatibility, use either:
- A Java static helper class (what we did with `StorageManagerCompat.java`)
- Keep the method as a member of the class

## The ccapi/cli Source Sets Can't See jvmMain

The `ccapi` and `cli` source sets only see `main` output (configured in `build.gradle.kts`). Extension functions or classes in `jvmMain` are invisible to them. Keep Java compat code in `main/java`.

## Registration Pattern for Platform-Specific Factories

When commonMain code needs to use platform-specific implementations:
1. Define a nullable factory/callback in a companion object
2. JVM initialization registers the concrete implementation
3. commonMain code calls through the registered factory

This is cleaner than expect/actual when the types involved aren't in commonMain.
