package com.example.flightinstruments

import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager

data class SatelliteData(
    val inUse: Int = 0,
    val inView: Int = 0,
    val beidou: Int = 0,
    val gps: Int = 0,
    val glonass: Int = 0
)

class SatelliteHub(
    private val context: Context,
    private val onData: (SatelliteData) -> Unit
) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var inUse = 0
            var inView = 0
            var beidou = 0
            var gps = 0
            var glonass = 0

            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) {
                    inUse++
                }
                inView++

                when (status.getConstellationType(i)) {
                    GnssStatus.CONSTELLATION_BEIDOU -> beidou++
                    GnssStatus.CONSTELLATION_GPS -> gps++
                    GnssStatus.CONSTELLATION_GLONASS -> glonass++
                }
            }

            onData(
                SatelliteData(
                    inUse = inUse,
                    inView = inView,
                    beidou = beidou,
                    gps = gps,
                    glonass = glonass
                )
            )
        }
    }

    fun start() {
        try {
            locationManager.registerGnssStatusCallback(gnssCallback)
        } catch (_: SecurityException) {
        }
    }

    fun stop() {
        locationManager.unregisterGnssStatusCallback(gnssCallback)
    }
}
