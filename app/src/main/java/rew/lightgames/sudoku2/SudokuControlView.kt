package rew.lightgames.sudoku2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat

interface SudokuControlListener {
    fun onNotesModeChanged(notesMode: Boolean)
    fun onNumberButtonClicked(value: Int)
    fun onEraseButtonClicked() // Add this method
    fun onHintsButtonClicked()
}

@SuppressLint("SuspiciousIndentation")
class SudokuControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val buttonDrawables = listOf(
        R.drawable.ic_key1_tap,
        R.drawable.ic_key2_tap,
        R.drawable.ic_key3_tap,
        R.drawable.ic_key4_tap,
        R.drawable.ic_key5_tap,
        R.drawable.ic_key6_tap,
        R.drawable.ic_key7_tap,
        R.drawable.ic_key8_tap,
        R.drawable.ic_key9_tap
    )
    var listener: SudokuControlListener? = null
    private var notesMode = false
    private val buttons = mutableListOf<Button>()

    init {
        val notesButton = Button(context)
        val buttonsLayout = GridLayout(context).apply {
            id = View.generateViewId()
            columnCount = 4
            rowCount = 3
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )

        }


        // Add number buttons (1-9)
        for (i in 1..9) {
            val button = Button(context).apply {
                id = View.generateViewId()

                background = ContextCompat.getDrawable(context, buttonDrawables[i - 1])
                setOnClickListener {
                    val avd = background as? AnimatedVectorDrawable
                    avd?.start()
                    onNumberButtonClicked(i)

                    // Handle button click here
                }
            }
            val layoutParams = GridLayout.LayoutParams()
            layoutParams.width = 0
            layoutParams.height = 0
            layoutParams.columnSpec = GridLayout.spec((i - 1) % 3, GridLayout.FILL, 1f)
            layoutParams.rowSpec = GridLayout.spec((i - 1) / 3, GridLayout.FILL, 1f)
            layoutParams.setGravity(Gravity.FILL_VERTICAL)
            button.layoutParams = layoutParams
            buttons.add(button)
            buttonsLayout.addView(button)
        }

        // Add hint, delete, and notes buttons
        val specialButtons = listOf(
            Pair(R.drawable.ic_hint, {listener?.onHintsButtonClicked()}),
            Pair(R.drawable.ic_delete, { listener?.onEraseButtonClicked() }),
            Pair(R.drawable.ic_notes_on) {
                notesMode = !notesMode
                notesButton.background = if (notesMode) {
                    ContextCompat.getDrawable(context, R.drawable.ic_notes_off)
                } else {
                    ContextCompat.getDrawable(context, R.drawable.ic_notes_on)
                }
                listener?.onNotesModeChanged(notesMode)
            }

        )

        specialButtons.forEachIndexed { index, pair ->
            val button = if (index == 2) {
                notesButton
            } else {
                Button(context)
            }
            button.id = View.generateViewId()
            button.background = ContextCompat.getDrawable(context,
                pair.first)
            button.setOnClickListener {
                pair.second.invoke()
                val avd = button.background as? AnimatedVectorDrawable
                avd?.start()
            }
            buttons.add(button)
            val layoutParams = GridLayout.LayoutParams()

            layoutParams.width = 0
            layoutParams.height = 0
            layoutParams.columnSpec = GridLayout.spec(3, GridLayout.FILL, 1f)
            layoutParams.rowSpec = GridLayout.spec(index, GridLayout.FILL, 1f)
            layoutParams.setGravity(Gravity.FILL_HORIZONTAL)
            button.layoutParams = layoutParams
            buttonsLayout.addView(button)
        }

        addView(buttonsLayout)
        startInitialAnimations()

    }

    private fun onNumberButtonClicked(value: Int) {
        listener?.onNumberButtonClicked(value)
        //Log.d("SudokuControlView", "Number button clicked: $value")
    }

    private fun startInitialAnimations() {
        buttons.forEach { button ->
            val avd = button.background as? AnimatedVectorDrawable
            avd?.start()
        }

    }
    private fun getTransparentDrawable(context: Context, drawableId: Int, color: Int): Drawable {
        val drawable = ContextCompat.getDrawable(context, drawableId)
        drawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        return drawable!!
    }
}