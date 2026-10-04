# Final Report: Repository Custody

## 1. Untouched dirty clone
`/home/andyrew66/StudioProjects/zensudoku`
Not modified, reset, stashed, or altered in any way.

## 2. New clean clone
`/home/andyrew66/StudioProjects/zensudoku-clean`

## 3. Production master HEAD
`3cc81f7f00c034bbfb83416161565b86fce8305f` — "first version"
Branch: `master` | Clean working tree

## 4. Legacy branch HEAD
`de1a8babf1179f678b3249ccccf96ecea4e9ade0` — "test"
Branch: `legacy/zen-sudoku`

## 5. Remote configuration
| Remote | URL |
|--------|-----|
| origin | `https://github.com/andyrew66/Sudoku_Zen_final.git` |
| legacy | `https://github.com/andyrew66/Zen_Sudoku.git` |

## 6. Merge-base / ancestry result
**The two histories are completely unrelated — no common ancestor.**

Production master has 2 commits with an independent root:
```
3cc81f7 first version
└── 2dd12c4 first version (root commit, no parent)
```

Legacy branch has 7 commits with its own independent root:
```
de1a8ba test
└── 619f71f Rename .java to .kt
    └── 1e9d93f updated ads
        └── d3e297a updated ads
            └── 98c12b1 updated levelSelect
                └── d62096f updated levelSelect
                    └── d94cff2 Initial commit (root commit, no parent)
```

- `merge-base`: **none** (empty result)
- `git merge-base --is-ancestor legacy/zen-sudoku master`: **false**
- `git merge-base --is-ancestor master legacy/zen-sudoku`: **false**
- Commits unique to master: `3cc81f7`, `2dd12c4`
- Commits unique to legacy: `de1a8ba`, `619f71f`, `1e9d93f`, `d3e297a`, `98c12b1`, `d62096f`, `d94cff2`
- **The production rewrite was NOT committed on top of the legacy ancestry.** It is a completely independent fresh-start repository.

## 7. Donor puzzle file paths (on legacy/zen-sudoku)
| File | Path |
|------|------|
| Easy.txt | `app/src/main/assets/Easy.txt` |
| Medium.txt | `app/src/main/assets/Medium.txt` |
| Hard.txt | `app/src/main/assets/Hard.txt` |
| Test.txt | `app/src/main/assets/Test.txt` |

## 8. Donor game-logic file paths (on legacy/zen-sudoku)
| Asset | Path |
|-------|------|
| Board | `app/src/main/java/com/example/zensudoku/game/Board.kt` |
| Cell | `app/src/main/java/com/example/zensudoku/game/Cell.kt` |
| PuzzleSelector | `app/src/main/java/com/example/zensudoku/game/PuzzleSelector.kt` |
| SudokuSolver | `app/src/main/java/com/example/zensudoku/game/SudokuSolver.kt` |
| SudokuGame | `app/src/main/java/com/example/zensudoku/game/SudokuGame.kt` |

## 9. Final branch checked out
`master`

## 10. Final git status
Clean — no modified, staged, or untracked files.

## 11. Confirmation: no application files modified
- Production master `applicationId`: `rew.lightgames.sudoku2`
- No `com.example.zensudoku` references in production build.gradle
- Working tree matches committed HEAD exactly

## 12. Push status
**None** — no commits were pushed to any remote.

## 13. Merge status
**None** — no merge was performed. The two branches remain independent.
