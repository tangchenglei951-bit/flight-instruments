package com.example.flightinstruments

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class CpuMonitor {

    private val running = AtomicBoolean(false)
    private var workers = emptyList<Thread>()

    fun coreCount(): Int = Runtime.getRuntime().availableProcessors()

    fun currentFrequenciesKHz(): List<Int> {
        val result = mutableListOf<Int>()
        for (i in 0 until coreCount()) {
            try {
                val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
                val text = File(path).readText().trim()
                result.add(text.toIntOrNull() ?: 0)
            } catch (_: Exception) {
                result.add(0)
            }
        }
        return result
    }

    fun isRunning(): Boolean = running.get()

    fun startStress() {
        if (running.get()) return
        running.set(true)

        workers = (0 until coreCount()).map {
            Thread {
                var x = 0L
                while (running.get()) {
                    x = x * 6364136223846793005L + 1442695040888963407L
                }
            }.apply {
                isDaemon = true
                start()
            }
        }
    }

    fun stopStress() {
        running.set(false)
        workers = emptyList()
    }
}
