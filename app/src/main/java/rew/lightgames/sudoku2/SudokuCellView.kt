package rew.lightgames.sudoku2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.ceil

class SudokuCellView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var cell: Cell? = null
    private var error = false
    private var fallbackText = ""
    private var fallbackNotes: Collection<Int> = emptyList()

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val notesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    fun setCell(cell: Cell, isError: Boolean = false) {
        this.cell = cell
        error = isError
        invalidate()
    }

    fun setText(text: String) {
        fallbackText = text
        invalidate()
    }

    fun setNotes(notes: Collection<Int>) {
        fallbackNotes = notes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = cell?.number ?: fallbackText.trim().toIntOrNull() ?: 0
        if (value == 0) {
            drawNotes(canvas, cell?.notes ?: fallbackNotes)
        } else {
            drawNumber(canvas, value)
        }
    }

    private fun drawNumber(canvas: Canvas, value: Int) {
        val currentCell = cell
        numberPaint.apply {
            textSize = width * 0.58f
            typeface = if (currentCell?.isEditable == false) {
                Typeface.create("sans-serif", Typeface.BOLD)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
            color = when {
                error -> color(R.color.gameplay_error_ink)
                currentCell?.isHint == true -> color(R.color.gameplay_ink_muted)
                currentCell?.isEditable == false -> color(R.color.gameplay_ink)
                else -> color(R.color.gameplay_player_value)
            }
        }
        val baseline = height / 2f - (numberPaint.descent() + numberPaint.ascent()) / 2f
        canvas.drawText(value.toString(), width / 2f, baseline, numberPaint)
    }

    private fun drawNotes(canvas: Canvas, notes: Collection<Int>) {
        val noteCellSize = width / 3f
        notesPaint.textSize = noteCellSize * 0.58f
        notesPaint.color = color(R.color.gameplay_player_value)
        notes.forEach { note ->
            if (note in 1..9) {
                val row = ceil(note / 3.0).toInt() - 1
                val col = (note - 1) % 3
                val centerX = col * noteCellSize + noteCellSize / 2f
                val centerY = row * noteCellSize + noteCellSize / 2f -
                    (notesPaint.descent() + notesPaint.ascent()) / 2f
                canvas.drawText(note.toString(), centerX, centerY, notesPaint)
            }
        }
    }

    private fun color(colorRes: Int): Int = ContextCompat.getColor(context, colorRes)
}
