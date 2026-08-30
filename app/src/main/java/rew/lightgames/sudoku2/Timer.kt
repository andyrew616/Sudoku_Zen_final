package rew.lightgames.sudoku2

import android.os.Handler
import android.os.Looper

class Timer(private val listener: TimerListener) {
    var timeElapsed: Long = 0
    get() = seconds.toLong()
    private val handler = Handler(Looper.getMainLooper())
    var seconds = 0
    private var isRunning = false

    private val runnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                seconds++

                // Convert seconds to minutes and remaining seconds
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                val timeString = String.format("%02d:%02d", minutes, remainingSeconds)

                listener.onTimerUpdate(seconds, timeString) // pass the timeString to the listener

                handler.postDelayed(this, 1000)
            }
        }
    }

    fun start() {
        if (!isRunning) {
            isRunning = true
            handler.post(runnable)

        }
    }

    fun pause() {
        isRunning = false
        handler.removeCallbacks(runnable)
    }

    fun reset() {
        pause()
        seconds = 0
    }

    fun destroy() {
        pause()
    }
}
