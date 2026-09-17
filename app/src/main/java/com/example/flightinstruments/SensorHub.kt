package com.example.flightinstruments

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

data class SensorData(
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val heading: Float = 0f,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val magX: Float = 0f,
    val magY: Float = 0f,
    val magZ: Float = 0f,
    val lightLux: Float = 0f,
    val proximity: Float = 0f,
    val pressureHpa: Float = 0f,
    val verticalSpeed: Float = 0f,
    val turnRate: Float = 0f
)

class SensorHub(
    private val context: Context,
    private val onData: (SensorData) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)
    private var state = SensorData()

    fun start() {
        register(Sensor.TYPE_ROTATION_VECTOR, SensorManager.SENSOR_DELAY_GAME)
        register(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME)
        register(Sensor.TYPE_GYROSCOPE, SensorManager.SENSOR_DELAY_GAME)
        register(Sensor.TYPE_MAGNETIC_FIELD, SensorManager.SENSOR_DELAY_GAME)
        register(Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_NORMAL)
        register(Sensor.TYPE_PROXIMITY, SensorManager.SENSOR_DELAY_NORMAL)
        register(Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        state = SensorData()
    }

    private fun register(type: Int, delay: Int) {
        val sensor = sensorManager.getDefaultSensor(type)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, delay)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationValues)
                val azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                val pitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
                val roll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()
                state = state.copy(
                    pitch = pitch,
                    roll = roll,
                    heading = ((azimuth + 360f) % 360f)
                )
            }
            Sensor.TYPE_ACCELEROMETER -> state = state.copy(
                accelX = event.values[0],
                accelY = event.values[1],
                accelZ = event.values[2]
            )
            Sensor.TYPE_GYROSCOPE -> state = state.copy(
                gyroX = event.values[0],
                gyroY = event.values[1],
                gyroZ = event.values[2],
                verticalSpeed = event.values[0] * 5f,
                turnRate = event.values[1]
            )
            Sensor.TYPE_MAGNETIC_FIELD -> state = state.copy(
                magX = event.values[0],
                magY = event.values[1],
                magZ = event.values[2]
            )
            Sensor.TYPE_LIGHT -> state = state.copy(lightLux = event.values[0])
            Sensor.TYPE_PROXIMITY -> state = state.copy(proximity = event.values[0])
            Sensor.TYPE_PRESSURE -> state = state.copy(pressureHpa = event.values[0])
        }

        onData(state)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}
