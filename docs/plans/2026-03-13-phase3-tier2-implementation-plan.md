# Phase 3 Tier 2: Daily Field Worker Features — Implementation Plan

**Date:** 2026-03-13
**Prerequisite:** Phase 3 Tier 1 complete (14/14 tasks, 16 tests passing)
**Goal:** A field worker can do their actual job with this app.
**Estimated tasks:** 30-40, organized into 8 waves

## Scope Prioritization

Tier 2 features are ordered by field worker impact. A worker needs to:
1. Fill out real forms (all question types, repeat groups, field-list groups)
2. Navigate cases (search, sort, detail tabs)
3. Manage forms (save/resume drafts, review completed)
4. Sync reliably (incremental, auto-sync)
5. Use multi-language apps
6. Navigate with breadcrumbs

Features requiring hardware (camera, GPS, barcode) or platform-specific APIs (Mapbox, background sync) are deferred to late waves.

## Wave 1: Form Entry — Core Question Types (6 tasks)

*Currently only TEXT, INTEGER, SELECT_ONE render. Add all basic types.*

### Task 1: Date and time widgets
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/engine/FormEntrySession.kt`

**What to do:**
- Add `QuestionType.DATE` and `QuestionType.TIME` handling in `mapControlType()`
- Create `DateData` and `TimeData` answer handling in `FormEntrySession.createAnswerData()`
- Add date picker UI (text input with date format validation for Tier 2; native picker in Tier 3)
- Add time picker UI (text input with HH:MM format)
- Wire `Constants.DATATYPE_DATE` and `Constants.DATATYPE_TIME` in createAnswerData

### Task 2: Decimal widget
**Files:**
- Modify: `FormEntryScreen.kt`
- Modify: `FormEntryViewModel.kt`

**What to do:**
- Decimal already handled in `createAnswerData()` but UI doesn't distinguish from text
- Add numeric keyboard hint for DECIMAL type via `KeyboardOptions(keyboardType = KeyboardType.Decimal)`
- Add numeric keyboard hint for INTEGER type via `KeyboardOptions(keyboardType = KeyboardType.Number)`

### Task 3: Select-multi widget
**Files:**
- Modify: `FormEntryScreen.kt`
- Modify: `FormEntrySession.kt`

**What to do:**
- Add checkbox-based multi-select UI (vs. radio for select-one)
- Create `SelectMultiData` from selected choices in `createAnswerData()`
- Handle `Constants.CONTROL_SELECT_MULTI` → multiple selection state
- Track selected choices as a Set in QuestionState

### Task 4: Label and trigger widgets
**Files:**
- Modify: `FormEntryScreen.kt`

**What to do:**
- Label widget already handled (no input) — verify rendering
- Trigger widget: display as an "OK" button that marks the question as answered
- Handle `Constants.CONTROL_TRIGGER` in the UI

### Task 5: String widget appearances (multiline, numeric)
**Files:**
- Modify: `FormEntryScreen.kt`
- Modify: `FormEntryViewModel.kt`

**What to do:**
- Read `appearance` from `FormEntryPrompt.getAppearanceHint()`
- Add appearance field to `QuestionState`
- "multiline" → multi-line text field
- "numeric" → numeric keyboard
- Default → single-line text

### Task 6: Oracle tests for all question types
**Files:**
- Create: `app/src/jvmTest/resources/test_all_question_types.xml` (XForm with every type)
- Modify: `app/src/jvmTest/kotlin/org/commcare/app/oracle/OracleComparisonTest.kt`

**What to do:**
- Create comprehensive test form with text, integer, decimal, date, time, select-one, select-multi
- Add oracle comparison tests for each question type with answers
- Verify cross-platform serializer handles DateData, TimeData, SelectMultiData correctly

---

## Wave 2: Form Entry — Structure (4 tasks)

### Task 7: Repeat groups
**Files:**
- Modify: `FormEntryViewModel.kt`
- Modify: `FormEntryScreen.kt`
- Modify: `FormEntrySession.kt`

**What to do:**
- Handle `EVENT_PROMPT_NEW_REPEAT` in `advanceToQuestion()` — currently skipped
- Add "Add another?" prompt when repeat event encountered
- Call `controller.newRepeat()` to create new repeat instance
- Track repeat count in ViewModel state
- Handle `EVENT_REPEAT` navigation (step into/out of repeats)

### Task 8: Field-list groups (multiple questions per screen)
**Files:**
- Modify: `FormEntryViewModel.kt`
- Modify: `FormEntryScreen.kt`

**What to do:**
- Detect `appearance="field-list"` on group elements
- When entering a field-list group, collect ALL questions in the group
- Display them as a scrollable list on one screen (already rendering `questions` as a list)
- The engine's `getQuestionPrompts()` already returns all prompts in a field-list group

### Task 9: Skip logic / relevancy
**Files:**
- Modify: `FormEntryViewModel.kt`

**What to do:**
- Relevancy is already handled by the engine — `FormEntryController` skips non-relevant questions
- Verify that when answers change, re-evaluating questions updates visibility
- After answering, call `updateQuestions()` to refresh the question list
- Test with a form that has `relevant="/data/q1 = 'yes'"` conditions

### Task 10: Constraints and validation messages
**Files:**
- Modify: `FormEntryViewModel.kt`
- Modify: `FormEntryScreen.kt`

**What to do:**
- Already partially implemented (constraint violated → validationMessage)
- Show constraint text per-question (not global) via `prompt.getConstraintText()`
- Show required field indicators (already done)
- Add `constraintMessage` per QuestionState, display inline below the question
- Handle `ANSWER_REQUIRED_BUT_EMPTY` when navigating forward (validate all questions before stepping)

---

## Wave 3: Case Management (4 tasks)

### Task 11: Case list search
**Files:**
- Modify: `CaseListViewModel.kt`
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/CaseListScreen.kt`

**What to do:**
- `updateSearch()` already filters by name — extend to search all displayed properties
- Add search bar UI to CaseListScreen
- Debounce search input

### Task 12: Case list sort
**Files:**
- Modify: `CaseListViewModel.kt`
- Modify: `CaseListScreen.kt`

**What to do:**
- Add sort options: by name (A-Z, Z-A), by date opened
- Read sort configuration from suite's `<detail>` element if available
- Add sort toggle UI

### Task 13: Case detail view
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/CaseDetailScreen.kt`
- Create: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/CaseDetailViewModel.kt`

**What to do:**
- Display all case properties in a detail view
- Read `<detail>` configuration from suite for field labels and ordering
- Support tabbed display (tabs from `<detail id="...">` elements)
- Navigate to case detail on case selection, then "Continue" to proceed

### Task 14: Case create/update/close via form submission
**Files:**
- Modify: `SyncViewModel.kt`
- Modify: `FormEntryViewModel.kt`

**What to do:**
- After form submission, parse case blocks from the submitted XML
- Apply case create/update/close transactions to the local sandbox
- Use `CaseXmlParser` to process case blocks
- Update case storage immediately (optimistic local update)

---

## Wave 4: Form Management (4 tasks)

### Task 15: Save form as draft (incomplete)
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormRecordViewModel.kt`
- Modify: `FormEntryViewModel.kt`
- Modify: SQLDelight schema (`CommCare.sq`)

**What to do:**
- Add `form_records` table: form_id, xmlns, status (incomplete/complete/submitted), serialized_instance, created_at, updated_at
- Save button in form entry that serializes current state to draft
- Serialize FormDef instance data for resumption

### Task 16: Resume saved form
**Files:**
- Modify: `FormRecordViewModel.kt`
- Modify: `FormEntrySession.kt`

**What to do:**
- Load serialized instance data back into FormDef
- Re-initialize FormEntryController at the saved position
- Display list of incomplete forms on home screen

### Task 17: Completed form review
**Files:**
- Modify: `FormRecordViewModel.kt`
- Modify: `FormEntryScreen.kt`

**What to do:**
- After form completion, store as "complete" record
- Read-only review mode: display all answers, no editing
- Configurable retention period (Days for Review setting)

### Task 18: Unsent form queue UI
**Files:**
- Modify: `app/src/commonMain/kotlin/org/commcare/app/ui/SyncScreen.kt`
- Modify: `FormQueueViewModel.kt`

**What to do:**
- Display pending form count on home screen
- Show queue with status per form (pending, submitting, failed)
- Manual retry for failed forms
- Clear submitted forms button

---

## Wave 5: Sync & Data (3 tasks)

### Task 19: Incremental sync with hash-based diff
**Files:**
- Modify: `SyncViewModel.kt`
- Modify: `SqlDelightUserSandbox.kt`

**What to do:**
- Send `X-CommCareHQ-LastSyncToken` header for delta syncs
- Handle HTTP 412 (no new data) gracefully — already done
- Parse incremental restore: merge new/updated cases, handle deletions
- Track and persist sync token across app restarts

### Task 20: Sync on login
**Files:**
- Modify: `LoginViewModel.kt`

**What to do:**
- After successful authentication, automatically trigger sync
- Show sync progress during login flow
- Handle sync failure gracefully (allow proceeding with stale data)

### Task 21: Auto-send forms on sync
**Files:**
- Modify: `SyncViewModel.kt`
- Modify: `FormQueueViewModel.kt`

**What to do:**
- During sync, first submit all pending forms, then pull restore
- Already partially implemented in `SyncViewModel.sync(formQueue)`
- Add retry logic with exponential backoff for failed submissions
- Mark forms as permanently failed after N retries

---

## Wave 6: Multi-Language (2 tasks)

### Task 22: Language switching
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/viewmodel/LanguageViewModel.kt`
- Modify: `FormEntryViewModel.kt`
- Modify: `MenuViewModel.kt`

**What to do:**
- Read available languages from `FormDef.getLocalizer().getAvailableLocales()`
- Add language selector in settings
- Switch form display language via `FormDef.getLocalizer().setLocale()`
- Update menu text when language changes

### Task 23: RTL support
**Files:**
- Modify: UI composables

**What to do:**
- Detect RTL languages (Arabic, Urdu, etc.)
- Apply `CompositionLocalLayoutDirection` for RTL rendering
- Test with RTL test form

---

## Wave 7: Navigation & UX (3 tasks)

### Task 24: Breadcrumb navigation
**Files:**
- Create: `app/src/commonMain/kotlin/org/commcare/app/ui/BreadcrumbBar.kt`
- Modify: `MenuViewModel.kt`

**What to do:**
- Track navigation path: Home → Module → Form
- Display breadcrumb trail at top of screen
- Tappable breadcrumb segments to navigate back

### Task 25: Swipe navigation in forms
**Files:**
- Modify: `FormEntryScreen.kt`

**What to do:**
- Add horizontal swipe gesture detection
- Swipe left = next question, swipe right = previous
- Animate question transitions

### Task 26: Form end navigation
**Files:**
- Modify: `FormEntryViewModel.kt`
- Modify: `FormEntryScreen.kt`

**What to do:**
- After form completion, check session for next step
- If chained form, navigate directly
- If case list, return to case list
- If menu, return to menu

---

## Wave 8: Platform Features (4 tasks)

### Task 27: GeoPoint widget (basic)
**Files:**
- Modify: `FormEntryScreen.kt`
- Create platform-specific location code

**What to do:**
- Basic GPS capture (latitude, longitude, altitude, accuracy)
- Display coordinates as text
- JVM: stub implementation
- iOS: CoreLocation integration (deferred to iOS-specific wave)

### Task 28: Image widget (basic)
**Files:**
- Modify: `FormEntryScreen.kt`

**What to do:**
- File picker for image selection
- Display thumbnail preview
- Store image path as answer
- JVM: file chooser dialog
- iOS: PHPickerViewController (deferred)

### Task 29: Lookup tables / fixtures
**Files:**
- Modify: `SqlDelightUserSandbox.kt`
- Modify: `FormEntrySession.kt`

**What to do:**
- Ensure fixture storage populated during sync
- Wire `CommCareInstanceInitializer` with fixture storage for XPath evaluation
- Support `instance('fixtures:...')` references in forms
- Test cascading selects (select choices driven by fixture lookups)

### Task 30: Comprehensive oracle test suite
**Files:**
- Create: additional test forms
- Modify: `OracleComparisonTest.kt`

**What to do:**
- Add 15+ test forms covering all Tier 2 features
- Test repeat groups, field-lists, skip logic, calculations
- Test all question types with various appearances
- Target: 20+ oracle test apps passing

---

## Dependencies

```
Wave 1 (question types) → Wave 2 (structure) → Wave 6 (language)
                        → Wave 3 (cases) → Wave 4 (form mgmt)
                        → Wave 5 (sync)
                        → Wave 7 (navigation)
                        → Wave 8 (platform features)
```

Waves 1-2 must be sequential (structure depends on types).
Waves 3-5 can run in parallel after Wave 2.
Waves 6-8 can run in parallel after Wave 2.

## Exit Criteria

- All basic question types render correctly (text, integer, decimal, date, time, select-one, select-multi)
- Repeat groups work (add/delete instances)
- Field-list groups display multiple questions per screen
- Case search, sort, and detail view functional
- Form save/resume/review working
- Incremental sync with sync tokens
- Multi-language switching
- 20+ oracle test apps passing
- Android parity tests pass for core workflows

## Estimated Total

30 tasks across 8 waves. Start with Wave 1 (core question types) immediately.
