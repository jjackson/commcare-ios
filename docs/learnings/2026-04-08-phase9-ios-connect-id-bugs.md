---
title: Phase 9 iOS Connect ID registration bugs
date: 2026-04-08
tags: [phase9, e2e-testing, ios, kotlin-native, connect-id, keychain]
---

Phase 9's first real end-to-end registration run against connect-id
production caught three distinct product bugs in the iOS Connect ID
flow. This learning captures the root causes and the debugging process
so future iOS platform work doesn't repeat any of them.

## TL;DR

1. **Kotlin/Native `Map<Any?, Any?> as CFDictionaryRef` silently breaks
   in some runtime contexts.** The implicit interop bridging that
   normally converts `mapOf(...)` to an NSDictionary-backed CF type
   throws `ClassCastException: HashMap cannot be cast to CPointer` in
   Compose `onClick` handlers on Xcode 26.4 + iOS 26.3. The fix: catch
   the exception and fall back to an explicit `NSMutableDictionary +
   CFBridgingRetain + reinterpret` path.
2. **The iOS app's "new registration" code called the recovery-only
   `confirm_backup_code` endpoint.** Server logic does
   `ConnectUser.objects.get(...)` unconditionally, so calling it
   before the user exists raises `DoesNotExist` and returns 500 HTML.
   The fix: don't call the endpoint in new-registration mode; hold
   the backup code in view-model state and pass it to
   `complete_profile` on the next step.
3. **`complete_profile` rejects an empty photo field.** The iOS app's
   skip-photo button passes an empty string. Server validation
   requires a non-empty `photo` field, AND the server's
   `upload_photo_to_s3` expects the `data:image/<type>;base64,...`
   data-URI format (not raw base64). The fix: substitute a 1×1
   transparent PNG data URI placeholder when the user skips.

All three bugs were in code that had shipped to TestFlight. Nobody
had hit them because manual testing happened on physical devices
with photos, real user interaction, and (for bug 1) on older Xcode
versions. Phase 9's automated E2E on a fresh simulator was the first
caller that exercised the exact combination that triggers each bug.

## Bug 1 — Kotlin/Native CF dictionary bridging

### Symptom

Tapping `Set PIN` on `BiometricSetupStep` crashes the app with
`EXC_CRASH / SIGABRT` from `terminateWithUnhandledException`. The
Kotlin exception propagates out of a Compose `onClick` handler via
`BaseComposeScene.sendPointerEvent` and terminates the process.

No visible error to the user. The app just disappears.

### Root cause

`PlatformKeychainStore.store()` built its query as:

```kotlin
val query = mapOf<Any?, Any?>(
    kSecClass to kSecClassGenericPassword,
    kSecAttrService to SERVICE_NAME,
    kSecAttrAccount to key,
    kSecValueData to valueData,
    kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly
)

@Suppress("UNCHECKED_CAST")
SecItemAdd(query as CFDictionaryRef, null)
```

The `as CFDictionaryRef` cast relies on Kotlin/Native's implicit
interop bridging to convert the Kotlin `Map` into an `NSDictionary`
(backed by `kotlin.native.internal.NSDictionaryAsKMap`) that is
toll-free bridged to `CFDictionary`. This bridging usually works
transparently.

But in the specific context of a Compose `onClick` handler running
under Xcode 26.4's newer Kotlin/Native runtime, the cast instead
throws:

```
kotlin.ClassCastException: class kotlin.collections.HashMap cannot be
cast to class kotlinx.cinterop.CPointer
```

The Kotlin runtime treats `mapOf(...)` as a plain `HashMap` rather
than the bridged NSDictionary variant, and the unchecked cast to
`CPointer` fails hard. The exception escapes the `onClick` lambda,
propagates up through Compose's pointer event dispatch, and
terminates the app via the K/N unhandled-exception handler.

The same code works fine in earlier Kotlin/Native runtimes and in
non-Compose contexts (including Gradle's `iosSimulatorArm64Test`
standalone test binary).

### Debugging process

1. The first crash log (`~/Library/Logs/DiagnosticReports/CommCare-*.ips`)
   showed a Kotlin unhandled exception on the main thread but no
   human-readable message. Just frames like
   `terminateWithUnhandledException` and
   `BaseComposeScene#sendPointerEvent`.

2. `xcrun simctl spawn booted log stream` filtered to the app process
   didn't show the exception message either — K/N's terminate handler
   writes to stderr, not `os_log`.

3. `xcrun simctl launch --console booted <bundle>` routes the app's
   stderr to the caller's terminal. Running Maestro in the background
   while this captured stderr revealed one line:
   ```
   void * _Nullable NSMapGet(NSMapTable * _Nonnull, const void * _Nullable): map table argument is NULL
   ```
   (This was a misleading error from a different code path during an
   earlier debug iteration; the actual cast failure message is the
   `HashMap cannot be cast to CPointer` ClassCastException, which only
   became visible after adding a try/catch wrapper around the store
   call.)

4. Wrapping the store call in try/catch surfaced the actual exception
   type and its message, which pointed at the unchecked cast.

### Fix

Dual-path implementation that tries both bridging strategies and
selects whichever works in the current runtime context:

```kotlin
try {
    val query = mapOf<Any?, Any?>(...)
    @Suppress("UNCHECKED_CAST")
    SecItemAdd(query as CFDictionaryRef, null)
} catch (_: ClassCastException) {
    val dict = NSMutableDictionary().apply {
        setObject(kSecClassGenericPassword, forKey = kSecClass as NSCopyingProtocol)
        ...
    }
    withCFDictionary(dict) { cfDict -> SecItemAdd(cfDict, null) }
}
```

Where `withCFDictionary` uses `CFBridgingRetain + reinterpret` to
get a valid `CFDictionaryRef` pointer out of an `NSMutableDictionary`
without relying on an unchecked cast:

```kotlin
private inline fun <R> withCFDictionary(
    dict: NSMutableDictionary,
    block: (CFDictionaryRef) -> R,
): R {
    val retained = CFBridgingRetain(dict)
        ?: throw IllegalStateException("CFBridgingRetain returned null")
    try {
        val cfDict: CFDictionaryRef = retained.reinterpret<__CFDictionary>()
        return block(cfDict)
    } finally {
        CFRelease(retained)
    }
}
```

Also added a `try/catch` in `ConnectIdViewModel.completeBiometricSetup`
as a belt-and-suspenders guard. Any future keychain failure surfaces
as an error message to the user instead of terminating the app.

### Lessons

- **Unchecked `as CFDictionaryRef` casts are inherently fragile in
  Kotlin/Native.** The bridging behavior depends on the runtime
  version, the calling context (Compose vs coroutine vs standalone
  test), and the specific types inside the Map. Prefer explicit
  CFBridgingRetain + reinterpret for anything that must work across
  runtime contexts.

- **Compose `onClick` handlers must not throw.** K/N's
  terminate-with-unhandled-exception behavior converts any escaped
  exception into a process abort. Always wrap potentially-throwing
  synchronous code in try/catch inside event handlers. This is
  defense-in-depth: even if the underlying code is correct, future
  refactors or runtime upgrades can reintroduce failures.

- **For debugging unhandled K/N exceptions, use `--console` to
  capture stderr.** `xcrun simctl launch --console booted <bundle>`
  routes the app's stdout/stderr to the caller's terminal. Run
  Maestro (or any driver) in the foreground while launch --console
  runs in the background — stderr captures exception dumps that
  don't appear in `log stream` or the crash reporter's diagnostic
  report.

## Bug 2 — New registration called recovery-only endpoint

### Symptom

Submitting a backup code during new-user registration returned
`Server Error (500)` HTML:

```html
<!doctype html>
<html lang="en">
<head><title>Server Error (500)</title></head>
<body><h1>Server Error (500)</h1><p></p></body>
</html>
```

The iOS app displayed this raw HTML as the `errorMessage`. No
actionable information.

### Root cause

`ConnectIdViewModel.submitBackupCode()` in new-registration mode
(line ~175) called:

```kotlin
val result = api.confirmBackupCode(token, backupCode)
```

`confirmBackupCode` posts to `/users/recover/confirm_backup_code`.
That connect-id endpoint is **recovery only**:

```python
@api_view(["POST"])
@authentication_classes([SessionTokenAuthentication])
def confirm_backup_code(request):
    session = request.auth
    if not session.is_phone_validated:
        return JsonResponse({"error_code": ErrorCodes.PHONE_NOT_VALIDATED}, status=403)

    user = ConnectUser.objects.get(
        phone_number=session.phone_number, is_active=True
    )
    # ... uses user ...
```

During new registration, no `ConnectUser` exists yet. Django's
`objects.get()` raises `ConnectUser.DoesNotExist`, which propagates
out of the view because there's no `try/catch` around it. Django's
default 500 handler returns HTML.

The right flow: during new registration, the client should hold the
backup code in state and pass it to `complete_profile` on the next
step. That's where the server actually persists the backup code as
the new user's recovery pin.

### Fix

In `ConnectIdViewModel.submitBackupCode()`, remove the API call in
new-registration mode. Just advance to `PHOTO_CAPTURE`:

```kotlin
} else {
    // New registration: the backup code is stored in the `backupCode`
    // state here and passed to complete_profile (see createAccount
    // below) which is where it actually gets saved on the server.
    // We do NOT call confirm_backup_code here — that endpoint is
    // recovery-only and requires the user to already exist in the DB.
    isLoading = false
    currentStep = RegistrationStep.PHOTO_CAPTURE
}
```

`createAccount` was already passing `backupCode` to `complete_profile`
on line 203, so the server-side persistence already worked.

### Lessons

- **A single method name that means different things based on flow
  state is a bug magnet.** `confirmBackupCode` was being used as if
  it meant "set this backup code for the user I'm about to create,"
  when the server only supports "verify this backup code for an
  existing user on a new device." The client code needed to know the
  server endpoint was recovery-only but didn't.

- **When an iOS flow forks on server response** (e.g., `checkName`
  returns `accountExists`), each branch should have a dedicated
  code path with its own API calls. Don't share a single `submit*`
  method that switches on the flow-state flag — it hides the fact
  that some steps run in one branch and not the other.

- **HTML error responses from Django endpoints should be caught
  upstream.** Right now `ConnectIdApi.confirmBackupCode` just surfaces
  whatever the response body is as the error message. A 500 HTML
  blob is a terrible thing to show a user. An `HttpClient` layer
  that checks `Content-Type` and formats accordingly would help.

## Bug 3 — complete_profile rejects skipped photo

### Symptom

After skipping the photo step, account creation fails with:

```
Account creation failed: Profile completion failed (400)
```

And after a partial fix (substituting raw base64 for the empty
string):

```
Account creation failed: Profile completion failed (500)
```

### Root cause

Two layers.

**Layer 1 — empty photo:** `PhotoCaptureStep`'s skip button passes
an empty string to `onPhotoCaptured`:

```kotlin
onClick = { viewModel.onPhotoCaptured("") },
```

`ConnectIdViewModel.createAccount` then passes that empty string to
`api.completeProfile`. The server validates:

```python
if not (name and recovery_pin and photo):
    return JsonResponse({"error": ErrorCodes.MISSING_DATA}, status=400)
```

Empty string is falsy in Python, so the server returns 400
`MISSING_DATA`.

**Layer 2 — data-URI format:** Substituting a non-empty value (like
a raw base64 PNG) gets past the empty check, but the server's
`upload_photo_to_s3` calls `split_base64_string(image_base64)` which
expects the data-URI format:

```
data:image/png;base64,iVBORw0K...
```

Raw base64 (without the `data:image/png;base64,` prefix) makes
`split_base64_string` fail, and the server returns 500
`FAILED_TO_UPLOAD`.

### Fix

In `ConnectIdViewModel.createAccount`, substitute a 1×1 transparent
PNG as a data URI when the photo is blank:

```kotlin
private val PLACEHOLDER_PHOTO_BASE64 =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkAAIAAAoAAv/lxKUAAAAASUVORK5CYII="

private fun createAccount() {
    val token = session?.sessionToken ?: return
    val photo = photoBase64?.takeIf { it.isNotBlank() } ?: PLACEHOLDER_PHOTO_BASE64
    ...
}
```

The server accepts it, uploads the (blank) image to S3, and
completes registration.

### Lessons

- **"Skip" on a UI step does not mean "send nothing" to the server.**
  Skipping a photo means "use a default," not "omit the field
  entirely." The client needs to know what the server expects for
  the skipped state and substitute accordingly.

- **Server contracts with data-URI formats need to be documented
  client-side.** The iOS code had no comment saying "photo must be a
  data URI." That contract was hidden in
  `upload_photo_to_s3.split_base64_string` and discoverable only by
  reading the server source.

- **Client should either parse server errors into actionable
  messages OR display the raw response AND the HTTP status.** Right
  now the user sees `(400)` or `(500)` with no detail. A developer
  working on the bug has no clue what the actual problem is without
  server log access.

## Meta-observation: three bugs, one run

All three bugs surfaced on the very first fully-automated Phase 9
registration run. That's a strong validation of the premise: **UI
E2E tests catch bugs that unit and integration tests don't**,
because they exercise the full stack in the same way a real user
would. Each of these bugs had been shipped to TestFlight, so every
existing automated test suite had passed against the buggy code.

Specifically:

- Bug 1 (keychain bridging) required Xcode 26.4 + Compose onClick
  context to reproduce. The existing
  `PlatformKeychainStoreTest.testStoreAndRetrieve` runs in the
  standalone test binary context (different runtime) and passes.
- Bug 2 (recovery endpoint misuse) required actually submitting a
  backup code during new registration. The `ConnectIdApiJsonTest`
  mock tests only verify request formatting, not that the right
  endpoint is called in the right branch.
- Bug 3 (photo rejection) required an actual `complete_profile`
  round trip with an empty photo. Integration tests with real
  fixture data didn't exist because fixture users couldn't be
  created in prod until the `+7426` unblock.

The value of Phase 9 isn't the individual tests — it's the
expansion of the test surface to cover things that weren't
previously testable at all.

## Follow-ups

- **Report bug 1 upstream to the Kotlin/Native team** if not
  already known. The exact combination (Xcode 26.4 + Compose onClick
  + mapOf + `as CFDictionaryRef`) is reproducible and diagnostic.
- **Add an iOS platform test for `PlatformKeychainStore`** that
  runs under Compose context, not just standalone. Hard to do
  without a full simulator boot, so this may need to live as a
  Maestro flow rather than an iosTest.
- **Audit other `*as CF*` casts in iosMain/** for the same pattern.
  Grep for `as CFDictionaryRef`, `as CFArrayRef`, `as CFStringRef` —
  each is a potential crash site under the same conditions.
- **Audit other uses of `api.confirmBackupCode`** — it's the
  recovery-only endpoint. If anything else calls it in a
  new-registration context, same bug.
- **Audit the iOS app's server error handling** for other cases
  where 400/500 HTML gets shown to users as opaque numbers.
