package com.example.flightinstruments

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class ReverseGeoData(
    val address: String = "",
    val recommend: String = ""
)

class ReverseGeocoder(context: Context) {

    companion object {
        private const val API_KEY = "D7LBZ-GYV6G-VHIQN-IIASI-NQMH2-YZBN7"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val dbHelper = CacheDbHelper(context)

    fun reverse(latitude: Double, longitude: Double, onResult: (ReverseGeoData) -> Unit) {
        val key = "%.4f,%.4f".format(latitude, longitude)

        val cached = dbHelper.get(key)
        if (cached != null) {
            onResult(cached)
            return
        }

        executor.execute {
            try {
                val url = "https://apis.map.qq.com/ws/geocoder/v1/?key=$API_KEY" +
                        "&location=$latitude,$longitude"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 10000

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(body)
                val result = json.optJSONObject("result")
                val address = result?.optString("address") ?: ""
                val recommend = result
                    ?.optJSONObject("formatted_addresses")
                    ?.optString("recommend") ?: address

                val data = ReverseGeoData(address, recommend)
                dbHelper.put(key, data)
                onResult(data)
            } catch (_: Exception) {
                onResult(ReverseGeoData())
            }
        }
    }

    fun close() {
        executor.shutdown()
        dbHelper.close()
    }

    private class CacheDbHelper(context: Context) :
        SQLiteOpenHelper(context, "geocache.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS cache (" +
                        "key TEXT PRIMARY KEY, " +
                        "address TEXT, " +
                        "recommend TEXT)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS cache")
            onCreate(db)
        }

        fun get(key: String): ReverseGeoData? {
            val db = readableDatabase
            db.rawQuery(
                "SELECT address, recommend FROM cache WHERE key=?",
                arrayOf(key)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return ReverseGeoData(
                        cursor.getString(0),
                        cursor.getString(1)
                    )
                }
            }
            return null
        }

        fun put(key: String, data: ReverseGeoData) {
            val db = writableDatabase
            db.execSQL(
                "INSERT OR REPLACE INTO cache(key,address,recommend) VALUES(?,?,?)",
                arrayOf(key, data.address, data.recommend)
            )
        }
    }
}
