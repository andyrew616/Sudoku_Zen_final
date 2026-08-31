package rew.lightgames.sudoku2

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface GameplayLoadState {
    data object Idle : GameplayLoadState
    data class Loading(val requestedDifficulty: SudokuDifficulty) : GameplayLoadState
    data class Ready(
        val requestedDifficulty: SudokuDifficulty?,
        val source: GameplayPuzzleSource
    ) : GameplayLoadState
    data class Failure(
        val requestedDifficulty: SudokuDifficulty?,
        val reason: GameplayPuzzleFailureReason
    ) : GameplayLoadState
}

class SudokuViewModel internal constructor(
    shouldGenerateNewGame: Boolean,
    private val requestedDifficulty: SudokuDifficulty?,
    private val puzzleLoader: GameplayPuzzleLoader,
    private val seedSource: GameplaySeedSource,
    private val generationDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    constructor(
        context: Context,
        shouldGenerateNewGame: Boolean,
        requestedDifficulty: SudokuDifficulty?
    ) : this(
        shouldGenerateNewGame = shouldGenerateNewGame,
        requestedDifficulty = requestedDifficulty,
        puzzleLoader = productionPuzzleLoader(context),
        seedSource = ProductionGameplaySeedSource()
    )

    /** Retains the pre-PR9 construction surface for resume-only callers. */
    constructor(
        context: Context,
        shouldGenerateNewGame: Boolean
    ) : this(context, shouldGenerateNewGame, requestedDifficulty = null)

    private val _sudokuBoard = MutableLiveData<SudokuBoard?>()
    private val _hintsUsed = MutableLiveData<Int>(0)
    private val _gameplayLoadState = MutableLiveData<GameplayLoadState>(GameplayLoadState.Idle)
    val sudokuBoard: LiveData<SudokuBoard?> = _sudokuBoard
    val hintsUsed = _hintsUsed
    val gameplayLoadState: LiveData<GameplayLoadState> = _gameplayLoadState
    private var notesMode = false
    private val _selectedCell = MutableLiveData<Pair<Int, Int>>()
    val selectedCell: LiveData<Pair<Int, Int>> = _selectedCell
    private var generationJob: Job? = null
    private var latestRequestId = 0L

    init {
        if (shouldGenerateNewGame) {
            requestPuzzleGeneration()
        }
    }

    fun setBoard(board: SudokuBoard) {
        generationJob?.cancel()
        latestRequestId++
        _sudokuBoard.value = board
        _gameplayLoadState.value = GameplayLoadState.Ready(
            requestedDifficulty = requestedDifficulty,
            source = GameplayPuzzleSource.RESUMED
        )
    }

    fun reportResumeUnavailable() {
        generationJob?.cancel()
        latestRequestId++
        _sudokuBoard.value = null
        _gameplayLoadState.value = GameplayLoadState.Failure(
            requestedDifficulty = null,
            reason = GameplayPuzzleFailureReason.RESUME_UNAVAILABLE
        )
    }

    fun reportGenerationInterrupted() {
        generationJob?.cancel()
        latestRequestId++
        _sudokuBoard.value = null
        _gameplayLoadState.value = GameplayLoadState.Failure(
            requestedDifficulty = requestedDifficulty,
            reason = GameplayPuzzleFailureReason.PUZZLE_LOAD_FAILED
        )
    }

    fun setHintsUsed(hintsUsed: Int) {
        _hintsUsed.value = hintsUsed

    }



    fun selectCell(row: Int, col: Int) {

        _selectedCell.value = Pair(row, col)

    }

    fun updateSelectedCellValue(value: Int) {
        Log.d("Notes Mode", "updateSelectedCellValue:$notesMode ")
        _selectedCell.value?.let { (row, col) ->
            if (row == -1 || col == -1) {
                return
            }

            if (notesMode) {
                addNoteToSelectedCell(value)
            } else {
                _sudokuBoard.value = _sudokuBoard.value?.apply {
                    val currentCell = getCell(row, col)
                    if (currentCell.isEditable) {
                        val newCell = currentCell.copy(number = value, original_number = 0)
                        setCell(row, col, newCell)
                        Log.d("SudokuViewModel", "Number button clicked: $value")
                    }
                }
            }
        }
    }

    fun loadNextPuzzle() {
        if (!canGenerateNextPuzzle()) {
            Log.d("SudokuViewModel", "A target difficulty is required for the next puzzle")
            return
        }
        Log.d("SudokuViewModel", "Loading next puzzle")
        requestPuzzleGeneration()
    }

    fun canGenerateNextPuzzle(): Boolean =
        requestedDifficulty != null && requestedDifficulty != SudokuDifficulty.UNSUPPORTED

    fun retryPuzzleGeneration() {
        if (_gameplayLoadState.value is GameplayLoadState.Failure) {
            requestPuzzleGeneration()
        }
    }

    private fun requestPuzzleGeneration() {
        val difficulty = requestedDifficulty
        if (difficulty == null || difficulty == SudokuDifficulty.UNSUPPORTED) {
            _sudokuBoard.value = null
            _gameplayLoadState.value = GameplayLoadState.Failure(
                requestedDifficulty = difficulty,
                reason = GameplayPuzzleFailureReason.INVALID_DIFFICULTY
            )
            return
        }

        generationJob?.cancel()
        val requestId = ++latestRequestId
        val seed = seedSource.nextSeed()
        _hintsUsed.value = 0
        _selectedCell.value = Pair(-1, -1)
        _sudokuBoard.value = null
        _gameplayLoadState.value = GameplayLoadState.Loading(difficulty)
        Log.d("SudokuViewModel", "Generating $difficulty puzzle")

        generationJob = viewModelScope.launch {
            val result = try {
                withContext(generationDispatcher) {
                    puzzleLoader.createPuzzle(difficulty, seed)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: RuntimeException) {
                Log.e("SudokuViewModel", "Puzzle generation failed", exception)
                null
            }
            if (requestId != latestRequestId) return@launch

            when (result) {
                is PuzzleLoadResult.Ready -> {
                    if (
                        result.requestedDifficulty != difficulty ||
                        result.actualRating.difficulty != difficulty
                    ) {
                        _gameplayLoadState.value = GameplayLoadState.Failure(
                            requestedDifficulty = difficulty,
                            reason = GameplayPuzzleFailureReason.PUZZLE_LOAD_FAILED
                        )
                    } else {
                        _sudokuBoard.value = result.board
                        _gameplayLoadState.value = GameplayLoadState.Ready(
                            requestedDifficulty = difficulty,
                            source = result.source
                        )
                    }
                }
                is PuzzleLoadResult.Failure -> {
                    Log.e(
                        "SudokuViewModel",
                        "Puzzle load failed: ${result.reason}; target=${result.targetFailureReason}"
                    )
                    _gameplayLoadState.value = GameplayLoadState.Failure(
                        requestedDifficulty = difficulty,
                        reason = result.reason
                    )
                }
                null -> {
                    _gameplayLoadState.value = GameplayLoadState.Failure(
                        requestedDifficulty = difficulty,
                        reason = GameplayPuzzleFailureReason.PUZZLE_LOAD_FAILED
                    )
                }
            }
        }
    }

    fun provideHint() {
        _selectedCell.value?.let { (row, col) ->
            if(row == -1 || col == -1){
                return
            }
            val solutionValue = _sudokuBoard.value?.solutionValueAt(row, col)
            solutionValue?.let { value ->
                _sudokuBoard.value = _sudokuBoard.value?.apply {
                    val cell = getCell(row, col).copy(number = value, isHint = true, original_number = value)
                    setCell(row, col, cell)
                    Log.d("SudokuViewModel", "Hint provided for cell ($row, $col): $value")
                    _hintsUsed.value = _hintsUsed.value?.plus(1)
                }
                //deselectCell()
            }
        }
        //deselectCell() // Deselect the cell after providing a hint
    }

    private fun deselectCell() {
        _selectedCell.value = Pair(-1, -1)
    }
    fun toggleNotesMode() {
        notesMode = !notesMode
    }

    fun addNoteToSelectedCell(value: Int) {
        _selectedCell.value?.let { (row, col) ->
            _sudokuBoard.value = _sudokuBoard.value?.apply {
                val currentCell = getCell(row, col)
                if (currentCell.isEditable) {
                    val newNotes = currentCell.notes
                    if (newNotes.contains(value)) {
                        newNotes.remove(value)
                    } else {
                        newNotes.add(value)
                    }
                    val newCell = currentCell.copy(notes = newNotes)
                    setCell(row, col, newCell)
                    Log.d("SudokuViewModel", "Note added to cell ($row, $col): $value")
                }
            }
        }
    }

    fun isBoardCorrect(): Boolean {
        return sudokuBoard.value?.isBoardCorrect() ?: false
    }

    companion object {
        private fun productionPuzzleLoader(context: Context): GameplayPuzzleLoader {
            val applicationContext = context.applicationContext
            val fallbackSource = CompactFallbackPuzzleProvider {
                applicationContext.assets.open(GRADED_FALLBACK_ASSET)
                    .bufferedReader()
                    .use { it.readText() }
            }
            return GameplayPuzzleProvider(fallbackSource)
        }
    }
}
