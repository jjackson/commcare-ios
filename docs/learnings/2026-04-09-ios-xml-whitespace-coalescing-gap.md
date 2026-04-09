# iOS XML Parser Whitespace/Comment Coalescing — Another Test-Coverage Gap

**Date:** 2026-04-09
**Context:** Phase 9 Wave 5 scouting. Could not load the Visit form on iOS — `TreeElementParser` crashed with "Can't add children to node that has data value!" the moment the form tried to instantiate the `casedb` instance. This is the second distinct iOS XML parser bug in the same subsystem (see `2026-03-18-ios-platform-test-gap-learnings.md` for the first) and the fourth bug in the Phase 9 Wave 5 bug chain.

## The Bug Chain Around It

Wave 5 required form submit to work end-to-end on an existing case. Walking into the Visit form on iOS tripped four distinct bugs in sequence, each hiding the next:

1. **#391 — `LoginViewModel.resolveDomain()` fell back to hardcoded `"demo"`** for short-form usernames, so Basic-auth requests for `haltest` went to the wrong HQ path. Fixed in PR #398.
2. **#399 — `CaseListViewModel.loadCases()` passed `datum.getDataId()`** (the datum *variable name*) as the case-type filter, so every case list returned empty. Fixed in PR #400.
3. **#401 — `loadClasspathResource()` was a stub that threw** on iOS, so anything that needed `/casedb_instance_structure.xml` (every casedb-aware form) failed to load. Fixed in PR #402 by embedding the 26-line static schema as a Kotlin string constant.
4. **This one — `TreeElementParser` crashed on embedded casedb schema** because the iOS XML parser emits two whitespace TEXT events around comments at `depth > 0` and the `ElementParser.nextNonWhitespace()` helper only skipped *one* of them. Fixed here.

Every single one passed JVM tests. Every single one broke on iOS.

## Root Cause of This Bug

The `casedb_instance_structure.xml` schema has the shape:

```xml
<case ...>
    <!-- case_id: The unique GUID of this case -->
    <case_name/>
    <!-- The name of the case-->
    <date_opened/>
    ...
</case>
```

Two different XML parser backends handle the whitespace between `<case_name/>` and `<date_opened/>` differently:

### kxml2 (JVM backend)
kxml2 emits a single TEXT event spanning the whitespace → comment → whitespace run, or no TEXT event at all once the comment is skipped. A single call to `parser.next()` after a whitespace TEXT moves past all of it.

### `IosXmlParserIos.kt` (iOS pure-Kotlin backend)
`skipWhitespaceAndComments()` short-circuits at `depth > 0`:

```kotlin
private fun skipWhitespaceAndComments() {
    // Only skip at document level (depth 0), not inside elements where
    // whitespace text might be meaningful content.
    if (depth > 0) return
    ...
}
```

So inside an element, `next()` emits:
1. `TEXT("\n    ")` — whitespace before the `<!--`
2. The `<!--` token gets handled by `skipDeclaration()` + `return next()` recursion
3. `TEXT("\n    ")` — whitespace *after* the `-->`, as a second separate event
4. `START_TAG` for the next sibling

Two TEXT events instead of one. That's the divergence.

### Why the crash happened

`ElementParser.nextNonWhitespace()` was written assuming a single whitespace advance was enough — true for kxml2, false for the iOS parser:

```kotlin
// BEFORE
protected fun nextNonWhitespace(): Int {
    val ret = parser.next()
    if (ret == PlatformXmlParser.TEXT && parser.isWhitespace()) {
        return parser.next()
    }
    return ret
}
```

On iOS this left the *second* whitespace TEXT event live. `TreeElementParser.parse()`'s main loop then dispatched to the `TEXT` branch and called `element.setValue(UncastData(""))`. Setting a value on a `TreeElement` makes `isChildable()` return `false` — the next `addChild()` call then throws:

> "Can't add children to node that has data value!"

The exception surfaces on the second sibling, not the comment, so the failure looks like a completely different problem than what actually went wrong.

## The Fix

Two-layer, both in `commonMain` so both platforms benefit:

**Layer 1 — `ElementParser.nextNonWhitespace()`** now loops over *all* consecutive whitespace events, not just one. This is the correct primitive for "get the next non-whitespace event" regardless of how any particular parser backend coalesces.

**Layer 2 — `TreeElementParser.parse()`** defensively skips empty-after-trim text instead of calling `setValue("")`. A stray whitespace TEXT event should never be able to flip a parent into a non-childable leaf, on any parser.

Both layers are cheap and idiomatic. Either alone would have fixed this specific bug. Together they guard against the entire *class* of "parser emits an extra whitespace event we didn't expect."

## Why JVM Tests Did Not Catch This

This is the heart of the learning. There are **zero tests for `TreeElementParser`** in the entire repo — not in `jvmTest`, not in `commonTest`, not in `iosTest`. The class is used indirectly from `TreeUtilities.xmlToTreeElement()`, which is in turn used by production code paths (`CaseDataInstance` init, external-instance loading) — but none of those call sites have a test that parses a multi-child XML blob with comments between siblings.

The existing `XmlParserTest` in `commonTest` tests the *underlying parser* but not the *tree builder layered on top of it*. `XmlParserTest.testXFormStructure()` has comments... no wait, it doesn't. None of the `XmlParserTest` cases has a `<!-- comment -->` between sibling elements at `depth > 0`.

So the exact interaction — comment between siblings at depth > 0 → two whitespace events → `nextNonWhitespace` only skips one → `setValue("")` on parent → crash on next child — was never tested on either JVM or iOS, and was invisible until a real form loaded a real casedb schema at runtime.

## Why This Is Evidence of a Systematic AI-Port Gap

This bug is the **fourth** low-level iOS engine bug caught by Phase 9 E2E testing, after `#401` (classpath resource loader stub), the original `skipWhitespaceAndComments()` depth-0 bug from March, and the case-list filter regression. Each one:

- **Lived in code the AI port generated** — either an `actual fun` stub marked TODO, or a commonMain algorithm that happened to rely on a JVM-only parser quirk.
- **Passed the ported JVM test suite** — because the ported tests exercised the JVM path only.
- **Was invisible until a real E2E flow hit it** — because there were no iOS-side unit tests for the affected class, and no commonTest that loaded the real production fixture.

The original 2026-03-18 learning articulated the rule: *every new iOS platform implementation gets a unit test in `iosTest/` before integration*. Rediscovering the same class of bug a month later in the same subsystem shows that the rule wasn't retroactively applied to the rest of the AI-ported code. We have `iosTest/IosHttpRootTest.kt` (10 tests) because that's where the March bug hit. We have nothing covering `IosXmlParserIos`, `PlatformResourceLoader`, `TreeElementParser`-on-iOS, or the 45+ other `actual fun` bodies.

## Tests Added

| File | Tests | Runs On | Purpose |
|------|-------|---------|---------|
| `commcare-core/src/commonTest/.../TreeElementParserTest.kt` | 4 | JVM + iOS | Cover comment-between-siblings + whitespace-only separators + trimmed text preservation. Parses the exact production casedb schema as the most-direct regression guard. |

Running these on iOS (`./gradlew :commcare-core:iosSimulatorArm64Test --tests "org.javarosa.xml.TreeElementParserTest"`) was verified green with the fix applied, and the test *does* reproduce the crash without the fix — I confirmed the exception path manually during debugging.

## Recommended Policy Going Forward

This entire class of bug is one failure mode. The policy has to match:

1. **Every new or modified `actual fun` body in `iosMain/` needs at least one iOS-side test** — either in `iosTest/` (for genuinely platform-specific behavior) or in `commonTest/` (for behavior that should be identical across platforms, where `commonTest` runs on both and divergence is a test failure). This is the 2026-03-18 rule, repeated.

2. **Every commonMain algorithm that depends on XML-parser event sequencing needs a commonTest that runs on iOS.** XML pull parsers are notoriously inconsistent about exactly when they emit whitespace TEXT events, how they handle comments, and how they coalesce runs of text. kxml2 and `IosXmlParserIos` will keep diverging on edge cases forever unless commonTest pins the contract.

3. **Production fixtures belong in tests.** The casedb_instance_structure.xml is a static, version-locked schema bundled with the engine. It should be the fixture in the commonTest that guards against future regressions of "loads the engine's own core schemas." If it loads on JVM and breaks on iOS, the test should fail on iOS — that's what commonTest is *for*.

4. **Assume the AI port missed iOS-specific sequencing bugs wherever XML parsing, file I/O, or platform APIs are involved.** Phase 9's bug-finding rate is not slowing. Until it does, every iOS E2E wave should budget for 1-3 low-level bugs of this class per wave and treat them as expected work, not surprises.

5. **Backfill commonTest coverage for the rest of `commcare-core/src/commonMain/kotlin/org/javarosa/xml/*`.** Currently `TreeElementParser`, `ElementParser` helper methods, and the various concrete parsers (e.g. `TransactionParser`, `DataInstanceParser`) all sit on top of the pull-parser event stream and any of them could have an analogous bug. Sweeping commonTest coverage over these would front-load the bug discovery instead of paying for it one wave at a time in Maestro flows.

## Broader Pattern Reference

See `2026-03-18-ios-platform-test-gap-learnings.md` (the original articulation) and `2026-04-08-phase9-ios-connect-id-bugs.md` (three Connect ID bugs of the same class caught by Wave 0). The pattern is stable enough to have its own rule in the user-level feedback memory (`feedback_low_level_bug_pattern.md`): treat every low-level iOS engine bug found during E2E as evidence of a test-coverage gap, investigate sibling paths, file a dated learning, keep fixing without asking.

The raw count of unique bugs in the chain so far:
- 2026-03-18: iOS profile XML whitespace-at-doc-level (fixed + learning + iosTest added)
- 2026-04-08: K/N `mapOf as CFDictionaryRef` crashing in Compose onClick (Wave 0)
- 2026-04-08: recovery-endpoint misuse in new-registration flow (Wave 0)
- 2026-04-08: `complete_profile` rejecting empty photo (Wave 0)
- 2026-04-09: `LoginViewModel` domain fallback for short usernames (#391 / PR #398)
- 2026-04-09: `CaseListViewModel` filtering by datum-id instead of case-type (#399 / PR #400)
- 2026-04-09: iOS `loadClasspathResource` stub that never loaded anything (#401 / PR #402)
- 2026-04-09: **this bug** — iOS XML parser whitespace/comment coalescing gap

Eight bugs in four weeks of E2E expansion, all in code the AI port judged successful.
