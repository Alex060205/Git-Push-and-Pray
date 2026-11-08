# Setup checklist and change log (UI integration)

## A. Things to do OUTSIDE the code (needed before the app fully works)

1. **Firebase Console > Authentication > Sign-in method: enable "Email/Password".**
   Without this, sign-up shows "Email sign-in is not switched on in Firebase yet".
2. **Firestore security rules.** The app now stores everything under `patients/{uid}/...`.
   Replace test-mode rules (they expire) with:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{db}/documents {
       match /patients/{uid}/{document=**} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```
   The older repositories (`PatientRepository`, `CheckInRepository`, `ExercisesSessionRepository`,
   `PrescriptionRepository`) use top-level collections and are **not used by the new UI**; these rules block them.
3. **`local.properties` needs `GEMINI_API_KEY`** (plan photo/PDF/typed-plan reading and the compile step use it).
   Also check the model name `gemini-3.6-flash` in `PrescriptionExtractor` is valid for your key.
   The key ships inside the APK (dev/demo only; move to a server or Firebase AI before any release).
4. **Plan photos are stored on the phone only** (PL-8). To keep them across devices, add Firebase Storage
   (bucket already in google-services.json) and upload `filesDir/plans/{programId}/`.
5. **Account deletion needs a recent sign-in.** If Firebase refuses, the app tells the user to sign in again.
   The "confirmation email" (AC-6) needs a Cloud Function or Firebase email template; not done.
6. Optional: set a password-reset email template in Firebase Console > Authentication > Templates.
7. Regulatory / clinical review (SF-3, SF-4) before any release.

## B. What I added
- `app/` (new package `com.example.vitala.app`): `AppViewModel` (all wiring), `Models`, `UserStore` (Firestore JSON docs, offline-first),
  `PlanReview` (OK / Check / Missing items), `Reminders` (local notifications, survive reboot/timezone change),
  `CalendarAvailability` (free/busy only, on device), `Nav`.
- `ui/`: theme (brief's colour tokens, text size + high contrast), `Components`, and screens for sign-in, onboarding,
  Home, exercise flow (detail, pain rating, safety questions, session, level, timer, log), daily + weekly check-in, Insights
  (week / month / day / edit / PDF), Plan (view, capture, typed form, review, reminder times, history, photos), Settings (all sub-screens).
- `docs/SETUP_AND_CHANGES.md` (this file).

## C. Existing files I touched
- `app/build.gradle.kts`: + `firebase-auth`, `material-icons-extended`.
- `AndroidManifest.xml`: + notification / boot / calendar-read permissions, two receivers, `singleTop`.
- `res/xml/file_paths.xml`: + cache path for "Export my data".
- `MainActivity.kt`: now hosts the new app (old `PrescriptionScreen` kept in `Recognition/ScanScreen.kt`, unused).
- `ui/theme/Color.kt, Type.kt, Theme.kt`: rewritten (purple template theme replaced).
- `pacing/integration/AdaptiveRepository.kt`: + `currentState()` (read the assigned dose).
- `Recognition/PrescriptionViewModel.kt`: + `analyzeTypedPlan()` and `finishScan()`.
- `report/ReportPdf.kt`: no longer prints made-up height/weight (170 cm / 70 kg defaults) when none was entered.

## D. How the engine is used
Pain (0-10) + the plan's own safety questions go to `AdaptiveRepository.saveCheckIn` -> `evaluate` for each exercise.
Safety hits show the plan's instructions and stop the session. Progression is shown as a question ("Not today" keeps the dose).
Starting applies the assignment (`applyAssignment`); the confirmed minutes go to `recordSession` (a timer alone never counts).
Check-ins, sessions and PEG use `HealthLogRepository`; the PDF uses `ReportPdf`.

## E. Known gaps / decisions for you
- PDF period is fixed at the last 28 days (`ReportData.DAYS`); the period/condition pickers from EP-1 are not built.
- ER-3's 6:00 am re-run and ER-17's "Where was it?" follow-up are not implemented. ER-10 "context noted" labels are stored but the engine ignores them.
- Engine supports one session/day per exercise; PL-13's "several sessions a day" asks one time per exercise.
- Pre-session pain is mapped to the report's 3-level `PainFeeling` (0 none, 1-4 some, 5+ a lot), which loses detail in the PDF.
- Biometric/PIN lock, ft-in/lb units, wording of plan-specific pain question, and Google/Outlook direct sign-in (they read through the phone's calendar) are not done.
- Not exercised end to end: I could only launch the sign-in screen (no test account in your Firebase project). Please test: sign up > onboarding > add plan > exercise > check-in.

## F. Demo user (offline) and staying signed in
- **Demo user (debug builds only):** on the sign-in screen tap "Use demo user (Maria Lutke)". No account, no internet, nothing sent to Firebase.
  The app swaps its three data sources (`DataStore`, `HealthSource`, `AdaptiveSource` in `app/Sources.kt`) for in-memory versions,
  including a local copy of the engine bookkeeping that still runs the real `AdaptiveEngine`. So every screen and feature works on her data.
  Her data is rebuilt from Appendix A by `app/DemoSeeder.kt` with RELATIVE dates: the plan started 6 days ago, so today is always day 7 of 21.
  Data is in memory only: Sign out (or restarting the app) resets her; tap the button again for a fresh Maria.
- She has: profile + settings from A.1, "Right hand and wrist sensitivity", the tendon-glide plan (Incomplete: no fatigue/daily limits),
  six days of check-ins and sessions (pain 3, 3, 5, 4, 3, 3; day 3 reduced to 1 minute), no medicines, weekly check-in due.
- The engine's own rules decide today's dose (pain 3, same as yesterday -> 2 minutes); the "no sooner than" date is derived by the engine,
  so it differs from the mockup's Nov 20 (open question 10 in the brief).
- Plan photo reading still needs internet + GEMINI_API_KEY even in demo mode; reminders are real local notifications.
- **Stay logged in:** Firebase Auth keeps the real login on the phone. If loading data fails (e.g. offline) you get a "Try again" screen
  instead of being treated as a new user.
