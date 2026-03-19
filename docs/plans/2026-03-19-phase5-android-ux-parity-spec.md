# Phase 5: Android UX Parity — Design Spec

**Date:** 2026-03-19
**Status:** Draft
**Scope:** App setup flow, Connect ID, Connect marketplace, multi-app management, navigation drawer, login enhancements

## 1. Overview

Phase 5 brings the iOS app's user experience to full parity with the Android CommCare app. The current iOS app jumps straight to a developer-facing login screen with manual Domain/App ID fields. Android users experience a polished setup → login → home flow with QR code app installation, Connect ID integration, and a navigation drawer for app switching and messaging.

### Goals

- Match Android's first-time and returning-user flows screen-for-screen
- Implement Connect ID (Personal ID) registration, biometric unlock, and SSO
- Implement the Connect marketplace (Opportunities, job claiming, messaging)
- Support multiple installed apps with switching
- Add navigation drawer with profile, app list, and messaging

### Non-Goals

- Apple App Attest for open registration (deferred — use invite bypass for now)
- Push notifications via APNs (can use polling; APNs integration is separate work)
- Simprints biometric integration
- CommCare Sense Mode (low-literacy)
- SMS-based app install links (legacy Android feature, currently disabled on Android too)
- Enterprise MDM/managed configuration provisioning
- Offline .ccz install (menu placeholder only — full implementation deferred)
- Secondary phone number recovery for Connect ID
- Work History screens (feature-flagged on Android, defer)
- Channel-level encryption for Connect messaging (implement in later polish phase)

## 2. User Journeys

### 2.1 First-Time User (Standard)

1. Launch app → **Dispatch** routes to **Setup Screen** (no app installed)
2. Setup Screen: scan QR code, enter code/URL, or install from list
3. **Install Screen** shows step-by-step progress (profile → suite → forms → locale → init)
4. **Login Screen** with username + password (domain extracted from username)
5. OTA restore → **Home Screen**

### 2.2 Returning User

1. Launch app → Dispatch routes to **Login Screen** (app installed, no active session)
2. Login with password, PIN, or biometric (Face ID / Touch ID)
3. OTA restore → **Home Screen**

### 2.3 Connect ID Gig Worker

1. Launch app → Dispatch detects Connect ID registration
2. **Opportunities Screen** — browse available jobs
3. Claim job → app auto-installs → auto-login with generated credentials
4. Fill forms → submit → track deliveries → get paid

### 2.4 Multi-App User

1. Login screen shows app switcher dropdown (if multiple apps installed)
2. Navigation drawer provides app switching from any screen
3. App Manager screen for install/archive/uninstall

## 3. Architecture Changes

### 3.1 State Model

Replace the current linear `AppState` with a hierarchical model:

```
sealed class AppState {
    // No apps installed — show setup
    object NoAppsInstalled : AppState()

    // App(s) installed but no active session — show login
    data class NeedsLogin(
        val seatedApp: ApplicationRecord,
        val allApps: List<ApplicationRecord>
    ) : AppState()

    // Active session
    data class Ready(
        val platform: CommCarePlatform,
        val sandbox: SqlDelightUserSandbox,
        val app: ApplicationRecord,
        val serverUrl: String,
        val domain: String,
        val authHeader: String
    ) : AppState()

    // Error/recovery states
    data class AppCorrupted(val app: ApplicationRecord, val message: String) : AppState()
    data class AppUnvalidated(val app: ApplicationRecord) : AppState()

    // Transient states
    data class Installing(val progress: Float, val message: String, val appName: String) : AppState()
    data class LoggingIn(val serverUrl: String, val username: String) : AppState()
    data class LoginError(val message: String) : AppState()
    data class InstallError(val message: String) : AppState()
}
```

Note: The existing `AppState.Ready` consumers (HomeScreen, SyncViewModel, FormQueueViewModel, etc.) will need updates to access the new `app: ApplicationRecord` field. This migration happens in Wave 1.

### 3.2 ApplicationRecord

New data model for installed apps, stored in SQLDelight:

```
data class ApplicationRecord(
    val id: String,              // unique app ID (from profile uniqueid)
    val profileUrl: String,      // URL to profile.ccpr
    val displayName: String,     // app display name from profile
    val domain: String,          // HQ domain
    val majorVersion: Int,       // installed major version
    val minorVersion: Int,       // installed minor version
    val status: AppStatus,       // INSTALLED, ARCHIVED
    val resourcesValidated: Boolean, // whether resources have been verified
    val installDate: Long,       // timestamp
    val bannerUrl: String?,      // custom login banner image
    val iconUrl: String?         // custom app icon
)

enum class AppStatus { INSTALLED, ARCHIVED }
```

The "seated" app is tracked via a separate preference (`SeatedAppPreference`), not a column on `ApplicationRecord`. This avoids multi-row update transactions on every app switch.

### 3.3 Connect ID Module

Separate module with its own storage and state. Uses two distinct servers:
- **ConnectID server** (`connectid.dimagi.com`) — identity, registration, OAuth2 tokens
- **CommCare-Connect server** (separate deployment) — opportunities, payments, messaging

```
// Connect ID state (independent of app state)
sealed class ConnectIdState {
    object NotRegistered : ConnectIdState()
    object Registering : ConnectIdState()
    data class Registered(
        val userId: String,
        val name: String,
        val phone: String,
        val photoPath: String?,
        val hasConnectAccess: Boolean
    ) : ConnectIdState()
}

// Token management — handles two token types
class ConnectIdTokenManager(
    private val repository: ConnectIdRepository,
    private val keychainStore: PlatformKeychainStore  // iOS Keychain for secure storage
) {
    // ConnectID identity token (single, for connectid.dimagi.com)
    fun getConnectIdToken(): String?
    fun refreshConnectIdToken(): Boolean

    // HQ SSO tokens (per-domain, for commcarehq.org)
    fun getHqSsoToken(domain: String): String?
    fun refreshHqSsoToken(domain: String): Boolean

    // Account linking
    fun linkHqAccount(username: String, password: String, domain: String): Boolean
}
```

**Secure storage:** OAuth tokens and encrypted passwords are stored in iOS Keychain via `PlatformKeychainStore` (expect/actual), NOT in plaintext SQLite columns. The `connect_id_user` table stores only non-sensitive metadata. The ConnectID server returns a `db_key` from `complete_profile` and `fetch_db_key` endpoints, used for local database encryption.

### 3.4 Navigation Structure

```
DispatchScreen (entry point — routes based on state)
  ├── SetupScreen (no apps installed)
  │     ├── QR Scanner (uses existing PlatformBarcodeScanner)
  │     ├── EnterCodeScreen
  │     └── InstallFromListScreen (authenticate → browse → select)
  │
  ├── InstallProgressScreen (app downloading/installing)
  │
  ├── LoginScreen (app installed, no session)
  │     ├── Password mode
  │     ├── PIN mode
  │     └── Biometric mode
  │
  ├── HomeScreen (active session) — with Navigation Drawer
  │     ├── Menu/Forms/Cases/Sync/Settings (existing)
  │     └── Drawer:
  │           ├── Profile card (Connect ID info)
  │           ├── App list (switch apps)
  │           ├── Opportunities (Connect marketplace)
  │           ├── Messaging
  │           └── About
  │
  ├── AppManagerScreen (install/archive/uninstall apps)
  │
  ├── PersonalIdScreen (Connect ID registration wizard)
  │     ├── PhoneEntryStep
  │     ├── OtpVerificationStep
  │     ├── NameEntryStep
  │     ├── BackupCodeStep
  │     ├── PhotoCaptureStep
  │     ├── BiometricSetupStep
  │     └── SuccessStep
  │
  └── ConnectScreen (marketplace)
        ├── OpportunitiesListScreen
        ├── OpportunityDetailScreen
        ├── LearnProgressScreen
        ├── DeliveryProgressScreen
        ├── PaymentScreen
        └── MessagingScreen
```

### 3.5 Database Schema Changes

Add new SQLDelight tables:

```sql
-- Installed apps registry
CREATE TABLE application_record (
    id TEXT PRIMARY KEY,
    profile_url TEXT NOT NULL,
    display_name TEXT NOT NULL,
    domain TEXT NOT NULL,
    major_version INTEGER NOT NULL,
    minor_version INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'INSTALLED',
    resources_validated INTEGER NOT NULL DEFAULT 0,
    install_date INTEGER NOT NULL,
    banner_url TEXT,
    icon_url TEXT
);

-- Seated app preference (singleton row)
CREATE TABLE seated_app_preference (
    key TEXT PRIMARY KEY DEFAULT 'seated_app',
    app_id TEXT NOT NULL
);

-- Connect ID user metadata (singleton — one per device)
-- Sensitive data (tokens, passwords) stored in iOS Keychain, not here
CREATE TABLE connect_id_user (
    user_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT NOT NULL,
    photo_path TEXT,
    has_connect_access INTEGER NOT NULL DEFAULT 0,
    security_method TEXT NOT NULL DEFAULT 'pin'
);

-- Connect ID linked HQ accounts
CREATE TABLE connect_hq_link (
    hq_username TEXT NOT NULL,
    domain TEXT NOT NULL,
    connect_user_id TEXT NOT NULL,
    PRIMARY KEY (hq_username, domain)
);

-- User key records (for PIN/biometric login)
-- encrypted_password is the user's actual password, encrypted with a
-- device-derived key. PIN/biometric unlock decrypts it for server auth.
CREATE TABLE user_key_record (
    username TEXT NOT NULL,
    domain TEXT NOT NULL,
    encrypted_password TEXT,
    pin_hash TEXT,
    is_primed INTEGER NOT NULL DEFAULT 0,
    record_type TEXT NOT NULL DEFAULT 'normal',
    valid_from INTEGER,
    valid_to INTEGER,
    last_login INTEGER,
    PRIMARY KEY (username, domain)
);
```

### 3.6 Code Patterns

New ViewModels follow the existing codebase pattern: Compose `mutableStateOf` for reactive state (not `StateFlow`). All existing platform abstractions are reused:
- `PlatformBarcodeScanner` (existing) — for QR code scanning in Setup
- `PlatformBiometricAuth` (existing) — for Face ID / Touch ID in Connect ID and login
- `PlatformImageCapture` (existing) — for selfie in Connect ID registration
- New: `PlatformKeychainStore` — expect/actual for iOS Keychain / Android Keystore secure storage

## 4. Screen Designs

### 4.1 Dispatch Screen

No visible UI — pure routing logic. On launch:

1. Check if any apps are installed → if not, go to Setup
2. Check if seated app is usable → if corrupted, try seating another; if none usable, show recovery
3. Check if seated app resources are validated → if not, show verification
4. Check for active session → if yes, go to Home
5. Check for Connect-managed redirect → if yes, route to Opportunities or auto-login
6. Otherwise → go to Login

### 4.2 Setup Screen

**Primary actions (large buttons):**
- **Scan Application Barcode** — opens camera via existing `PlatformBarcodeScanner`. QR contains profile URL.
- **Enter Code or URL** — navigates to text field for manual profile URL or app code entry.

**Secondary actions:**
- **Install from App List** — authenticate with HQ credentials, fetch list from `GET /phone/list_apps` (hits both `commcarehq.org` and `india.commcarehq.org`), show selectable list of `AppAvailableToInstall` objects
- **Sign up for Personal ID** — launches Connect ID registration wizard

**Menu items:**
- Offline Install (placeholder — browse for .ccz file, full implementation deferred)
- App Manager (if apps already installed but user navigated here)

### 4.3 Install Progress Screen

Shows step-by-step progress:
1. ✓ Profile downloaded
2. ✓ Suite installed
3. ◐ Downloading forms... (with percentage)
4. ○ Locale files
5. ○ Initializing

Cancel button available. On completion, auto-navigates to Login.

### 4.4 Login Screen

**Layout (top to bottom):**
- App banner (custom branding from profile, or CommCare default)
- Welcome message ("Welcome" or "Welcome, {PersonalID name}")
- App switcher dropdown (only if multiple apps installed)
- Connect ID button (only if Personal ID registered + has Connect access)
- "or" divider (only if Connect button shown)
- Username field (with user icon, `AutoCompleteTextView` behavior)
- Password/PIN field (with lock icon, show/hide toggle)
- Login button
- Bottom links: Practice Mode, App Manager, version info

**Login modes:**
- **Password** — default, full password entry
- **PIN** — if UserKeyRecord has PIN set for this username, show numeric keypad
- **Biometric** — if device supports Face ID/Touch ID and user is primed, prompt automatically

**Domain resolution:** Username can be `user@domain.commcarehq.org` or just `user` (uses seated app's domain). No separate domain field.

**How PIN/biometric login works:** On first successful password login, the password is encrypted with a device-derived key and stored in `user_key_record.encrypted_password`. When the user sets a PIN, the PIN is used to derive the decryption key. On subsequent logins, entering the correct PIN (or biometric) decrypts the stored password, which is then used for server authentication. This matches Android's `ManageKeyRecordTask` pattern.

### 4.5 Navigation Drawer

Accessible via hamburger icon on home screen toolbar. Slides in from left.

**Sections:**
- **Profile card** — photo, name, phone (from Connect ID; or username if no Connect ID)
- **Opportunities** — link to Connect marketplace (only if Connect access enabled)
- **Unread badge** on messaging icon
- **App list** — all installed apps, current app highlighted, tap to switch
- **Messaging** — link to Connect messaging (only if Connect access enabled)
- **About** — app version, build info
- **Sign in / Sign out Personal ID** — at bottom

### 4.6 App Manager Screen

List of installed apps, each showing:
- App name, domain, version, install date
- Status badge (Active / Archived)
- Actions: Archive, Uninstall, Update

"Install New App" button at bottom → navigates to Setup Screen.

Maximum 4 installed apps (matches Android limit).

### 4.7 Connect ID Registration Wizard

Multi-step flow using a shared `ConnectIdViewModel`. Steps match the actual Android `PersonalIdActivity` navigation graph order:

1. **Phone Entry** — country code picker + phone number. Consent checkbox required. Calls `POST /users/start_configuration` (invited users bypass integrity). Server returns `session_token`, `sms_method`, and `required_lock`.
2. **OTP Verification** — first calls `POST /users/send_session_otp` to trigger Twilio SMS. User enters OTP code (iOS `textContentType = .oneTimeCode` for auto-fill). Calls `POST /users/confirm_session_otp` to validate.
3. **Name Entry** — verify or enter full name. Calls `POST /users/check_name`. Data collected for the `complete_profile` call.
4. **Backup Code** — set a 6-digit recovery PIN. Calls `POST /users/recover/confirm_backup_code`. Data collected for the `complete_profile` call.
5. **Photo Capture** — selfie for profile using existing `PlatformImageCapture`.
6. **Account Creation** — calls `POST /users/complete_profile` with `name`, `recovery_pin`, and `photo` (base64). This is the account-creation call that returns `username`, `password`, and `db_key`. All three fields from steps 3-5 are submitted together in this single call.
7. **Biometric Setup** — prompt to enable Face ID / Touch ID via existing `PlatformBiometricAuth`. Fallback to PIN setup (6-digit). Uses `required_lock` from step 1 to determine what's required.
8. **Success** — confirmation message. Calls `PersonalIdManager.completeSignin()` to persist credentials.

**Recovery flow** (existing user, new device): Phone → OTP → Backup Code verification → Photo → Biometric → Success.

### 4.8 Connect Marketplace Screens

Note: Opportunity/marketplace endpoints live on the **CommCare-Connect server** (separate from the ConnectID identity server). The API client must use a different base URL.

**Opportunities List** — fetched from `GET /api/opportunity/`. Shows:
- Job name, organization, description
- Status (available, claimed, completed)
- Pay amount if visible

**Opportunity Detail** — full description, requirements, pay structure. "Claim" button calls `POST /api/opportunity/{id}/claim`. "Start Learning" calls `POST /users/start_learn_app`.

**Learn Progress** — training module progress for claimed opportunity. Fetched from `GET /api/opportunity/{id}/learn_progress`.

**Delivery Progress** — form submission progress. Fetched from `GET /api/opportunity/{id}/delivery_progress`.

**Payment Screen** — payment confirmation. Calls `POST /api/payment/confirm`.

**Messaging Screen** — conversation threads with coordinators. Requires consent flow first (`POST /messaging/update_consent/`). Messages fetched via `GET /messaging/retrieve_messages/`. Send via `POST /messaging/send_message/`. Delivery receipts via `POST /messaging/update_received/`.

## 5. API Integration

### 5.1 Endpoints Used

**CommCare HQ (`commcarehq.org`):**
| Endpoint | Purpose |
|----------|---------|
| `GET /phone/list_apps` | List available apps for authenticated user |
| `GET /a/{domain}/phone/restore/` | OTA data restore |
| `GET /a/{domain}/apps/download/{appId}/profile.ccpr` | Download app profile |
| `POST /a/{domain}/receiver/{appId}/` | Form submission |
| `POST /settings/users/commcare/link_connectid_user/` | Link Connect ID to HQ account |
| `POST /oauth/token/` | HQ SSO token exchange (ConnectID token as password) |

**ConnectID Server (`connectid.dimagi.com`):**
| Endpoint | Purpose |
|----------|---------|
| `POST /users/start_configuration` | Begin registration (returns session_token, sms_method, required_lock) |
| `POST /users/send_session_otp` | Trigger OTP SMS via Twilio |
| `POST /users/confirm_session_otp` | Verify OTP |
| `POST /users/check_name` | Verify name |
| `POST /users/recover/confirm_backup_code` | Set/verify backup code |
| `POST /users/complete_profile` | Create account (name + recovery_pin + photo → username + password + db_key) |
| `POST /users/update_profile` | Update profile info after registration |
| `GET /users/fetch_db_key` | Get encryption key for local database |
| `POST /o/token/` | OAuth2 token (ROPC grant) |
| `POST /users/heartbeat` | Periodic heartbeat (accepts optional fcm_token + device type) |
| `GET /toggles` | Feature flags |

**CommCare-Connect Server (separate from ConnectID):**
| Endpoint | Purpose |
|----------|---------|
| `GET /api/opportunity/` | List available opportunities |
| `POST /api/opportunity/{id}/claim` | Claim opportunity |
| `GET /api/opportunity/{id}/learn_progress` | Learning module progress |
| `GET /api/opportunity/{id}/delivery_progress` | Delivery/form progress |
| `POST /users/start_learn_app` | Signal start of learning module |
| `POST /api/payment/confirm` | Payment confirmation |
| `POST /messaging/update_consent/` | Consent to messaging (required before messaging works) |
| `GET /messaging/retrieve_messages/` | Get message threads |
| `POST /messaging/send_message/` | Send message |
| `POST /messaging/update_received/` | Delivery receipts |
| `GET /messaging/retrieve_notifications/` | Get notifications |

### 5.2 Authentication Patterns

- **Standard login**: HTTP Basic Auth (username:password) to HQ restore endpoint
- **Connect ID OAuth2**: ROPC grant to `connectid.dimagi.com/o/token/` with client ID `zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa`, scope `openid`
- **HQ SSO via Connect ID**: ROPC grant to `{hq}/oauth/token/` with ConnectID access token as password, client ID `4eHlQad1oasGZF0lPiycZIjyL0SY1zx7ZblA6SCV`, scope `mobile_access sync`
- **Connect API calls**: Bearer token from ConnectID OAuth2
- **Token refresh**: Tokens are re-requested using stored credentials (encrypted password from Keychain). No OAuth refresh token — the ROPC grant is repeated with the stored password.

### 5.3 Server Changes Needed

**General improvements (not iOS-specific):**
1. `users/fcm_utils.py`: Accept `device_type` parameter instead of hardcoding `DeviceType.ANDROID` (~3 lines)
2. `users/views.py`: Pass device type in `heartbeat()` endpoint (~3 lines)
3. Add `/.well-known/apple-app-site-association` for deep links (~10 lines)

**Deferred (only needed for open registration):**
- Apple App Attest validator in `utils/app_integrity/` (~100+ lines)
- For development, invited users bypass integrity checks entirely

## 6. Waves

### Wave 1: App Icon + Login Cleanup (~8 files)

**What:** Add CommCare app icon. Simplify login screen — remove Domain, App ID, Server URL fields. Extract domain from username automatically. Begin `AppState` migration.

**New/Modified files:**
- `app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` — icon assets (1024px master + Contents.json)
- `app/src/commonMain/.../ui/LoginScreen.kt` — remove fields, add app banner placeholder
- `app/src/commonMain/.../viewmodel/LoginViewModel.kt` — remove domain/appId state, use app's domain
- `app/src/commonMain/.../state/AppState.kt` — add `NoAppsInstalled`, `NeedsLogin`, `AppCorrupted` states; update `Ready` to include `ApplicationRecord`
- `app/src/commonMain/.../ui/HomeScreen.kt` — update `AppState.Ready` usage
- `app/iosApp/project.yml` — reference Assets.xcassets
- Update all `AppState.Ready` consumers (SyncViewModel, FormQueueViewModel, etc.)

**Tests:** Maestro login flow works with just username + password.

**Acceptance criteria:**
- [ ] App icon visible on home screen and in simulator
- [ ] Login screen shows only Username + Password
- [ ] Domain extracted from username@domain format
- [ ] All existing tests pass with updated AppState

### Wave 2: App Setup Flow (~12 files)

**What:** First-launch setup screen with QR scan (via existing `PlatformBarcodeScanner`), code entry, and install-from-list. Dispatch routing. Install progress screen.

**New files:**
- `app/src/commonMain/.../ui/DispatchScreen.kt` — routing logic
- `app/src/commonMain/.../ui/SetupScreen.kt` — QR/code/list setup
- `app/src/commonMain/.../ui/EnterCodeScreen.kt` — manual URL/code entry
- `app/src/commonMain/.../ui/InstallFromListScreen.kt` — authenticate + browse apps
- `app/src/commonMain/.../ui/InstallProgressScreen.kt` — step-by-step install progress
- `app/src/commonMain/.../viewmodel/SetupViewModel.kt` — setup state management
- `app/src/commonMain/.../viewmodel/AppInstallViewModel.kt` — install orchestration

**Modified files:**
- `app/src/commonMain/.../ui/HomeScreen.kt` — use DispatchScreen as entry point
- `app/src/commonMain/.../engine/AppInstaller.kt` — report granular progress steps
- SQLDelight schema — add `application_record`, `seated_app_preference` tables

**Tests:**
- Unit test: SetupViewModel routes correctly based on app state
- Unit test: AppInstaller reports step-by-step progress
- Maestro: fresh install → setup → enter code → install → login → home

**Acceptance criteria:**
- [ ] Fresh install shows Setup screen (not login)
- [ ] Can enter profile URL and install app
- [ ] Install progress shows step-by-step completion
- [ ] After install, routes to Login screen
- [ ] App record persisted in SQLDelight

### Wave 3: Multi-App Management (~9 files)

**What:** Support up to 4 installed apps. App switcher on login screen. App Manager for install/archive/uninstall. Seated app tracked via preference (not column).

**New files:**
- `app/src/commonMain/.../ui/AppManagerScreen.kt` — list/manage installed apps
- `app/src/commonMain/.../viewmodel/AppManagerViewModel.kt` — app CRUD operations
- `app/src/commonMain/.../storage/AppRecordRepository.kt` — SQLDelight queries for app records

**Modified files:**
- `app/src/commonMain/.../ui/LoginScreen.kt` — add app switcher dropdown
- `app/src/commonMain/.../viewmodel/LoginViewModel.kt` — seat app on selection
- `app/src/commonMain/.../ui/DispatchScreen.kt` — handle multiple apps, corrupted apps
- `app/src/commonMain/.../state/AppState.kt` — NeedsLogin carries app list
- `app/src/commonMain/.../engine/AppInstaller.kt` — write ApplicationRecord on install
- SQLDelight schema — queries for app records

**Tests:**
- Unit test: install 2 apps, switch between them, verify seated app changes
- Unit test: archive app, verify it's not selectable
- Unit test: max 4 apps enforced

**Acceptance criteria:**
- [ ] Can install a second app from App Manager
- [ ] Login screen shows app switcher when multiple apps exist
- [ ] Switching apps changes the seated app and reinitializes platform
- [ ] Can archive and uninstall apps
- [ ] Maximum 4 apps enforced

### Wave 4: Navigation Drawer + Branding (~7 files)

**What:** Hamburger menu on home screen with profile card, app list, messaging placeholder. Custom app banner on login screen.

**New files:**
- `app/src/commonMain/.../ui/NavigationDrawer.kt` — drawer content
- `app/src/commonMain/.../ui/ProfileCard.kt` — user/Connect ID profile display
- `app/src/commonMain/.../viewmodel/DrawerViewModel.kt` — drawer state, app switching from drawer

**Modified files:**
- `app/src/commonMain/.../ui/HomeScreen.kt` — add drawer, hamburger icon in toolbar
- `app/src/commonMain/.../ui/LoginScreen.kt` — app banner from ApplicationRecord
- `app/src/commonMain/.../storage/AppRecordRepository.kt` — fetch banner/icon URLs

**Tests:**
- Maestro: open drawer, verify profile and app list visible
- Maestro: switch apps from drawer

**Acceptance criteria:**
- [ ] Hamburger icon on home screen opens drawer
- [ ] Drawer shows profile card (username or Connect ID info)
- [ ] Drawer lists installed apps, current highlighted
- [ ] Tapping different app switches to it
- [ ] Login screen shows app-specific banner

### Wave 5: Connect ID / Personal ID (~18 files)

**What:** Full Connect ID registration wizard (matching Android's `PersonalIdActivity` step order), biometric unlock via existing `PlatformBiometricAuth`, OAuth2 SSO to HQ, profile management. Secure credential storage via iOS Keychain.

**Depends on:** Wave 4 (navigation drawer for Connect ID profile display)

**New files:**
- `app/src/commonMain/.../ui/connect/PersonalIdScreen.kt` — wizard container
- `app/src/commonMain/.../ui/connect/PhoneEntryStep.kt`
- `app/src/commonMain/.../ui/connect/OtpVerificationStep.kt`
- `app/src/commonMain/.../ui/connect/NameEntryStep.kt`
- `app/src/commonMain/.../ui/connect/BackupCodeStep.kt`
- `app/src/commonMain/.../ui/connect/PhotoCaptureStep.kt`
- `app/src/commonMain/.../ui/connect/BiometricSetupStep.kt`
- `app/src/commonMain/.../ui/connect/SuccessStep.kt`
- `app/src/commonMain/.../viewmodel/ConnectIdViewModel.kt` — registration wizard state
- `app/src/commonMain/.../viewmodel/ConnectIdTokenManager.kt` — OAuth2 token lifecycle
- `app/src/commonMain/.../network/ConnectIdApi.kt` — API client for connectid.dimagi.com
- `app/src/commonMain/.../storage/ConnectIdRepository.kt` — SQLDelight persistence
- `app/src/commonMain/.../platform/PlatformKeychainStore.kt` — expect/actual for secure credential storage
- SQLDelight schema — add `connect_id_user`, `connect_hq_link` tables

**Modified files:**
- `app/src/commonMain/.../ui/LoginScreen.kt` — Connect ID login button, biometric prompt
- `app/src/commonMain/.../viewmodel/LoginViewModel.kt` — SSO login path
- `app/src/commonMain/.../ui/NavigationDrawer.kt` — show Connect ID profile, sign in/out
- `app/src/commonMain/.../ui/SetupScreen.kt` — "Sign up for Personal ID" link

**Tests:**
- Unit test: ConnectIdTokenManager OAuth2 flow with mock server
- Unit test: registration wizard state machine (step order, data accumulation)
- Unit test: SSO token exchange
- Integration test: register (invited user) → send OTP → verify → complete profile

**Acceptance criteria:**
- [ ] Can register a Connect ID with phone number + OTP (invited user path)
- [ ] Registration follows correct step order: phone → OTP → name → backup → photo → create account → biometric → success
- [ ] `complete_profile` call sends name + recovery_pin + photo together, receives username + password + db_key
- [ ] Biometric setup (Face ID / Touch ID or PIN) using existing PlatformBiometricAuth
- [ ] Can link Connect ID to HQ account
- [ ] Can login to HQ via Connect ID SSO
- [ ] Profile shown in navigation drawer
- [ ] Tokens stored securely in iOS Keychain (not plaintext SQLite)

### Wave 6: Connect Marketplace (~17 files)

**What:** Opportunities listing, job claiming, progress tracking, payment, messaging. Uses CommCare-Connect server (separate base URL from ConnectID).

**Depends on:** Wave 5 (Connect ID tokens required for API authentication)

**New files:**
- `app/src/commonMain/.../ui/connect/OpportunitiesListScreen.kt`
- `app/src/commonMain/.../ui/connect/OpportunityDetailScreen.kt`
- `app/src/commonMain/.../ui/connect/LearnProgressScreen.kt`
- `app/src/commonMain/.../ui/connect/DeliveryProgressScreen.kt`
- `app/src/commonMain/.../ui/connect/PaymentScreen.kt`
- `app/src/commonMain/.../ui/connect/MessagingScreen.kt`
- `app/src/commonMain/.../ui/connect/MessageThreadScreen.kt`
- `app/src/commonMain/.../viewmodel/OpportunitiesViewModel.kt`
- `app/src/commonMain/.../viewmodel/OpportunityDetailViewModel.kt`
- `app/src/commonMain/.../viewmodel/MessagingViewModel.kt`
- `app/src/commonMain/.../network/ConnectMarketplaceApi.kt` — separate API client for Connect server
- `app/src/commonMain/.../model/Opportunity.kt` — data models
- `app/src/commonMain/.../model/Message.kt`
- `app/src/commonMain/.../storage/OpportunityRepository.kt`

**Modified files:**
- `app/src/commonMain/.../ui/NavigationDrawer.kt` — Opportunities + Messaging links with badges
- `app/src/commonMain/.../ui/DispatchScreen.kt` — Connect-managed app launch routing
- `app/src/commonMain/.../viewmodel/LoginViewModel.kt` — auto-login for Connect-managed apps

**Tests:**
- Unit test: fetch and display opportunities
- Unit test: claim opportunity flow
- Unit test: messaging consent + send/receive
- Integration test: browse opportunities with real ConnectID token

**Acceptance criteria:**
- [ ] Can browse available opportunities
- [ ] Can claim an opportunity
- [ ] Can start learning module (`start_learn_app` called)
- [ ] Can view learn and delivery progress
- [ ] Can confirm payments
- [ ] Messaging requires consent flow before first use
- [ ] Can send and receive messages with delivery receipts
- [ ] Unread message badge in navigation drawer
- [ ] Connect-managed apps auto-install and auto-login

### Wave 7: Login Enhancements (~8 files)

**What:** PIN login mode, saved session restore, Connect-managed auto-login, practice mode improvements. Builds on Connect ID infrastructure from Wave 5.

**Depends on:** Wave 5 (Connect ID for auto-login, biometric infrastructure)

**New files:**
- `app/src/commonMain/.../ui/PinEntryScreen.kt` — numeric PIN pad
- `app/src/commonMain/.../viewmodel/UserKeyRecordManager.kt` — encrypted password storage, PIN/biometric credential management
- `app/src/commonMain/.../storage/UserKeyRecordRepository.kt` — SQLDelight queries
- SQLDelight schema — add `user_key_record` table

**Modified files:**
- `app/src/commonMain/.../ui/LoginScreen.kt` — PIN mode, biometric prompt, restore session checkbox
- `app/src/commonMain/.../viewmodel/LoginViewModel.kt` — detect login mode from UserKeyRecord, auto-login for Connect-managed
- `app/src/commonMain/.../ui/DispatchScreen.kt` — handle saved session restore
- `app/src/commonMain/.../ui/SettingsScreen.kt` — PIN setup option

**Tests:**
- Unit test: set PIN after password login, re-login with PIN
- Unit test: PIN decrypts stored password for server auth
- Unit test: biometric unlock triggers auto-login
- Unit test: session restore skips OTA if data is fresh
- Maestro: login with PIN

**Acceptance criteria:**
- [ ] After first password login, user can set a PIN
- [ ] Subsequent logins show PIN pad (not password field)
- [ ] PIN decrypts encrypted_password from UserKeyRecord for server auth
- [ ] Face ID / Touch ID prompt on login (if enabled) via existing PlatformBiometricAuth
- [ ] "Forgot PIN" falls back to password mode
- [ ] Saved session restore checkbox works
- [ ] Connect-managed apps auto-login without user interaction
- [ ] Practice mode accessible from login screen

## 7. Risk Mitigations

| Risk | Mitigation |
|------|------------|
| Apple App Attest not implemented | Use invite-bypass path for all Connect ID registrations during development |
| QR scanner on iOS simulator | Provide manual URL entry as fallback; QR only works on real device |
| ConnectID server changes needed | Only 3 small general-improvement changes; all can be deferred |
| Large scope (7 waves, ~80 files) | Each wave is independently testable; can ship incrementally |
| Firebase Phone Auth on iOS | Use Twilio SMS path exclusively; no Firebase iOS SDK dependency |
| Multi-app data isolation | Each app gets its own sandbox database; shared registry for app records only |
| Secure credential storage | Use iOS Keychain (PlatformKeychainStore) for tokens and encrypted passwords |
| Two separate Connect servers | Separate API client classes with different base URLs |
| Token expiry | Re-authenticate using stored encrypted password; no refresh token flow |
| Wave 7 depends on Wave 5 | Explicitly documented; cannot parallelize these two |

## 8. File Count Summary

| Wave | New Files | Modified Files | Total |
|------|-----------|---------------|-------|
| 1: Icon + Login | 2 | 6 | ~8 |
| 2: Setup Flow | 7 | 3 | ~10 |
| 3: Multi-App | 3 | 6 | ~9 |
| 4: Nav Drawer | 3 | 3 | ~6 |
| 5: Connect ID | 14 | 4 | ~18 |
| 6: Marketplace | 14 | 3 | ~17 |
| 7: Login Enhance | 4 | 4 | ~8 |
| **Total** | **47** | **29** | **~76** |
