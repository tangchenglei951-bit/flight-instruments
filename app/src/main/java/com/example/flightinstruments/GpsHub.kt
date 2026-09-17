package com.example.flightinstruments

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

data class GpsData(
    val valid: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speedKmh: Float = 0f,
    val bearing: Float = 0f,
    val accuracy: Float = 0f
)

class GpsHub(
    private val context: Context,
    private val onData: (GpsData) -> Unit
) : LocationListener {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Suppress("MissingPermission")
    fun start() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                this,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
        }
    }

    fun stop() {
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        onData(
            GpsData(
                valid = true,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                speedKmh = location.speed * 3.6f,
                bearing = location.bearing,
                accuracy = location.accuracy
            )
        )
    }

    override fun onProviderDisabled(provider: String) {
        onData(GpsData(valid = false))
    }

    override fun onProviderEnabled(provider: String) {
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }
}
