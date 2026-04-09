# Bug: Can't type into constraint-validated text form fields

**Tracking issue:** #394
**Found by:** Phase 9 Wave 4b scouting, 2026-04-08 (US) / 2026-04-09 (UTC)
**Severity:** High — blocks mobile workers from filling any form with a text/integer field that has an XForm constraint
**Affects:** iOS FormEntryScreen, any XForm with constrained text/integer/decimal questions

## Reproduce

1. Install Bonsaaso Application in jonstest.
2. Log in as `haltest@jonstest.commcarehq.org`.
3. Start → Registration → Register Household.
4. Answer p1 (sub-district: `tontokrom`), p2 (health facility: `aboaboso`), p3 (township: `britcherkrom`), p4 (location: skip).
5. On p5, "Household Head Health ID", type any value into the Answer field.
6. Observe: the field displays empty despite typing, and shows "Constraint violated" as the error message.

## Root cause

`app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt:111-139`:

```kotlin
fun answerQuestion(index: Int, answer: IAnswerData?): Boolean {
    val result = formSession.answerAtIndex(index, answer)
    when (result) {
        ANSWER_OK -> {
            clearConstraint(index)
            updateQuestions()   // ← only updates UI state on success
            return true
        }
        ANSWER_CONSTRAINT_VIOLATED -> {
            setConstraint(index, ...)  // ← just sets the error message
            // UI state NOT refreshed — question.answer stays at the old value
        }
        ANSWER_REQUIRED_BUT_EMPTY -> {
            setConstraint(index, "This field is required")
        }
    }
    return false
}
```

The `OutlinedTextField` in `FormEntryScreen.kt:292-301` is bound to `value = question.answer`, which comes from the viewmodel's `questions[index]` state. When the user types into the field:

1. `onValueChange` fires for every character.
2. `answerQuestionString` is called with the partial value (e.g., "1", "12", "123"...).
3. `answerQuestion` hands the value to the form engine.
4. The engine's constraint check rejects the partial value (e.g., the XForm says `string-length(.) = 10`).
5. `ANSWER_CONSTRAINT_VIOLATED` path runs: sets the error message but does NOT call `updateQuestions()`.
6. `question.answer` stays at its prior value (empty string on first edit, or last-accepted value otherwise).
7. Compose recomposes the field with `value = ""`, so the user's typed character never appears.

Every keystroke fails validation because no intermediate partial value satisfies the constraint. The field is effectively un-typeable via normal keyboard input.

## Why it's a real-user bug, not just a test bug

A real mobile worker filling this form on iPhone would:
1. Tap the field.
2. Type the first digit of their 10-digit health ID.
3. See nothing appear.
4. Type more digits. Still nothing.
5. Give up, unable to complete the form.

Maestro exposed this because we tried to fill the field programmatically. Manual testing on Android or web HQ probably doesn't hit this because those platforms commit state even on constraint violation and show the error inline.

## Suggested fix

The proper fix requires decoupling the displayed field value from the engine-committed value. The view model needs:

- A per-question "draft" text state that tracks what the user has typed.
- `answerQuestionString` updates the draft immediately, regardless of constraint result.
- The field binds to the draft, not the committed answer.
- On Next, the draft is pushed to the engine; if the engine rejects it, the user stays on the question with the constraint error visible AND their draft still in the field.

Rough shape:

```kotlin
// In the state
var questionDrafts by mutableStateOf<Map<Int, String>>(emptyMap())

// When user types
fun answerQuestionString(index: Int, value: String): Boolean {
    questionDrafts = questionDrafts + (index to value)
    // Still try to commit — clear constraint if it now passes
    val committed = tryCommit(index, value)
    return committed
}

// In FormEntryScreen
OutlinedTextField(
    value = viewModel.questionDrafts[index] ?: question.answer,
    ...
)
```

Existing `answerQuestion` behavior on ANSWER_OK is preserved; the UI just gets a separate draft layer for the in-progress typing.

## Alternative (simpler but weaker) fix

Change `updateQuestions()` to be called on every path (including CONSTRAINT_VIOLATED), so the UI reflects what the engine thinks the current state is. But this doesn't actually help because the engine rejects constraint-violating answers and never commits them in the first place — `updateQuestions()` would still read the old empty value.

The draft-state approach is the only one that actually lets users type values that don't pass constraints until they're fully typed.

## Workaround for Wave 4b testing

Wave 4b is refocused to navigation-only: walk into a form, verify the first few pages render, tap Back to exit. Full fill-and-submit testing (Wave 4c) is blocked on fixing this bug.

## Links

- `app/src/commonMain/kotlin/org/commcare/app/viewmodel/FormEntryViewModel.kt:111-139` — the bug
- `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt:292-301` — the field that binds to the broken state
