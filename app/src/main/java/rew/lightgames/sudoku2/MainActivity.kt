package rew.lightgames.sudoku2

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.SoundPool
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.gson.Gson
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity(), SudokuControlListener, OnCellSelectedListener, TimerListener {
    companion object {
        const val PREF_NAME = "my_preferences"
        const val PREF_AUTO_NOTES = "auto_notes"
        private const val PREF_SOUND_EFFECTS = "sound_effects"
        private const val PREF_SAVED_GAME = "saved_game"
        private const val PREF_SAVED_DIFFICULTY = "saved_game_difficulty"
        private const val STATE_ACTIVE_GAME = "gameplay_active_game"
        private const val STATE_REQUESTED_DIFFICULTY = "gameplay_requested_difficulty"
        private const val STATE_TIMER_SECONDS = "gameplay_timer_seconds"
        private const val STATE_PAUSED = "gameplay_paused"
    }
    private var mInterstitialAdCompletion: InterstitialAd? = null
    private var mInterstitialAdOnExit: InterstitialAd? = null
    private final var TAG = "MainActivity"
    private var soundPool: SoundPool? = null
    var soundId: Int? = null
    private var previouslySelectedCell: SudokuCellView? = null
    private var notesMode = false
    private var autoNotesEnabled = false
    private var soundEffectsOn: Boolean = false
    private var currentTime: String = ""
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var timer: Timer
    private lateinit var sudokuControlView: SudokuControlView
    private lateinit var sudokuBoardView: SudokuBoardView
    private lateinit var timerTextView: TextView
    private lateinit var hintsCountTextView: TextView
    private lateinit var modeTextView: TextView
    private lateinit var difficultyTimeLabel: TextView
    private lateinit var pauseButton: ImageView
    private lateinit var adView: AdView
    private lateinit var generationOverlay: View
    private lateinit var generationProgress: ProgressBar
    private lateinit var generationStatusText: TextView
    private lateinit var generationRetryButton: Button
    private lateinit var generationBackButton: Button
    private lateinit var viewModel: SudokuViewModel
    private var gameplayDifficulty: SudokuDifficulty? = null
    private var hintSnackbar: Snackbar? = null
    private var pauseDialog: Dialog? = null
    private var completionDialog: Dialog? = null
    private var lastAutoNotesBoard: SudokuBoard? = null
    private var lastAutoNotesValues: IntArray? = null
    private var lastAutoNotesEditableCells: BooleanArray? = null
    private val hintTextFormatter by lazy { LogicalHintTextFormatter(this) }

    private val onBackPressedCallback: OnBackPressedCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showPauseMenu()


            }
        }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PREF_SOUND_EFFECTS -> {
                soundEffectsOn = sharedPreferences.getBoolean(PREF_SOUND_EFFECTS, true)
            }
            PREF_AUTO_NOTES -> {
                applyAutoNotesPreference(
                    sharedPreferences.getBoolean(PREF_AUTO_NOTES, false),
                    forceRecompute = true
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sudoku_board_view)

        val root = findViewById<android.view.View>(R.id.bg)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        setupSharedPreferences()
        val explicitResume = intent.getBooleanExtra("Resume", false)
        val activityRecreation = savedInstanceState != null
        val restorePauseAfterRecreation =
            savedInstanceState?.getBoolean(STATE_PAUSED) == true
        val launchMode = GameplayLaunchPolicy.mode(
            explicitResume = explicitResume,
            activityRecreation = activityRecreation,
            savedActiveGame = savedInstanceState?.getBoolean(STATE_ACTIVE_GAME) == true
        )
        val requestedDifficulty = if (explicitResume) {
            savedDifficulty()
        } else if (activityRecreation) {
            difficultyFromValue(savedInstanceState?.getString(STATE_REQUESTED_DIFFICULTY))
        } else {
            when (
                val mapping = GameplayDifficultyAdapter.fromExternalValue(
                    intent.getStringExtra(GameplayDifficultyAdapter.INTENT_EXTRA)
                )
            ) {
                is GameplayDifficultyMapping.Valid -> mapping.difficulty
                is GameplayDifficultyMapping.Invalid -> null
            }
        }
        gameplayDifficulty = requestedDifficulty
        viewModel = ViewModelProvider(
            this,
            SudokuViewModelFactory(
                application,
                launchMode == GameplayLaunchMode.NEW_GAME,
                requestedDifficulty
            )
        ).get(SudokuViewModel::class.java)

        setupViews()
        setupSound()
        timer = Timer(this)
        restoreTimerSnapshot(savedInstanceState?.getInt(STATE_TIMER_SECONDS) ?: 0)
        setupViewModel()

        val bannerHeight = adView.adSize?.getHeightInPixels(this) ?: 0
        var hasRenderedBanner = false

        fun updateBannerLayout(isRendered: Boolean) {
            sudokuControlView.setReclaimedBottomSpace(if (isRendered) 0 else bannerHeight)
        }

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                hasRenderedBanner = true
                updateBannerLayout(isRendered = true)
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                if (!hasRenderedBanner) {
                    updateBannerLayout(isRendered = false)
                }
            }
        }
        updateBannerLayout(isRendered = false)

        var adRequest = AdRequest.Builder().build()
        if (ConsentManager.canRequestAds()) {
            adView.loadAd(adRequest)
            InterstitialAd.load(this,"ca-app-pub-4002896469283656/4701071767", adRequest, object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, adError.toString())
                    mInterstitialAdCompletion = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(TAG, "Ad was loaded.")
                    mInterstitialAdCompletion = interstitialAd
                }
            })
        }
        if (viewModel.gameplayLoadState.value == GameplayLoadState.Idle) {
            when (launchMode) {
                GameplayLaunchMode.RESTORE_PERSISTED_GAME -> {
                    val game = loadGame()
                    if (game != null) {
                        viewModel.setHintsUsed(game.hintsUsed)
                        restoreTimerSnapshot(game.timeElapsed)
                        viewModel.setBoard(game.board)
                    } else {
                        viewModel.reportResumeUnavailable()
                    }
                }
                GameplayLaunchMode.RECREATED_WITHOUT_ACTIVE_GAME ->
                    viewModel.reportGenerationInterrupted()
                GameplayLaunchMode.NEW_GAME -> Unit
            }
        }

        if (ConsentManager.canRequestAds()) {
            InterstitialAd.load(this,"ca-app-pub-4002896469283656/2976818750", adRequest, object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, adError.toString())
                    mInterstitialAdOnExit = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(TAG, "Ad was loaded.")
                    mInterstitialAdOnExit = interstitialAd
                }
            })
        }

        if (viewModel.gameplayLoadState.value is GameplayLoadState.Ready) {
            timer.start()
        }
        if (
            restorePauseAfterRecreation &&
            viewModel.gameplayLoadState.value is GameplayLoadState.Ready
        ) {
            showPauseMenu(playFeedback = false)
        }


    }

    private fun setupViews() {
        sudokuBoardView = findViewById(R.id.sudokuBoardView)
        sudokuControlView = findViewById(R.id.sudokuControlView)
        timerTextView = findViewById(R.id.timerTextView)
        hintsCountTextView = findViewById(R.id.hintsCountTextView)
        modeTextView = findViewById(R.id.modeTextView)
        difficultyTimeLabel = findViewById(R.id.difficultyTimeLabel)
        pauseButton = findViewById(R.id.pauseButton)
        adView = findViewById(R.id.adView)
        generationOverlay = findViewById(R.id.generationOverlay)
        generationProgress = findViewById(R.id.generationProgress)
        generationStatusText = findViewById(R.id.generationStatusText)
        generationRetryButton = findViewById(R.id.generationRetryButton)
        generationBackButton = findViewById(R.id.generationBackButton)

        sudokuControlView.listener = this
        sudokuBoardView.cellSelectedListener = this

        pauseButton.setOnClickListener {
            showPauseMenu()

        }
        generationRetryButton.setOnClickListener {
            viewModel.retryPuzzleGeneration()
        }
        generationBackButton.setOnClickListener {
            exit()
        }
        renderDifficultyLabel()
        applyAutoNotesPreference(autoNotesEnabled, forceRecompute = false)
    }

    private fun setupViewModel() {
        viewModel.hintsUsed.observe(this) { hintsUsed ->
            hintsCountTextView.text = getString(R.string.gameplay_numeric_value, hintsUsed)
        }

        viewModel.sudokuBoard.observe(this, Observer { board ->
            handleBoardUpdate(board)
        })
        viewModel.gameplayLoadState.observe(this) { state ->
            renderGameplayLoadState(state)
        }
        viewModel.logicalHintResult.observe(this) { result ->
            renderLogicalHintResult(result)
        }
    }

    private fun renderLogicalHintResult(result: LogicalHintResult?) {
        if (result == null) {
            sudokuBoardView.setHintHighlights(emptyList())
            hintSnackbar?.dismiss()
            hintSnackbar = null
            return
        }

        val text = when (result) {
            is LogicalHintResult.Available -> {
                sudokuBoardView.setHintHighlights(result.hint.highlights)
                hintTextFormatter.format(result.hint)
            }
            LogicalHintResult.INCORRECT_VALUE_PRESENT -> {
                sudokuBoardView.setHintHighlights(emptyList())
                getString(R.string.hint_incorrect_value_present)
            }
            LogicalHintResult.INVALID_PLAYER_STATE -> {
                sudokuBoardView.setHintHighlights(emptyList())
                getString(R.string.hint_invalid_player_state)
            }
            LogicalHintResult.NO_SUPPORTED_LOGICAL_HINT -> {
                sudokuBoardView.setHintHighlights(emptyList())
                getString(R.string.hint_no_supported_logical_hint)
            }
            LogicalHintResult.SOLVED -> {
                sudokuBoardView.setHintHighlights(emptyList())
                getString(R.string.hint_solved)
            }
        }

        hintSnackbar?.dismiss()
        hintSnackbar = Snackbar.make(findViewById(R.id.bg), text, Snackbar.LENGTH_INDEFINITE)
            .setAnchorView(adView)
            .setAction(R.string.gameplay_hint_dismiss) {
                sudokuBoardView.setHintHighlights(emptyList())
            }
            .also { snackbar ->
                val snackbarView = snackbar.view
                snackbarView.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                snackbarView.background = ContextCompat.getDrawable(
                    this,
                    R.drawable.gameplay_hint_snackbar
                )
                snackbarView.elevation = resources.getDimension(R.dimen.gameplay_board_elevation)
                snackbarView.setPadding(dp(4), dp(28), dp(4), dp(4))
                val messageView = snackbarView.findViewById<TextView>(
                    com.google.android.material.R.id.snackbar_text
                )
                messageView.apply {
                    maxLines = Int.MAX_VALUE
                    ellipsize = null
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gameplay_ink))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = ResourcesCompat.getFont(this@MainActivity, R.font.ubuntu_regular)
                        ?: Typeface.create("sans-serif", Typeface.NORMAL)
                    setLineSpacing(dp(2).toFloat(), 1f)
                    this.text = styledHintText(text)
                }
                snackbarView.findViewById<TextView>(
                    com.google.android.material.R.id.snackbar_action
                ).apply {
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.zen_primary))
                    typeface = ResourcesCompat.getFont(this@MainActivity, R.font.ubuntu_medium)
                }
                val stage = getString(hintStageLabel(result))
                messageView.contentDescription = "$stage. $text"
                (snackbarView as? ViewGroup)?.addView(
                    TextView(this).apply {
                        this.text = stage
                        setTextColor(
                            ContextCompat.getColor(this@MainActivity, R.color.gameplay_ink_muted)
                        )
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        typeface = ResourcesCompat.getFont(
                            this@MainActivity,
                            R.font.ubuntu_medium
                        )
                        letterSpacing = 0.08f
                        isAllCaps = true
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.START
                        ).apply {
                            marginStart = dp(16)
                            topMargin = dp(7)
                        }
                    }
                )
                snackbar.show()
            }
    }

    private fun styledHintText(text: String): CharSequence {
        val styled = SpannableString(text)
        val firstSentenceEnd = text.indexOf('.').let { if (it >= 0) it + 1 else text.length }
        if (firstSentenceEnd > 0) {
            styled.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                firstSentenceEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return styled
    }

    private fun hintStageLabel(result: LogicalHintResult): Int = when (result) {
        is LogicalHintResult.Available -> when (result.hint.detailLevel) {
            HintDetailLevel.TECHNIQUE -> R.string.gameplay_hint_stage_technique
            HintDetailLevel.EVIDENCE -> R.string.gameplay_hint_stage_evidence
            HintDetailLevel.ACTION -> R.string.gameplay_hint_stage_action
        }
        else -> R.string.gameplay_hint_stage_message
    }

    private fun setupSound() {
        soundPool = SoundPool.Builder().setMaxStreams(1).build()
        soundId = soundPool?.load(this, R.raw.pop, 1)
    }

    private fun setupSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        soundEffectsOn = sharedPreferences.getBoolean(PREF_SOUND_EFFECTS, true)
        autoNotesEnabled = sharedPreferences.getBoolean(PREF_AUTO_NOTES, false)
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun handleBoardUpdate(board: SudokuBoard?) {
        sudokuBoardView.setBoard(board)
        updateAutoNotes(board)
        if (board == null) return
        viewModel.selectedCell.value?.let { cell ->
            if (cell.first !in 0..8 || cell.second !in 0..8) return@let
            board.getCell(cell.first, cell.second).let { value ->
                sudokuBoardView.updateSelectedCell(cell.first, cell.second, value)
            }
        }

        if (viewModel.isBoardCorrect()) {
            timer.pause()
            showCompletionDialog(viewModel.hintsUsed.value ?: 0, currentTime)
        }

        saveGame()
    }

    private fun renderGameplayLoadState(state: GameplayLoadState) {
        when (state) {
            GameplayLoadState.Idle,
            is GameplayLoadState.Loading -> {
                timer.pause()
                setGameplayAccessibilityHidden(true)
                generationOverlay.visibility = View.VISIBLE
                generationProgress.visibility = View.VISIBLE
                generationStatusText.setText(R.string.gameplay_generating_puzzle)
                generationRetryButton.visibility = View.GONE
                generationBackButton.visibility = View.GONE
            }
            is GameplayLoadState.Ready -> {
                gameplayDifficulty = state.requestedDifficulty ?: gameplayDifficulty
                renderDifficultyLabel()
                generationOverlay.visibility = View.GONE
                generationProgress.visibility = View.GONE
                setGameplayAccessibilityHidden(false)
                saveGame()
                if (
                    lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) &&
                    pauseDialog?.isShowing != true &&
                    completionDialog?.isShowing != true
                ) {
                    timer.start()
                }
            }
            is GameplayLoadState.Failure -> {
                timer.pause()
                setGameplayAccessibilityHidden(true)
                generationOverlay.visibility = View.VISIBLE
                generationProgress.visibility = View.GONE
                generationStatusText.setText(R.string.gameplay_generation_failed)
                generationRetryButton.visibility = if (state.requestedDifficulty == null) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
                generationBackButton.visibility = View.VISIBLE
                Log.e(TAG, "Gameplay puzzle unavailable: ${state.reason}")
            }
        }
    }

    private fun setGameplayAccessibilityHidden(hidden: Boolean) {
        sudokuBoardView.importantForAccessibility = if (hidden) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        sudokuControlView.importantForAccessibility = if (hidden) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    }

    private fun renderDifficultyLabel() {
        if (!::difficultyTimeLabel.isInitialized) return
        val label = when (gameplayDifficulty) {
            SudokuDifficulty.EASY -> getString(R.string.gameplay_difficulty_easy)
            SudokuDifficulty.MEDIUM -> getString(R.string.gameplay_difficulty_medium)
            SudokuDifficulty.HARD -> getString(R.string.gameplay_difficulty_hard)
            SudokuDifficulty.UNSUPPORTED, null -> null
        }
        difficultyTimeLabel.text = if (label == null) {
            getString(R.string.gameplay_difficulty_unknown)
        } else {
            getString(R.string.gameplay_difficulty_time, label)
        }
    }

    override fun onTimerUpdate(seconds: Int, timeString: String) {
        currentTime = timeString
        val timerText = String.format("%02d:%02d", seconds / 60, seconds % 60)
        updateTextViews(
            timerText,
            viewModel.hintsUsed.value ?: 0,
            gameplayModeText()
        )
    }

    private fun updateTextViews(timerText: String, hintsCount: Int, modeText: String) {
        timerTextView.text = timerText
        hintsCountTextView.text = getString(R.string.gameplay_numeric_value, hintsCount)
        modeTextView.text = modeText
        modeTextView.contentDescription = modeText
    }

    override fun onNotesModeChanged(notesMode: Boolean) {
        this.notesMode = notesMode && !autoNotesEnabled
        viewModel.setNotesMode(this.notesMode)
        val modeText = gameplayModeText()
        modeTextView.text = modeText
        modeTextView.contentDescription = modeText
        playSound()
    }

    override fun onNumberButtonClicked(value: Int) {
        viewModel.updateSelectedCellValue(value)
        playSound()
    }

    override fun onCellSelected(row: Int, col: Int) {
        previouslySelectedCell?.invalidate()
        previouslySelectedCell = sudokuBoardView.getCellView(row, col)
        viewModel.selectCell(row, col)
    }

    override fun onEraseButtonClicked() {
        viewModel.updateSelectedCellValue(0)
        playSound()
    }

    override fun onHintsButtonClicked() {
        viewModel.provideHint()
        playSound()
    }

    private fun playSound() {
        if (soundEffectsOn) {
            soundPool?.play(soundId ?: 0, 1F, 1F, 0, 0, 1F)
        }
        saveGame()
    }

    private fun showCompletionDialog(hintsUsed: Int, totalTime: String) {
        if (completionDialog?.isShowing == true) return
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_completion)

        ViewCompat.setAccessibilityPaneTitle(
            dialog.findViewById(R.id.completionDialogRoot),
            getString(R.string.completion_title)
        )
        ViewCompat.setAccessibilityHeading(
            dialog.findViewById(R.id.completionDialogTitle),
            true
        )

        dialog.findViewById<TextView>(R.id.hintsUsedTextView).text = getString(
            R.string.completion_hints,
            hintsUsed
        )
        dialog.findViewById<TextView>(R.id.totalTimeTextView).text = getString(
            R.string.completion_time,
            totalTime.ifBlank { getString(R.string.gameplay_timer_zero) }
        )
        dialog.findViewById<TextView>(R.id.completionDifficultyTextView).apply {
            val difficulty = difficultyDisplayName()
            if (difficulty == null) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = getString(R.string.completion_difficulty, difficulty)
            }
        }

        dialog.findViewById<Button>(R.id.nextLevelButton).setOnClickListener {
            if (mInterstitialAdCompletion != null) {
                mInterstitialAdCompletion?.show(this)
            } else {
                Log.d(TAG, "The interstitial ad wasn't ready yet.")
            }
            performPostAdActions(dialog)
        }
        dialog.setOnDismissListener {
            if (completionDialog === dialog) completionDialog = null
        }
        completionDialog = dialog
        dialog.show()
        configureFullscreenDialog(dialog)
    }

    private fun performPostAdActions(dialog: Dialog) {
        if (!viewModel.canGenerateNextPuzzle()) {
            dialog.dismiss()
            openDifficultySelection()
            return
        }
        viewModel.loadNextPuzzle() // Load the next puzzle
        sudokuBoardView.invalidate()
        sudokuBoardView.invalidateAllCells()
        timer.reset() // Reset the timer
        restoreTimerSnapshot(0)
        playSound()
        dialog.dismiss()
    }

    private fun showPauseMenu(playFeedback: Boolean = true) {
        if (pauseDialog?.isShowing == true || completionDialog?.isShowing == true) return
        timer.pause()
        saveGame()
        if (playFeedback) playSound()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.pause_menu)

        ViewCompat.setAccessibilityPaneTitle(
            dialog.findViewById(R.id.pauseDialogRoot),
            getString(R.string.pause_title)
        )
        ViewCompat.setAccessibilityHeading(
            dialog.findViewById(R.id.pauseDialogTitle),
            true
        )

        val resumeBtn = dialog.findViewById<Button>(R.id.resume_bttn)
        val optionsBtn = dialog.findViewById<Button>(R.id.options_bttn)
        val exitBtn = dialog.findViewById<Button>(R.id.exit_bttn)

        resumeBtn.setOnClickListener {
            if (viewModel.gameplayLoadState.value is GameplayLoadState.Ready) {
                timer.start()
            }
            dialog.dismiss()
        }
        optionsBtn.setOnClickListener {
            val intent = Intent(this, OptionsActivity::class.java)
            startActivity(intent)
        }
        exitBtn.setOnClickListener {
            saveGame()
            if (mInterstitialAdOnExit != null) {
                mInterstitialAdOnExit?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        // Perform actions after ad is dismissed
                        exit()
                    }

                    override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                        Log.d(TAG, "The interstitial ad failed to show.")
                        // Perform actions even if the ad fails to show
                        exit()
                    }

                    override fun onAdShowedFullScreenContent() {
                        // Called when ad is shown.
                        mInterstitialAdOnExit = null
                    }
                }

                mInterstitialAdOnExit?.show(this)
            } else {
                Log.d(TAG, "The interstitial ad wasn't ready yet.")
                // Perform actions if the ad wasn't ready
                exit()

            }
        }
        dialog.setOnDismissListener {
            if (pauseDialog === dialog) pauseDialog = null
        }
        pauseDialog = dialog
        dialog.show()
        configureFullscreenDialog(dialog)
    }

    @Suppress("DEPRECATION")
    private fun configureFullscreenDialog(dialog: Dialog) {
        dialog.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            navigationBarColor = ContextCompat.getColor(
                this@MainActivity,
                R.color.zen_navigation_bar
            )
            WindowInsetsControllerCompat(this, decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    private fun difficultyDisplayName(): String? = when (gameplayDifficulty) {
        SudokuDifficulty.EASY -> getString(R.string.menu_easy)
        SudokuDifficulty.MEDIUM -> getString(R.string.menu_medium)
        SudokuDifficulty.HARD -> getString(R.string.menu_hard)
        SudokuDifficulty.UNSUPPORTED, null -> null
    }

    private fun exit(){
        startActivity(Intent(this, MenuHostActivity::class.java))
        finish()
    }

    private fun openDifficultySelection() {
        startActivity(
            Intent(this, MenuHostActivity::class.java)
                .putExtra(MenuHostActivity.EXTRA_OPEN_DIFFICULTY, true)
        )
        finish()
    }

    override fun onPause() {
        super.onPause()
        timer.pause()
        saveGame()
    }

    override fun onResume() {
        super.onResume()
        val preferenceEnabled = sharedPreferences.getBoolean(PREF_AUTO_NOTES, false)
        if (preferenceEnabled != autoNotesEnabled) {
            applyAutoNotesPreference(preferenceEnabled, forceRecompute = true)
        }
        if (
            viewModel.gameplayLoadState.value is GameplayLoadState.Ready &&
            pauseDialog?.isShowing != true &&
            completionDialog?.isShowing != true
        ) {
            timer.start()
        }
    }

    private fun applyAutoNotesPreference(enabled: Boolean, forceRecompute: Boolean) {
        autoNotesEnabled = enabled
        if (enabled) {
            notesMode = false
            if (::viewModel.isInitialized) {
                viewModel.setNotesMode(false)
            }
        }
        if (::sudokuControlView.isInitialized) {
            sudokuControlView.setAutoNotesEnabled(enabled)
        }
        if (::modeTextView.isInitialized) {
            modeTextView.text = gameplayModeText()
            modeTextView.contentDescription = gameplayModeText()
        }
        if (::sudokuBoardView.isInitialized) {
            updateAutoNotes(
                if (::viewModel.isInitialized) viewModel.sudokuBoard.value else null,
                forceRecompute
            )
        }
    }

    private fun updateAutoNotes(board: SudokuBoard?, forceRecompute: Boolean = false) {
        if (!autoNotesEnabled || board == null) {
            lastAutoNotesBoard = null
            lastAutoNotesValues = null
            lastAutoNotesEditableCells = null
            sudokuBoardView.setAutoNotes(null)
            return
        }

        val values = board.playerValues()
        val editableCells = board.editableCells()
        val unchanged = lastAutoNotesBoard === board &&
            lastAutoNotesValues?.contentEquals(values) == true &&
            lastAutoNotesEditableCells?.contentEquals(editableCells) == true
        if (!forceRecompute && unchanged) return

        lastAutoNotesBoard = board
        lastAutoNotesValues = values.copyOf()
        lastAutoNotesEditableCells = editableCells.copyOf()
        sudokuBoardView.setAutoNotes(AutoNotesCalculator.compute(values, editableCells))
    }

    private fun gameplayModeText(): String = getString(
        when {
            autoNotesEnabled -> R.string.gameplay_auto_notes_mode
            notesMode -> R.string.gameplay_notes_mode
            else -> R.string.gameplay_normal_mode
        }
    )

    override fun onDestroy() {
        hintSnackbar?.dismiss()
        pauseDialog?.dismiss()
        completionDialog?.dismiss()
        super.onDestroy()
        timer.destroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun restoreTimerSnapshot(seconds: Int) {
        val safeSeconds = seconds.coerceAtLeast(0)
        timer.seconds = safeSeconds
        currentTime = String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60)
        timerTextView.text = currentTime
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val state = viewModel.gameplayLoadState.value
        outState.putBoolean(
            STATE_ACTIVE_GAME,
            state is GameplayLoadState.Ready && viewModel.sudokuBoard.value != null
        )
        outState.putInt(STATE_TIMER_SECONDS, timer.seconds)
        outState.putBoolean(STATE_PAUSED, pauseDialog?.isShowing == true)
        val difficulty = when (state) {
            is GameplayLoadState.Loading -> state.requestedDifficulty
            is GameplayLoadState.Ready -> state.requestedDifficulty
            is GameplayLoadState.Failure -> state.requestedDifficulty
            GameplayLoadState.Idle, null -> null
        }
        difficulty?.let {
            outState.putString(
                STATE_REQUESTED_DIFFICULTY,
                GameplayDifficultyAdapter.toExternalValue(it)
            )
        }
        super.onSaveInstanceState(outState)
    }


    private fun saveGame() {
        val board = viewModel.sudokuBoard.value
        val hintsUsed = viewModel.hintsUsed.value

        if (board != null && hintsUsed != null) {
            val game = SavedGameState(board, hintsUsed, timer.seconds)
            val editor = sharedPreferences.edit()
            val gson = Gson()
            val json = gson.toJson(game)
            editor.putString(PREF_SAVED_GAME, json)
            gameplayDifficulty?.let { difficulty ->
                editor.putString(
                    PREF_SAVED_DIFFICULTY,
                    GameplayDifficultyAdapter.toExternalValue(difficulty)
                )
            }
            editor.apply()
        }
    }


    private fun loadGame(): SavedGameState? {
        return try {
            val gson = Gson()
            val json = sharedPreferences.getString(PREF_SAVED_GAME, null) ?: return null
            val game = gson.fromJson(json, SavedGameState::class.java)
            Log.d("MainActivity", "Loaded game: $game")
            game
        } catch (exception: RuntimeException) {
            Log.e("MainActivity", "Saved game could not be loaded", exception)
            null
        }
    }

    private fun savedDifficulty(): SudokuDifficulty? = when (
        val mapping = GameplayDifficultyAdapter.fromExternalValue(
            sharedPreferences.getString(PREF_SAVED_DIFFICULTY, null)
        )
    ) {
        is GameplayDifficultyMapping.Valid -> mapping.difficulty
        is GameplayDifficultyMapping.Invalid -> null
    }

    private fun difficultyFromValue(value: String?): SudokuDifficulty? = when (
        val mapping = GameplayDifficultyAdapter.fromExternalValue(value)
    ) {
        is GameplayDifficultyMapping.Valid -> mapping.difficulty
        is GameplayDifficultyMapping.Invalid -> null
    }

}



interface TimerListener {
    fun onTimerUpdate(seconds: Int, timeString: String)
}
