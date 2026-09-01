package rew.lightgames.sudoku2

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlin.math.roundToInt

interface SudokuControlListener {
    fun onNotesModeChanged(notesMode: Boolean)
    fun onNumberButtonClicked(value: Int)
    fun onEraseButtonClicked()
    fun onHintsButtonClicked()
}

class SudokuControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var listener: SudokuControlListener? = null

    private var notesMode = false
    private val numberGrid = GridLayout(context)
    private val utilityHeight = resources.getDimensionPixelSize(R.dimen.gameplay_utility_height)
    private val utilityGap = resources.getDimensionPixelSize(R.dimen.gameplay_utility_gap)
    private val numberGridMinHeight =
        resources.getDimensionPixelSize(R.dimen.gameplay_number_grid_min_height)
    private val numberGridMaxHeight =
        resources.getDimensionPixelSize(R.dimen.gameplay_number_grid_max_height)
    private var reclaimedBottomSpace = 0
    private lateinit var notesAction: LinearLayout
    private lateinit var notesLabel: TextView
    private var autoNotesEnabled = false

    init {
        val content = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                startToStart = LayoutParams.PARENT_ID
                endToEnd = LayoutParams.PARENT_ID
                topToTop = LayoutParams.PARENT_ID
            }
        }

        val utilityRail = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.gameplay_utility_rail)
            dividerDrawable = ContextCompat.getDrawable(context, R.drawable.gameplay_utility_divider)
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            setPadding(dp(3), 0, dp(3), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                utilityHeight
            )
        }

        notesAction = createUtilityAction(
            R.drawable.ic_gameplay_notes,
            R.string.gameplay_notes
        ) {
            if (autoNotesEnabled) {
                Toast.makeText(
                    context,
                    R.string.gameplay_manual_notes_unavailable,
                    Toast.LENGTH_SHORT
                ).show()
                return@createUtilityAction
            }
            notesMode = !notesMode
            renderNotesAction()
            listener?.onNotesModeChanged(notesMode)
        }
        notesAction.id = R.id.gameplayNotesAction
        notesLabel = notesAction.getChildAt(1) as TextView
        renderNotesAction()
        utilityRail.addView(notesAction)
        utilityRail.addView(
            createUtilityAction(R.drawable.ic_gameplay_hint, R.string.gameplay_hint) {
                listener?.onHintsButtonClicked()
            }.apply { id = R.id.gameplayHintAction }
        )
        utilityRail.addView(
            createUtilityAction(R.drawable.ic_gameplay_erase, R.string.gameplay_erase) {
                listener?.onEraseButtonClicked()
            }.apply { id = R.id.gameplayEraseAction }
        )

        numberGrid.apply {
            id = View.generateViewId()
            columnCount = 3
            rowCount = 3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                numberGridMinHeight
            ).apply {
                topMargin = utilityGap
            }
        }

        for (value in 1..9) {
            numberGrid.addView(createNumberButton(value))
        }

        content.addView(utilityRail)
        content.addView(numberGrid)
        addView(content)
    }

    fun setAutoNotesEnabled(enabled: Boolean) {
        autoNotesEnabled = enabled
        if (enabled) {
            notesMode = false
        }
        renderNotesAction()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        if (availableWidth > 0) {
            val preferredHeight = (availableWidth * 0.48f).roundToInt()
                .coerceIn(numberGridMinHeight, numberGridMaxHeight)
            numberGrid.layoutParams = (numberGrid.layoutParams as LinearLayout.LayoutParams).apply {
                height = preferredHeight + reclaimedBottomSpace
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun setReclaimedBottomSpace(height: Int) {
        val newHeight = height.coerceAtLeast(0)
        if (reclaimedBottomSpace != newHeight) {
            reclaimedBottomSpace = newHeight
            requestLayout()
        }
    }

    private fun createNumberButton(value: Int): Button {
        return Button(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.gameplay_numeric_value, value)
            contentDescription = context.getString(R.string.gameplay_number_key, value)
            background = ContextCompat.getDrawable(context, R.drawable.gameplay_number_key)
            setTextColor(ContextCompat.getColor(context, R.color.gameplay_player_value))
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.gameplay_number_text_size)
            )
            typeface = ResourcesCompat.getFont(context, R.font.ubuntu_medium)
                ?: Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
            isAllCaps = false
            minWidth = resources.getDimensionPixelSize(R.dimen.zen_min_touch_target)
            minHeight = resources.getDimensionPixelSize(R.dimen.zen_min_touch_target)
            stateListAnimator = null
            setPadding(0, 0, 0, 0)
            setOnClickListener { listener?.onNumberButtonClicked(value) }
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec((value - 1) % 3, GridLayout.FILL, 1f)
                rowSpec = GridLayout.spec((value - 1) / 3, GridLayout.FILL, 1f)
                setGravity(Gravity.FILL)
                val gap = resources.getDimensionPixelSize(R.dimen.gameplay_number_key_gap)
                setMargins(gap, gap, gap, gap)
            }
        }
    }

    private fun createUtilityAction(
        iconRes: Int,
        labelRes: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minimumHeight = utilityHeight - dp(6)
            background = ContextCompat.getDrawable(context, R.drawable.gameplay_utility_action)
            contentDescription = context.getString(labelRes)
            setOnClickListener { onClick() }
            ViewCompat.setAccessibilityDelegate(
                this,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = Button::class.java.name
                    }
                }
            )
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )

            addView(ImageView(context).apply {
                setImageResource(iconRes)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.gameplay_utility_icon_size),
                    resources.getDimensionPixelSize(R.dimen.gameplay_utility_icon_size)
                )
            })
            addView(TextView(context).apply {
                setText(labelRes)
                setTextColor(ContextCompat.getColor(context, R.color.gameplay_ink_muted))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = ResourcesCompat.getFont(context, R.font.ubuntu_medium)
                    ?: Typeface.create("sans-serif", Typeface.BOLD)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(6)
                }
            })
        }
    }

    private fun renderNotesAction() {
        if (!::notesAction.isInitialized || !::notesLabel.isInitialized) return
        notesAction.isSelected = notesMode && !autoNotesEnabled
        notesAction.alpha = if (autoNotesEnabled) 0.58f else 1f
        notesLabel.setText(
            when {
                autoNotesEnabled -> R.string.gameplay_notes_locked_label
                notesMode -> R.string.gameplay_notes_on_label
                else -> R.string.gameplay_notes
            }
        )
        notesLabel.setTextColor(
            ContextCompat.getColor(
                context,
                if (notesMode && !autoNotesEnabled) {
                    R.color.gameplay_mode_ink
                } else {
                    R.color.gameplay_ink_muted
                }
            )
        )
        notesAction.contentDescription = context.getString(
            when {
                autoNotesEnabled -> R.string.gameplay_manual_notes_unavailable
                notesMode -> R.string.gameplay_notes_mode_on
                else -> R.string.gameplay_notes_mode_off
            }
        )
        ViewCompat.setStateDescription(
            notesAction,
            context.getString(
                when {
                    autoNotesEnabled -> R.string.gameplay_notes_locked_state
                    notesMode -> R.string.settings_switch_on
                    else -> R.string.settings_switch_off
                }
            )
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
