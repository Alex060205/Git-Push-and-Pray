# Chronic Pain Plan Companion: Product Requirements (v2.1)

**Scope for now:** rheumatology patients, older adults.
**Goal:** reduce the cognitive load of following a doctor-prescribed exercise plan for one or more chronic pain conditions, by turning the plan into one clear daily suggestion, collecting a small amount of structured data, and producing a clean record for the clinician.

Status of this document: proposal. The monitoring set (section 6) is a *proposed monitoring specification, not a validated questionnaire or clinical decision rule*. The only validated instrument in it is the PEG.

---

## 1. Design principles

| # | Principle | What it means in practice |
|---|---|---|
| P1 | The doctor's plan is the boundary | The app never invents a dose, progression, fallback or symptom limit. It only chooses *among* options the plan contains. |
| P2 | One decision per screen | One question, one primary button. Max 4 tabs. No gestures required (no swipe, long-press, drag). |
| P3 | Plain words | No clinical jargon in patient-facing text. Clinical terms live in the PDF export. |
| P4 | Large and forgiving | Body text 18 pt or larger by default and respects OS text-size settings; touch targets at least 56 pt; high contrast (WCAG 2.2 AA minimum, AAA for body text where feasible); no time-limited UI. |
| P5 | Never colour alone | Every colour-coded value also shows a number, label or icon. |
| P6 | Nothing changes silently | OCR results, plan changes and deletions always need explicit confirmation. Every destructive action is confirmable and, where possible, undoable. |
| P7 | User keeps control | The app suggests; the person chooses. Any level inside the plan can be picked, including resting. |
| P8 | Low burden | Daily check-in target: under 3 minutes. Only ask what the engine or the clinician will actually use. |

---

## 2. Core concepts

- **User**: the patient. One account, one profile.
- **Condition**: a tracked problem, e.g. "Left knee, [diagnosis]" or "Neck pain, [diagnosis]". A user has 0..n conditions. Each has a name, diagnosis (free text), and status (active/archived).
- **Plan**: the doctor's prescription for one condition. Versioned: a condition has exactly one *current* plan and any number of *past* plans. The prescribing doctor is a **text field on the plan**, not a separate model.
- **Exercise level**: a dose the plan allows. Always one of: **Prescribed** (the plan's current dose), **Backup** (the plan's fallback prescription), **Rest** (skip, logged). No other levels exist.
- **Recommendation**: the engine's suggested level for the day, with a plain-language reason. **The patient sees one recommendation per day covering all conditions** (see 5.2). Internally the engine still evaluates each condition's plan.
- **Medication**: independent of conditions. Scheduled or as-needed. Reminders are optional.
- **Check-in**: daily (patient-reported outcomes) and weekly (PEG).

---

## 3. Navigation

Bottom bar, four tabs, always visible on top-level screens:

| Tab | Purpose |
|---|---|
| **Home** | Today: exercises, medicines, daily check-in, weekly check-in banner when due. |
| **Insights** | Week view with day selector; month view colour-coded; day detail; export PDF. |
| **Plan** | Tabs per condition; current plan; update plan (photo or form); plan history. |
| **Settings** | Account, conditions (and their plans), health information, medications, notifications, calendar, display. |

---

## 4. Plan requirements

### 4.1 Plan fields

Each plan version must be able to hold the following. These come from the clinical prescription template.

| Prescription field | Required information |
|---|---|
| Each exercise | Activity, duration/repetitions, intensity or resistance, frequency, relevant technique instructions. |
| Permitted progression | Increment, minimum interval between increases, maximum dose, which dimension can change. |
| Acceptable symptom response | Expected discomfort during exercise and acceptable recovery afterward. |
| Evidence required | Number of completed sessions and observation period required before progressing. |
| Incomplete-session instructions | What to do during the session and before the next attempt. |
| Fallback prescription | Specific dose or schedule to use when rollback criteria are met. |
| Overall tolerance limits | Acceptable changes in fatigue, daily function and other relevant outcomes. |
| Review and urgent-care triggers | When adjustment should stop, who to contact, and what to do while waiting. |

Plus metadata: condition, prescribing doctor (text), plan date, source (photo / manual), original image(s), version number, status.

### 4.2 Requirements

- **PL-1** A condition's plan is entered by (a) taking a photo, (b) choosing a photo/file, or (c) typing into a form.
- **PL-2** OCR/extraction fills the fields above. The user **must** pass through a review screen before saving. Each field shows a status: **OK** (confident), **Check** (low confidence or ambiguous), **Missing** (not found).
- **PL-3** Extraction must never silently guess. Low-confidence values are pre-filled but flagged "Check". Missing branches stay empty.
- **PL-4** *Absent branch rule:* if a required field is absent, the app does not invent it. The plan is saved with status **Incomplete**, the missing item appears on the Plan tab as "Ask your doctor about this", and the engine behaves as defined in ER-9. The clinician clarifies it once during setup, not each time it occurs.
- **PL-5** Qualitative text that the engine needs as a threshold (e.g. "mild discomfort is fine", "fatigue shouldn't get much worse") must be converted to a structured value on the review screen and confirmed by the user, or left Missing. The user is shown the structured value in plain words.
- **PL-6** Saving a new plan creates a new version and makes it current. The previous version is retained, read-only, with its dates. Engine state (progression counters, observation windows) restarts on the new plan; history is kept.
- **PL-7** Plan history: list of versions per condition with date range, doctor, source image link, and a view-only detail screen.
- **PL-8** Original photo(s) are stored with the plan version and viewable alongside the extracted fields.
- **PL-9** Multi-page or multiple photos per plan are supported.
- **PL-10** Plan Tab shows exercises in full and the other fields as plain-language summaries ("How to increase", "What is OK to feel", "If you miss a session", "Backup plan", "When to call your clinic").
- **PL-11** Manual form mirrors the field list in 4.1 with plain labels and examples; every field has a "Not on my plan" choice that creates a Missing item rather than blocking save.
- **PL-12** If the plan's urgent-care/review triggers are Missing, the app shows a generic safety message (see section 9) and flags the gap.
- **PL-13** *Exercise reminder times.* Immediately after a plan is saved (a new plan, an updated plan, or the first plan in onboarding), the app shows one view, "When should we remind you?". It has one block per exercise in the saved plan, with the exercise name and its frequency from the plan (e.g. "Once a day · up to 2 minutes"). Each block asks **what time** (quick choices plus "Choose another time") and **how long before** to remind (default 15 minutes). Times are prefilled from the user's usual exercise time, or, on a plan update, from the times already set for exercises with the same name. The user confirms or skips; skipping keeps the defaults and the view can be reopened from Settings > Notifications. If the plan asks for several sessions a day, one time per session is asked. The app never picks a time silently (P6).
- **PL-14** The reminder-times view is part of the plan-saving flow, not of the review screen: saving the plan (PL-2 to PL-6) comes first, then this view. If the plan was saved as Incomplete (PL-4), the view still appears.

---

## 5. Recommendation engine

### 5.1 Behaviour

- **ER-1** Output per day: **one** level (Prescribed / Backup / Rest), one suggested time, and a one-line reason in plain language, covering all active conditions. Every exercise in the output must come from a plan.
- **ER-2** Version 1 is a **transparent rule engine** whose rules are parameterised by the plan (progression, symptom response, evidence required, tolerance limits, fallback). Any statistical or ML component may only influence choices the plan leaves open (for example *time of day*), never doses.
- **ER-3** *When it runs:* after the daily check-in is saved (produces tomorrow's suggestion), and again at 6:00 am local time if no check-in was recorded (see ER-7).
- **ER-4** *Reduce to Backup* when the plan's rollback conditions are met, evaluated from: symptom response "familiar but beyond the agreed range" or "new or different"; recovery "still above" beyond the plan's acceptable recovery period; fatigue or interference beyond the plan's tolerance limits.
- **ER-5** *Offer progression* only if **all** hold: evidence required is met (completed sessions within the observation period at the current dose); symptom response and recovery within the plan's range; tolerance limits respected; minimum interval since last increase has elapsed; a PEG was completed in the last 7 days; the new dose does not exceed the maximum; only the permitted dimension changes. Progression is offered as a question ("Your plan allows 1 more set. Ready to try it?") and the user can decline.
- **ER-6** *Hold/escalate:* if the user reports "new or different" symptoms, or any of the plan's review/urgent-care triggers fires, adjustment stops, the plan's own contact instructions are shown, and no progression is offered until the user confirms the review happened (or the plan's stated window passes).
- **ER-7** *Missing data:* if a check-in is skipped, the engine holds the current level and does not progress.
- **ER-8** *Incomplete sessions* (shortened/skipped): show the plan's incomplete-session instructions. Record reason (discomfort / fatigue / practical reason / other). Practical reasons must not trigger a reduction.
- **ER-9** *Plan Incomplete:* the engine holds at Prescribed, never progresses on a branch it lacks, and shows what is missing. It does not make up a fallback.
- **ER-10** *Context:* when the user logs unusually demanding activity, illness, a medication change, or increased as-needed medication, the engine labels the affected period "context noted" and does not treat a bad day in that window as evidence for or against progression. (It may still apply ER-4/ER-6.)
- **ER-11** Every recommendation stores: inputs used, rule that fired, plan version, and the reason text, for audit and the clinician report.
- **ER-12** User overrides (choosing a different level than suggested) are logged as overrides.
- **ER-13** Each condition's plan is evaluated against its own rules internally; the patient-facing result is merged per 5.2.

### 5.2 One combined day across conditions
- **ER-14** *Merge rule (conservative):* the day's level is the most conservative level any active plan calls for (Rest over Backup over Prescribed). Progression is offered only when **every** active plan's criteria are met, and is applied to the plans concerned.
- **ER-15** *Backup level:* each plan's own fallback prescription is used. If a plan has no fallback (Incomplete, PL-4), that plan's exercises stay at Prescribed and the gap is flagged on the Plan tab; the app does not make one up.
- **ER-16** *Combined session:* the day's exercises from all plans are presented as one list, in one session, at one suggested time, with total duration. Patient-facing screens do not label exercises by condition. Each exercise keeps a hidden link to its plan for logging and the PDF.
- **ER-17** *Attribution:* check-in answers (pain, fatigue, interference, recovery, symptom response) are whole-person and are applied to every active plan's rules. Only when the patient answers "more than agreed" or "new or different" does the app ask one optional follow-up, "Where was it?", with the condition names as chips (multi-select, "Not sure" allowed), so a reduction or escalation can be applied to the right plan(s). If skipped, it applies to all.
- **ER-18** The suggested time comes from the time the user set for each exercise (PL-13); the usual exercise time is only the default. Exercises that share a time form one session. If exercises have different times, they are separate sessions on Home (see open question 11). If a calendar is linked (section 10.7), the engine may *propose* a different time (CAL-3). The user accepts or ignores it; the time never changes on its own. Time of day is a choice the plan leaves open, so this is allowed under ER-2.
- **Trade-off:** one combined level is simpler but can reduce an unaffected area on the day another one flares. Mitigated by ER-17 and by the patient being able to choose any level.

### 5.3 Wording
Recommendation text is calm and brief ("Gentle day suggested. Your knee was sore after yesterday's walk."). The app never diagnoses, never says a flare "is" a condition, and never claims exercise caused or cured symptoms.

---

## 6. Monitoring specification (proposed)

This is a proposed monitoring specification, not a validated questionnaire or clinical decision rule.

| When | Metric and response format | Purpose |
|---|---|---|
| Every exercise session | **Actual dose:** minutes/repetitions, plus prescribed resistance/intensity. Populate from the timer where possible; patient confirms completion. | Establishes exposure. A running timer alone does not prove exercise occurred. |
| Immediately after each session | **Symptom response:** familiar and within the agreed range / familiar but beyond the agreed range / new or different. | Captures discomfort that affected the session. |
| Only if shortened or skipped | **Reason:** discomfort / fatigue / practical reason / other. Record actual dose. | Separates symptom-related difficulty from scheduling problems. |
| Once daily, consistent time, including rest days | **Average pain over the previous 24 hours:** 0 = none, 10 = worst imaginable. | Tracks symptom burden beyond the session. |
| Same daily check-in | **Average fatigue over the previous 24 hours:** 0 to 10. | Detects delayed and accumulating fatigue. |
| Same daily check-in | **Interference with usual daily activities over the previous 24 hours:** 0 = none, 10 = completely prevented them. | Measures the cost of the programme to everyday life. |
| Same daily check-in while evaluating a change or flare | **Recovery:** returned to the agreed usual range / still above it / unsure. If recovered, approximate time: same day / next day / later. | Establishes recovery without requiring continuous symptom tracking. |
| Once weekly, before considering progression | **Standard PEG questionnaire:** its three original items covering average pain and interference with enjoyment of life and general activity over the past week. | Provides a consistent weekly outcome measure. |
| Whenever something changes | **Context:** unusually demanding other activity, illness, medication change or increased prescribed rescue-medication use; optional note. | Helps interpret changes without assuming exercise caused everything. |

The PEG is an established brief measure. Its enjoyment and activity items capture important aspects of life impact, but it is not a complete quality-of-life assessment. Keep its standard wording and recall period. **The PEG items must be shown verbatim and unmodified.**

### 6.1 Application of the spec to this app

- **MS-1 (daily check-in, Home):** asks, in this order: pain, fatigue, interference with daily activities, recovery (when applicable), then one combined exercise log (section 7), then "Anything different lately?" (context). About 6 screens, **the same for 1 or several conditions**. Each question is one screen; the 0-10 items use a large tappable number grid with the anchors from the table (not a slider).
- **MS-2 (whole-person answers):** every check-in item is asked **once for the whole person**, not per condition. Pain is average pain overall; recovery applies to the person's exercise and any flare. Attribution to a condition happens only via the optional follow-up in ER-17. Recovery is shown after a session or flare in the last 48 h (see open questions).
- **MS-3 (recovery wording):** "Is your pain back to normal for you?" with helper text: "Normal means the level you and your doctor agreed is OK." Options: Yes / Still more than normal / Not sure; if Yes, "When did it settle?" Same day / Next day / Later.
- **MS-4 (weekly):** the PEG banner appears on Home when 7 or more days have passed since the last PEG (default prompt day set in Settings, default Sunday). It stays until done and can be postponed one day at a time. It is a single flow of 3 screens, one item each.
- **MS-5 (context):** available from the end of the daily check-in ("Anything different lately?") and from a "Something changed" action on Home. Multi-select of the four context types plus optional note.
- **MS-6** The check-in is for "the previous 24 hours". Timing is consistent: reminder default 7:00 pm, user-editable. If a check-in is missed, the user can complete it later that day or the next morning; it is labelled with the date it covers.
- **MS-7** Entries are editable for 7 days (from Insights > day detail), with the edit logged.

---

## 7. Exercise logging

- **EX-1** Home shows **one** "Today's exercises" card (level, time, number of exercises, total duration, preview of names) with a **See exercises** action. No per-condition cards (one card per session time if the exercises have different times, see ER-18). Empty state if no plan has exercises today (rest day).
- **EX-2** Exercise detail: one combined list of the day's exercises for the suggested level exactly as in the plans (activity, dose, intensity/resistance, technique tip), why this level was suggested, **Start**, and **Choose a different level**.
- **EX-3** Choose-level screen shows only plan-derived options for the whole day: Prescribed, Backup (where plans have one), Rest. The suggested one is marked "Suggested".
- **EX-4** Timer: optional running timer pre-fills actual minutes. The user always confirms what was actually done.
- **EX-5** Logging happens at the end of the day in the daily check-in, on **one screen**: actual dose per exercise (steppers prefilled from timer/plan), one symptom response for the session, and, if anything was shortened or skipped, the reason. The optional "Where was it?" follow-up appears only for "more than agreed" or "new or different" (ER-17). The user can also log right after the session from Home.
- **EX-6** If the user selects "New or different" the app shows the plan's urgent/review instruction right away (ER-6).
- **EX-7** Marking an exercise "did not do it" is one tap and never shows guilt-inducing wording.

---

## 8. Medications

- **MD-1** Medications are independent of conditions. Fields: name, dose/amount (free text), instructions (e.g. "with food"), type (**scheduled** with 1+ daily times, or **as needed**), start/end date (optional), reminders on/off.
- **MD-2** Adding a medication is manual in v1. (Photo-of-label extraction is a later option.)
- **MD-3** Reminder actions: **I took it**, **Remind me in 15 minutes**, **Skip this time**. All three are logged with timestamps.
- **MD-4** As-needed medications have an "I took one" action on Home; every use is logged (feeds the context item "increased rescue-medication use" and the PDF).
- **MD-5** Home shows today's medicines with status (due / taken / skipped / missed). Missed doses are shown neutrally, never as alarms.
- **MD-6** The app does not advise on dosing, interactions, or changes to medicines.

---

## 9. Safety

- **SF-1** Generic message shown where urgent triggers are missing or on "new or different" symptoms: "If you feel very unwell or think this is an emergency, call your local emergency number. Otherwise contact your clinic."
- **SF-2** The app is a support tool for following a clinician's plan, not a diagnosis or treatment tool.
- **SF-3** Clinical rules are only defined by the plan. Product/clinical advisors must review rule logic, wording and defaults before any release.
- **SF-4** Confirm with regulatory/legal advice whether the recommendation function is regulated software as a medical device in the target market (Health Canada / FDA) and which privacy law applies (e.g. PIPEDA / Alberta PIPA, Alberta HIA, HIPAA).

---

## 10. Screens and functional requirements

### 10.1 Home
- **HM-1** Date and greeting. Optional weather line is **out of v1** (nice-to-have only).
- **HM-2** *Weekly check-in banner* when due.
- **HM-3** *Exercises today*: a single combined card for all conditions (EX-1). Home and the daily check-in never ask the patient to think about conditions.
- **HM-4** *Medicines*: scheduled doses for today with "I took it", plus as-needed medicines with "I took one".
- **HM-5** *Daily check-in* button: primary action pinned above the tab bar, with state ("Not done yet · about 3 minutes" / "Done today"). Becomes prominent from the check-in reminder time.
- **HM-6** *Something changed* action (context entry).
- **HM-7** Empty states: no conditions → "Add your first condition" opens the add-condition flow.

### 10.2 Insights
- **IN-1** Default **week view**: week header with previous/next, 7-day selector (Mon-Sun, today highlighted), metric tabs (Pain / Fatigue / Daily life / Recovery), a bar chart for the selected metric, and a day summary card for the selected day.
- **IN-2** Day summary: pain, fatigue, daily-life interference, recovery, exercises done (level and what was done), medicines taken/skipped, context events and note.
- **IN-3** **Month view** reachable from the week view. Each day cell is colour-coded **and** shows the number: average of that day's value for the selected metric. Metric tabs switch the metric. Tapping a day opens its day detail. Days with no data show a dash and neutral grey.
- **IN-4** Colour bands for 0-10 metrics: 0-1, 2-3, 4-6, 7-8, 9-10, from green to red, with a visible legend. Recovery uses three categories (back to normal / still above / unsure) with distinct icons. Colour is never the only signal (P5).
- **IN-5** Metrics are whole-person; there is no condition filter in Insights. Condition-level detail appears only in the PDF (via exercises and the plan).
- **IN-6** Weekly PEG score and trend are shown on the week view when available.
- **IN-7** *What we noticed* (patterns): **not in MVP**. If added, wording must be descriptive ("Your pain was lower on days you exercised"), never causal, and shown only with enough data.
- **IN-8** **Export PDF** (see 10.3) from both views.

### 10.3 PDF export ("Report for my doctor")
- **EP-1** Options: period (last 2 weeks / last month / last 3 months / pick dates); conditions to include.
- **EP-2** Contents: patient name (and optional date of birth), period; for each condition: current plan summary, any plan changes in the period; planned vs actual dose per session; symptom response counts; daily pain, fatigue, interference charts; recovery summary; weekly PEG scores; medicines (scheduled adherence and as-needed use); context events and notes; recommendation history (level + reason + overrides).
- **EP-3** Includes a short note that the data is patient-reported and the monitoring set is not a validated clinical tool (except PEG).
- **EP-4** Generated on the device; shared via the OS share sheet (email, print, save). No upload to a server unless the user shares it.

### 10.4 Plan tab
- **PT-1** Tabs for each active condition (horizontally scrollable if needed), plus an "Add condition" entry.
- **PT-2** Per condition: header (name, diagnosis, doctor, plan date, version, status), "Missing items" banner if Incomplete, exercises list, summaries of other fields (tappable for full detail).
- **PT-3** **Update plan** → choose Take photo / Choose photo / Type it in → review screen (PL-2 to PL-5) → save as new version.
- **PT-4** **Plan history** (PL-7).

### 10.5 Settings
- **ST-1** *My conditions:* list; add condition (name, diagnosis → step 2: add plan, may be skipped and done later); edit; archive. Archiving keeps history.
- **ST-2** *My medicines:* list, add, edit, stop (keeps history).
- **ST-3** *Health information:* height and weight (editable), "last updated" date. Stored as numbers with units (cm/kg default, with option for ft-in/lb).
- **ST-4** *Notifications:* exercise reminders (on/off, minutes before, default 15), exercise times (one per plan exercise, PL-13; the usual exercise time is only the default), medicine reminders (on/off), daily check-in time (default 7:00 pm), weekly check-in day and time, quiet hours.
- **ST-5** *Display:* text size (Normal / Large / Extra large), high-contrast mode.
- **ST-6** *Account:* first name, last name, medical provider, email/sign-in method, **sign out**, **export my data**, **delete account and all data** (two-step confirmation: explain what is deleted, then type or tap confirm).
- **ST-7** *Times to avoid* (manual blocked times, from the earlier concept) is **not in MVP**. Calendar linking (ST-8) covers the same need.
- **ST-8** *Calendar:* link or unlink a calendar, show its state ("Not linked" / "Linked: name of source"), and a switch for "Suggest times for my exercises". Details in section 10.7.

### 10.6 Account creation and onboarding
- **AC-1** Sign-up/sign-in with email (passwordless link or password) and optional biometric/PIN lock. The sign-in credential is handled by the sign-in step itself; it is not repeated in the profile screens below.
- **AC-2** *Account creation is deliberately short: two views.*
  1. **About you** (step 1 of 2): first name (required), last name (required), medical provider (optional free text: the doctor, physiotherapist or clinic who looks after them). Helper text: "You can change this later."
  2. **Set up your day** (step 2 of 2): one view that repeats the main inputs from Settings, all prefilled with defaults and all optional: text size (ST-5), height and weight (ST-3), usual exercise time and exercise reminder lead (ST-4), daily check-in time, weekly check-in day and time, quiet hours. It is one list view, not one question per screen. This is an intentional exception to P2 because nothing on it is required.
- **AC-3** *One source of truth.* The "Set up your day" values are the same stored values as in Settings. Changing one changes the other. The view is not shown again after onboarding.
- **AC-4** *Onboarding order:* sign-in, About you, Set up your day, add first condition, add plan (photo/manual, with review), **exercise reminder times (PL-13)**, optional medicines, optional calendar link (10.7). Each step is skippable except first and last name.
- **AC-5** The medical provider is shown in Settings > Account and in the PDF report header (EP-2). It may be offered as a prefill for a plan's "prescribing doctor" text field; the user confirms it (P6).
- **AC-6** Delete account removes server data and local data; confirmation email.

### 10.7 Calendar link and suggested times
- **CAL-1** Settings > Calendar lets the user link a calendar: the calendar on the phone, Google Calendar or Outlook. Linking asks the operating system or provider for permission and can be undone at any time. It is optional, also offered at the end of onboarding.
- **CAL-2** *Privacy.* Only when the user is busy or free is used. Event titles, attendees, locations and notes are never read into the app's records, never stored and never sent to a server. Availability is read on the device. Unlinking deletes anything cached.
- **CAL-3** *Suggested times.* When a calendar is linked, the app may suggest a better time for an exercise. The rule is transparent and simple: the free window closest to the time the user set for that exercise (PL-13), outside quiet hours, long enough for the exercise's session time, and not overlapping a scheduled medicine time. The suggestion appears as a screen ("Suggested time") and as an optional line on Home when the usual time is busy. Any statistical or ML ranking may only reorder these free windows (ER-2).
- **CAL-4** *Choice.* The suggestion has three actions: use the suggested time, keep the current time, see other times. Accepting changes that exercise's time only for that day, unless the user chooses "Use this time every day". Nothing changes without a tap (P6).
- **CAL-5** The suggested time never changes the dose, level or exercises. It is shown in plain words and never implies the time is medically better.
- **CAL-6** If the calendar cannot be read (permission removed, account signed out), the app falls back to the times set in PL-13 and shows a neutral note in Settings > Calendar.
- **CAL-7** Suggestions are re-evaluated when the check-in saves tomorrow's suggestion (ER-3) and when the user opens the Suggested time screen.

---

## 11. Notifications

| Type | Trigger | Actions |
|---|---|---|
| Exercise | N minutes before the time set for that exercise (default 15; PL-13) | Start now / Remind me in 15 minutes / Change the time |
| Medicine | At scheduled time (if reminders on) | I took it / Remind me in 15 minutes / Skip this time |
| Daily check-in | Daily at chosen time | Open check-in |
| Weekly check-in | Chosen weekday/time | Open weekly check-in |

- **NT-1** Notification text avoids sensitive details on lock screen (e.g. "Time for your medicine" with no name by default; option to show).
- **NT-2** Maximum 1 nudge per item after the initial reminder.
- **NT-3** Respect quiet hours except medicines the user marks as important.

---

## 12. Data model (summary)

| Entity | Key fields |
|---|---|
| User | id, first_name, last_name, medical_provider (text, optional), auth, locale, units, text size |
| HealthInfo | user, height, weight, updated_at |
| Condition | id, user, name, diagnosis, status |
| Plan | id, condition, version, doctor (text), plan_date, status (Complete/Incomplete), source, image refs, is_current |
| PlanExercise | plan, activity, dose (reps/sets/minutes), intensity/resistance, frequency (days), technique text |
| PlanRules | plan, progression (increment, min interval, max dose, dimension), acceptable symptom response, evidence required (sessions, window), incomplete-session instructions, fallback prescription, tolerance limits, review/urgent triggers; each with status OK/Check/Missing |
| Recommendation | user, date, level, time, reason, per-plan evaluation (plan, rule fired, plan version), inputs snapshot, user override |
| ExerciseSession | user, date, level, symptom response (one per session), shortened/skipped reason; child rows: exercise (linked to plan/condition), actual dose |
| DailyCheckin | user, covers_date, pain, fatigue, interference, recovery (+ time to recover), flagged_conditions[] (optional follow-up) |
| WeeklyPEG | user, week, item1, item2, item3 |
| ContextEvent | user, date, types[], note |
| Medication | user, name, dose, instructions, type (scheduled / as needed), times[], reminders_on, start/end |
| MedicationEvent | medication, scheduled_at, action (taken/skipped/snoozed/missed), timestamp |
| NotificationSettings | per type: enabled, time, lead time, quiet hours |
| ExerciseReminder | plan_exercise, session_index (for several sessions a day), time, lead_minutes, enabled, set_at |
| CalendarLink | user, source (device / Google / Outlook), status (linked / unlinked / error), suggest_times (on/off), linked_at. No event content is stored. |

---

## 13. Non-functional requirements

- **Platforms:** iOS and Android phones first (cross-platform acceptable); tablet layouts later.
- **Accessibility:** WCAG 2.2 AA minimum; screen reader labels on everything; dynamic type up to 200%; no reliance on colour; avoid small tap targets; avoid time-outs; consistent layouts.
- **Offline-first:** daily check-in, exercise logging, medicine logging and reminders work with no connection and sync later. Local notifications scheduled on device.
- **Privacy and security:** encryption in transit and at rest; minimal data collection; no third-party advertising; analytics (if any) must be de-identified and opt-in; clear consent screen; data export and delete.
- **Calendar data:** read on the device, free/busy only, never uploaded (CAL-2). Calendar permission is requested only when the user taps a link option, with a plain-words reason first.
- **OCR:** extraction quality target to be set by testing on real prescriptions (printed and handwritten). On-device or server processing is a decision for review; if server-side, images are encrypted and deletable.
- **Performance:** Home loads under 1 s from local data; PDF export under 10 s for 3 months of data.
- **Localization:** English first; all strings externalised; date, time and unit formats follow locale.
- **Reliability:** reminders must still fire after device restart and timezone change.

---

## 14. MVP scope

**In:** accounts (short two-view creation, AC-2); conditions; plans via photo + manual form with review; plan versioning/history; rule-based engine (ER-1 to ER-13); Home; daily and weekly check-ins; exercise logging; medications with reminders; per-exercise reminder times after each plan save (PL-13); calendar link with suggested times (10.7; can move to Later if time is short); Insights week/month/day; PDF export; settings; notifications.

**Later:** manual "times to avoid"; weather; pattern insights ("What we noticed"); medication label scanning; clinician portal/sharing link; wearables; caregiver access; more languages; ML for timing only.

---

## 15. Open questions and decisions to confirm

1. **Combined day** (section 5.2). Home and the check-in are now fully condition-blind, with one level for the day. Confirm the conservative merge (ER-14), or whether a flare in one area should only reduce that area's exercises (more precise, but the patient then sees a mixed list).
2. **Recovery asked every day or only when needed.** The monitoring spec asks it "while evaluating a change or flare"; the brief lists it as a daily item. The mockups show it conditionally (after a session or flare). Switching to always-daily is a small change.
3. **Threshold structure.** Plans often state limits in words. Who confirms the numeric interpretation: patient on the review screen (current design), or the clinician during setup?
4. **Engine authority.** Should progression be auto-applied or always offered as a question? (Current: always offered.)
5. **OCR location** (on device vs server) and handling of handwriting.
6. **Mockup changes versus the earlier version:** Home, exercise screens, check-in and Insights are condition-blind (one card, one level, one question set); removed the invented "Gentle" exercises (ankle circles), now plan-derived Backup only; "pain right now" bands replaced with 24-hour average 0-10; weather and "What we noticed" removed from MVP; the Calendar tab and "Times to avoid" folded into Settings or deferred.
7. Is a clinician-facing view or secure share link wanted in v1, or is the PDF enough?
8. Target markets and regulatory path (SF-4).
9. **Pre-session pain is not in the monitoring spec.** The demo plan (Appendix A) adjusts each day's session from *pain recorded immediately before the session* compared with the day before. Section 6 only defines a 24-hour average pain item. The mockups already show a "before you start" pain screen, so the next revision needs a "plan-specified pre-session measure" row in section 6 (see A.6).
10. **Earliest-increase date.** The mockups say an increase is allowed "no sooner than Nov 20" on Nov 15. The handout rules (2 steady sessions, one increase per 7 days, weekly ceilings) would not by themselves give Nov 20. Confirm how that date is derived (A.6).
11. **Per-exercise times versus one combined session.** PL-13 asks for a time per exercise, but the combined-day design (ER-14, ER-16) shows one session at one time. Current proposal: exercises with the same time form one session; different times give separate sessions on Home. Alternative: ask one time per *day* and apply it to all exercises. Demo user has one exercise, so both look the same for her.
12. **Calendar scope.** Which calendars in v1 (phone calendar only, or also Google and Outlook)? Is on-device free/busy enough, or do any providers force a server connection? Regulatory and privacy review (SF-4) should cover this before it ships.
13. **Medical provider field.** Free text (current) or a searchable list of providers? Should it prefill the plan's prescribing doctor (AC-5)?
14. **Where the sign-in email is captured.** AC-1 treats it as part of sign-in, so the profile screens do not ask for it. Confirm.

---

## 16. Visual style (revised: Alberta Health Services inspiration)

**Direction.** Take colour theme, type and component styling from the Alberta Health Services (AHS) public website. Layout stays as in the mockups. This replaces the earlier Government of Alberta (alberta.ca) palette (interactive blue #0070C4, brand teal #0081A2).

**Caveats.**
- Colour values come from public brand-colour listings and the Government of Alberta design system, not from an official AHS style guide. Verify against the real AHS site before build.
- The app is independent. It must not use the AHS logo or wording that implies it is an AHS product.
- The AHS web font could not be confirmed. Use a neutral sans-serif (below) until it is.

### 16.1 Colour tokens

| Token | Value | Use | Contrast note |
|---|---|---|---|
| `--brand-blue` | #007AC2 (AHS blue) | Primary buttons, links, active tab, selected number | White text on it is about 4.6:1 (AA for normal text) |
| `--brand-blue-dark` | #005A8F (derived) | Headings, pressed state, header band | Higher contrast; verify |
| `--brand-green` | #00B374 (AHS green) | "Done", success, progress, green end of the 0-10 scale | **Not for text on white (about 2.7:1).** Use with dark text, an icon or a label |
| `--text` | #333333 | Body text | Above 10:1 on white |
| `--text-secondary` | #595959 | Helper text | About 7:1 on white |
| `--panel` | #F2F6F9 | Cards and panels | Light blue-grey |
| `--border` | #C9D3DB | Dividers, input borders | |
| `--green-dark` | #00704A (derived) | Text and check marks on light green tint (#E6F7F0) | About 5.5:1 on the tint |
| `--focus` | #FEBA35 | 3 px focus ring and keyboard focus; border of the amber notice | From the Government of Alberta design system |
| `--amber-tint` / `--amber-text` | #FFF4D6 / #6B4A00 (derived) | Non-urgent notices (weekly check-in due, plan incomplete) | Always with words and an icon where it flags a gap |
| `--alert` | #B42318 (derived) | Urgent safety message, destructive actions | Always with icon and words |

- Mostly white and light grey, with blue for action and green for positive state. Same proportion as the AHS site.
- The 0-10 bands (IN-4) run from `--brand-green` through yellow and orange to `--alert`. Numbers and a legend stay on every cell (P5).
- High-contrast mode (ST-5): white background, #000000 text, `--brand-blue-dark` actions, 3 px borders.

### 16.2 Typography
- Primary font: a neutral sans-serif. Use the system font on each platform (San Francisco on iOS, Roboto on Android) with Helvetica or Arial as the fallback. Nothing that needs a paid licence.
- Body 18 pt regular; helper text and captions 16 pt minimum; headings semibold. No light weights, no italics for instructions, no long runs of capitals. Sentence case everywhere.
- Large numbers in the 0-10 grid use 28 pt semibold.
- All sizes scale with OS text size and ST-5 (up to 200%).

### 16.3 Components
- Corners: 4 px radius, flat, thin borders, almost no shadow.
- Primary button: solid `--brand-blue`, white text, full width, at least 56 pt high. Secondary: 2 px blue outline, same size. Destructive: `--alert` outline with a label.
- Selected number or option: filled blue with a check mark (not colour alone).
- Tab bar: white, selected tab in `--brand-blue` with a 4 px top rule and bold label.
- Header: white with a small two-tone blue and green accent bar. It echoes the AHS blue and green pairing but is not a copy of the logo.
- Icons: simple outline icons, always paired with a text label.
- Safety message (SF-1): panel with `--alert` left border, warning icon and plain words.

---

## Appendix A. Demo user: Maria Lutke

All mockups use this person. The data below is fictional. It comes from the physiotherapy handout supplied for the demo. The handout's own wording says its numbers are "illustrative adjustment instructions for this sample program" and "not universal pain-treatment thresholds".

### A.1 Profile

| Field | Value |
|---|---|
| First name | Maria |
| Last name | Lutke |
| Medical provider | Treating physiotherapist, (111) 111-1111 (from the handout; clinic name not given) |
| Email (placeholder) | name@email.com |
| Conditions | 1: "Right hand and wrist sensitivity" |
| Diagnosis (free text) | Treated carpal tunnel syndrome |
| Health information | Not added (height, weight empty) |
| Medicines | None added |
| Text size | Large |
| Exercise time (Gentle tendon glides) | 10:30 am (also her usual exercise time) |
| Exercise reminder | 15 minutes before |
| Calendar | Not linked |
| Daily check-in | Every day at 7:00 pm |
| Weekly check-in | Sundays at 10:00 am |
| Quiet hours | 9:00 pm to 7:00 am |

Note: the product scope (document header) says rheumatology patients. The demo plan is from a physiotherapist for a hand and wrist problem. Both fit the plan model, which is clinician-agnostic.

### A.2 Plan header (maps to section 4.1 metadata)

| Field | Value |
|---|---|
| Programme | Graded hand tendon gliding |
| Condition | Right hand and wrist sensitivity |
| Prescriber | Treating physiotherapist, (111) 111-1111 |
| Clinic contact | (222) 222-2222 |
| Date reviewed and approved | Nov 8, 2026 |
| Start date | Nov 9, 2026 (day 1) |
| Review | After 3 weeks (about Nov 30, 2026), or sooner if the concerns in A.4 occur |
| Source | Photo of handout (demo) |
| Version / status | Version 1, current. **Incomplete** (one missing item, see A.7) |

**Clinical context (as written):** persistent right hand and wrist sensitivity after previously treated carpal tunnel syndrome; no current acute injury. **Treatment focus:** comfortable movement, confidence using the right hand, and gradual return to everyday activity. A temporary increase in familiar pain does not, by itself, mean the tissue has been damaged. Hand to exercise: the affected right hand.

### A.3 The exercise: gentle tendon glides

**Setup.** Sit comfortably, forearm supported, shoulder relaxed. Keep the wrist comfortably straight (not bent forward or back). Move slowly, breathe normally, use a comfortable range. Do not squeeze, do not force the fingertips into the palm, do not push the fingers with the other hand.

**The five positions (one continuous cycle):**
1. Hand upright, fingers and thumb pointing up. Fingers relaxed, wrist in line with the forearm.
2. Hook: gently bend the middle and end joints of the fingers. Fingertips approach the base of the fingers and do not have to touch. Large knuckles stay comfortably open.
3. Loose fist: gently bend the large knuckles. Thumb rests lightly beside the index finger. No squeezing.
4. Straight fist: open the end joints slightly, fingertips approach the palm. A full position is not required. Thumb relaxed.
5. Return to the start position, fingers and thumb pointing up.

**Pace.** One cycle takes about 10 to 15 seconds. No long holds. Relax the hand between cycles. If a position feels too sensitive, use a smaller movement through it and do not force the end position.

**Dose.**
- One session per day. No extra sessions and no making up a missed session.
- Session time includes both gentle movement and short rests. Continuous movement is not required.
- Start with one cycle, rest about 20 to 30 seconds, repeat at an easy pace within the session time.
- Do not increase speed, grip force or range to fit in more cycles. **No repetition target is prescribed.**
- Dimension that can change: **session time only.**

| Days (from start) | Dates | Session time ceiling |
|---|---|---|
| 1 to 7 | Nov 9 to Nov 15 | 2 minutes, once daily |
| 8 to 14 | Nov 16 to Nov 22 | Up to 3 minutes, once daily |
| 15 to 21 | Nov 23 to Nov 29 | Up to 4 minutes, once daily |

These are weekly **ceilings, not deadlines.** Stay below the ceiling when an adjustment is needed. Never exceed 4 minutes in one session, and never go past this schedule without review. The minimum session for an automatic reduction is **1 minute**. The stop instructions (A.4) always take priority over this minimum.

### A.4 Safety: when to pause or seek advice

- **Pause the session** if discomfort keeps building, feels substantially different from usual symptoms, or does not settle with a short rest. Record the time actually completed. The person does not need to push through increasing symptoms to finish the timer.
- **Stop the exercise and seek assessment** for new or worsening numbness, persistent tingling, loss of hand strength, dropping objects, marked swelling, or a change in hand colour or temperature. These must not simply be attributed to central sensitisation.
- **Seek urgent care** for sudden significant weakness, a cold or pale hand, or severe rapidly worsening symptoms.
- **Contact the physiotherapy team before further progression** if a flare continues into the following day, pain keeps rising despite reductions, or the person is repeatedly unable to complete the minimum session. 
- New or worsening symptoms take priority over the progression timetable.

(Maps to ER-6, EX-6, SF-1. These are the plan's own review and urgent-care triggers, so PL-12 does not apply.)

### A.5 Recording

- **Before each session:** familiar hand or wrist pain, 0 to 10 (0 = no pain, 10 = worst pain you can imagine), at about the same time and in the same circumstances each day.
- **After each session:** the session time actually completed, including planned short rests. Never record the suggested time if less was done. A session not done is recorded as **missed**, not completed.
- **Adjustment input:** the next day's pre-exercise pain score is the response used to adjust that day's session.
- **Also note:** new symptoms, unusual response, prolonged increase in pain (for review).
- **Follow-up:** bring daily pain scores, completed session times and symptom notes to the review. The physiotherapist reassesses dose, movement range and readiness to progress. Medication and other treatments are outside the scope of the handout.

### A.6 Adjustment rules (parameters for the rule engine, ER-2)

Let *P(today)* be today's pre-exercise pain, *P(yesterday)* yesterday's, and *T(yesterday)* yesterday's **actual** completed time.

| Rule | Condition | Result |
|---|---|---|
| **A. Reduce** | P(today) is at least 2 points higher than P(yesterday) and yesterday's session was completed | Today's session = T(yesterday) minus 1 minute. Never above today's scheduled time or the weekly ceiling. Never automatically below 1 minute. |
| A (floor) | T(yesterday) was already 1 minute | No further automatic reduction. Contact the physiotherapy team if the increase persists. Stop criteria still apply. |
| **B. Hold** | P(today) unchanged or lower | Keep the last assigned session time until the progression conditions are met. Do not jump back to the weekly ceiling. |
| **C. Progress** | At least 2 consecutive daily sessions at the current dose, with no rise in the next morning's pre-exercise pain; no new or worsening neurological symptoms; no unusual or prolonged flare | Increase by 1 minute, never above the weekly ceiling. At most one increase in any 7-day period. A reduction can happen sooner. If conditions are not met, stay or reduce; the schedule may take longer than 3 weeks. |
| **D. Missing data** | Yesterday's pain or completed time is missing, or yesterday was missed | Do not read missing as zero pain or zero tolerance. Do not progress on an incomplete comparison. Keep the last assigned dose within the ceiling unless a stop instruction applies. Day 1 uses the 2-minute start. After several missed days, or if unsure how to restart, contact the physiotherapist. |

**Worked example (from the handout):** yesterday pain 3 and a 2-minute session; today pain 5. Today's adjusted session is 1 minute, provided no stop instruction applies. This does not prove yesterday's exercise caused the rise.

**How this maps to the requirements (for the next revision):**
- Rule A is the plan's Fallback prescription (4.1): "1 minute less if pain up 2 or more". In the app's three levels it is the **Backup** level, and the 1-minute floor is the "Shorter: 1 minute" option in the mockups.
- Rule D matches ER-7 and ER-9. Rule C is the progression criteria of ER-5, expressed as 2 sessions and a 7-day gap. Note ER-5 also requires a PEG in the last 7 days; the plan itself does not. The engine should apply both unless the clinician says otherwise.
- The rules depend on a **pre-session pain** value that section 6 does not define. Add it as a plan-specified measure: asked before each session (Before you start screen), 0 to 10, same wording as the plan.
- The "Acceptable symptom response" field is only partly stated: "some familiar discomfort may occur" with pause conditions. The mockup review screen flags it **Check** (extracted text: "Familiar discomfort that settles") per PL-5.

### A.7 Missing from the handout (Incomplete plan, PL-4)

| Item | Status |
|---|---|
| Overall tolerance limits: acceptable changes in fatigue and daily function | **Missing.** Shown on the Plan tab as "Fatigue and daily limits: ask your physiotherapist". |
| Acceptable symptom response (as structured threshold) | Check (see A.6) |
| Everything else in 4.1 | Present (exercise, progression, evidence required, incomplete-session instructions, fallback, urgent triggers) |

Effect: plan status is Incomplete. Per ER-9 the engine holds and does not invent a fatigue or daily-life limit. Fatigue and daily-life answers are still collected for the report (EP-2) but are not used as a rollback or progression threshold.

### A.8 Demo timeline

Demo "today" in the mockups: **Sunday, Nov 15, 2026 (day 7 of 21).** The handout was approved Nov 8 and the plan started Nov 9.

| Date | Day | Average pain (Insights) | Notes |
|---|---|---|---|
| Mon Nov 9 | 1 | 3 | First day, 2-minute start |
| Tue Nov 10 | 2 | 3 | Pain unchanged, 2 minutes kept |
| Wed Nov 11 | 3 | 5 | Pre-exercise pain 5, up 2 from Tuesday. Plan reduced the session to 1 minute (completed 1 min). Fatigue 4, daily life 4. Recovery: back to normal, next day. Symptom response: familiar, within agreed range. |
| Thu Nov 12 | 4 | 4 | |
| Fri Nov 13 | 5 | 3 | |
| Sat Nov 14 | 6 | 3 | |
| Sun Nov 15 | 7 | not yet entered | Pre-session pain 3, same as yesterday, so today's session is 2 minutes at 10:30 am. Weekly PEG banner is due. Daily check-in not done yet. |

Other values in the mockups: tomorrow (Mon Nov 16) starts at 2 minutes, to be confirmed after the pre-session pain rating; the mockups say an increase is allowed "no sooner than Nov 20" (see open question 10). Values for pre-session pain, fatigue, daily life and recovery on days other than Nov 11 are not specified in the mockups and should be filled in when the demo data is built.

### A.9 Contacts and next steps shown in the app
- Physiotherapist: (111) 111-1111. Clinic: (222) 222-2222. Shown under "When to call your clinic" and in the PDF report.
- Review around Nov 30, 2026.
- Report for my physiotherapist: "Since my plan started (Nov 9)" includes pain before each session, minutes done, adjustments and why, daily pain, fatigue, daily life, recovery, weekly PEG and notes.

### A.10 Demo screens added for exercise times, calendar and account creation
- **Reminder times (after saving the plan):** Gentle tendon glides, once a day, 10:30 am, reminder 15 minutes before.
- **Calendar:** Settings > Calendar shows "Not linked" with three ways to link. A separate "Suggested time" screen is an **example of the linked state only**: on Monday, Nov 16 her calendar is busy at 10:30 am, so the app suggests 11:15 am (free 11:00 am to 12:00 pm), with "Use 11:15 am" and "Keep 10:30 am". This calendar data is invented for the demo; it is not in the handout.
- **Account creation:** About you (Maria, Lutke, Treating physiotherapist), then Set up your day with her existing settings (text size Large, height and weight not added, 10:30 am, 15 minutes before, daily check-in 7:00 pm, weekly check-in Sundays 10:00 am, quiet hours 9:00 pm to 7:00 am).
