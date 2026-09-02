package rew.lightgames.sudoku2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context

class SudokuViewModelFactory(
    private val context: Context,
    private val generateSudokuPuzzle: Boolean,
    private val requestedDifficulty: SudokuDifficulty? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SudokuViewModel::class.java)) {

            return SudokuViewModel(context, generateSudokuPuzzle, requestedDifficulty) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
