# Phase 3 Wave 2: XML Consumer Migration Learnings

## Property-based interfaces enable Kotlin property syntax

**Problem:** PlatformXmlParser originally defined `fun getName(): String?`, `fun getText(): String?`, etc. Kotlin callers could only use `parser.getName()` — not `parser.name` — because Kotlin only synthesizes property access for *Java* getters, not Kotlin `fun` declarations (checklist item 19 applies to interface design too).

**Fix:** Changed the interface to use `val name: String?`, `val text: String?`, etc. This enables `parser.name` syntax in Kotlin callers while still generating `getName()` for Java callers.

**Lesson:** When designing Kotlin interfaces that wrap Java APIs, use `val` properties for simple getters. This gives both Kotlin property syntax and Java getter compatibility.

## Platform type to nullable creates massive cascade

**Problem:** kxml2's `KXmlParser.getName()` returned platform type `String!` — Kotlin treated it as non-null at call sites. After switching to `PlatformXmlParser` with explicit `val name: String?`, ~93 call sites broke with type mismatches expecting `String` but receiving `String?`.

**Fix:** Added `!!` at call sites where the parser is known to be at START_TAG/END_TAG (name is always non-null in those states). This is safe because the XML parser contract guarantees non-null names at element events.

**Lesson:** Budget significant effort for nullable cascades when replacing platform types with explicit nullable types. Same pattern as Wave 1's Hashtable→HashMap migration (~30% of effort).

## Java cannot see Kotlin expect/actual typealiases

**Problem:** `PlatformXmlParserException` was defined as `expect class` with `actual typealias` to `XmlPullParserException` on JVM. Java files importing `PlatformXmlParserException` failed to compile — Java cannot see Kotlin typealiases at all.

**Fix:** Java files continue using `XmlPullParserException` directly. Only Kotlin files use `PlatformXmlParserException`.

**Lesson:** Any `expect`/`actual typealias` is invisible to Java. When migrating mixed Java/Kotlin codebases, Java files must use the concrete platform type, not the typealias.

## Class-extending interop requires decorator pattern

**Problem:** `InterningKXmlParser` extended `KXmlParser` (a concrete JVM class). The migration script changed `KXmlParser` to `PlatformXmlParser` in the extends clause, but `PlatformXmlParser` is an interface — the class lost all its concrete behavior.

**Fix:** Rewrote `InterningKXmlParser` as a decorator that implements `PlatformXmlParser` and delegates to a wrapped instance, adding string interning on top.

**Lesson:** When a class extends a platform-specific concrete class, it can't simply switch to implementing the interface. Use the decorator pattern: implement the interface, wrap the delegate, add behavior on top.

## JVM-only DOM types need isolation, not abstraction

**Problem:** 8 files used kxml2 DOM types (`Document`, `Element`, `Node`) for XForm parsing. These are deeply JVM-specific and too complex to abstract cross-platform in this wave.

**Fix:** Created JVM-only typealiases (`XmlDocument`, `XmlElement`, `XmlNode`) and helper classes (`XmlDocumentHelper`, `XmlDomExtensions`) in `jvmMain/xml/dom/`. This isolates the dependency without attempting full abstraction.

**Lesson:** Not everything needs cross-platform abstraction immediately. For complex JVM-specific APIs (like DOM parsing), isolating behind typealiases in `jvmMain` is a pragmatic intermediate step. Full abstraction can come in a later phase.

## Bulk migration scripts need exact matching

**Problem:** A Python migration script checked `'import org.javarosa.xml.PlatformXmlParser' not in content` to decide whether to add the import. This substring matched `PlatformXmlParserException`, causing 37 files to miss the `PlatformXmlParser` import entirely.

**Fix:** Used regex with negative lookahead: `PlatformXmlParser(?!Exception)`.

**Lesson:** When writing bulk migration scripts, always use regex with word boundaries or negative lookahead for import matching. Substring matching is too fragile for identifiers that are prefixes of other identifiers.

## Null namespace in attribute serialization

**Problem:** `DataModelSerializer.serializeAttributes()` called `instanceNode.getAttributeNamespace(i)!!` but `AbstractTreeElement.getAttributeNamespace()` can return null for attributes without a namespace prefix. The `!!` caused NPE in 5 tests.

**Fix:** Removed `!!` — `PlatformXmlSerializer.attribute()` already accepts `namespace: String?`.

**Lesson:** Don't blindly add `!!` during nullable cascades. Check whether the receiving method actually requires non-null. Serializer/writer APIs commonly accept null namespace to mean "no namespace."
