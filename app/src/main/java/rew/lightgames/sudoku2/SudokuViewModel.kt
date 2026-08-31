package rew.lightgames.sudoku2

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class SudokuViewModel(private val context: Context, private var shouldGenerateNewGame: Boolean) : ViewModel() {

    private val _sudokuBoard = MutableLiveData<SudokuBoard>().apply {
        value = SudokuBoard(
            cells = Array(9) { row ->
                Array(9) { col ->
                    Cell(isEditable = false, number = 0, original_number = 0)
                }
            },
            solution = Array(9) { IntArray(9) { 0 } }
        ) // Initialize the LiveData with a new SudokuBoard instance
    }
    private val _hintsUsed = MutableLiveData<Int>(0)
    val sudokuBoard: LiveData<SudokuBoard> = _sudokuBoard
    val hintsUsed = _hintsUsed
    private var notesMode = false
    private val _selectedCell = MutableLiveData<Pair<Int, Int>>()
    val selectedCell: LiveData<Pair<Int, Int>> = _selectedCell



    init {
        if (shouldGenerateNewGame) {
            generateSudoku()
        }
    }

    fun setBoard(board: SudokuBoard) {
        _sudokuBoard.value = board
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

    private fun generateSudoku() {
        Log.d("SudokuViewModel", "Generating puzzle")
        viewModelScope.launch(Dispatchers.IO) {
            val sudokuGenerator = SudokuGenerator(context)
            val randomIndex = (1..sudokuGenerator.getNumberOfPuzzles()).random()
            val solvedGrid = sudokuGenerator.getSolution(randomIndex)
            val partiallySolvedGrid = sudokuGenerator.getPuzzle(randomIndex)
            if (solvedGrid != null) {
                updateUI(partiallySolvedGrid, solvedGrid)
            }
        }
    }

    fun loadNextPuzzle() {
        shouldGenerateNewGame = true
        generateSudoku()
        Log.d("SudokuViewModel", "Loading next puzzle")
        _hintsUsed.value = 0 // reset hints
        // reset any other game state here...
        sudokuBoard.value?.clearNotes()
    }


    private fun updateUI(partiallySolvedGrid: Array<IntArray>, solution: Array<IntArray>) {
        viewModelScope.launch(Dispatchers.Main) {
            val newBoard = SudokuBoard(
                cells = partiallySolvedGrid.map { row ->
                    row.map { number ->
                        Cell(number = number, original_number = number)
                    }.toTypedArray()
                }.toTypedArray(),
                solution = solution
            )

            _sudokuBoard.value = newBoard
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

}
