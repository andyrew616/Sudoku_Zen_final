package rew.lightgames.sudoku2

import java.util.Collections

enum class LogicalSolveStatus {
    SOLVED,
    STALLED,
    INVALID
}

typealias CandidateSnapshot = CandidateGridSnapshot

/** Immutable result of applying only the supported human-logical techniques. */
class LogicalSolveResult(
    val status: LogicalSolveStatus,
    finalBoard: IntArray,
    val remainingCandidates: CandidateSnapshot,
    steps: Collection<LogicalStep>
) {
    private val finalBoardState: IntArray = finalBoard.clone()

    val finalBoard: IntArray
        get() = finalBoardState.clone()

    val steps: List<LogicalStep> = Collections.unmodifiableList(ArrayList(steps))

    override fun equals(other: Any?): Boolean =
        other is LogicalSolveResult &&
            status == other.status &&
            finalBoardState.contentEquals(other.finalBoardState) &&
            remainingCandidates == other.remainingCandidates &&
            steps == other.steps

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + finalBoardState.contentHashCode()
        result = 31 * result + remainingCandidates.hashCode()
        result = 31 * result + steps.hashCode()
        return result
    }

    override fun toString(): String =
        "LogicalSolveResult(status=$status, finalBoard=${finalBoardState.contentToString()}, " +
            "remainingCandidates=$remainingCandidates, steps=$steps)"
}

/** Deterministic human-logical solver. It never guesses, branches, or backtracks. */
class SudokuLogicalSolver {
    fun nextStep(grid: CandidateGrid): LogicalStep? =
        findNakedSingle(grid)
            ?: findHiddenSingle(grid)
            ?: findPointing(grid)
            ?: findClaiming(grid)
            ?: findNakedSubset(grid, 2, SudokuTechnique.NAKED_PAIR)
            ?: findHiddenSubset(grid, 2, SudokuTechnique.HIDDEN_PAIR)
            ?: findNakedSubset(grid, 3, SudokuTechnique.NAKED_TRIPLE)
            ?: findHiddenSubset(grid, 3, SudokuTechnique.HIDDEN_TRIPLE)
            ?: findXWing(grid)
            ?: findXYWing(grid)
            ?: findSkyscraper(grid)

    fun solve(board: IntArray): LogicalSolveResult {
        val creation = CandidateGrid.create(board)
        if (creation is CandidateGridCreationResult.Failure) {
            return invalidResult(board)
        }

        val grid = (creation as CandidateGridCreationResult.Success).grid
        val steps = ArrayList<LogicalStep>()
        val maximumSteps = progress(grid)

        // Progress is finite (at most 81 empty cells + 729 candidates) and strictly decreases.
        repeat(maximumSteps) {
            if (isSolved(grid)) return result(LogicalSolveStatus.SOLVED, grid, steps)

            val step = nextStep(grid)
                ?: return result(LogicalSolveStatus.STALLED, grid, steps)
            val progressBefore = progress(grid)
            if (grid.applyActions(step.actions) != CandidateGridMutationResult.Success) {
                // A detected logical step that contradicts the grid proves the state invalid.
                return result(LogicalSolveStatus.INVALID, grid, steps)
            }
            if (progress(grid) >= progressBefore) {
                return result(LogicalSolveStatus.INVALID, grid, steps)
            }
            steps.add(step)
        }

        return if (isSolved(grid)) {
            result(LogicalSolveStatus.SOLVED, grid, steps)
        } else {
            // Exhausting the initial finite progress bound indicates an internal invariant failure.
            result(LogicalSolveStatus.INVALID, grid, steps)
        }
    }

    internal fun findNakedSingle(grid: CandidateGrid): LogicalStep? {
        for (cell in grid.cellsRowMajor()) {
            if (grid.valueAt(cell) != 0) continue
            val candidates = grid.candidatesAt(cell)
            if (candidates.size != 1) continue
            val digit = candidates.digitsAscending().single()
            return singleStep(
                technique = SudokuTechnique.NAKED_SINGLE,
                cell = cell,
                digit = digit,
                candidates = candidates,
                uniqueIn = null
            )
        }
        return null
    }

    internal fun findHiddenSingle(grid: CandidateGrid): LogicalStep? {
        for (type in HouseType.entries) {
            for (houseIndex in 0..8) {
                val house = HouseRef(type, houseIndex)
                for (digit in 1..9) {
                    if (grid.cellsIn(house).any { grid.valueAt(it) == digit }) continue
                    val positions = grid.candidatePositions(house, digit)
                    if (positions.size != 1) continue
                    val cell = positions.single()
                    return singleStep(
                        technique = SudokuTechnique.HIDDEN_SINGLE,
                        cell = cell,
                        digit = digit,
                        candidates = grid.candidatesAt(cell),
                        uniqueIn = house
                    )
                }
            }
        }
        return null
    }

    internal fun findPointing(grid: CandidateGrid): LogicalStep? {
        for (boxIndex in 0..8) {
            val box = HouseRef(HouseType.BOX, boxIndex)
            for (digit in 1..9) {
                val supportingCells = grid.candidatePositions(box, digit)
                if (supportingCells.size < 2) continue

                val rows = supportingCells.map { it.row }.distinct()
                if (rows.size == 1) {
                    val row = HouseRef(HouseType.ROW, rows.single())
                    val targets = grid.candidatePositions(row, digit)
                        .filter { it !in grid.cellsIn(box) }
                    if (targets.isNotEmpty()) {
                        return lockedCandidatesStep(
                            SudokuTechnique.LOCKED_CANDIDATES_POINTING,
                            digit,
                            box,
                            row,
                            supportingCells,
                            targets
                        )
                    }
                }

                val columns = supportingCells.map { it.column }.distinct()
                if (columns.size == 1) {
                    val column = HouseRef(HouseType.COLUMN, columns.single())
                    val targets = grid.candidatePositions(column, digit)
                        .filter { it !in grid.cellsIn(box) }
                    if (targets.isNotEmpty()) {
                        return lockedCandidatesStep(
                            SudokuTechnique.LOCKED_CANDIDATES_POINTING,
                            digit,
                            box,
                            column,
                            supportingCells,
                            targets
                        )
                    }
                }
            }
        }
        return null
    }

    internal fun findClaiming(grid: CandidateGrid): LogicalStep? {
        for (type in listOf(HouseType.ROW, HouseType.COLUMN)) {
            for (houseIndex in 0..8) {
                val source = HouseRef(type, houseIndex)
                for (digit in 1..9) {
                    val supportingCells = grid.candidatePositions(source, digit)
                    if (supportingCells.size < 2) continue
                    val boxes = supportingCells.map(::boxFor).distinct()
                    if (boxes.size != 1) continue
                    val box = boxes.single()
                    val targets = grid.candidatePositions(box, digit)
                        .filter { it !in grid.cellsIn(source) }
                    if (targets.isEmpty()) continue
                    return lockedCandidatesStep(
                        SudokuTechnique.LOCKED_CANDIDATES_CLAIMING,
                        digit,
                        source,
                        box,
                        supportingCells,
                        targets
                    )
                }
            }
        }
        return null
    }

    internal fun findNakedSubset(
        grid: CandidateGrid,
        size: Int,
        technique: SudokuTechnique
    ): LogicalStep? {
        require(size == 2 || size == 3) { "Only pairs and triples are supported" }
        for (house in canonicalHouses()) {
            val unsolved = grid.cellsIn(house).filter { grid.valueAt(it) == 0 }
            val eligible = unsolved.filter { grid.candidatesAt(it).size in 1..size }
            for (cells in cellCombinations(eligible, size)) {
                val union = DigitSet.fromMask(cells.fold(0) { mask, cell ->
                    mask or grid.candidatesAt(cell).mask
                })
                if (union.size != size) continue

                if (size == 2) {
                    if (cells.any { grid.candidatesAt(it) != union }) continue
                    if (unsolved.count { grid.candidatesAt(it) == union } != 2) continue
                } else {
                    val confinedCells = unsolved.filter { cell ->
                        grid.candidatesAt(cell).mask and union.mask.inv() == 0
                    }
                    if (confinedCells.size != 3 || confinedCells != cells) continue
                }

                val actions = unsolved
                    .filter { it !in cells }
                    .mapNotNull { cell -> eliminationForIntersection(grid, cell, union) }
                if (actions.isEmpty()) continue
                return LogicalStep(
                    technique,
                    actions,
                    StepEvidence.Subset(house, union, cells, hidden = false)
                )
            }
        }
        return null
    }

    internal fun findHiddenSubset(
        grid: CandidateGrid,
        size: Int,
        technique: SudokuTechnique
    ): LogicalStep? {
        require(size == 2 || size == 3) { "Only pairs and triples are supported" }
        for (house in canonicalHouses()) {
            for (digits in digitCombinations(size)) {
                val positionLists = digits.map { grid.candidatePositions(house, it) }
                if (positionLists.any { it.isEmpty() }) continue
                val cells = positionLists.flatten().distinct().sorted()
                if (cells.size != size) continue
                if (size == 2 && positionLists.any { it != cells }) continue

                val digitSet = DigitSet.of(*digits.toIntArray())
                val actions = cells.mapNotNull { cell ->
                    val extras = grid.candidatesAt(cell).remove(digitSet)
                    if (extras.isEmpty) null else SolveAction.EliminateCandidates(cell, extras)
                }
                if (actions.isEmpty()) continue
                return LogicalStep(
                    technique,
                    actions,
                    StepEvidence.Subset(house, digitSet, cells, hidden = true)
                )
            }
        }
        return null
    }

    internal fun findXWing(grid: CandidateGrid): LogicalStep? {
        for (baseType in listOf(HouseType.ROW, HouseType.COLUMN)) {
            val coverType = if (baseType == HouseType.ROW) HouseType.COLUMN else HouseType.ROW
            for (digit in 1..9) {
                for (firstBaseIndex in 0..7) {
                    val firstBase = HouseRef(baseType, firstBaseIndex)
                    val firstPositions = grid.candidatePositions(firstBase, digit)
                    if (firstPositions.size != 2) continue
                    val firstCoverIndices = firstPositions.map { cell ->
                        if (coverType == HouseType.COLUMN) cell.column else cell.row
                    }

                    for (secondBaseIndex in firstBaseIndex + 1..8) {
                        val secondBase = HouseRef(baseType, secondBaseIndex)
                        val secondPositions = grid.candidatePositions(secondBase, digit)
                        if (secondPositions.size != 2) continue
                        val secondCoverIndices = secondPositions.map { cell ->
                            if (coverType == HouseType.COLUMN) cell.column else cell.row
                        }
                        if (firstCoverIndices != secondCoverIndices) continue

                        val support = (firstPositions + secondPositions).sorted()
                        val coverHouses = firstCoverIndices.map { HouseRef(coverType, it) }
                        val targets = coverHouses
                            .flatMap { grid.candidatePositions(it, digit) }
                            .filter { it !in support }
                            .distinct()
                            .sorted()
                        if (targets.isEmpty()) continue

                        return LogicalStep(
                            SudokuTechnique.X_WING,
                            targets.map {
                                SolveAction.EliminateCandidates(it, DigitSet.of(digit))
                            },
                            StepEvidence.Fish(
                                digit = digit,
                                baseHouses = listOf(firstBase, secondBase),
                                coverHouses = coverHouses,
                                cells = support
                            )
                        )
                    }
                }
            }
        }
        return null
    }

    internal fun findXYWing(grid: CandidateGrid): LogicalStep? {
        for (pivot in grid.cellsRowMajor()) {
            if (grid.valueAt(pivot) != 0) continue
            val pivotDigits = grid.candidatesAt(pivot)
            if (pivotDigits.size != 2) continue

            val possiblePincers = grid.peersOf(pivot).filter { cell ->
                grid.valueAt(cell) == 0 && grid.candidatesAt(cell).size == 2
            }
            for (firstIndex in 0 until possiblePincers.lastIndex) {
                val first = possiblePincers[firstIndex]
                val firstCandidates = grid.candidatesAt(first)
                val firstShared = DigitSet.fromMask(firstCandidates.mask and pivotDigits.mask)
                if (firstShared.size != 1) continue
                val firstThird = firstCandidates.remove(pivotDigits)
                if (firstThird.size != 1) continue

                for (secondIndex in firstIndex + 1 until possiblePincers.size) {
                    val second = possiblePincers[secondIndex]
                    val secondCandidates = grid.candidatesAt(second)
                    val secondShared = DigitSet.fromMask(secondCandidates.mask and pivotDigits.mask)
                    if (secondShared.size != 1 || secondShared == firstShared) continue
                    val secondThird = secondCandidates.remove(pivotDigits)
                    if (secondThird.size != 1 || secondThird != firstThird) continue

                    val eliminationDigit = firstThird.digitsAscending().single()
                    val support = listOf(pivot, first, second)
                    val firstPeers = grid.peersOf(first)
                    val secondPeers = grid.peersOf(second)
                    val targets = grid.cellsRowMajor().filter { cell ->
                        cell !in support &&
                            grid.valueAt(cell) == 0 &&
                            eliminationDigit in grid.candidatesAt(cell) &&
                            cell in firstPeers &&
                            cell in secondPeers
                    }
                    if (targets.isEmpty()) continue

                    return LogicalStep(
                        SudokuTechnique.XY_WING,
                        targets.map {
                            SolveAction.EliminateCandidates(it, DigitSet.of(eliminationDigit))
                        },
                        StepEvidence.XYWing(
                            pivot = pivot,
                            pincers = listOf(first, second),
                            pivotDigits = pivotDigits,
                            eliminationDigit = eliminationDigit
                        )
                    )
                }
            }
        }
        return null
    }

    internal fun findSkyscraper(grid: CandidateGrid): LogicalStep? {
        for (orientation in listOf(HouseType.ROW, HouseType.COLUMN)) {
            for (digit in 1..9) {
                for (firstHouseIndex in 0..7) {
                    val firstHouse = HouseRef(orientation, firstHouseIndex)
                    val firstPositions = grid.candidatePositions(firstHouse, digit)
                    if (firstPositions.size != 2) continue
                    val firstCovers = firstPositions.map { coverIndex(it, orientation) }

                    for (secondHouseIndex in firstHouseIndex + 1..8) {
                        val secondHouse = HouseRef(orientation, secondHouseIndex)
                        val secondPositions = grid.candidatePositions(secondHouse, digit)
                        if (secondPositions.size != 2) continue
                        val secondCovers = secondPositions.map { coverIndex(it, orientation) }
                        val alignedCovers = firstCovers.filter { it in secondCovers }.distinct()
                        // Zero alignments is not a Skyscraper; two alignments is an X-Wing.
                        if (alignedCovers.size != 1) continue

                        val alignedCover = alignedCovers.single()
                        val firstAligned = firstPositions.single {
                            coverIndex(it, orientation) == alignedCover
                        }
                        val secondAligned = secondPositions.single {
                            coverIndex(it, orientation) == alignedCover
                        }
                        val firstTower = firstPositions.single { it != firstAligned }
                        val secondTower = secondPositions.single { it != secondAligned }
                        if (coverIndex(firstTower, orientation) ==
                            coverIndex(secondTower, orientation)
                        ) continue

                        val support = firstPositions + secondPositions
                        val firstTowerPeers = grid.peersOf(firstTower)
                        val secondTowerPeers = grid.peersOf(secondTower)
                        val targets = grid.cellsRowMajor().filter { cell ->
                            cell !in support &&
                                grid.valueAt(cell) == 0 &&
                                digit in grid.candidatesAt(cell) &&
                                cell in firstTowerPeers &&
                                cell in secondTowerPeers
                        }
                        if (targets.isEmpty()) continue

                        return LogicalStep(
                            SudokuTechnique.SKYSCRAPER,
                            targets.map {
                                SolveAction.EliminateCandidates(it, DigitSet.of(digit))
                            },
                            StepEvidence.Skyscraper(
                                digit = digit,
                                orientation = orientation,
                                sourceHouses = listOf(firstHouse, secondHouse),
                                alignedCells = listOf(firstAligned, secondAligned),
                                towers = listOf(firstTower, secondTower)
                            )
                        )
                    }
                }
            }
        }
        return null
    }

    private fun singleStep(
        technique: SudokuTechnique,
        cell: CellRef,
        digit: Int,
        candidates: DigitSet,
        uniqueIn: HouseRef?
    ): LogicalStep = LogicalStep(
        technique = technique,
        actions = listOf(SolveAction.PlaceValue(cell, digit)),
        evidence = StepEvidence.Single(cell, digit, candidates, uniqueIn)
    )

    private fun lockedCandidatesStep(
        technique: SudokuTechnique,
        digit: Int,
        source: HouseRef,
        target: HouseRef,
        supportingCells: List<CellRef>,
        targets: List<CellRef>
    ): LogicalStep = LogicalStep(
        technique,
        targets.map { SolveAction.EliminateCandidates(it, DigitSet.of(digit)) },
        StepEvidence.LockedCandidates(digit, source, target, supportingCells)
    )

    private fun eliminationForIntersection(
        grid: CandidateGrid,
        cell: CellRef,
        digits: DigitSet
    ): SolveAction.EliminateCandidates? {
        val intersection = DigitSet.fromMask(grid.candidatesAt(cell).mask and digits.mask)
        return if (intersection.isEmpty) null else SolveAction.EliminateCandidates(cell, intersection)
    }

    private fun canonicalHouses(): List<HouseRef> = HouseType.entries.flatMap { type ->
        (0..8).map { index -> HouseRef(type, index) }
    }

    private fun cellCombinations(cells: List<CellRef>, size: Int): List<List<CellRef>> {
        val combinations = ArrayList<List<CellRef>>()
        for (first in 0 until cells.size) {
            for (second in first + 1 until cells.size) {
                if (size == 2) {
                    combinations.add(listOf(cells[first], cells[second]))
                } else {
                    for (third in second + 1 until cells.size) {
                        combinations.add(listOf(cells[first], cells[second], cells[third]))
                    }
                }
            }
        }
        return combinations
    }

    private fun digitCombinations(size: Int): List<List<Int>> {
        val combinations = ArrayList<List<Int>>()
        for (first in 1..9) {
            for (second in first + 1..9) {
                if (size == 2) {
                    combinations.add(listOf(first, second))
                } else {
                    for (third in second + 1..9) {
                        combinations.add(listOf(first, second, third))
                    }
                }
            }
        }
        return combinations
    }

    private fun boxFor(cell: CellRef): HouseRef =
        HouseRef(HouseType.BOX, (cell.row / 3) * 3 + cell.column / 3)

    private fun coverIndex(cell: CellRef, orientation: HouseType): Int =
        if (orientation == HouseType.ROW) cell.column else cell.row

    internal fun progress(grid: CandidateGrid): Int = grid.cellsRowMajor().sumOf { cell ->
        if (grid.valueAt(cell) == 0) 1 + grid.candidatesAt(cell).size else 0
    }

    private fun isSolved(grid: CandidateGrid): Boolean =
        grid.cellsRowMajor().all { grid.valueAt(it) != 0 }

    private fun result(
        status: LogicalSolveStatus,
        grid: CandidateGrid,
        steps: Collection<LogicalStep>
    ): LogicalSolveResult {
        val snapshot = grid.snapshot()
        return LogicalSolveResult(status, snapshot.values, snapshot, steps)
    }

    private fun invalidResult(board: IntArray): LogicalSolveResult = LogicalSolveResult(
        status = LogicalSolveStatus.INVALID,
        finalBoard = board,
        // No authoritative candidate state exists when CandidateGrid creation fails.
        remainingCandidates = CandidateGridSnapshot(IntArray(81), IntArray(81)),
        steps = emptyList()
    )
}
