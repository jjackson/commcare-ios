# XFormParser Port and Cross-Platform Validation Learnings

**Date:** 2026-03-14

## XFormParser Port: kxml2 DOM → Cross-Platform XmlElement

### Key challenge: namespace handling
KXmlParser with `FEATURE_PROCESS_NAMESPACES` enabled does NOT expose `xmlns:*` attributes through `getAttributeCount()`. They're only available via `getNamespaceCount(depth)`, `getNamespacePrefix(i)`, `getNamespaceUri(i)`.

**Fix:** Added `getNamespaceCount()`, `getNamespacePrefix(index)`, `getNamespaceUri(index)` to the `PlatformXmlParser` interface. XmlDomBuilder uses these to capture namespace declarations on XmlElement.

### Reader → ByteArray API change
XFormParser constructors changed from `Reader` to `ByteArray`. All callers (XFormUtils, Harness.java, FormInstanceLoader.java, FormInstanceValidator.java) updated. JVM callers use `inputStream.readAllBytes()`.

### Nullability differences
kxml2 Element methods return non-null types, but XmlElement returns nullable:
- `getText(i)` → `String?` (was `String`)
- `getElement(i)` → `XmlElement?` (was `Element`)
- `namespace` → `String?` (was `String`)

Required ~15 nullability fixes across XFormParser.

## Golden File Testing Pattern

### Classpath collision trap
`test_constraints.xml` existed in both `src/test/resources/` (upstream) and `src/commonTest/resources/` (ours) with completely different content. JVM classloader found the upstream one first, causing golden files to be generated from the wrong form.

**Fix:** Rename to `test_field_list_constraints.xml` to avoid collision. When adding test resources to commonTest, always check `src/test/resources/` for name conflicts.

### Golden file access from app module
The app module's jvmTest classpath does NOT include commcare-core's commonTest resources. GoldenFileStalenessTest must read golden files from the filesystem (`File("../commcare-core/src/commonTest/resources/golden/")`) rather than via classloader.

### iOS test resource loading
Kotlin/Native tests can't use classloader resource loading. Use POSIX file I/O (`fopen/fread`) with multiple candidate paths since the working directory varies between local and CI runs.
