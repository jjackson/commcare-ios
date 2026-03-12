# Gavaghan Geodesy Replacement Learnings

**Date**: 2026-03-12
**PR**: #163
**Issue**: #152

## 1. Vincenty's Formulae as a Drop-in Replacement

The `org.gavaghan:geodesy` library was used for only 3 API surfaces:
- `GlobalCoordinates(lat, lon)` — simple data holder
- `GeodeticCalculator.calculateGeodeticCurve(ellipsoid, start, end)` — Vincenty inverse
- `GeodeticCalculator.calculateEndingGlobalCoordinates(ellipsoid, start, azimuth, dist)` — Vincenty direct

These are well-documented mathematical algorithms (Vincenty 1975) that can be implemented in ~180 lines of pure Kotlin. The WGS84 ellipsoid parameters are public constants (a=6378137.0, f=1/298.257223563).

**Lesson**: Before accepting an external dependency as a "must keep in jvmMain" blocker, check the actual API surface. A small, well-defined mathematical API is often trivially replaceable.

## 2. Floating Point Differences Between Implementations

The Gavaghan library normalizes coordinates using `Math.IEEEremainder(latitude, 360.0)` in its `GlobalCoordinates` constructor. This introduces tiny floating point artifacts even for values in the normal range. For example, storing `27.175569` becomes `27.175568999999996`.

Our pure Kotlin `GeoCoordinate` data class stores values without normalization, producing exact values. This meant test expected values needed updating.

**Lesson**: When replacing a geodesic library, expect minor floating point differences (< 1e-10 degrees, ~0.01mm). The differences are in the noise floor of GPS accuracy. Update test expected values rather than adding unnecessary normalization to match library quirks.

## 3. Cascading Moves Beyond the Original Scope

The issue described 4 files to modify, but the actual work also moved `XPathDistanceFunc` (which only depended on `GeoPointUtils`, now in commonMain) — a 5th file that became movable as a bonus. Always check for newly-unblocked files after moving dependencies.

## 4. Test Ordering Dependencies

Discovered that `XPathEvalTest.testEncryptString` was a flaky test — it depended on `JvmXPathFunctions.ensureRegistered()` being called by another test class first (via JVM static initialization). Running it in isolation always failed. Fixed by adding explicit init call.

**Lesson**: When moving function registrations between modules (JVM platform init → commonMain static init), check whether tests relied on implicit initialization order.

## 5. IEEErem Availability in Kotlin Common

`Double.IEEErem()` is declared as `expect` in Kotlin common stdlib but may not resolve in all Kotlin versions or build configurations. For coordinate normalization that's mathematically a no-op in the valid range, it's better to skip it entirely rather than fight availability issues.
