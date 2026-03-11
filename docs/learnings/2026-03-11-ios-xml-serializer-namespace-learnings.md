# iOS XML Serializer Namespace Learnings

## Platform abstractions must be tested for parameter handling, not just happy paths

**Problem:** `IosXmlSerializer` was created with the correct method signatures (`startTag(namespace, name)`, `attribute(namespace, name, value)`) but silently ignored the `namespace` parameter in all three methods. The serializer produced flat XML (`<html>`) instead of namespace-qualified XML (`<h:html xmlns:h="...">`).

**Root cause:** The iOS serializer was built to pass the existing cross-platform tests, which only tested null-namespace serialization. The `PlatformXmlSerializer` interface also lacked `setPrefix()`, so there was no way to register namespace-prefix mappings even if the serializer tried to use them.

**Discovery:** Found during a comprehensive code review comparing the iOS implementation against kxml2's JVM behavior. The bug would have manifested when namespace-aware code (e.g., `XFormSerializingVisitor`) moves to commonMain in Phase 3 Wave 2/7.

**Fix:** Added `setPrefix(prefix, namespace)` to the interface and implemented namespace-to-prefix tracking in `IosXmlSerializer` with three components:
1. A `namespaceToPrefix` map for URI-to-prefix lookup during tag/attribute output
2. A `pendingPrefixes` list for xmlns declarations to emit on the next `startTag()`
3. A `qualifiedName()` helper that resolves namespace URIs to `prefix:localName`

**Lesson:** When creating platform abstractions with parameters that one platform ignores, always write cross-platform tests that exercise those parameters. A method that compiles with the right signature but ignores its arguments is worse than a missing method — it fails silently at runtime instead of failing at compile time.

## Cross-platform tests should cover the contract, not just the common case

**Problem:** The existing `XmlParserTest` in commonTest had 9 tests covering XML *parsing* with namespaces, but no serializer tests existed in commonTest. The JVM-only `PlatformXmlRoundTripTest` tested serialization but only with `null` namespaces, never exercising the namespace parameters.

**Fix:** Added `XmlSerializerTest` to commonTest with 8 tests covering: prefixed namespaces, default namespaces, multiple namespaces, namespaced attributes, self-closing elements with namespaces, serialize-parse round-trips, and escaping in namespaced content. These tests run on both JVM and iOS, catching behavioral divergence.

**Lesson:** Every expect/actual pair should have commonTest coverage that exercises the full API surface, not just the subset used today. The cost of writing these tests is low; the cost of discovering divergent behavior after migrating hundreds of files is high.
