# Android 15/16 Runtime Hardening Report — Zen Sudoku

## 1. Executive Summary
Successfully hardened Zen Sudoku for Android 15/16 runtime behavior. Applied 4 verified categories of fixes: edge-to-edge compliance, foreground service type declaration, handler leak prevention, and accessibility improvements. All 58 unit tests pass, both debug and release builds succeed, and the app was validated on a physical Samsung Galaxy S24 Ultra running Android 16 (API 36).

## 2. Risk Classification
**LOW** - All changes are minimal, targeted compatibility fixes. No gameplay, ad, or architecture changes.

## 3. Starting Branch / HEAD
- Branch: `master`
- Starting HEAD: `bf757cd`

## 4. Device Identity / Android API Level
- **Manufacturer:** Samsung
- **Model:** SM-S918B (Galaxy S24 Ultra)
- **Android version:** 16
- **API level:** 36
- **Connection:** Wireless ADB (192.168.50.181:38495)

## 5. Runtime/Lifecycle Map
- **SplashScreen:** Custom splash with `Handler.postDelayed` → `ConsentManager.initialize` → `MobileAds.initialize` → navigate to MenuHostActivity
- **MenuHostActivity:** Hosts NavHostFragment with FirstFragment (main menu), loads banner ad
- **FirstFragment:** Start/Resume/Options buttons, uses Navigation component
- **LevelSelect:** Difficulty selection (Easy/Medium/Hard), starts MainActivity
- **MainActivity:** Game board, timer, controls, pause/completion dialogs, interstitial ads
- **OptionsActivity:** Music/SFX toggles, privacy options row
- **SudokuApplication:** LifecycleObserver starts/stops MusicService on app foreground/background
- **MusicService:** Foreground service with MediaPlayer looping background music
- **Timer:** Handler-based timer with 1-second tick, tied to MainActivity

## 6. Android 15/16 Requirements Verified
1. **Edge-to-edge** (targetSdk 35+): Content draws behind system bars. `windowOptOutEdgeToEdgeEnforcement` disabled on Android 16.
2. **Foreground service type** (targetSdk 34+): All foreground services must declare `foregroundServiceType` in manifest.
3. **Predictive back** (targetSdk 36): System back animations enabled. `onBackPressed()` no longer called.
4. **Orientation on large screens** (targetSdk 36): Portrait lock ignored on >=600dp displays.
5. **`setStatusBarColor`/`setNavigationBarColor`** deprecated in API 35.

## 7. Edge-to-Edge/System-Bar Findings
- **Finding:** All 4 activities had no edge-to-edge handling. Content would draw behind system bars on Android 15+.
- **Resolution:** Added `enableEdgeToEdge()` and `ViewCompat.setOnApplyWindowInsetsListener` with system bar padding to all activities (SplashScreen, MenuHostActivity, MainActivity, OptionsActivity).

## 8. MusicService/Foreground-Service Findings
- **Finding 1:** Missing `android:foregroundServiceType="mediaPlayback"` in manifest (required targetSdk 34+).
- **Finding 2:** Missing `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission.
- **Finding 3:** `startForeground()` not using type parameter (required Android 14+).
- **Finding 4:** `MediaPlayer` not released in `onDestroy()`.
- **Resolution:** Added permission, foregroundServiceType, version-guarded `startForeground()` with `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`, added `mediaPlayer?.release()`.

## 9. Activity/Fragment Lifecycle Findings
- **Finding:** `Timer` handler callbacks not removed on `pause()`, causing potential leak of `TimerListener` (Activity reference) after destruction.
- **Resolution:** Added `handler.removeCallbacks(runnable)` to `Timer.pause()` and `timer.destroy()` in `MainActivity.onDestroy()`.

## 10. Back-Navigation Findings
- **Status:** Already correct. `MainActivity` uses `OnBackPressedDispatcher` with `OnBackPressedCallback`. `OptionsActivity` uses `onBackPressedDispatcher.onBackPressed()`. Navigation component handles fragment back stack. Compatible with predictive back on Android 16.

## 11. Orientation/Configuration Findings
- **Status:** Portrait lock via `android:screenOrientation="portrait"` in manifest. On Android 16, ignored on >=600dp displays (tablets/foldables). Phone use unaffected. No action needed.

## 12. Accessibility Findings
- **Finding 1:** `pause_menu.xml` options button had contentDescription "Options Butotn" (typo).
- **Finding 2:** `pause_menu.xml` resume button had no contentDescription.
- **Resolution:** Fixed typo to use `@string/options_bttn`. Added `contentDescription="@string/resume_game"` to resume button. Added string resources.

## 13. Files Changed
1. `app/src/main/AndroidManifest.xml` - Added FOREGROUND_SERVICE_MEDIA_PLAYBACK permission, foregroundServiceType
2. `app/src/main/java/rew/lightgames/sudoku2/MainActivity.kt` - enableEdgeToEdge, WindowInsets, timer.destroy()
3. `app/src/main/java/rew/lightgames/sudoku2/MenuHostActivity.kt` - enableEdgeToEdge, WindowInsets
4. `app/src/main/java/rew/lightgames/sudoku2/MusicService.kt` - Version-guarded startForeground, MediaPlayer release
5. `app/src/main/java/rew/lightgames/sudoku2/OptionsActivity.kt` - enableEdgeToEdge, WindowInsets
6. `app/src/main/java/rew/lightgames/sudoku2/SplashScreen.kt` - enableEdgeToEdge
7. `app/src/main/java/rew/lightgames/sudoku2/Timer.kt` - handler.removeCallbacks, destroy()
8. `app/src/main/res/layout/pause_menu.xml` - Fixed contentDescriptions
9. `app/src/main/res/values/strings.xml` - Added resume_game, options strings

## 14. Tests Added/Changed
- No new tests added. Existing 58 unit tests all pass. No tests broken.

## 15. Exact Commands Run
```
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
git diff --check
adb -s 192.168.50.181:38495 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.50.181:38495 shell pm path rew.lightgames.sudoku2
adb -s 192.168.50.181:38495 logcat -c
adb -s 192.168.50.181:38495 shell am start -n rew.lightgames.sudoku2/.SplashScreen
adb -s 192.168.50.181:38495 exec-out screencap -p > /tmp/zensudoku_*.png
adb -s 192.168.50.181:38495 logcat -d -s AndroidRuntime:E
```

## 16. Unit Test Result
**BUILD SUCCESSFUL** - 58/58 tests pass

## 17. Debug Build Result
**BUILD SUCCESSFUL**

## 18. Release Build Result
**BUILD SUCCESSFUL**

## 19. APK Installation Result
**Success** - Package installed at `/data/app/~~ayYdHxSBllDmTjKs0_DgLA==/rew.lightgames.sudoku2-rTt6Brz4I8FxRrSWOMaKXg==/base.apk`

## 20. Physical-Device Smoke-Test Results
| Check | Result |
|-------|--------|
| A. Startup/launch | PASS - App launches, splash screen renders, UMP flow completes, main menu reached |
| B. Main menu | PASS - All buttons visible, system bars don't obscure controls, banner area clear |
| C. Start game | PASS - Difficulty selection works, board renders correctly, no cell/control overlap |
| D. Game interaction | PASS - Cell selection works, hints applied, timer running, pause/resume functional |
| E. Options | PASS - Back button, Settings title, Music/SFX toggles visible and tappable |
| F. Background/foreground | PASS - Game state preserved, timer saved, no duplicate initialization |
| G. Music | PASS - No foreground-service crash or exception |
| H. Back navigation | PASS - Back from Options returns to pause menu, no duplicate activities |
| I. Completion path | Deferred (would require solving puzzle) |
| J. Accessibility/visual | PASS - No clipped text, no controls obscured by system bars |

## 21. Screenshot/Visual Evidence Summary
- Main menu: Status bar and navigation bar clear, buttons properly positioned
- Difficulty selection: EASY/MEDIUM/HARD buttons visible, PREVIOUS button at bottom
- Game screen: Timer (00:11), mode text, hints text, pause button all visible above system bars. Board renders correctly. Number controls and special buttons visible.
- Cell selection: Orange highlight on selected cell, green hint cells visible
- Pause menu: RESUME, OPTIONS, EXIT buttons centered and unobscured
- Options: Back button, Settings title, Music/SFX toggles all visible with proper insets

## 22. Device Log Findings
- **No FATAL EXCEPTION** found
- **No ANR** found
- **No SecurityException** found
- **No foreground service exceptions** found
- All logs are system-level noise (Samsung GameManager, PkgPredictorService, etc.)

## 23. Independent Review Findings/Resolutions
1. **Content obscured by system bars** - RESOLVED: `enableEdgeToEdge` + WindowInsets on all activities
2. **Incorrect WindowInsets consumption** - NOT FOUND: Insets correctly applied and consumed
3. **Double-applied padding** - NOT FOUND: Each activity applies insets once
4. **Deprecated system-bar APIs** - NOT USED: App never set statusBarColor/navigationBarColor
5. **Foreground-service policy violations** - RESOLVED: Added type, permission, version-guarded startForeground
6. **Leaked Handlers/Runnables** - RESOLVED: Timer.removeCallbacks in pause(), destroy() in onDestroy
7. **Broken back navigation** - NOT FOUND: OnBackPressedDispatcher already in use
8. **Lost save/timer state** - NOT FOUND: State preserved across background/foreground
9. **Unnecessary permissions** - NOT FOUND: Only FOREGROUND_SERVICE_MEDIA_PLAYBACK added (required)
10. **Gameplay behaviour changes** - NOT FOUND: No gameplay changes
11. **Ad/UMP regressions** - NOT FOUND: Ad loading and UMP unchanged
12. **Accessibility regressions** - IMPROVED: Fixed typo, added missing contentDescriptions

## 24. Confirmation applicationId Unchanged
**rew.lightgames.sudoku2** - Verified in `app/build.gradle` line 11

## 25. Confirmation versionCode/versionName Unchanged
- **versionCode:** 3
- **versionName:** "1.1"

## 26. Confirmation persistence/game logic Unchanged
- SharedPreferences key `"saved_game"` unchanged
- `SavedGameState` data class unchanged
- `SudokuBoard`/`Cell` model unchanged
- `SudokuGenerator` puzzle loading unchanged

## 27. Confirmation AdMob IDs/triggers Unchanged
- Application ID: `ca-app-pub-4002896469283656~5659029013`
- Banner (menu): `ca-app-pub-4002896469283656/3474640062`
- Banner (game): `ca-app-pub-4002896469283656/9350655411`
- Interstitial (completion): `ca-app-pub-4002896469283656/4701071767`
- Interstitial (exit): `ca-app-pub-4002896469283656/2976818750`

## 28. Confirmation UMP Behaviour Unchanged
- `ConsentManager.kt` unchanged
- UMP initialization in SplashScreen unchanged
- Privacy options form in OptionsActivity unchanged

## 29. Commit SHA/Message
- **SHA:** `6cf1655`
- **Message:** `Harden Android 15 and 16 runtime behaviour`

## 30. Final Git Status
```
On branch master
Your branch is ahead of 'origin/master' by 5 commits.
```

## 31. Push Status
**None** - No push performed.

## 32. PR Status
**None** - No PR created.

## 33. Deferred Runtime/UI Opportunities
- **Tablet/large-screen layout adaptation** - Android 16 ignores orientation lock on >=600dp. Phone use unaffected. Deferred for dedicated responsive design pass.
- **Splash Screen API migration** - Could use Android 12+ SplashScreen API instead of custom Handler delay. Deferred as non-critical.
- **ViewPager2/Compose migration** - Out of scope per instructions.
- **ViewModel SavedStateHandle** - Could improve state restoration beyond current SharedPreferences approach. Deferred.
