# Zen Sudoku first-listing upload signing

The release application ID is `rew.lightgames.zensudoku`; authoritative version
values are in `app/build.gradle`: name `1.0`, code `1`. The namespace and internal
Kotlin/XML class packages remain `rew.lightgames.sudoku2` intentionally.

This is a new Android app. Its private storage/backup identity is separate from
`rew.lightgames.sudoku2`; old installed-app data does not automatically migrate.
Existing SharedPreferences filenames and JSON keys/schema are unchanged within
the new identity. There is no cross-package import or upgrade assumption in code.

## Private local configuration

Gradle reads `.local-upload-signing/upload-signing.properties`. The directory
must be ignored by Git and mode 0700; the keystore, password file and local
properties must be mode 0600. Required non-secret property names:

```properties
storeFile=.local-upload-signing/zensudoku-upload.p12
passwordFile=.local-upload-signing/upload-password.txt
keyAlias=upload
```

The password file contains the owner-selected password, entered through a masked
local prompt. Gradle reads it locally; do not place the password in command-line
arguments, Gradle properties committed to Git, reports, chat or logs. Store and
key passwords are the same for this PKCS12 keystore. Credentials and key material
are not in this document. Preserve a secure owner-controlled backup of both key
and credential outside this repository; do not attach them to a report.

The keystore is a **local upload key**, generated for this new listing only.
Google Play should generate/manage its separate **app-signing key** through
Play App Signing. No local Play app-signing key is created. Locally generated
APKs used for testing may use the upload certificate; Google-delivered APKs will
use Play's app-signing certificate and need not share it.

Release packaging invokes `verifyUploadSigning` and refuses to use debug signing
or silently create an unsigned release. JVM tests/lint remain usable without the
private key. Never run Gradle with secret values in verbose command-line flags.

## Authoritative candidate and validation

The reconciliation base is documented in [RECONCILIATION.md](RECONCILIATION.md).
There is currently **no release candidate for this source base**. All bundles
and device evidence built from `ab6cfd73` or `7d377950` are superseded: those
revisions omitted later mainline UI/gameplay changes. Their signatures and
successful tests do not make them suitable for the next release.

Only `app/build/outputs/bundle/release/app-release.aab` is the future candidate
path after a separately authorized build. Always clear stale generated output before freezing a new candidate. The
`NOT-FOR-UPLOAD` archive contains historical evidence for the old identity.
Do not select a bundle based solely on its filename.

Build one signed bundle from a frozen local source commit. Record application
ID, version, source commit, size, SHA-256 and upload-certificate fingerprints.
Use bundletool to inspect that actual bundle and derive the APK set for connected
testing, passing upload signing explicitly so it cannot fall back to a debug key.
Do not rebuild a separate APK and attribute its validation to the candidate AAB.
No old-package upgrade test is needed: this is a distinct application identity.

## Preserved external items

`privacy_policy_url` is the published policy at
`https://andyrew616.github.io/Sudoku_Zen_final/privacy.html`. Settings always
exposes a separate Privacy policy button with supporting text and external-browser
navigation, independent of conditional UMP Privacy settings. Every replacement
candidate must verify that actual browser route. When no browser is available,
the app shows a graceful error dialog.
Purple Planet's **A Touch of Zen** still needs entitlement/attribution evidence;
see `ASSET-LICENSING.md` and the artwork inventory. No asset was removed/replaced.
The existing AdMob app ID and banner/completion ad units are preserved. Their
association with the new Play listing and production UMP message configuration
must be confirmed by the owner in AdMob; no external configuration is changed
here. Ad requests remain consent gated, and exit-action ads remain removed.

The historical handoff in `archive/` is evidence only and is superseded for this
new listing. The final candidate handoff will identify the exact new artifact.
