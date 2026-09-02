package rew.lightgames.sudoku2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlin.math.ceil

class SudokuCellView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var cell: Cell? = null
    private var error = false
    private var fallbackText = ""
    private var fallbackNotes: Collection<Int> = emptyList()
    private var presentedNotes: Collection<Int>? = null
    private var automaticNotes = false
    private var selected = false
    private var hintRole: HintHighlightRole? = null
    private val fixedTypeface = ResourcesCompat.getFont(context, R.font.ubuntu_medium)
        ?: Typeface.create("sans-serif", Typeface.BOLD)
    private val playerTypeface = ResourcesCompat.getFont(context, R.font.ubuntu_regular)
        ?: Typeface.create("sans-serif", Typeface.NORMAL)
    private val notesTypeface = ResourcesCompat.getFont(context, R.font.ubuntu_medium)
        ?: Typeface.create("sans-serif", Typeface.NORMAL)

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val notesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        typeface = notesTypeface
    }
    private val statePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.gameplay_cell_state_stroke_width)
    }

    fun setCell(
        cell: Cell,
        isError: Boolean = false,
        presentedNotes: Collection<Int>? = null,
        automaticNotes: Boolean = false,
        selected: Boolean = false,
        hintRole: HintHighlightRole? = null
    ) {
        this.cell = cell
        error = isError
        this.presentedNotes = presentedNotes?.toList()
        this.automaticNotes = automaticNotes
        this.selected = selected
        this.hintRole = hintRole
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
            drawNotes(canvas, presentedNotes ?: cell?.notes ?: fallbackNotes)
        } else {
            drawNumber(canvas, value)
        }
        drawStateDecoration(canvas)
    }

    private fun drawNumber(canvas: Canvas, value: Int) {
        val currentCell = cell
        numberPaint.apply {
            textSize = width * 0.58f
            typeface = if (currentCell?.isEditable == false) {
                fixedTypeface
            } else {
                playerTypeface
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
        notesPaint.textSize = noteCellSize * 0.62f
        notesPaint.color = color(
            if (automaticNotes) R.color.gameplay_auto_note else R.color.gameplay_player_value
        )
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

    private fun drawStateDecoration(canvas: Canvas) {
        val outlineColor = when {
            hintRole == HintHighlightRole.TARGET ||
                hintRole == HintHighlightRole.ELIMINATION -> R.color.gameplay_hint_outline
            selected -> R.color.gameplay_selected_outline
            else -> null
        }
        val inset = resources.getDimension(R.dimen.gameplay_cell_state_inset)
        if (outlineColor != null) {
            statePaint.color = color(outlineColor)
            statePaint.style = Paint.Style.STROKE
            canvas.drawRoundRect(
                RectF(inset, inset, width - inset, height - inset),
                inset,
                inset,
                statePaint
            )
        }
        if (hintRole == HintHighlightRole.SUPPORT) {
            statePaint.color = color(R.color.gameplay_hint_outline)
            statePaint.style = Paint.Style.FILL
            canvas.drawCircle(width - inset * 1.8f, inset * 1.8f, inset * 0.72f, statePaint)
        }
    }

    private fun color(colorRes: Int): Int = ContextCompat.getColor(context, colorRes)
}
