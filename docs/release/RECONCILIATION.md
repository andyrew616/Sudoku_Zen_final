# Zen Sudoku source reconciliation — 2026-10-04

This is the current source handoff. It supersedes artifact-readiness claims in
archived handoffs. No release artifact was generated from this base, and no
existing artifact is an upload candidate for it.

## Repository truth and visual provenance

Authoritative mainline: `origin/master`, remotely verified and fetched at
`9da45ca5980720b205203f3a9f80906f7a22ed34` (Add Zen Sudoku privacy policy).
Origin HEAD explicitly points to master; there is no origin/main. The untouched
local master is `87e557dc6698d638fdc10107e08356989d08a87f`, an
obsolete local pointer. Cached origin/master initially pointed to `2e4e3d9`;
relying on that cache would have missed the later approved work.

`codex/menu-grid-visual-refresh` and its remote branch remain at
`2a9a3746bc9016d90ab8940dfdb07ea5c38b3cd0`. This branch does contain the August
menu/difficulty refresh. It was merged into master in PRs #4 (`7afc04d`) and #5
(`3334a3a`), so it is now an ancestor, **24 commits behind and zero ahead** of
current mainline. It is not a current release base despite its name.

Later approved mainline visuals are the app-wide polish `2dfe7c8`, fullscreen
`d29b571`, spacing/hint layout `87908ed`, and latest fixed-gameplay hint positioning
`dcba3bd55965c8c1ea828d99b47f859b80274111`. They were merged via PR #6 (`6c9c877`)
and PR #7 (`2151879`) and are present in current master. These implement the
newer gameplay, Settings, dialogs, theme and accessibility behavior. Git merge
history is the approval evidence; no separate design approval record was found.

Actual initial checkout was `codex/zensudoku-first-release` at
`7d3779508505efee32cecef6af63eec708fa1ff6`, not the branch stated in the older
handoff. Its only two commits outside mainline were `ab6cfd73` and `7d377950`;
it diverged at `2a9a374` (24 mainline-only commits, two release-only commits).
Those commits contain the recent release changes. No equivalent release-fix
commits were already present on master; the public policy HTML itself was
already added to mainline by `9da45ca` and was not reapplied.

Original six local branches (their pointers were not altered):

```text
codex/menu-grid-visual-refresh 2a9a3746bc9016d90ab8940dfdb07ea5c38b3cd0
codex/visible-conflict-validation 2e4e3d9e6429e4f9feb2361a76932aa628e0e98c
codex/zen-gameplay-polish e2f8858cddd5fb810c55af2fc2e32ac96e3b497c
codex/zensudoku-first-release 7d3779508505efee32cecef6af63eec708fa1ff6
legacy/zen-sudoku de1a8babf1179f678b3249ccccf96ecea4e9ade0
master 87e557dc6698d638fdc10107e08356989d08a87f
```

## Installed-build provenance

A read-only pull of the installed base APK on SM-S918B found embedded Git revision
`7d3779508505efee32cecef6af63eec708fa1ff6`, package `rew.lightgames.zensudoku`,
version 1.0/code 1, target 36, and no DEBUGGABLE flag. SHA-256:
`825ead664432bd319c442d80c69796802ed989ba2d1eb7baafbeb6220c8fd49a`.
It exactly matches `.release-validation/privacy-rc/device.apks` member
`splits/base-master.apk`. Thus the installed old-looking build is conclusively
the stale menu base plus release fixes, not the latest mainline visual state.
No app installation, launch, input or data mutation was performed in this task.

## Uncommitted work and reconciliation

Initially there were no tracked modifications or staged changes. Nine untracked
Markdown files were present: eight historical reports (AdMob/UMP, Android
hardening, build modernization and correction, dead Compose cleanup, regression
baseline, repository custody, generator) and the prior release HANDOFF.md.
The eight reports are preserved verbatim under
`archive/pre-reconciliation-reports/`; the handoff is preserved verbatim as
`archive/2026-10-04-7d377950-handoff.md`. They contain historical/sometimes obsolete
claims and no unapplied production code. Private signing/configuration, Gradle
caches, validation evidence and generated outputs were already ignored.

Fresh local branch: `codex/reconciled-release-base`, created from current
origin/master. Original branches/history are preserved, local master unchanged,
and the reconciliation branch has no upstream to reduce accidental pushes.
Recovered commits with `git cherry-pick -x`:

- `8d64b65`: recovery of `ab6cfd73`, release identity/signing safeguards,
  exit-ad removal, pause dismissal, privacy route, tests, licence evidence,
  artifact ignores and byte-identical historical-bundle archive.
- `58685f9`: recovery of `7d377950`, published privacy URL, accessible native
  policy button/supporting text, assertions and documentation.

Conflicts were resolved by retaining current mainline's async initialization,
readiness-gated timer, recreation handling, modern Settings/auto-notes and all
newer strings. The policy button uses existing mainline styling. Save/return
still runs saveGame(), dialog.dismiss(), exit(); the exit placement/load/field
are absent. Banner and completion ads and their consent gates are retained.
Mainline's additional pause/completion lifecycle cleanup is also retained.

Already committed intentional identity changes are preserved exactly once:
applicationId `rew.lightgames.zensudoku`, versionName 1.0/versionCode 1; internal
namespace/class packages intentionally remain `rew.lightgames.sudoku2`. No further
package migration or data migration was introduced. The privacy URL is populated
in the existing follow-up commit and is preserved rather than reverted to blank.

The recovered device regression was adapted to supply Easy difficulty and await
async readiness, restoring saved_game and saved_game_difficulty afterward.
No stale source from the old baseline was brought in, no duplicate menu refresh
was applied, no unrelated feature branches were merged, and no diagnostics,
generated output or private signing material was committed. The two original
historical bundles match mainline bytes exactly and all 74 artwork ledger hashes
still match. The ledger is scoped historical evidence, not a licence clearance
for all newer or existing assets.

The previously generated stale bundle (SHA-256
`6218d2778fdaa3ecc717ba3e85b8e9d4a97775386dfcfb37a8e260404b9c102b`) was moved
unchanged from app/build/outputs/bundle/release/app-release.aab to ignored
`.release-validation/reconciliation/superseded-7d377950-app-release.aab`.
Existing APK sets and logs remain ignored historical evidence. No replacement
bundle was built. UPLOAD-SIGNING.md explicitly marks prior candidates superseded.

## Validation and independent review

Commands and outcomes:

- Initial sandboxed Gradle invocation could not acquire the read-only wrapper
  cache lock. Retried with authorized filesystem access; this is not a test failure.
- Unrestricted `./gradlew testReleaseUnitTest lintRelease
  compileDebugAndroidTestKotlin compileReleaseSources --offline` compiled production
  and instrumentation sources, then was intentionally terminated (exit 143) in the
  unchanged 3,000-request generator correctness cohort. That class also includes
  a 15,000-request acceptance cohort. No unrestricted-suite pass is claimed.
- Proportionate rerun uses the same tasks with
  `--init-script /tmp/zensudoku-reconciliation-tests.init.gradle`, which excludes
  only `**/*CohortTest.class`: DifficultyTargetGeneratorCohortTest,
  GameplayPuzzleIntegrationCohortTest, LogicalHintGeneratedCohortTest,
  SudokuDifficultyGraderCohortTest and SudokuLogicalSolverCohortTest (five classes,
  six methods). Their underlying production implementations are unchanged from
  mainline. No committed test exclusions or reduced sample sizes were introduced.
  Exit 0, BUILD SUCCESSFUL in 1m25s; 59 tasks (4 executed, 55 up-to-date).
  Unit tests: 348; failures=0; errors=0; skipped=0; suites=27. Lint: Warning=2852.
  Release sources/resources and final instrumentation sources compiled successfully.
  Lint warnings are retained, not a zero-warning or security-clearance claim.
- `./gradlew compileDebugAndroidTestKotlin --offline` after the readiness adaptation:
  exit 0, BUILD SUCCESSFUL in 13s, 27 tasks (1 executed, 26 up-to-date). No app
  installed or device test run.
- In `/tmp/zensudoku-signing-guard-check`, an isolated git archive of `58685f9`
  with no private signing directory: `./gradlew :app:verifyUploadSigning --offline`
  exits 1, BUILD FAILED in 30s, exactly one task executed, with explicit missing
  upload-configuration/debug-never-used error. This expected failure exercises the
  guard; no signing or packaging tasks ran.
- `git diff --check`: exit 0. Historical AABs byte-equal mainline; 74 artwork
  inventory checksums match; installed APK revision/hash matches previous candidate.
  Private signing configuration remains ignored; no key material is tracked.

Full/scoped logs and guard/device-compilation evidence are preserved under ignored
`.release-validation/reconciliation/`.

One inherited-model read-only reviewer `/root/release_base_review` independently
verified branch ancestry, latest visuals, final production diff, preserved
release fixes, absence of stale implementation, signing configuration and installed
APK provenance. It found no must-fix source reconciliation issue. The lead checks
actual validation output before recording success; archived test/device results
are not used to validate this new source base.

## Remaining release work and boundaries

- Validate the reconciled UI, Save/return/resume, privacy browser/fallback,
  completion ads and lifecycle on connected devices after a separately authorized
  build. The device test is compiled, not executed here; old release device results
  do not transfer to the newer source. Broad platform/accessibility/UMP regional
  checks remain outstanding.
- Application identity migration is already implemented; confirm listing/version
  availability and new-identity consequences next round. Old-package data does
  not automatically migrate. No new migration is required by this reconciliation.
- Existing local signing setup is retained, ignored and unchanged. Next release
  round must verify upload-key registration and separate Play app-signing identity,
  freeze source, and generate/validate a new exact artifact under authorization.
- Privacy URL remains the committed public URL; revalidate live content and the
  actual in-app browser route next round. No hosting or policy publication changed.
- Purple Planet music entitlement/credit, artwork and corpus provenance remain
  unresolved. Retained licence evidence is not publication approval.
- AdMob association and observed UMP publisher-message misconfiguration require
  external owner work and device validation. No Console/AdMob changes occurred.

No remote branch was changed: only ls-remote and fetch were performed. No push,
force-push, shared-history rewrite, PR merge, signing operation, new AAB/APK
packaging, upload, publishing, Play work or CI setup occurred. Primary agent is
GPT-6-based Codex as identified by the environment; exact runtime model ID is not
exposed. One inherited-model reviewer was used without a model override.
