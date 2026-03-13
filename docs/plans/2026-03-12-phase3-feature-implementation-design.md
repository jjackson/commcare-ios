# Phase 3: Feature Implementation Design

**Date**: 2026-03-12
**Status**: Approved
**Approach**: Vertical slice with progressive deepening, both platforms from day one

## Context

Phases 1-8 completed the engine port (Java → Kotlin → KMP) and an iOS-only scaffold app. Phase 3 builds the real cross-platform app with full feature parity against commcare-android.

The feature inventory was derived from two sources:
1. **Codebase audit** of `dimagi/commcare-android` — all Activities, Fragments, widgets, services, receivers, manifest
2. **Documentation audit** of Dimagi's public docs — all user-facing features, question types, integrations

## Architecture

```
app/
├── src/commonMain/          # Shared UI + ViewModels (95% of code)
│   ├── ui/                  # Compose Multiplatform screens
│   ├── viewmodel/           # ViewModels calling commcare-core engine APIs
│   └── storage/             # SQLDelight schemas + DAOs
├── src/androidMain/         # Android entry point + platform impls
├── src/iosMain/             # iOS entry point + platform impls
├── src/commonTest/          # Cross-platform tests + oracle tests
└── oracle/                  # Oracle test harness (FormPlayer comparison)
```

**Key decisions**:
- **Both platforms from day one**: `androidTarget()` + iOS targets in `app/build.gradle.kts`
- **SQLDelight** for cross-platform storage (replaces IosInMemoryStorage)
- **Oracle test harness** built in Tier 1, validates every subsequent tier
- **ViewModels in commonMain** call engine APIs directly (FormEntryController, CommCareSession, ResourceManager, etc.)

## Approach: Vertical Slice with Progressive Deepening

- **Tier 1**: One complete user journey end-to-end (login → install → menu → case → form → submit → sync)
- **Tier 2**: Deepen each feature to cover daily field worker needs
- **Tier 3**: Advanced configuration options and power features
- **Tier 4**: Full APK parity including newer product features (Connect, PersonalID)

Each tier is a milestone with its own exit criteria via the oracle test harness.

## Feature Parity Spec

### Tier 1 — Minimum Viable App (~15-20 tasks)

*Goal: One complete user journey works end-to-end on both Android and iOS.*

**Infrastructure:**
- Add `androidTarget()` to `app/build.gradle.kts`, Android manifest, Activity shell
- SQLDelight storage: schemas for cases, users, fixtures, forms, resources
- Implement `SqlDelightUserSandbox` in commonMain (replaces IosInMemoryStorage)
- Oracle test harness: FormPlayer comparison framework

**Features:**

| Feature | Engine APIs | Description |
|---------|------------|-------------|
| Auth (username/password) | HTTP restore endpoint | Real HQ auth with session/token management |
| App install (online) | ResourceManager, ResourceTable, CommCarePlatform | Download profile, install suites/forms/fixtures, real progress |
| Menu navigation (list) | SessionWrapper, getNeededData(), CommCareSession | Full session state machine: STATE_COMMAND_ID → menu display |
| Case list (basic) | IStorageUtilityIndexed\<Case\>, UserSandbox | Load from SQLDelight, display, select |
| Case detail | Case properties, SessionNavigator | Property display, datum selection |
| Form entry (basic) | FormEntryController, FormEntryModel, FormDef | Question types: text, integer, decimal, select-one, select-multi, date, time |
| Form submission | HTTP POST to receiver | Submit XML to HQ receiver endpoint |
| Basic sync | TransactionParserFactory, CommCareTransactionParserFactory | Parse restore response: cases, users, fixtures |
| Encrypted storage | AES-256, password-derived keys | PlatformCrypto implementation for both platforms |

**Exit criteria**: Basic form submission matches FormPlayer output for 5 test apps. App launches and completes full journey on both Android emulator and iOS simulator.

### Tier 2 — Daily Field Worker Features (~30-40 tasks)

*Goal: A field worker can do their actual job with this app.*

**Form Entry — All Question Types:**

| Widget | Type | Appearances |
|--------|------|-------------|
| StringWidget | text | default, multiline, numeric, address |
| StringNumberWidget | text (numeric storage) | phone numbers, IDs |
| IntegerWidget | integer | — |
| DecimalWidget | decimal | — |
| DateWidget | date | default (Gregorian) |
| DateTimeWidget | dateTime | — |
| TimeWidget | time | — |
| SelectOneWidget | select1 | default (radio) |
| SelectOneAutoAdvanceWidget | select1 | quick (auto-advance) |
| SpinnerWidget | select1 | minimal (dropdown) |
| ComboboxWidget | select1 | combobox, combobox multiword, combobox fuzzy |
| ListWidget | select1 | label, list-nolabel |
| GridWidget | select1 | compact, compact-2, compact-3 |
| SelectMultiWidget | select | default (checkbox) |
| SpinnerMultiWidget | select | minimal |
| ListMultiWidget | select | label, list-nolabel |
| GridMultiWidget | select | compact variants |
| LabelWidget | display | — |
| TriggerWidget | trigger | default, minimal |
| GeoPointWidget | geopoint | default, maps (Mapbox) |
| BarcodeWidget | barcode | — |
| ImageWidget | image | camera, gallery |
| AudioWidget | audio | — |
| CommCareAudioWidget | audio | extended (background/doze) |
| VideoWidget | video | — |
| SignatureWidget | signature | — |
| IntentWidget | intent callout | intent:\[action\] |

**Form Entry — Structure & Logic:**

| Feature | Description |
|---------|-------------|
| Repeat groups | User-controlled, fixed-count, model iteration add/delete |
| Field-list groups | Multiple questions on one screen (appearance: field-list) |
| Collapsible groups | group-border, collapse-open, collapse-closed appearances |
| Skip logic / relevancy | XPath display conditions on questions |
| Constraints + validation | XPath validation with custom error messages |
| Calculations + defaults | Computed values via XPath |
| Inline multimedia | Images, audio playback, video playback in questions/labels |
| Swipe navigation | Swipe between questions/pages |
| Form end navigation | Configurable destination after completion |

**Form Management:**

| Feature | Description |
|---------|-------------|
| Incomplete/saved forms | Save draft, resume later |
| Completed form review | Read-only view, configurable retention (Days for Review) |
| Unsent form queue | Queue management with count on home screen |
| Auto-send | Automatic submission when connection available |
| Form quarantine | Isolate corrupted forms from send queue |
| Form purge | Automatic cleanup after configurable period |

**Case Management:**

| Feature | Description |
|---------|-------------|
| Case list: search | Text search across displayed properties |
| Case list: sort | Single and multi-property, ascending/descending |
| Case list: filter | XPath-based nodeset filters |
| Case list: icons/images | Configurable columns with images |
| Case detail tabs | Tabbed property display with conditional visibility |
| Case create/update/close | Via form submission case blocks |
| Parent/child cases | Hierarchical relationships via indices |
| Extension cases | Linked cases via extensions |

**Sync & Data:**

| Feature | Description |
|---------|-------------|
| Incremental sync | Hash-based diff, sync tokens |
| Auto-sync | Background sync when connection available |
| Sync on login | Automatic after authentication |

**Auth & Navigation:**

| Feature | Description |
|---------|-------------|
| PIN login | Create/use numeric PIN after initial password |
| Multi-language | Runtime switching, RTL support, per-language multimedia |
| Breadcrumb navigation | Trail showing position in app hierarchy |
| Lookup tables / fixtures | Server-defined tables, cascading selects, user-specific filtering |

**Exit criteria**: All question types match FormPlayer output for 20+ test apps. ccqa.ccz comprehensive test app passes. Android parity tests pass for core workflows.

### Tier 3 — Advanced / Power Features (~25-35 tasks)

*Goal: All CommCare configuration options and power features work.*

| Feature | Description |
|---------|-------------|
| Case search and claim | Remote server search (QueryScreen, ElasticSearch), extension case creation |
| Case tiles | Card-style display with images |
| Persistent case tile | Info bar persists during form entry |
| Case sharing | Groups, owner assignment |
| Tiered case selection | Parent/child selection flow (Select Parent First) |
| Case list action buttons | Configurable actions (e.g., "Register New Case") |
| Auto-select case | Automatic when only one matches |
| Grid menus | Grid layout with icons, configurable columns |
| Shadow modules | Shared forms, independent case lists |
| Display conditions | Conditional module/form visibility (XPath) |
| Form linking | Dynamic routing based on expression values |
| Session stack operations | Push/pop for nested workflows, chained forms |
| App update flow | Staged upgrade via ResourceManager, rollback |
| Offline install (.ccz) | Install from file without internet |
| Demo/practice mode | demo_user, data isolation, practice data refresh |
| Biometric login | Fingerprint (Android BiometricPrompt) / FaceID (iOS LocalAuthentication) |
| Background sync | WorkManager (Android) / BGTaskScheduler (iOS) |
| Push notifications | FCM (Android) / APNs (iOS) |
| Graphing | D3/C3 charts in case details via WebView/WKWebView |
| Report modules | Server-defined UCR reports displayed on mobile |
| Printing | HTML templates, ZPL labels, PDF generation |
| Document upload | Word, Excel, PDF, HTML, RTF, TXT, MSG |
| Ethiopian calendar | Alternative date widget (appearance: ethiopian) |
| Nepali calendar | Alternative date widget (appearance: nepali) |
| Recovery mode | Submit unsent forms, view logs, data wipe/restore |
| Content Provider equivalent | Android: ContentProvider / iOS: App Groups or URL schemes |
| Full settings parity | All app-level, device-level, and developer settings |
| Connection diagnostics | Built-in connectivity testing |
| Crash logging | Device log capture and submission to server |
| Heartbeat | Periodic server check-in for version, forced updates |

**Exit criteria**: Full feature parity tests pass. Advanced workflows (case search & claim, session stack, form linking) match commcare-android behavior. Correctness scorecard at 95%+.

### Tier 4 — Platform Products (~20-30 tasks)

*Goal: Full APK parity including newer product features.*

**CommCare Connect:**

| Feature | Description |
|---------|-------------|
| Connect authentication | Separate auth flow for Connect users |
| Jobs marketplace | Browse, claim, and complete jobs |
| Learning modules | Training content consumption |
| Delivery tracking | Delivery confirmation and verification |
| Payments | Payment status, history, and disbursement |
| Messaging | In-app messaging between Connect participants |
| Connect UI | Dedicated screens, navigation, and state management |

**PersonalID:**

| Feature | Description |
|---------|-------------|
| Phone verification | Phone number-based identity confirmation |
| Biometric verification | Biometric template matching |
| Photo verification | Photo-based identity confirmation |
| ID registration | Register new identity records |
| ID lookup | Search identity database |

**External Integrations:**

| Feature | Description |
|---------|-------------|
| Simprints biometrics | Fingerprint scanning via external app (iOS: SDK integration) |
| ABHA integration | India health ID system |
| RDT integration | Rapid diagnostic test capture |
| NFC | Near-field communication reads/writes |
| Custom intent callouts | Generic external app integration (iOS: URL schemes / Universal Links) |

**App Management:**

| Feature | Description |
|---------|-------------|
| Multiple app management | App Manager for switching between installed apps |
| Smart Links | URL-based installation |
| Staged rollout | Server-controlled release management |

**Exit criteria**: All product features functional. CommCare Connect full workflow completes. PersonalID verification passes. Correctness scorecard at 99%+. App Store / Play Store submission ready.

## Oracle Testing & Verification Strategy

### Oracle Test Harness (built in Tier 1)

1. **FormPlayer comparison**: Run test forms through FormPlayer REST API, capture submitted XML + case transactions as golden fixtures
2. **KMP engine replay**: Feed same inputs to KMP engine (via commonTest), compare output XML
3. **Test app corpus**: Start with commcare-core's 20+ mini test apps and 162+ XForm fixtures, expand to ccqa.ccz in Tier 2
4. **Correctness scorecard**: CI produces pass/fail counts per tier

### Android Parity Tests (Tier 2+)

- Install same app on commcare-android and the new KMP app
- Compare: submitted XML, case transactions, sync state hash
- Run on both Android emulator and iOS simulator in CI

### Exit Gates Per Tier

| Tier | Oracle Tests | Parity Tests | Scorecard |
|------|-------------|-------------|-----------|
| 1 | 5 test apps pass | App launches on both platforms | — |
| 2 | 20+ test apps, ccqa.ccz pass | Core workflows match commcare-android | 80%+ |
| 3 | Full test suite pass | Advanced workflows match | 95%+ |
| 4 | All tests + product features | Full APK parity | 99%+ |

## Scope Estimate

| Tier | Tasks | Dependency |
|------|-------|-----------|
| Tier 1 | 15-20 | — |
| Tier 2 | 30-40 | Tier 1 complete |
| Tier 3 | 25-35 | Tier 2 complete |
| Tier 4 | 20-30 | Tier 3 complete |
| **Total** | **90-125** | — |

## Key Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Intent callouts have no iOS equivalent | 13+ integrations blocked on iOS | Use iOS URL schemes / SDK integrations where available; document gaps |
| SQLDelight migration complexity | Storage layer instability | Build comprehensive migration tests in Tier 1 |
| Compose Multiplatform rendering differences | UI inconsistencies between platforms | Test on both platforms in CI from day one |
| CommCare Connect scope creep | Tier 4 larger than estimated | Treat Connect as a separable module; can ship core app without it |
| FormPlayer oracle test flakiness | False failures block progress | Pin FormPlayer version; document known differences in behavior catalog |
| D3/C3 graphing via WebView | Performance and rendering issues on iOS | Use WKWebView on iOS; consider native charting lib as fallback |

## Feature Parity Audit Source

The complete feature inventory was derived from:
- `dimagi/commcare-android` GitHub repo: ~55 Activities, ~35 Fragments, 30+ widgets, manifest analysis
- CommCare public documentation: dimagi.atlassian.net, confluence.dimagi.com, dimagi.github.io/xform-spec
- commcare-core engine API audit (commonMain source)

This inventory serves as the **definitive parity checklist**. No feature ships without a corresponding item checked off. No item is added without evidence from the Android codebase or documentation.
