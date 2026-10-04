# Build Modernization Correction Report

## 1. Toolchain Before (start of this corrective pass)

| Component | Value |
|-----------|-------|
| Gradle | 8.12.1 |
| AGP | 8.8.2 |
| Kotlin | 2.1.0 |
| compileSdk | 36 (suppressed) |
| targetSdk | 36 |
| `suppressUnsupportedCompileSdk` | present |

## 2. Toolchain After

| Component | Value |
|-----------|-------|
| Gradle | 8.12.1 (unchanged) |
| AGP | **8.9.1** |
| Kotlin | 2.1.0 (unchanged) |
| compileSdk | 36 (no suppression needed) |
| targetSdk | 36 (unchanged) |
| `suppressUnsupportedCompileSdk` | **removed** |

## 3. Official Compatibility Evidence

From [developer.android.com/build/releases/about-agp](https://developer.android.com/build/releases/about-agp):

| API level | Minimum AGP version |
|-----------|---------------------|
| 36 | **8.9.1** |
| 35 | 8.6.0 |
| 34 | 8.1.1 |

AGP/Gradle minimum version matrix:

| AGP | Minimum Gradle |
|-----|----------------|
| 8.9 | 8.11.1 |
| 8.8 | 8.10.2 |

Our Gradle 8.12.1 satisfies the 8.11.1+ requirement for AGP 8.9.1. No Gradle wrapper change was needed.

## 4. Files Changed (this corrective pass only)

| File | Change |
|------|--------|
| `build.gradle` | AGP 8.8.2 → 8.9.1 |
| `gradle.properties` | Removed `android.suppressUnsupportedCompileSdk=36` (restored to HEAD state) |

No other files changed.

## 5. Commands Run and Results

| Command | Result |
|---------|--------|
| `./gradlew assembleDebug` | BUILD SUCCESSFUL (1m 29s) — no compileSdk warning |
| `./gradlew test` | BUILD SUCCESSFUL (26s) — all tests pass |
| `./gradlew assembleRelease` | BUILD SUCCESSFUL (1m 23s) |
| `git diff --check` | clean |

## 6. Remaining Warnings

| Warning | Source | Severity |
|---------|--------|----------|
| `overrides a deprecated member` | BorderDrawable.kt:38 | Low — pre-existing |
| `Unchecked cast` | SudokuViewModelFactory.kt:11 | Low — pre-existing |
| `statusBarColor is deprecated` | Theme.kt:60 | Low — pre-existing |
| Gradle 9.0 deprecation warnings | Build output | Low — future Gradle upgrade |
| `Unable to strip libandroidx.graphics.path.so` | Build output | Info — packaging fallback |

## 7. Final Git Diff Summary

```
 app/build.gradle | 20 ++----------------
 build.gradle     |  7 ++++---
 gradlew          |  0
 3 files changed, 6 insertions(+), 18 deletions(-)
```

Note: `gradle.properties` has zero diff from HEAD — the suppression was added in the prior pass and removed in this pass, netting to no change.

## 8. Final Git Status

```
 M app/build.gradle
 M build.gradle
 M gradlew
?? build-modernization-report.md
?? repo-custody-report.md
```

## 9. Confirmations

- [x] applicationId remains `rew.lightgames.sudoku2`
- [x] compileSdk = 36, targetSdk = 36
- [x] versionCode 3, versionName "1.1" unchanged
- [x] Zero files in `app/src/` modified
- [x] AndroidManifest.xml untouched — AdMob ID `ca-app-pub-4002896469283656~5659029013` preserved
- [x] Persistence files untouched
- [x] No source compatibility fixes needed

## 10. Compose Usage Finding

Compose is configured (`compose true` in build.gradle) and Compose dependencies are present. However, **Compose is effectively unused in production**:

- `Sudoku2Theme` composable in `ui/theme/Theme.kt` is defined but **never called** by any Activity or Fragment
- The app uses XML theme `Theme.MyApplication` (based on `Theme.AppCompat.Light.NoActionBar`)
- `SudokuControlView.kt` imports `@Preview` but **never applies it**
- All `ui/theme/` files (`Theme.kt`, `Color.kt`, `Type.kt`) are dead code

**Recommendation:** Remove Compose plugin, BOM, and `ui/theme/` files in a separate cleanup pass. This is safe but falls outside the bounded scope of this corrective task.

## 11. Commit Status

**None** — no commits made.

## 12. Push Status

**None** — no push performed.

## 13. PR Status

**None** — no PR opened.
