package com.example.flightinstruments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var instrumentView: FlightInstrumentView
    private lateinit var btnCpu: Button
    private lateinit var btnNetwork: Button
    private lateinit var btnSensor: Button
    private lateinit var btnMode: Button

    private lateinit var sensorHub: SensorHub
    private lateinit var gpsHub: GpsHub
    private lateinit var satelliteHub: SatelliteHub
    private lateinit var networkTester: NetworkSpeedTester
    private lateinit var reverseGeocoder: ReverseGeocoder
    private lateinit var speechHelper: SpeechHelper

    private val cpuMonitor = CpuMonitor()
    private val tripRecorder = TripRecorder()

    private var sensorStarted = false
    private var cpuStarted = false
    private var networkStarted = false
    private var displayMode = FlightInstrumentView.MODE_INSTRUMENT

    private var sensor = SensorData()
    private var gps = GpsData()
    private var satellite = SatelliteData()
    private var reverseText = ""

    private val handler = Handler(Looper.getMainLooper())
    private val debugLog = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        instrumentView = findViewById(R.id.instrumentView)
        btnCpu = findViewById(R.id.btnCpu)
        btnNetwork = findViewById(R.id.btnNetwork)
        btnSensor = findViewById(R.id.btnSensor)
        btnMode = findViewById(R.id.btnMode)

        initHubs()
        initButtons()

        if (hasLocationPermission()) {
            startLocationServices()
        } else {
            requestLocationPermission()
        }

        handler.post(periodicTask)
    }

    private fun initHubs() {
        sensorHub = SensorHub(this) { data ->
            sensor = data
            instrumentView.setFlightData(
                data.roll, data.pitch, data.heading,
                gps.speedKmh,
                if (gps.valid) gps.altitude.toFloat() else 0f,
                data.verticalSpeed,
                data.turnRate,
                0f
            )
            instrumentView.sensorText =
                "加速度 ${data.accelX.format(2)} ${data.accelY.format(2)} ${data.accelZ.format(2)}  " +
                "陀螺 ${data.gyroX.format(2)} ${data.gyroY.format(2)} ${data.gyroZ.format(2)}  " +
                "磁力 ${data.magX.format(2)} ${data.magY.format(2)} ${data.magZ.format(2)}"
        }

        gpsHub = GpsHub(this) { data ->
            gps = data
            if (data.valid) {
                tripRecorder.update(data.speedKmh / 3.6)
                instrumentView.gpsText =
                    "经纬 ${data.latitude.format(6)}, ${data.longitude.format(6)}  " +
                    "精度 ${data.accuracy.format(1)}m"
                instrumentView.setNavData(sensor.heading, data.bearing, 0f, data.accuracy / 1000f)
                instrumentView.invalidate()

                reverseGeocoder.reverse(data.latitude, data.longitude) { result ->
                    runOnUiThread {
                        reverseText = result.recommend.ifBlank { result.address }
                    }
                }
            } else {
                instrumentView.gpsText = "定位不可用"
                instrumentView.invalidate()
            }
        }

        satelliteHub = SatelliteHub(this) { data ->
            satellite = data
            instrumentView.satellitesInUse = data.inUse
            instrumentView.satellitesInView = data.inView
            instrumentView.satelliteText =
                "卫星 使用${data.inUse} 可见${data.inView}  " +
                "北斗${data.beidou} GPS${data.gps} GLONASS${data.glonass}"
            instrumentView.invalidate()
        }

        networkTester = NetworkSpeedTester { message ->
            runOnUiThread { debugLog.appendLine(message) }
        }

        reverseGeocoder = ReverseGeocoder(this)
        speechHelper = SpeechHelper(this)
    }

    private fun initButtons() {
        btnCpu.setOnClickListener {
            if (cpuStarted) {
                cpuMonitor.stopStress()
                cpuStarted = false
                btnCpu.text = getString(R.string.btn_cpu)
            } else {
                cpuMonitor.startStress()
                cpuStarted = true
                btnCpu.text = "CPU测试中"
            }
        }

        btnNetwork.setOnClickListener {
            if (networkStarted) {
                networkTester.stop()
                networkStarted = false
                btnNetwork.text = getString(R.string.btn_network)
            } else {
                networkTester.start()
                networkStarted = true
                btnNetwork.text = "网络测试中"
            }
        }

        btnSensor.setOnClickListener {
            if (sensorStarted) {
                stopSensors()
            } else {
                showSpeechDialog()
            }
        }

        btnMode.setOnClickListener {
            displayMode = (displayMode + 1) % 3
            instrumentView.mode = displayMode
            instrumentView.invalidate()
            when (displayMode) {
                FlightInstrumentView.MODE_TEXT -> btnMode.text = getString(R.string.mode_text)
                FlightInstrumentView.MODE_DEBUG -> btnMode.text = getString(R.string.mode_debug)
                else -> btnMode.text = getString(R.string.mode_instrument)
            }
        }
    }

    private fun showSpeechDialog() {
        AlertDialog.Builder(this)
            .setTitle("语音播放")
            .setMessage("是否打开语音播报功能？\n请在系统设置中选择中文语音引擎。")
            .setNegativeButton("否") { _, _ ->
                speechHelper.setEnabled(false)
                startSensors()
            }
            .setPositiveButton("是") { _, _ ->
                speechHelper.setEnabled(true)
                startSensors()
            }
            .show()
    }

    private fun startSensors() {
        sensorStarted = true
        sensorHub.start()
        startLocationServices()
        tripRecorder.start()
        btnSensor.text = getString(R.string.btn_sensor_on)
    }

    private fun stopSensors() {
        sensorStarted = false
        sensorHub.stop()
        gpsHub.stop()
        satelliteHub.stop()
        tripRecorder.stop()
        speechHelper.setEnabled(false)
        btnSensor.text = getString(R.string.btn_sensor_off)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationServices() {
        if (!hasLocationPermission()) return
        gpsHub.start()
        satelliteHub.start()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationServices()
        }
    }

    private val periodicTask = object : Runnable {
        override fun run() {
            updatePeriodicText()
            handler.postDelayed(this, 1000)
        }
    }

    private fun updatePeriodicText() {
        val freqs = cpuMonitor.currentFrequenciesKHz()
        instrumentView.cpuText =
            "CPU ${cpuMonitor.coreCount()}核  " +
            freqs.joinToString(" ") { "${it / 1000}MHz" }

        val speed = networkTester.readSpeed()
        instrumentView.networkText =
            "网速 ${speed / 1024} KB/s  (${speed * 8 / 1000} Kbps)"

        instrumentView.tripText = tripRecorder.summary()
        instrumentView.invalidate()

        val speechText = buildString {
            if (gps.valid) {
                append("速度 ${gps.speedKmh.toInt()},")
                append("高度 ${gps.altitude.toInt()},")
            }
            append("方向 ${sensor.heading.toInt()},")
            if (reverseText.isNotBlank()) {
                append("位置 $reverseText")
            }
        }
        speechHelper.speak(speechText, 30_000L)

        debugLog.appendLine(
            "${System.currentTimeMillis()} cpu=$cpuStarted net=$networkStarted sensor=$sensorStarted"
        )
        instrumentView.debugText = debugLog.toString().takeLast(2000)
    }

    private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
    private fun Double.format(digits: Int): String = "%.${digits}f".format(this)

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        sensorHub.stop()
        gpsHub.stop()
        satelliteHub.stop()
        networkTester.stop()
        cpuMonitor.stopStress()
        speechHelper.shutdown()
        reverseGeocoder.close()
    }

    companion object {
        private const val REQUEST_LOCATION = 1001
    }
}
