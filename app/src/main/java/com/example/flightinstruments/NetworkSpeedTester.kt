package com.example.flightinstruments

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class NetworkSpeedTester(
    private val onMessage: (String) -> Unit
) {
    companion object {
        private const val THREAD_COUNT = 8
        private const val TEST_URL =
            "https://speed.cloudflare.com/__down?bytes=104857600"
        private const val SEGMENT_BYTES = 13_107_200L // 每个线程 12.5MB
    }

    private val running = AtomicBoolean(false)
    private val received = AtomicLong(0)
    private var lastReceived = 0L
    private var workers = emptyList<Thread>()

    fun start() {
        if (running.get()) return

        received.set(0)
        lastReceived = 0
        running.set(true)

        workers = (0 until THREAD_COUNT).map { index ->
            Thread {
                val start = index * SEGMENT_BYTES
                val end = start + SEGMENT_BYTES - 1
                while (running.get()) {
                    try {
                        downloadRange(start, end)
                    } catch (_: Exception) {
                        Thread.sleep(500)
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
        }
    }

    fun stop() {
        running.set(false)
    }

    fun isRunning(): Boolean = running.get()

    fun readSpeed(): Long {
        val total = received.get()
        val speed = total - lastReceived
        lastReceived = total
        return speed
    }

    private fun downloadRange(start: Long, end: Long) {
        val connection = URL(TEST_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 30000
            connection.setRequestProperty("Range", "bytes=$start-$end")
            connection.inputStream.use { input ->
                val buffer = ByteArray(32 * 1024)
                while (running.get()) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    received.addAndGet(read.toLong())
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
