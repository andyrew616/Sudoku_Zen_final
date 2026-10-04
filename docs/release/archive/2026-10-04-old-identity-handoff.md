# Zen Sudoku local release handoff — 2026-10-04

## Verdict

**BLOCKED on external signing/Play evidence. No authoritative signed release
candidate exists.** The app-side fixes are implemented. A public policy, music
rights and asset provenance also still need evidence. Do not upload an unsigned
build, a debug-signed build or either historical bundle.

## Changes and protected behaviour

- Save/return now saves, dismisses the pause dialog and exits directly. Removed the exit
  interstitial field, load request and SDK callbacks, including ad unit ending
  `2976818750`. Menu navigation cannot wait for an exit ad.
- Banner and natural-completion placement ending `4701071767` remain consent
  gated. Puzzle logic, JSON persistence schema/keys, package, supported SDK
  range and consent implementation were not changed.
- Settings has an always-visible **Privacy Policy** button, separate from UMP's
  conditional **Privacy Settings**. It opens the configured URL in a browser,
  or handles no-browser failure. `privacy_policy_url` in `res/values/strings.xml`
  is deliberately empty. Until configured it shows an honest unavailable
  message; this is **not** a compliant public policy or release approval.
- Removed debug signing from the release build. Release outputs are deliberately
  unsigned until the existing signing path can be restored with verified key
  material. No replacement key was generated and no private material was copied.
- Preserved tracked historical bundles unchanged under clearly labelled
  `docs/release/historical-artifacts/NOT-FOR-UPLOAD-*` paths. Added output/key
  ignore rules and documented hashes. Current generated files live only under
  `app/build/outputs/`.
- Added exit/privacy structural JVM guards and connected save/return/resume and
  policy regressions. Corrected the existing instrumentation package assertion
  from `com.example.sudoku2` to the real identity.

## Signing evidence and exact resumption requirements

| Evidence | What it proves |
| --- | --- |
| Current and original repository Gradle application ID `rew.lightgames.sudoku2` | Repository package continuity, not ownership of a Play listing. |
| Original/current version name `1.1`, code `3` | Local configuration only. Play availability is unknown. |
| Historical release AAB public certificate SHA-256 `74:05:5D:31:23:AF:73:04:C8:CA:FB:D9:09:A3:C4:DA:E3:90:C9:CA:CB:41:6D:53:6B:8A:6F:7F:A6:2B:77:40` | Historical bundle signer; not a recovered private key or confirmed Play upload key. |
| Historical `jarsigner -verify` | Signature verifies with self-signed/no-timestamp warnings and JarFile/JarInputStream consistency warnings. These historical bytes are not approved for upload. |
| Debug-named historical AAB | No JAR signature; not a candidate. |
| Installed phone APK certificate SHA-256 `578a083e1dde5207916fec5ab3e88563517a9fa15da7e25c52dbad5fdbe518e6` | Android Debug signer, distinct from historical release. |
| Installed phone app: v1.1/code3, target36, DEBUGGABLE, installer null, initiated by `com.android.shell` | Local side-loaded build, **not** production upgrade evidence. |

Repository/history/configuration, local signing filenames, Android directory,
StudioProjects/Documents/Downloads and signing-related environment/property
names were checked. No matching release keystore or certificate attestation from
Play was found. Only the ordinary debug keystore and unrelated local system/
Gradle keystores were located. This search does not prove that no backup exists
elsewhere. Secrets were not printed or placed in reports.

The **Android app signing key** signs installable APKs and establishes Android
update identity. With **Play App Signing**, Google holds that key and signs
APK delivery. The developer's **upload key** authenticates AAB submissions and
may differ from the app signing key. A **debug key** provides neither assurance.
A valid AAB signature alone cannot prove acceptance by an existing Play listing.
See [Android signing documentation](https://developer.android.com/studio/publish/app-signing).

Read-only evidence needed from the correct Play application:

1. Confirm package `rew.lightgames.sudoku2`, listing/account ownership and whether
   Play App Signing is enrolled. Do not create a duplicate app as a workaround.
   If there is no existing app/signing registration for this package, record that
   explicitly and obtain the owner's decision on historical-key recovery or
   first-release key creation in a later task; no key is created here.
2. Record/download the **upload certificate** and **app signing certificate**
   separately, with SHA-256 and SHA-1, including any rotation/upgrade history.
   Compare the historical fingerprint above to both; identify which role, if
   any, it has. Supply public certificates, never passwords or private keys.
3. Recover the corresponding existing private upload keystore from its owner/
   backup, plus alias and secure local credential configuration. If Play App
   Signing is not used, recover the original APK signing key. An upload key
   cannot necessarily install over Play's differently signed APKs.
4. If the upload key is lost, confirm Play's authorised recovery/reset route with
   the account owner. This task did **not** generate or register a new key.
5. Supply a trusted previous production APK/device installation and version for
   genuine upgrade testing. With differing Play/upload keys, local upload-signed
   APKs cannot prove production install-over continuity; use an appropriate
   Play-signed test artifact in a separately authorised follow-up.
6. Check all tracks, drafts and historical versions for used version codes.
   Confirm an unused code before the final build; **code 3 is not confirmed free**.

## Privacy-policy content and external action

No established production policy URL was found. The owner must prepare and
publicly host an approved policy outside this task, then set the real HTTPS URL
in `privacy_policy_url`, verify it in-app and rebuild/revalidate the final
candidate. The same URL must be entered in Play Console. A public accessible,
non-geofenced, non-editable policy page is required; a placeholder or PDF is not
sufficient. See [Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

Repository-backed policy scope:

- Zen Sudoku and the developer/legal entity, contact details, effective date,
  purposes, recipients, retention/deletion and user controls.
- Puzzle board, solution, notes, hints, elapsed time and music/sound preferences
  stored in private SharedPreferences (`my_preferences`, JSON `saved_game`).
  No account creation or developer-operated backend was found in app code.
- Android backup/device transfer: `allowBackup=true` with permissive/default
  backup rules. Do not promise data stays exclusively on one device or that
  uninstall deletes all backup copies. Describe clear-app-data controls and
  determine actual backup/SDK retention with the owner.
- Google Mobile Ads **24.3.0** and UMP **4.0.0**: consent requests and forms,
  banner/completion ads, ad/diagnostic identifiers and third-party processing.
  Merged manifest includes internet/network, advertising ID and AdServices
  permissions, plus foreground media/wakelock permissions; no explicit camera,
  contacts, microphone or precise-location permission was found.
- Google's SDK disclosure guidance describes IP-derived general location,
  interactions, diagnostics and device/account identifiers used for ads,
  analytics and fraud prevention. That guidance currently describes a newer
  SDK; verify the actual 24.3.0 configuration/AdMob partners when completing the
  policy and Data safety form. Do not state that the app collects no data merely
  because puzzle storage is local. See [Google's SDK disclosure guidance](https://developers.google.com/admob/android/privacy/play-data-disclosure).
- Explain consent/withdrawal via UMP Privacy Settings where required, Android ad
  controls and browser access to the policy. Confirm AdMob consent-message and
  partner configuration externally; SDK gating alone is not a compliance audit.

The exit change follows the prohibition on ads interrupting explicit exit
controls. Completion remains at the existing natural break; configured ad
content, dismissibility and audience suitability remain external review items.
See [Play Ads policy](https://support.google.com/googleplay/android-developer/answer/9857753).

## Artifact authority

There is **no upload candidate**. The only future authoritative upload path is
`app/build/outputs/bundle/release/app-release.aab`, after all gates above are met.
AABs cannot be installed directly: document the APK set derived from that exact
AAB, its signer and hashes, and test those exact bytes. Do not validate a rebuilt
APK and silently attribute the results to another bundle.

Current **unsigned validation outputs** (not upload candidates), built from
HEAD plus the uncommitted source changes described here:

| Field | Value |
| --- | --- |
| Application ID | `rew.lightgames.sudoku2` |
| Version | name `1.1`, code `3` (Play availability unknown) |
| Source baseline commit | `2a9a3746bc9016d90ab8940dfdb07ea5c38b3cd0` plus uncommitted working-tree changes; not a frozen candidate commit |
| AAB path | `app/build/outputs/bundle/release/app-release.aab` |
| AAB bytes / SHA-256 | `60589596` / `ef9f85f500f315dbf879f9521dc5f00104e968b5aa77433e6c50f1743b7af748` |
| APK path | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| APK bytes / SHA-256 | `60961985` / `9992f375d560382ff368264fee2976da33654318576ac685e20ced84ea0bcb23` |
| Signing status | AAB: `keytool -printcert -jarfile` reports **Not a signed jar file**. APK: `apksigner verify` reports **DOES NOT VERIFY / Missing META-INF/MANIFEST.MF**, as expected for an unsigned APK. Neither proves signing continuity or can be installed as an Android release candidate. |

The clean run removed the old generated debug-signed release APK. Only the
current unsigned APK is in the release APK output directory. The historical
bundles remain separately archived, with original bytes/hashes preserved.

## Validation evidence

Commands actually run and results:

| Check | Result |
| --- | --- |
| Initial offline release tests | Could not configure: missing cached Gradle dependencies. Retried online. |
| Initial new regression tests on original code | **2 tests, 2 failures**, correctly detecting exit ad path and missing policy route. XML retained in `.release-validation/regression-before.xml`. |
| First combined full run | Interrupted by the user's machine reboot; not counted as pass. Temporary logs lost, edits and pre-fix XML preserved. |
| Restarted `clean testReleaseUnitTest lintRelease assembleRelease bundleRelease connectedDebugAndroidTest` | JVM: **122 tests, 0 failures/errors/skips** before the dialog cleanup. Connected: 3 tests completed, **2 passed, 1 failed**. Failure retained in `.release-validation/device-before-dialog-fix.xml` and `.release-validation/full.log`. Task failure prevented final lint reporting/artifact production. |
| Dialog cleanup regression, before fix | Updated guard: **2 tests, 1 failure**, detecting missing dialog dismissal (`.release-validation/dialog-red.log`). |
| Focused regressions/build/connected retry after cleanup | Focused JVM guards passed. Device run **interrupted** during policy test: expected 3 tests, received 1 completed; phone went offline. Empty failure report is not a new proven policy assertion failure or a pass (`.release-validation/dialog-green.log`, `device-interrupted.xml`). |
| Final `testReleaseUnitTest lintRelease assembleRelease bundleRelease --offline` | **BUILD SUCCESSFUL**, exit 0, 4m33s. **122 JVM tests, 0 failures/errors/skips**, 10 suites, on final app code. Lint: **0 errors, 2862 warnings, 2 informational findings**. APK/AAB compilation and packaging completed. Log: `.release-validation/final-release.log`; XML/HTML reports under `app/build/`. |
| Archive integrity and output signing checks | Archived bundles match original hashes/bytes. New outputs confirmed unsigned; measurements above. |
| `git diff --check` | Passed. |
| Existing phone AVD fallback | Could not start: `/dev/kvm` absent, x86_64 hardware acceleration unavailable. No AVD wipe or persistent changes; read-only launch attempted. |

Connected evidence was on Samsung **SM-S918B, Android 16/API36**, existing
side-loaded debug installation. The first run exercised launch, first save/return
reaching the menu, Resume visibility, resumed puzzle board and hint preservation;
its later second-exit lookup failed. Logs also showed a leaked outgoing pause
window. The app now dismisses that dialog before exit, and the updated JVM guard
passes. The leak's existence is proven; it is not proven to be the sole cause of
the Espresso failure. The final connected rerun did not finish, so the final
lifecycle fix is **not connected-validated**. The first run's blank-policy route
and package-identity checks passed; this does not test the future real URL,
browser-unavailable branch or production UMP forms. Tests preserve/restore the
pre-existing `saved_game` value. No physical-device app uninstall/data clear was
performed. Installing debug test builds over a debug installation is not a
production-version upgrade test.

The phone's advertised endpoint changed and became unreachable; reconnect
attempts failed and the final ADB check showed no connected phone. The user was
asked to reconnect it; no reply was received before the handoff. A discovered
unrelated ADB endpoint was not used. Reconnect the phone and run
`./gradlew connectedDebugAndroidTest` to finish local regression validation;
if another root-selection failure occurs, diagnose it rather than counting the
partial run as a pass.

Exact-candidate clean install, production upgrade, release launch/crash smoke,
elapsed-time persistence, live completion interstitial, real public policy/browser
and production UMP forms remain unproven. Release installation/upgrade is blocked
by signing evidence, independently of the device connection. Debug checks are
local regressions and must not be presented as those release checks.

Lint is a successful check with retained warnings, not a clean bill of health:
2694 warnings concern complex vector paths; the remainder include unused
resources, accessibility/text/layout issues, exported MusicService, static-field
leak, locale, dependency and platform guidance. Those were not suppressed or
silently turned into passes. Java 21/source-target 8 and Gradle deprecation
warnings remain. No claim of zero-warning or complete security audit is made.

## Licences, risks and final external setup

See [asset evidence](ASSET-LICENSING.md): music entitlement/required credit is
unresolved; sound embeds CC0; Ubuntu licence is bundled; artwork and CSV source
ownership need confirmation. No asset was removed merely to make a green report.

Independent review also noted pre-existing MusicService export/repeated-start
player allocation. These are outside the narrow release edits, not newly
introduced; do not treat the preserved service as independently security-audited.

After evidence is supplied: configure the verified existing signing path and
policy URL, choose a confirmed unused version code, freeze a source commit,
build once, record ID/version/commit/path/bytes/SHA-256/certificate, and validate
that exact candidate and legitimate production upgrade. Only then prepare Play
onboarding (developer identity, store listing/assets, public privacy URL, Data
safety, contains-ads, target audience/content rating, consent configuration and
foreground-media declarations as requested by Console). Do not upload until
all gates are resolved. No Play configuration was inspected or modified here.

## Source and execution custody

Branch: `codex/menu-grid-visual-refresh`.
HEAD: `2a9a3746bc9016d90ab8940dfdb07ea5c38b3cd0`.
Changes are uncommitted; HEAD alone does not describe these builds. Existing eight
untracked review reports were preserved. No push, merge, publish, deployment,
Play upload/configuration change or CI setup occurred.

Lead: Codex, GPT-6-based agent as identified by this environment; exact runtime
variant is not exposed. Two independent read-only review agents used the inherited
model, with no model override: initial signing/assets reviewer and replacement
final-diff reviewer after reboot interrupted the former session. No delegate
implemented code. Independent review found no must-fix regression in the exit-ad removal,
policy route, unsigned release config, archive preservation or incremental dialog
cleanup. It verified archive equality and corrected wording to avoid implying
production signing provenance. It also required the precise connected limitations
recorded above. Lead checked the final diff/configuration, test totals, artifact
hashes and unsigned status. Remaining external gates and incomplete device smoke
are blockers, not a PASS.
