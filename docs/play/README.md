# Google Play internal testing — owner guide

Purpose: get the Android probes onto the owner's phone through a trusted
channel (R18) without turning off Samsung Auto Blocker, Play Protect or
Advanced Protection. Internal testing is private: only email addresses on the
tester list can install, and nothing appears in public Play search.

Requirements were checked against Play Console Help on 2026-10-29 (sources at
the end). Play changes forms often; if a screen differs, follow the screen and
tell the developer what changed.

## What is ready

| Item | Status |
|---|---|
| Signed release bundles (`.aab`) built by CI on every push to `main` once the two upload-key secrets exist (artifact `lockdin-play-bundles`) | Implemented; waiting for secrets |
| Play build leaves out the optional accessibility detector, so **no accessibility declaration or video** is needed for this release | Implemented |
| Foreground-service demo video recorded on a CI **emulator** (artifact `lockdin-emulator-demo-EMULATOR-ONLY`) | Implemented; see evidence in `docs/milestones.md` |
| Privacy policy page: `docs/play/privacy-policy.html` | Written; needs a contact email and a public URL |
| Answers for every form below | Written |

## Owner steps

### 0. Before you start (one time)
1. Add the two GitHub secrets (`LOCKDIN_UPLOAD_KEYSTORE_B64`,
   `LOCKDIN_UPLOAD_STORE_PASSWORD`). The next push builds the bundles.
2. Developer account: play.google.com/console/signup → Personal → pay
   US$25 → identity verification → verify the Android device with the Play
   Console app. Internal testing works while some verification steps are
   still pending; production does not.
3. Decide where the privacy policy lives (see "Privacy policy hosting").

### 1. Create the two apps
Play Console → **Create app**, twice:

| Field | Probe | Target |
|---|---|---|
| App name | Lockd In Probe | Lockd In Probe Target |
| Default language | English (United States) | same |
| App or game | App | App |
| Free or paid | Free | Free |
| Declarations | tick both (policies, US export laws) | same |

The package name is fixed by the first upload: `app.lockdin.probe` and
`app.lockdin.probetarget`.

### 2. Upload to Internal testing
For each app: **Test and release → Testing → Internal testing → Create new release**.
1. Play App Signing: accept "Use Google-generated key" (Google holds the app
   signing key; our CI key is only the upload key).
2. Upload the matching `.aab` from the `lockdin-play-bundles` artifact.
3. Release name: leave the default. Release notes: "Probe test build."
4. **Testers** tab → create an email list with your Google account → save.
5. Save → **Review release** → fix anything listed under Errors (usually the
   App content forms below) → **Start rollout to Internal testing**.

### 3. App content forms (Policy and programs → App content)
Answers are in the next section. The target app needs only privacy policy,
ads, app access, content rating, target audience and data safety. It has no
special permissions.

### 4. Install on the phone
Internal testing → **Testers** → copy the **opt-in link** → open it on the
S24+ while signed in to the tester account → **Accept** → **Download it on
Google Play** → install both apps. Auto Blocker stays on.

New builds reach the phone as normal Play updates, usually within minutes of
rollout.

### 5. Then: smoke test S1–S5 (`docs/device-test-checklist.md`).

## Form answers

### Privacy policy
URL of the hosted `privacy-policy.html` (see hosting below).

### Ads
No, the app does not contain ads.

### App access
"All functionality is available without any access restrictions." No login.

### Content rating (IARC questionnaire)
- Category: **All other app types** (utility / productivity).
- Every content question (violence, sexuality, language, controlled
  substances, gambling, user interaction, sharing location, digital purchases):
  **No**.
- Expected rating: Everyone / PEGI 3.

### Target audience and content
- Target age group: **18 and over** only. The test build is for adult
  testers; this keeps it outside the Families policy.
- Appeal to children: No.

### Data safety
Play exempts apps that are **only** on the internal testing track from this
form. Fill it in anyway (it takes two minutes) so it is ready for closed
testing later:

- Does the app collect or share any required user data types? **No.**
- Reasoning (for your records, not entered in Play): nothing is transmitted
  off the device by the app; it has no internet permission. "Share log" is a
  user-initiated transfer through the Android share sheet to an app the user
  picks. Play's Data safety guidance does not count user-initiated transfers
  the user expects as "sharing", and data processed only on the device is not
  "collected". If Play's form wording disagrees on the day, answer
  conservatively: declare **App activity → App interactions**, processed
  ephemerally = No, optional, purpose "App functionality", not shared.
- Data encrypted in transit: not applicable (no transmission).
- Deletion: users can clear the log in the app or uninstall.

### Government apps / Financial features / Health / News
Not a government app. No financial features. No health features. Not a news
app.

### Foreground service permissions (probe only)
Play asks this because the probe targets Android 14+ and declares
`FOREGROUND_SERVICE_SPECIAL_USE`.

- Type: **Special use**.
- Use case: choose "Other" if no listed case fits, and enter:
  > User-started focus session that blocks apps the user chose until the session ends.
- Description of the functionality:
  > The user starts a focus session of fixed length (1 to 60 minutes in this
  > test build) and picks which apps to block. While the session runs, a
  > foreground service with a persistent notification checks which app is in
  > front, using Usage Access, and shows a full-screen block screen when the
  > user opens a blocked app. The service ends itself when the session ends,
  > or immediately when the user taps "End now". It never runs without a
  > session the user started.
- Impact if the system defers or interrupts it:
  > The session the user started would silently stop blocking the apps they
  > chose. The task cannot be deferred: a blocked app must be covered within
  > about a second of opening, and only for the session's duration.
- Video link: the `lockdin-probe-fgs-demo.mp4` from the emulator artifact,
  uploaded to YouTube as **Unlisted** or to Google Drive with "Anyone with the
  link can view". It shows the app opened, a session started, the
  notification, the block screen over the target app, and "End now".

### Permissions that need no Play form
- `PACKAGE_USAGE_STATS` (Usage access) — a special access the user grants in
  Settings; covered by the privacy policy and the in-app explanation.
- `SYSTEM_ALERT_WINDOW` (Display over other apps) — user-granted in Settings.
- `QUERY_ADVANCED_PROTECTION_MODE` — read-only, normal permission.
- No `QUERY_ALL_PACKAGES`; package visibility is limited to the target app
  and the home screen.

## Privacy policy hosting

Play needs a public URL that works without signing in. Options:

1. **Your TF Tech Robotics website** (repos `tftechrobotics/TfTech_WebSite`
   or `tf-tech-robotics_Website`): add the page at a path like
   `/lockdin/privacy`. Best long-term home for the real product's policy too.
2. **A small public GitHub repository with GitHub Pages** (free for public
   repos; this repository is private, and Pages on private repos needs a paid
   plan).
3. **Google Sites / Google Docs "Publish to web"**: fastest, fine for a test
   build.

Before publishing, replace `CONTACT_EMAIL_PLACEHOLDER` with the address you
want testers and Google to use.

## Not in this release (on purpose)

- **Accessibility detector.** Including it requires the in-app prominent
  disclosure (exists), a Play declaration, and a video of disclosure, consent
  and refusal. It is added in a later release only if usage access fails the
  M1 go criteria (owner decision U8). Until then, device tests A-rows that
  need accessibility are marked "not tested — not in Play build".

## Sources
- Foreground service and full-screen intent requirements — support.google.com/googleplay/android-developer/answer/13392821
- Use of the AccessibilityService API — support.google.com/googleplay/android-developer/answer/10964491
- Data safety section — support.google.com/googleplay/android-developer/answer/10787469
- Prepare your app for review — support.google.com/googleplay/android-developer/answer/9859455
