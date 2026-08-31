package rew.lightgames.sudoku2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import androidx.core.content.ContextCompat

interface OnCellSelectedListener {
    fun onCellSelected(row: Int, col: Int)
}

class SudokuBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var board: SudokuBoard? = null
    var selectedRow = -1
    var selectedCol = -1
    var cellSelectedListener: OnCellSelectedListener? = null
    private var hintHighlights: List<HintHighlight> = emptyList()
    private var autoNotes: AutoNotesResult? = null

    private val cells = Array(9) { arrayOfNulls<SudokuCellView>(9) }
    private val thinGridPaint = gridPaint(
        R.color.gameplay_grid_line,
        R.dimen.gameplay_grid_line_width
    )
    private val boxGridPaint = gridPaint(
        R.color.gameplay_grid_box_line,
        R.dimen.gameplay_grid_box_width
    )
    private val outerGridPaint = gridPaint(
        R.color.gameplay_grid_outer_line,
        R.dimen.gameplay_grid_outer_width
    )

    init {
        setWillNotDraw(false)
        clipToOutline = true

        val gridLayout = GridLayout(context).apply {
            id = View.generateViewId()
            columnCount = 9
            rowCount = 9
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(gridLayout)

        for (index in 0 until 81) {
            val row = index / 9
            val col = index % 9
            val cellView = SudokuCellView(context, null).apply {
                id = View.generateViewId()
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = GridLayout.spec(col, GridLayout.FILL, 1f)
                    rowSpec = GridLayout.spec(row, GridLayout.FILL, 1f)
                    setGravity(Gravity.FILL)
                }
                setBackgroundColor(color(R.color.gameplay_board_cell))
            }
            cells[row][col] = cellView
            gridLayout.addView(cellView)
        }
    }

    fun getCellView(row: Int, col: Int): SudokuCellView? {
        return if (row in 0..8 && col in 0..8) cells[row][col] else null
    }

    fun highlightRowColBox(row: Int, col: Int) {
        selectedRow = row
        selectedCol = col
        renderAllCells()
    }

    fun setBoard(board: SudokuBoard?) {
        this.board = board
        renderAllCells()
    }

    fun setHintHighlights(highlights: Collection<HintHighlight>) {
        hintHighlights = highlights.toList()
        renderAllCells()
    }

    fun setAutoNotes(result: AutoNotesResult?) {
        autoNotes = result
        renderAllCells()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && width > 0 && height > 0) {
            val col = (event.x / (width / 9f)).toInt().coerceIn(0, 8)
            val row = (event.y / (height / 9f)).toInt().coerceIn(0, 8)
            if (board?.getCell(row, col)?.isEditable == true) {
                selectedRow = row
                selectedCol = col
                cellSelectedListener?.onCellSelected(row, col)
                renderAllCells()
                invalidate()
            }
        } else if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun updateSelectedCell(row: Int, col: Int, cell: Cell) {
        if (row in 0..8 && col in 0..8) {
            cells[row][col]?.setCell(cell, isError(row, col, cell))
            renderAllCells()
        }
    }

    fun updateNotes(row: Int, col: Int, notes: ArrayList<Int>) {
        if (row in 0..8 && col in 0..8) {
            cells[row][col]?.setNotes(notes)
        }
    }

    fun invalidateAllCells() {
        renderAllCells()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        val cellWidth = width / 9f
        val cellHeight = height / 9f
        for (line in 1 until 9) {
            val paint = if (line % 3 == 0) boxGridPaint else thinGridPaint
            canvas.drawLine(line * cellWidth, 0f, line * cellWidth, height.toFloat(), paint)
            canvas.drawLine(0f, line * cellHeight, width.toFloat(), line * cellHeight, paint)
        }

        val inset = outerGridPaint.strokeWidth / 2f
        canvas.drawRect(inset, inset, width - inset, height - inset, outerGridPaint)
    }

    private fun renderAllCells() {
        val currentBoard = board ?: return
        val selectedValue = if (selectedRow in 0..8 && selectedCol in 0..8) {
            currentBoard.getCell(selectedRow, selectedCol).number
        } else {
            0
        }

        for (row in 0..8) {
            for (col in 0..8) {
                val cell = currentBoard.getCell(row, col)
                val error = isError(row, col, cell)
                val selected = row == selectedRow && col == selectedCol
                val matching = !selected && selectedValue != 0 && cell.number == selectedValue
                val related = !selected && selectedRow in 0..8 && selectedCol in 0..8 && (
                    row == selectedRow ||
                        col == selectedCol ||
                        row / 3 == selectedRow / 3 && col / 3 == selectedCol / 3
                    )
                val cellHighlights = hintHighlights.filter { it.cell == CellRef(row, col) }
                val dominantHint = cellHighlights.maxByOrNull { hintPriority(it.role) }
                val presentedNotes = when (val result = autoNotes) {
                    is AutoNotesResult.Available -> result.candidatesAt(row, col)
                    AutoNotesResult.InvalidBoard -> emptyList()
                    null -> null
                }

                val backgroundColor = when {
                    error -> color(R.color.gameplay_error_cell)
                    dominantHint?.role == HintHighlightRole.ELIMINATION ->
                        color(R.color.gameplay_hint_cell)
                    dominantHint?.role == HintHighlightRole.TARGET ->
                        color(R.color.gameplay_selected_cell)
                    dominantHint?.role == HintHighlightRole.SUPPORT ->
                        color(R.color.gameplay_matching_cell)
                    selected -> color(R.color.gameplay_selected_cell)
                    matching -> color(R.color.gameplay_matching_cell)
                    cell.isHint -> color(R.color.gameplay_hint_cell)
                    dominantHint?.role == HintHighlightRole.HOUSE ->
                        color(R.color.gameplay_related_cell)
                    related -> color(R.color.gameplay_related_cell)
                    !cell.isEditable -> color(R.color.gameplay_board_cell_fixed)
                    else -> color(R.color.gameplay_board_cell)
                }

                cells[row][col]?.apply {
                    setCell(cell, error, presentedNotes)
                    setBackgroundColor(backgroundColor)
                    contentDescription = hintContentDescription(row, col, cellHighlights)
                        ?: notesContentDescription(row, col, cell, presentedNotes)
                }
            }
        }
        invalidate()
    }

    private fun isError(row: Int, col: Int, cell: Cell): Boolean {
        return cell.isEditable &&
            cell.number != 0 &&
            board?.hasVisibleConflict(row, col) == true
    }

    private fun hintContentDescription(
        row: Int,
        column: Int,
        highlights: List<HintHighlight>
    ): String? {
        val dominant = highlights.maxByOrNull { hintPriority(it.role) } ?: return null
        val digits = highlights
            .filter { it.role == dominant.role }
            .mapNotNull { it.digit }
            .distinct()
            .sorted()
            .joinToString(resources.getString(R.string.hint_digit_separator))
        return when (dominant.role) {
            HintHighlightRole.TARGET -> resources.getString(
                R.string.hint_accessibility_target,
                row + 1,
                column + 1,
                digits
            )
            HintHighlightRole.ELIMINATION -> resources.getString(
                R.string.hint_accessibility_elimination,
                digits,
                row + 1,
                column + 1
            )
            HintHighlightRole.SUPPORT -> resources.getString(
                R.string.hint_accessibility_support,
                row + 1,
                column + 1
            )
            HintHighlightRole.HOUSE -> resources.getString(
                R.string.hint_accessibility_house,
                row + 1,
                column + 1
            )
        }
    }

    private fun hintPriority(role: HintHighlightRole): Int = when (role) {
        HintHighlightRole.HOUSE -> 0
        HintHighlightRole.SUPPORT -> 1
        HintHighlightRole.TARGET -> 2
        HintHighlightRole.ELIMINATION -> 3
    }

    private fun notesContentDescription(
        row: Int,
        column: Int,
        cell: Cell,
        presentedNotes: Collection<Int>?
    ): String? {
        if (!cell.isEditable || cell.number != 0) return null
        if (autoNotes == null) {
            val visibleNotes = cell.notes.filter { it in 1..9 }.distinct().sorted()
            if (visibleNotes.isEmpty()) return null
            return resources.getString(
                R.string.manual_notes_accessibility_candidates,
                row + 1,
                column + 1,
                visibleNotes.joinToString(
                    resources.getString(R.string.hint_digit_separator)
                )
            )
        }
        if (autoNotes == AutoNotesResult.InvalidBoard) {
            return resources.getString(
                R.string.auto_notes_accessibility_unavailable,
                row + 1,
                column + 1
            )
        }
        return resources.getString(
            R.string.auto_notes_accessibility_candidates,
            row + 1,
            column + 1,
            presentedNotes.orEmpty().joinToString(
                resources.getString(R.string.hint_digit_separator)
            )
        )
    }

    private fun gridPaint(colorRes: Int, widthRes: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color(colorRes)
            style = Paint.Style.STROKE
            strokeWidth = resources.getDimension(widthRes)
        }
    }

    private fun color(colorRes: Int): Int = ContextCompat.getColor(context, colorRes)
}
