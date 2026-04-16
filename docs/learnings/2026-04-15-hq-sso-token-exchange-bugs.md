# HQ SSO Token Exchange Bugs

**Date**: 2026-04-15
**Context**: Debugging ConnectID -> CommCare HQ SSO for Connect marketplace learn/deliver apps

## Three bugs prevented HQ SSO from working

### 1. Username missing `.commcarehq.org` suffix (root cause of `invalid_grant`)

HQ mobile worker usernames are stored as `username@domain.commcarehq.org`, not `username@domain`. Our code sent `28e5e7b6e3b28156f680@andreaconnect` but HQ's `CouchUser.get_by_username()` needs `28e5e7b6e3b28156f680@andreaconnect.commcarehq.org`.

Android's `HiddenPreferences.getUserDomain()` likely returns the full domain including `.commcarehq.org`, so this works transparently there.

**Diagnosis**: curl with the full suffix returned HTTP 200 immediately. All prior attempts returned `invalid_grant`.

### 2. `getStoredUsername()` bypassed SQLite DB fallback

`getStoredUsername()` read only from iOS keychain (`keychainStore.retrieve`), while `retrieveCredential()` (used by `getConnectIdToken()`) falls back to the SQLite DB. Due to the known iOS `SecItemAdd` silent failure in Compose context, keychain was empty. Result: username was empty string, OAuth sent `@andreaconnect.commcarehq.org` with no user part.

**Fix**: Changed `getStoredUsername()` to use `retrieveCredential()` like all other credential reads.

### 3. `link_connectid_user` sent JSON, server expects form-encoded

Django's `request.POST.get("token")` only reads form-encoded bodies. We sent `{"token":"..."}` with `Content-Type: application/json`. Android sends `token=<value>` with form encoding.

**Impact**: Link call always got 401 (auth failure silenced the body parsing error). This is non-fatal per Android's flow (link failure is tolerated, token exchange retried), but fixing it aligns with spec.

## How HQ's ConnectID SSO actually works

Traced by reading `dimagi/commcare-hq` source and `dimagi/commcare-android` source:

1. **ConnectIDAuthBackend** (in `corehq/apps/domain/auth.py`) is a Django auth backend that only activates for `request.path == '/oauth/token/'`
2. It treats the ROPC `password` field as a **ConnectID access token**, NOT an HQ password
3. It calls `connectid.dimagi.com/o/userinfo/` with Bearer token to get the `sub` (Connect username)
4. Looks up `ConnectIDUserLink(connectid_username=sub, domain=worker.domain, commcare_user=worker)` — this link is created server-side when Connect provisions the worker via `start_learn_app`
5. If link exists and `is_active=True`, returns the worker as the authenticated user
6. `link_connectid_user` endpoint is a separate best-effort call to create/verify the link from the client side

## Key reference: Android's exact OAuth call

From `dimagi/commcare-android` `ApiPersonalId.java`:
```java
params.put("client_id", "4eHlQad1oasGZF0lPiycZIjyL0SY1zx7ZblA6SCV");
params.put("scope", "mobile_access sync");
params.put("grant_type", "password");
params.put("username", hqUsername + "@" + HiddenPreferences.getUserDomain());
params.put("password", connectToken);  // Connect access token, NOT HQ password
```

## Lesson

When porting API calls from Android, verify the exact string format of every parameter — especially usernames. A missing `.commcarehq.org` suffix is invisible in code review but causes authentication to silently fail. The `invalid_grant` error gives no hint about which parameter is wrong.
