package com.example.flightinstruments

class TripRecorder {

    companion object {
        private const val MOVE_THRESHOLD_MS = 12.0 / 3.6
    }

    private var started = false
    private var previousTime = 0L

    var mileMeters = 0.0
        private set
    var totalTimeMs = 0L
        private set
    var waitTimeMs = 0L
        private set
    var moveTimeMs = 0L
        private set
    var averageSpeedMs = 0.0
        private set
    var averageMoveSpeedMs = 0.0
        private set
    var currentSpeedMs = 0.0
        private set

    fun start() {
        if (started) return
        started = true
        previousTime = System.currentTimeMillis()
        reset()
    }

    fun stop() {
        if (!started) return
        started = false
        reset()
    }

    fun update(speedMs: Double) {
        if (!started) return

        val now = System.currentTimeMillis()
        val intervalMs = now - previousTime
        if (intervalMs <= 0) return

        previousTime = now
        val intervalSec = intervalMs / 1000.0
        mileMeters += (currentSpeedMs + speedMs) * 0.5 * intervalSec
        totalTimeMs += intervalMs

        if (speedMs > MOVE_THRESHOLD_MS) {
            moveTimeMs += intervalMs
        } else {
            waitTimeMs += intervalMs
        }

        averageSpeedMs = if (totalTimeMs > 0) mileMeters * 1000.0 / totalTimeMs else 0.0
        averageMoveSpeedMs = if (moveTimeMs > 0) mileMeters * 1000.0 / moveTimeMs else 0.0
        currentSpeedMs = speedMs
    }

    fun summary(): String {
        if (totalTimeMs == 0L) return "里程/均速 未开始"
        val percent = if (totalTimeMs > 0) moveTimeMs * 100 / totalTimeMs else 0
        return "%.2f km  均速%.1f/%.1f km/h  %d/%d/%d min  %d%%".format(
            mileMeters / 1000.0,
            averageSpeedMs * 3.6,
            averageMoveSpeedMs * 3.6,
            totalTimeMs / 60000,
            waitTimeMs / 60000,
            moveTimeMs / 60000,
            percent
        )
    }

    private fun reset() {
        mileMeters = 0.0
        totalTimeMs = 0
        waitTimeMs = 0
        moveTimeMs = 0
        averageSpeedMs = 0.0
        averageMoveSpeedMs = 0.0
        currentSpeedMs = 0.0
    }
}
