> SUPERSEDED: historical ab6cfd73 candidate only. See ../HANDOFF.md for the replacement candidate.

# Zen Sudoku first-listing candidate — 2026-10-04

Verdict: **signed local candidate technically ready for manual Play Console upload**. Exact-bundle device smoke validation passed. This is not publication clearance: the public privacy policy, asset rights evidence and observed UMP publisher misconfiguration remain unresolved. Nothing was uploaded, published, pushed, merged, deployed or changed in Play/AdMob; no CI was added.

## Authoritative artifact

| Field | Verified value |
| --- | --- |
| Application ID | `rew.lightgames.zensudoku` |
| Version name / code | `1.0` / `1` |
| Source branch | `codex/zensudoku-first-release` |
| Source commit / HEAD | `ab6cfd73e2a718585649d2216700abc5298c7a28` |
| AAB | `app/build/outputs/bundle/release/app-release.aab` |
| Bytes | `60659070` |
| SHA-256 | `3b0ae9791d461f5b63fa96c0844a9bb1fbcb6cf07cac543f5952c4e963eef5ad` |
| Signing | New local upload certificate; verified signed payloads |

The actual bundle manifest and embedded version-control metadata confirm these identity/version/revision values. No production source changed after the freeze commit. Only this path/hash is the candidate; historical bundles were preserved byte-for-byte under `historical-artifacts/NOT-FOR-UPLOAD-*`. Generated bundles/APKs/private signing files are Git-ignored. Rebuilding or changing the policy resource creates a different candidate that needs a new recorded hash and validation.

Upload certificate SHA-256:
`38:CC:3F:3F:D5:9C:53:96:9F:E2:C1:DD:7A:DF:3F:20:F6:D7:C5:D9:21:AF:81:76:67:F0:2E:2D:DA:83:51:B9`

Upload certificate SHA-1:
`CB:29:15:DD:F9:68:05:21:ED:AB:B3:A6:F5:46:CB:E8:42:75:8E:A5`

Public certificate only: `.release-validation/zensudoku-upload-certificate.pem`.

## Changes and signing decisions

Production ID/version now have one authoritative source in `app/build.gradle`. Internal namespace/classes intentionally remain `rew.lightgames.sudoku2`. Merged and actual bundle providers/receiver permission correctly derive `rew.lightgames.zensudoku`; no old identity remains where it controls shipped package identity or release tooling. Existing AdMob identifiers are independent identifiers and were preserved; their new-listing association needs owner confirmation.

The new package has separate Android private storage/backup identity. Existing save/preferences/schema/puzzle behavior is preserved within the new package. **Old-app data does not automatically migrate.** Old-package upgrade and signing continuity are irrelevant to this new listing.

A fresh RSA-3072 PKCS12 upload key (alias `upload`, SHA256withRSA, valid through 2054-02-19) was generated locally using the owner's masked-prompt password. Private key/password/properties remain in ignored `.local-upload-signing/`, directory mode0700/files0600. No secret was committed or included here. See `UPLOAD-SIGNING.md` for private local configuration and secure owner-controlled backup requirements. Release packaging refuses absent signing configuration; there is no debug fallback.

This certificate proves the candidate was signed by the newly created local **upload key**. It does not prove registration with Google Play or identify Google's future **app-signing key**. No Play app-signing key was created or manipulated. Google must manage the separate delivery signing key under Play App Signing.

Save/return now performs save, dismiss dialog, return to menu, with no exit interstitial loading/show path. Banner and completion ads remain. Privacy Policy is always reachable from Settings; the blank production URL produces an honest unavailable message. UMP privacy choices remain separate and conditional as before. Regression coverage protects exit navigation, completion placement, privacy route and new release identity.

## Validation actually performed

- Identity regression red phase: three new release-identity tests failed against old ID/version, as expected.
- `./gradlew testReleaseUnitTest lintRelease --offline`: successful; **125 tests, zero failures/errors/skips** across11 suites. Lint **zero errors,2853 warnings** (not warning-free). Preserved XML/logs under `.release-validation/new-listing-*`.
- Missing-signing guard deliberately rejected packaging before credentials existed. `./gradlew verifyUploadSigning --offline` then passed with private local configuration.
- `./gradlew clean bundleRelease --offline`: successful in48seconds,52 tasks. This produced the sole frozen signed candidate. Release vital lint also passed during packaging.
- `bundletool1.17.2 validate` and actual manifest dump succeeded. Official pinned tool SHA-256 `2d4ad908faea64047c1cc9cb747e6aa667c6ab192e09607bd16b67246a8cd6ae`.
- `jarsigner -verify`: jar verified. Independent Java JarFile verification read **1215 payload entries,zero unsigned**, all matching the reported certificate; no duplicate ZIP names or symlinks. Jarsigner warns about self-signed certificate/no timestamp/POSIX metadata and manifest order for JarInputStream. The manifest is last in AGP's ZIP; complete JarFile cryptographic verification succeeds. These warnings were assessed, not hidden or treated as Play acceptance.
- Three device APK split signatures verified with `apksigner`, all matching this upload certificate. Installed package independently reports1.0/code1 and no DEBUGGABLE flag.
- Source/staged diff whitespace checks passed. Independent read-only review found no new must-fix migration/configuration/signing/provenance defect.

## Exact-candidate connected evidence

SamsungSM-S918B, Android16/API36, available via host wireless adb. New package absent before install, so this was a clean install. Old installed package left untouched. Bundletool derived and installed APKs directly from the exact AAB, with the explicit upload signer; no separately rebuilt APK/debug target was substituted.

Device APK set `.release-validation/zensudoku-1.0-1-device.apks`,73993651bytes, SHA-256 `83715f88e818536c8f23e64b9759df4a59569f3d06871591cdd39ec4e919052c`. This is QA evidence, not a second upload candidate or evidence of Google's delivery signer.

Passed: launch/menu/difficulty selection; start puzzle; normal cell selection/hint; Save and return immediately to menu without interstitial; resume with hint count1; force-stop/relaunch/resume preserving the puzzle and hint count; settings/privacy unavailable message/OK/back navigation; natural completion via ordinary hint controls; completion dialog; Next triggered an actual live interstitial; Back dismissed it to the next puzzle with hints0; repeated Save and return without an interstitial. No ad was clicked. App-scoped process log contains zero `FATAL EXCEPTION`, `Fatal signal` or `WindowLeaked` markers.

Evidence under `.release-validation/`: UI XML `launch`, `privacy-unavailable`, `pause-back`, `returned-menu`, `restarted-game`, `completion`, `post-completion`, `after-ad-back`, `final-menu`; screenshots `game-saved`, `game-restarted`, `completion`, `post-completion`, `after-ad-back`; `installed-package.txt`, `release-app-logcat.txt`, `device-install.log`. Visual comparison showed the restored puzzle digits unchanged; cell-center masks after process restart differed by at most62/6720pixels per cell from antialiasing, not a claim of byte-level private-save inspection.

A floating chat bubble obscured Pause; Android Back opened the same app pause dialog. One early resume attempt preceded splash completion and one UI dump was killed; retry after launch settled succeeded. These were device automation interruptions, not observed app crashes. No old-package upgrade required. No new-version upgrade test or Google-delivered APK test performed. Regional UMP consent forms were not shown. The actual release log reports `Consent info update failed: Publisher misconfiguration: Failed to read publisher's account configuration; no form(s) configured for input app ID ca-app-pub-4002896469283656~5659029013` (line196). This is a concrete external configuration blocker; no fresh regional consent-flow PASS is claimed. Real URL/browser policy navigation cannot be tested until the URL exists. This one-device smoke does not replace broad Android coverage.

## Remaining owner actions before publication

1. In the new listing, confirm package `rew.lightgames.zensudoku`, code1 availability and Play App Signing enrollment. Have Google generate/manage the app-signing key. Verify the upload certificate fingerprints above against the registered upload certificate after onboarding; separately record Google's app-signing certificate. No Console acceptance/version availability has been assumed here.
2. Publish a real public privacy policy and enter its URL in Console and `privacy_policy_url`, then rebuild/revalidate the changed candidate before publication. Cover the actual local saved puzzles/preferences, Android backup behavior, Google Mobile Ads/advertising identifiers and SDK data practices, UMP consent/choices, retention/deletion/contact and appropriate audience/jurisdiction information. No own backend/account was found; do not claim SDKs collect no data. Use actual SDK disclosures for Data safety and declare advertising accurately.
3. Obtain Purple Planet **A Touch of Zen** source/licence/permission and monetised Android redistribution/attribution terms. No entitlement or required credit is established locally. Preserve the asset until a justified rights decision. `ASSET-LICENSING.md` also tracks74 legacy artwork paths and puzzle corpus authorship/source confirmation; Ubuntu font licence is bundled and pop.ogg has a local CC0 metadata declaration. Unknown provenance is not an infringement finding.
4. Confirm existing AdMob app/unit association with the new listing. Resolve the observed UMP publisher-misconfiguration error by configuring/publishing the appropriate consent message for AdMob app ID `ca-app-pub-4002896469283656~5659029013`, then test applicable regional forms and choices on this release. Existing banner/completion ad serving was observed, which alone does not establish correct account/listing association.
5. Complete normal listing/content-rating/target-audience/Data safety/privacy/ads declarations and any account-specific testing requirements manually. Upload only the AAB matching the recorded SHA-256 when authorized outside this task; do not mistake historical or QA APK artifacts for it.

## Repository/model handoff

One local source commit was made (`ab6cfd73...`) on `codex/zensudoku-first-release`; no push/merge. Tracked tree is unchanged after source freeze. This handoff is untracked documentation produced after the artifact, alongside the eight previously existing untracked reports; no signing material is tracked/staged.

Primary agent is the environment's GPT-6-based Codex; exact deployed model identifier is not exposed. One inherited-model read-only subagent, `/root/new_listing_review`, independently reviewed this new-listing task's source/configuration, signatures/provenance and validation evidence. No model override was used. Earlier old-identity investigation had other reviewers and is archived separately. Relevant skills included verification-before-completion and requesting-code-review.

References for the key distinctions: https://developer.android.com/build/configure-app-module ; https://developer.android.com/studio/publish/app-signing ; https://developer.android.com/tools/bundletool ; https://developers.google.com/admob/android/privacy/play-data-disclosure .
