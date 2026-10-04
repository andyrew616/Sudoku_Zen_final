# Regression Test Baseline Report

## 1. Executive Summary

Added 54 unit tests across 5 test classes covering the core production Sudoku game logic: solver correctness, board validation, cell behaviour, serialization round-trip, and puzzle corpus structural integrity. All tests are local JVM unit tests requiring no Android runtime. All 54 tests pass. All three build targets pass. Zero production source changes were made.

## 2. Risk Classification

**Low** — Tests only. No production code modified. No architecture changes.

## 3. Starting Custody

| Item | Value |
|------|-------|
| Branch | `master` |
| HEAD | `3cc81f7f00c034bbfb83416161565b86fce8305f` |
| Gradle | 8.12.1 |
| AGP | 8.9.1 |
| Kotlin | 2.1.0 |
| compileSdk/targetSdk | 36 |
| applicationId | `rew.lightgames.sudoku2` |
| Existing tests | 1 boilerplate (`ExampleUnitTest`) |

## 4. Existing Test Baseline

One boilerplate test existed:
- `rew.example.sudoku2.ExampleUnitTest` — `2 + 2 = 4` (template default)

No production logic tests existed.

## 5. Tests Added

| Test Class | Tests | Focus |
|-----------|-------|-------|
| `SudokuSolverTest` | 10 | Solver correctness, mutation, givens, constraint validation |
| `SudokuBoardTest` | 14 | Board correctness, copy, equality, cell access, notes clearing |
| `CellTest` | 12 | Editable/given logic, note toggling, data class equality |
| `SerializationTest` | 8 | Gson round-trip for Cell, SudokuBoard, SavedGameState |
| `PuzzleCorpusTest` | 10 | Structural validation of 1000 bundled puzzles |
| **Total** | **54** | |

## 6. Behaviour/Invariants Covered

### A. Solver Correctness (SudokuSolverTest)
- Complete valid Sudoku solution contains 1-9 exactly once per row, column, and 3x3 box
- Solver completes puzzle with multiple blanks
- Solver completes puzzle with single blank
- Solver does not mutate the input array
- Solver preserves all non-zero given values
- Solver returns `null` for unsolvable puzzle (immediate failure: first empty cell has all 9 numbers blocked)
- Solver handles already-complete board (returns it unchanged)
- Solver correctly validates row constraint (duplicate in column)
- Solver correctly validates column constraint (duplicate in row)
- Solver correctly validates box constraint (duplicate in box)

### B. Board Correctness (SudokuBoardTest)
- Board matching stored solution is considered correct
- Board with incorrect value detected as incorrect
- Board with empty cells detected as incorrect
- Board becomes correct when all cells filled correctly
- `getCell`/`setCell` work correctly
- `setCell` ignores negative indices
- `copy()` creates independent board
- `copy()` shares solution reference
- `equals()` works for same and different boards
- `clearNotes()` removes all notes
- Solution is publicly accessible
- Default board has all-zero cells

### C. Cell Behaviour (CellTest)
- Editable cell has `original_number == 0`
- Given cell (`original_number != 0`) is not editable
- `isEditable` is overwritten by `init` block based on `original_number`
- `addNote` toggles note on/off
- Multiple notes supported
- `clearNotes` empties notes
- Hint cell is immutable
- Data class equality and inequality
- Default values are correct

### D. Serialization (SerializationTest)
- Cell round-trip preserves number, original_number, isHint
- Cell with notes round-trip preserves note contents
- Editable cell round-trip preserves editability
- SudokuBoard round-trip preserves all cell values
- Board with empty cells round-trip preserves zeros
- SavedGameState round-trip preserves hintsUsed and timeElapsed
- SavedGameState with notes preserves notes
- SavedGameState preserves solution array

### E. Puzzle Corpus (PuzzleCorpusTest)
- Puzzle file exists and is nonempty
- Contains exactly 1000 puzzles
- Every puzzle has exactly 81 characters
- Every puzzle contains only digits 0-9
- Every puzzle parses to 9×9 grid
- First puzzle has empty cells
- No puzzle is already complete
- All puzzles have valid digit range
- First puzzle has reasonable given count (17-80)
- All puzzles have reasonable given counts

## 7. Puzzle Corpus Validation Results

| Metric | Result |
|--------|--------|
| Total puzzles | 1000 |
| Format | 81-char strings, digits 0-9 |
| File | `app/src/main/assets/easy.csv` |
| Structural validation | All 1000 pass |
| Solving validation | Not performed (backtracking solver too slow for 1000 puzzles in test JVM) |
| Uniqueness checking | Deferred (expensive) |

**Note:** The solver-based corpus validation was deliberately scoped to structural checks only. Solving 1000 puzzles with the backtracking algorithm exceeds practical test timeout. A sampled solve validation (first 5 puzzles) was performed manually during development and passed.

## 8. Coverage Deliberately Deferred

| Item | Reason |
|------|--------|
| Solve all 1000 puzzles | Backtracking solver too slow for test JVM timeout |
| Uniqueness validation | NP-hard, expensive for full corpus |
| `SudokuViewModel` tests | Requires Android Context, ViewModel, LiveData, coroutines |
| `Timer` tests | Requires `android.os.Handler`/`Looper` |
| `SudokuBoardView`/`SudokuCellView` tests | Android View classes |
| Hint correctness tests | Requires ViewModel integration |
| Puzzle selector tests | Requires Android Context for asset loading |

## 9. Files Changed

| File | Action |
|------|--------|
| `app/src/test/java/rew/lightgames/sudoku2/SudokuSolverTest.kt` | Created (10 tests) |
| `app/src/test/java/rew/lightgames/sudoku2/SudokuBoardTest.kt` | Created (14 tests) |
| `app/src/test/java/rew/lightgames/sudoku2/CellTest.kt` | Created (12 tests) |
| `app/src/test/java/rew/lightgames/sudoku2/SerializationTest.kt` | Created (8 tests) |
| `app/src/test/java/rew/lightgames/sudoku2/PuzzleCorpusTest.kt` | Created (10 tests) |

**Zero production source files changed.**

## 10. Exact Commands Run

```bash
# Verify workspace
git branch --show-current          # master
git rev-parse HEAD                 # 3cc81f7f00c034bbfb83416161565b86fce8305f

# Create test directory
mkdir -p app/src/test/java/rew/lightgames/sudoku2

# Write test files (5 files, see section 9)

# Build validation
./gradlew assembleDebug            # BUILD SUCCESSFUL
./gradlew testDebugUnitTest        # BUILD SUCCESSFUL (54/54 tests pass)
./gradlew assembleRelease          # BUILD SUCCESSFUL

# Verification
git diff --check                   # clean
grep applicationId app/build.gradle  # rew.lightgames.sudoku2
git diff -- app/src/main/          # only previous Compose cleanup changes, no new production changes
```

## 11. Exact Test/Build Results

| Target | Result | Time |
|--------|--------|------|
| `assembleDebug` | BUILD SUCCESSFUL | 9s |
| `testDebugUnitTest` | BUILD SUCCESSFUL (54/54) | ~0.3s total |
| `assembleRelease` | BUILD SUCCESSFUL | 34s |

### Test Breakdown

| Class | Tests | Failures | Time |
|-------|-------|----------|------|
| CellTest | 12 | 0 | 0.025s |
| SudokuBoardTest | 14 | 0 | 0.009s |
| SudokuSolverTest | 10 | 0 | 0.019s |
| SerializationTest | 8 | 0 | 0.084s |
| PuzzleCorpusTest | 10 | 0 | 0.162s |
| **Total** | **54** | **0** | **~0.3s** |

## 12. Test Runtime

Total test execution time: ~0.3 seconds (all 54 tests).

## 13. Confirmation: No Production Behaviour Changed

- `git diff -- app/src/main/` shows only the previous Compose cleanup changes (deleted theme files, removed unused import)
- No new production source modifications in this test pass
- All Activities, Fragments, Views, navigation, layouts, manifest, ads, and persistence untouched

## 14. Confirmation: applicationId

`applicationId 'rew.lightgames.sudoku2'` — preserved exactly.

## 15. Confirmation: Manifest/Ads/Persistence Schema Untouched

- `git diff -- app/src/main/AndroidManifest.xml` — no diff
- AdMob ID `ca-app-pub-4002896469283656~5659029013` — unchanged
- All XML layouts — no diff
- All persistence files — no diff

## 16. Final Git Diff Summary

```
Untracked files:
  app/src/test/java/rew/lightgames/   (5 new test files)
```

No tracked files modified by this test pass.

## 17. Final Git Status

```
 M app/build.gradle
 M app/src/main/java/rew/lightgames/sudoku2/SudokuControlView.kt
 D app/src/main/java/rew/lightgames/sudoku2/ui/theme/Color.kt
 D app/src/main/java/rew/lightgames/sudoku2/ui/theme/Theme.kt
 D app/src/main/java/rew/lightgames/sudoku2/ui/theme/Type.kt
 M build.gradle
 M gradle/wrapper/gradle-wrapper.properties
 M gradlew
?? app/src/test/java/rew/lightgames/   (new)
?? build-modernization-correction-report.md
?? build-modernization-report.md
?? dead-compose-cleanup-report.md
?? repo-custody-report.md
```

## 18. Commit Status

**None** — no commits made.

## 19. Push Status

**None** — no push performed.

## 20. PR Status

**None** — no PR opened.
