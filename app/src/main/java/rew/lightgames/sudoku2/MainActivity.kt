package rew.lightgames.sudoku2

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.SoundPool
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.gson.Gson
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class MainActivity : AppCompatActivity(), SudokuControlListener, OnCellSelectedListener, TimerListener {
    companion object {
        const val PREF_NAME = "my_preferences"
        private const val PREF_SOUND_EFFECTS = "sound_effects"
    }
    private var mInterstitialAdCompletion: InterstitialAd? = null
    private var mInterstitialAdOnExit: InterstitialAd? = null
    private final var TAG = "MainActivity"
    private var soundPool: SoundPool? = null
    var soundId: Int? = null
    private var previouslySelectedCell: SudokuCellView? = null
    private var notesMode = false
    private var soundEffectsOn: Boolean = false
    private var hintCount = 0
    private lateinit var currentTime: String
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var timer: Timer
    private lateinit var sudokuControlView: SudokuControlView
    private lateinit var sudokuBoardView: SudokuBoardView
    private lateinit var timerTextView: TextView
    private lateinit var hintsCountTextView: TextView
    private lateinit var modeTextView: TextView
    private lateinit var pauseButton: ImageView
    private lateinit var viewModel: SudokuViewModel

    private val onBackPressedCallback: OnBackPressedCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showPauseMenu()


            }
        }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF_SOUND_EFFECTS) {
            soundEffectsOn = sharedPreferences.getBoolean(PREF_SOUND_EFFECTS, true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sudoku_board_view)

        val root = findViewById<android.view.View>(R.id.bg)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        val shouldResume = intent.getBooleanExtra("Resume", false)
        viewModel = ViewModelProvider(
            this,
            SudokuViewModelFactory(application, !shouldResume)
        ).get(SudokuViewModel::class.java)

        setupViews()
        setupViewModel()
        setupSound()
        setupSharedPreferences()

        val adView = findViewById<AdView>(R.id.adView)
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
        timer = Timer(this)

        if (intent.getBooleanExtra("Resume", false)) {
            val game = loadGame()
            if (game != null) {
                // Restore the game state
                viewModel.setBoard(game.board)
                viewModel.setHintsUsed(game.hintsUsed)
                timer.seconds = game.timeElapsed.toInt()

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

        timer.start()


    }

    private fun setupViews() {
        sudokuBoardView = findViewById(R.id.sudokuBoardView)
        sudokuControlView = findViewById(R.id.sudokuControlView)
        timerTextView = findViewById(R.id.timerTextView)
        hintsCountTextView = findViewById(R.id.hintsCountTextView)
        modeTextView = findViewById(R.id.modeTextView)
        pauseButton = findViewById(R.id.pauseButton)

        sudokuControlView.listener = this
        sudokuBoardView.cellSelectedListener = this

        pauseButton.setOnClickListener {
            showPauseMenu()

        }
    }

    private fun setupViewModel() {
        viewModel.hintsUsed.observe(this) { hintsUsed ->
            hintsCountTextView.text = getString(R.string.gameplay_numeric_value, hintsUsed)
        }

        viewModel.sudokuBoard.observe(this, Observer { board ->
            handleBoardUpdate(board)
        })
    }

    private fun setupSound() {
        soundPool = SoundPool.Builder().setMaxStreams(1).build()
        soundId = soundPool?.load(this, R.raw.pop, 1)
    }

    private fun setupSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        soundEffectsOn = sharedPreferences.getBoolean(PREF_SOUND_EFFECTS, true)
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun handleBoardUpdate(board: SudokuBoard?) {
        sudokuBoardView.setBoard(board)
        viewModel.selectedCell.value?.let { cell ->
            board?.getCell(cell.first, cell.second)?.let { value ->
                sudokuBoardView.updateSelectedCell(cell.first, cell.second, value)
            }
        }

        if (viewModel.isBoardCorrect()) {
            timer.pause()
            showCompletionDialog(hintCount, currentTime)
        }

        saveGame()
    }

    override fun onTimerUpdate(seconds: Int, timeString: String) {
        currentTime = timeString
        val timerText = String.format("%02d:%02d", seconds / 60, seconds % 60)
        updateTextViews(
            timerText,
            viewModel.hintsUsed.value ?: 0,
            if (notesMode) getString(R.string.gameplay_notes_mode) else getString(R.string.gameplay_normal_mode)
        )
    }

    private fun updateTextViews(timerText: String, hintsCount: Int, modeText: String) {
        timerTextView.text = timerText
        hintsCountTextView.text = getString(R.string.gameplay_numeric_value, hintsCount)
        modeTextView.text = modeText
    }

    override fun onNotesModeChanged(notesMode: Boolean) {
        this.notesMode = notesMode
        viewModel.toggleNotesMode()
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
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_completion)

        val body = dialog.findViewById(R.id.completionImageView) as ImageView
        body.setImageResource(R.drawable.ic_lvl_complete_popup)

        val hintsTextView = dialog.findViewById(R.id.hintsUsedTextView) as TextView
        hintsTextView.text = getString(
            R.string.gameplay_hints_used,
            viewModel.hintsUsed.value ?: 0
        )

        val timeTextView = dialog.findViewById(R.id.totalTimeTextView) as TextView
        timeTextView.text = "Total time: $totalTime"

        val yesBtn = dialog.findViewById(R.id.nextLevelButton) as ImageView
        val window = dialog.window
        if (window != null) {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        yesBtn.setOnClickListener {
            if (mInterstitialAdCompletion != null) {
                mInterstitialAdCompletion?.show(this)
            } else {
                Log.d(TAG, "The interstitial ad wasn't ready yet.")
            }
            performPostAdActions(dialog)

        }

        dialog.show()
    }

    private fun performPostAdActions(dialog: Dialog) {
        viewModel
        viewModel.loadNextPuzzle() // Load the next puzzle
        sudokuBoardView.invalidate()
        sudokuBoardView.invalidateAllCells()
        timer.reset() // Reset the timer
        timer.start() // Start the timer for the new puzzle
        playSound()
        dialog.dismiss()
    }

    private fun showPauseMenu() {
        timer.pause()
        saveGame()
        playSound()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.pause_menu)

        val body = dialog.findViewById(R.id.pauseMenuImageView) as ImageView
        body.setImageResource(R.drawable.ic_menu_cloud)

        val resumeBtn = dialog.findViewById(R.id.resume_bttn) as ImageView
        val optionsBtn = dialog.findViewById(R.id.options_bttn) as ImageView
        val exitBtn = dialog.findViewById(R.id.exit_bttn) as ImageView
        val window = dialog.window
        if (window != null) {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        resumeBtn.setOnClickListener {

            timer.start() // Start the timer for the new puzzle
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



        dialog.show()
    }

    private fun exit(){
        startActivity(Intent(this, MenuHostActivity::class.java))
        finish()
    }

    override fun onPause() {
        super.onPause()
        timer.pause()
        saveGame()
    }

    override fun onResume() {
        super.onResume()
        timer.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer.destroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }


    private fun saveGame() {
        val board = viewModel.sudokuBoard.value
        val hintsUsed = viewModel.hintsUsed.value

        if (board != null && hintsUsed != null) {
            val game = SavedGameState(board, hintsUsed, timer.seconds)
            val editor = sharedPreferences.edit()
            val gson = Gson()
            val json = gson.toJson(game)
            editor.putString("saved_game", json)
            editor.apply()
        }
    }


    private fun loadGame(): SavedGameState? {
        val gson = Gson()
        val json = sharedPreferences.getString("saved_game", null)
        val game = gson.fromJson(json, SavedGameState::class.java)
        Log.d("MainActivity", "Loaded game: $game")
        return game
    }

}



interface TimerListener {
    fun onTimerUpdate(seconds: Int, timeString: String)
}
