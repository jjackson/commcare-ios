# Phase 9 Wave 4b — Form navigation (no submit)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prove end-to-end that a logged-in mobile worker can navigate INTO a real CommCare form, answer radio-choice questions across multiple pages, reach a text-input page, and tap Back repeatedly to retrace out of the form. Full fill-and-submit is deferred to Wave 4c pending a product bug fix.

**Spec:** `docs/superpowers/specs/2026-04-08-phase9-e2e-ui-testing-design.md` §7 (Wave 4)

**Status:** Shipped. Discovered and filed one significant product bug during scouting (#394 — text input into constraint-validated fields is broken, blocking full form entry).

---

## Why this is 4b and not the full Wave 4

Spec §7 W4 is "install → sync → case list → form entry → submit → HQ verify." Wave 4 has been split into sub-waves:

| Sub-wave | Scope | Status |
|---|---|---|
| 4a | module list + case list nav + back | shipped (PR #393) |
| **4b (this plan)** | form entry navigation, no submit | **shipping** |
| 4c | full form fill + submit + HQ verify | blocked on #394 |

Wave 4b proves:
1. Forms load from the module → sub-menu → form chain.
2. Radio-choice questions render and can be answered.
3. Cascading choices work (p1 answer determines p2 options, p2 determines p3).
4. Optional questions can be skipped.
5. Text-input fields render with their label + "Required" indicator.
6. Back navigation inside a form retraces page-by-page.

Wave 4b does NOT prove:
- Text input into form fields (blocked — see #394)
- Form submission
- HQ server roundtrip for submissions

## Scouting findings

Executed live against jonstest + Bonsaaso's Register Household form. Found a deterministic 5-page front section:

| Page | Label | Question type | Scout answer |
|---|---|---|---|
| 1 | What is the sub-district | radio (2 options) | tontokrom |
| 2 | Health Facility Tontokrom | radio (5 options) | aboaboso |
| 3 | Townships Under Aboaboso | radio (5 options) | britcherkrom |
| 4 | Collect the location (optional) | GPS capture | skip (optional) |
| 5 | Household Head Health ID | text (required, constrained) | **blocked** |

Pages beyond 5 couldn't be scouted because the text-input bug (#394) blocks advancement.

## Bug caught: #394

Typing any value into the Household Head Health ID field produces "Constraint violated" instead of displaying the typed characters. Root cause in `FormEntryViewModel.answerQuestion` at line 122: when the engine returns `ANSWER_CONSTRAINT_VIOLATED`, the viewmodel sets the error message but does NOT call `updateQuestions()`, so `question.answer` stays at the old empty value. Every keystroke fires the constraint check with a partial value that can never satisfy the constraint (e.g., a 10-digit-exact field rejects 1, 2, ..., 9 digit prefixes), and the Compose `value = question.answer` binding shows empty.

This is a real user-facing bug, not just a test bug. Mobile workers can't fill any form with a constrained text/integer field. Fix approach documented in `docs/phase9/wave4b-text-input-constraint-bug.md`: the viewmodel needs a per-question draft state layer that decouples the displayed value from the engine-committed value.

Wave 4c (full submit) is blocked on this fix. Wave 4b works around it by navigating up to the text field but not entering anything.

## File Map (as shipped)

| File | Action | Purpose |
|---|---|---|
| `app/src/commonMain/kotlin/org/commcare/app/ui/FormEntryScreen.kt` | Modify | testTags: `form_answer_text`, `form_answer_integer`, `form_answer_decimal`, `form_answer_date`, `form_next_button`, `form_back_button`, `form_save_draft_button`, `form_complete_label`, `form_submit_button` |
| `.maestro/flows/form-navigation.yaml` | Create | Walk into Register Household, answer p1-p3 radios, skip p4 location, assert p5 text field, back out |
| `.maestro/scripts/run-wave4b.sh` | Create | Orchestrator: Wave 3 setup + form-navigation |
| `docs/phase9/wave4b-text-input-constraint-bug.md` | Create | #394 writeup |
| `docs/superpowers/plans/2026-04-09-phase9-wave4b-form-navigation.md` | Create | This plan |
| `CLAUDE.md` | Modify | Mark Wave 4b complete, reference #394 + Wave 4c dependency |

## Acceptance Criteria (as shipped)

| Check | Verification | Pass condition |
|---|---|---|
| A | `.maestro/scripts/run-wave4b.sh` exits 0 with a fresh simulator | All 5 form pages reach assert-visible, back-trace returns to p1 |
| B | 2 back-to-back stability runs of `run-wave4b.sh` | Both pass |
| C | Issue #394 filed with full root-cause writeup | Open issue with `bug` label linking to the writeup doc |

## Task breakdown (abbreviated — already shipped)

1. testTags on FormEntryScreen widgets (text/integer/decimal/date answer fields, next/back/save-draft buttons, complete label, submit button)
2. Rebuild KMP framework + iOS app
3. Write form-navigation.yaml
4. Write run-wave4b.sh
5. Run end-to-end, debug any failures
6. File #394 with writeup
7. Write this plan doc
8. Stability runs
9. CLAUDE.md + commit + PR

## Follow-ups

- **Wave 4c (blocked on #394):** fix the draft-state bug, then write a flow that fills the Register Household form end-to-end and polls HQ for the submission.
- **Form answer field testTag collision:** all four data-type answer fields share `form_answer_*` tags, but only one should be visible at a time, so no collision in practice. If a future form has multiple text fields per page, we'll need per-index tags.
- **Constraint text display:** `prompts[index].getConstraintText()` returns the XForm's constraint message (e.g., "Must be exactly 10 digits"). This isn't surfaced in the UI today — the user just sees "Constraint violated" as a generic fallback. Wave 4c should wire this through.
