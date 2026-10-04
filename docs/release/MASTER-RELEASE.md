# Zen Sudoku master release authority

The authoritative local source branch is **master**. It was fast-forwarded from
`87e557dc6698d638fdc10107e08356989d08a87f` to the proven reconciled source
`0d0a1596fba5c2ec5a58631efb236edf167be55a`, preserving every commit. Remote
origin/master was rechecked at `9da45ca5980720b205203f3a9f80906f7a22ed34`.
No remote branch was modified. Source reconciliation and visual provenance are
recorded in [RECONCILIATION.md](RECONCILIATION.md).

Only release-authority documentation changed after that proven source commit.
Latest mainline visual/gameplay work, exit-ad removal, pause lifecycle cleanup,
privacy route/URL, identity and release signing guards remain unchanged.
Application ID is `rew.lightgames.zensudoku`, version name 1.0/code 1. Namespace
and internal class packages remain `rew.lightgames.sudoku2` intentionally.

## Exact candidate record

Freeze a clean master commit before running a single `clean bundleRelease`.
The current-round exact source revision, artifact path/bytes/SHA-256, manifest,
signature checks and validation results are recorded in the local ignored file:
`.release-validation/master-rc/HANDOFF.md`. That record becomes authoritative
only after the candidate build and verification finish. A bundle must match its
recorded hash and embedded Git revision; its filename alone provides no authority.
The artifact path is `app/build/outputs/bundle/release/app-release.aab`.

No prior candidate is valid for this source. Bundles from `ab6cfd73` and
`7d377950` omitted later mainline visual/gameplay work and remain superseded.
Historical NOT-FOR-UPLOAD archives retain their original bytes.

## Upload signing and boundaries

The owner requested retaining the existing fresh upload key created for this new
listing on 2026-10-04. No key rotation or new credential was introduced. Public
identity: CN=Zen Sudoku Upload, RSA3072, SHA256withRSA, PKCS12 PrivateKeyEntry,
expiry 2054-02-19. Certificate SHA-256:
`38:CC:3F:3F:D5:9C:53:96:9F:E2:C1:DD:7A:DF:3F:20:F6:D7:C5:D9:21:AF:81:76:67:F0:2E:2D:DA:83:51:B9`.
SHA-1: `CB:29:15:DD:F9:68:05:21:ED:AB:B3:A6:F5:46:CB:E8:42:75:8E:A5`.

Private material remains ignored in `.local-upload-signing/` with directory0700
and files0600; credentials are never printed or committed. Detailed setup is in
[UPLOAD-SIGNING.md](UPLOAD-SIGNING.md). Preserve a secure owner-controlled backup
outside this repository; this task does not claim that backup exists.

This key authenticates uploads; it is not Google's future Play app-signing key.
Google delivery and upload registration/acceptance remain external facts to verify
in the new listing. Locally derived APKs must explicitly use this upload key and
be verified before any same-signer replacement installation; never silently use
bundletool's debug default or clear app data to bypass a signer mismatch.

The successful proportionate JVM check excludes only the five unchanged statistical
cohort classes documented in RECONCILIATION.md. No committed exclusions or sample
sizes were changed. Exact current-round results belong to the candidate record;
previous device validation does not transfer to the reconciled source.

A signed, internally validated artifact does not resolve music/artwork rights,
production UMP message configuration, Play listing/version availability or broad
platform/accessibility testing. Retained [asset evidence](ASSET-LICENSING.md) lists
remaining entitlement/provenance work. There is no push, Play upload/configuration,
publishing, deployment or CI setup in this task.
