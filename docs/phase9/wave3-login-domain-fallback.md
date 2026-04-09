# Bug: iOS LoginViewModel.resolveDomain() falls back to hardcoded "demo"

**Tracking issue:** #391
**Found by:** Phase 9 Wave 3 E2E scouting, 2026-04-08
**Severity:** Medium — silently routes login requests to the wrong domain
**Affects:** iOS LoginScreen, any user who types a short-form username

## Reproduce

1. Install any CommCare app whose domain is NOT `demo` (e.g. Bonsaaso in `jonstest`).
2. On the login screen, type the short-form username (e.g. `haltest`) and the password.
3. Tap Log In.
4. Observe: 401 Invalid username or password.
5. Try again with the long-form username (`haltest@jonstest.commcarehq.org`) — login succeeds.

## Root cause

`app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt:327-333`:

```kotlin
private fun resolveDomain(): String {
    return if (username.contains("@")) {
        username.substringAfter("@").removeSuffix(".commcarehq.org")
    } else {
        "demo"
    }
}
```

When the username has no `@`, the function falls back to the hardcoded string `"demo"`. That was probably a development placeholder from the earliest login scaffolding and never got wired up to the real installed-app domain.

The login URL is constructed as `${serverUrl}/a/$resolvedDomain/phone/restore/`, so short usernames always hit `/a/demo/phone/restore/` regardless of what app the user installed.

## Why `configureApp` doesn't help

`LoginViewModel.configureApp(serverUrl, appId, app: ApplicationRecord?)` is called after install to set `currentApp`, and `ApplicationRecord` has a `domain` field. `resolveDomain` could read `currentApp?.domain` as the primary source and only fall back to the `@`-split when that is null. It currently ignores `currentApp` entirely.

## Suggested fix

```kotlin
private fun resolveDomain(): String {
    // Prefer the installed app's domain — that's the source of truth post-install.
    currentApp?.domain?.let { if (it.isNotBlank()) return it }

    // Fall back to parsing an @-suffixed username (for users who type the long form).
    if (username.contains("@")) {
        return username.substringAfter("@").removeSuffix(".commcarehq.org")
    }

    // Last resort: the "demo" default — only useful for the demo-mode bootstrap path.
    return "demo"
}
```

This makes the short-form username work for any installed app, and keeps the two existing behaviors as fallbacks.

## Test coverage

`.env.e2e.local` uses the long-form username (`haltest@jonstest.commcarehq.org`) as a workaround. When this bug is fixed, the short form should also work and the env var can be simplified. A Wave 3b or Wave 9 (settings / login variants) flow can explicitly exercise both forms.

## Links

- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt:82-95` — login request construction
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt:327-333` — the bug
- `app/src/commonMain/kotlin/org/commcare/app/model/ApplicationRecord.kt:3-9` — `domain` field exists
- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LoginViewModel.kt:70-76` — `configureApp` stores `currentApp`
