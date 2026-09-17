package com.example.flightinstruments

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class FlightInstrumentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 飞行数据
    var roll = 0f
    var pitch = 0f
    var heading = 0f
    var airspeed = 0f          // km/h
    var altitude = 0f          // m
    var verticalSpeed = 0f     // m/s
    var turnRate = 0f          // deg/s
    var slipSkid = 0f

    // 导航数据
    var navCourse = 0f
    var navBearing = 0f
    var navDeviation = 0f
    var navDistance = 0f

    var satellitesInUse = 0
    var satellitesInView = 0

    var mode = MODE_INSTRUMENT
    var currentPanel = PANEL_PFD

    var sensorText = ""
    var gpsText = ""
    var satelliteText = ""
    var cpuText = ""
    var networkText = ""
    var tripText = ""
    var debugText = ""

    private var downX = 0f
    private val density = resources.displayMetrics.density

    // 文本画笔（填充）
    private val textWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * density
    }
    private val textGray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = 13f * density
    }
    private val textCyan = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 210, 255)
        textSize = 16f * density
    }

    // 描边画笔（圆形、刻度、指针）
    private val strokeWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val strokeGray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val strokeCyan = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 210, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val strokeAmber = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 180, 0)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    // 填充画笔（天/地/侧滑球）
    private val fillSky = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 100, 200) }
    private val fillGround = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 74, 50) }
    private val fillAmber = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 180, 0) }

    fun setFlightData(
        roll: Float, pitch: Float, heading: Float,
        airspeed: Float, altitude: Float, verticalSpeed: Float,
        turnRate: Float, slipSkid: Float
    ) {
        this.roll = roll
        this.pitch = pitch
        this.heading = heading
        this.airspeed = airspeed
        this.altitude = altitude
        this.verticalSpeed = verticalSpeed
        this.turnRate = turnRate
        this.slipSkid = slipSkid
        invalidate()
    }

    fun setNavData(course: Float, bearing: Float, deviation: Float, distance: Float) {
        navCourse = course
        navBearing = bearing
        navDeviation = deviation
        navDistance = distance
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        when (mode) {
            MODE_TEXT -> drawTextMode(canvas)
            MODE_DEBUG -> drawDebugMode(canvas)
            else -> drawInstrumentMode(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) > width * 0.15f) {
                    currentPanel = if (currentPanel == PANEL_PFD) PANEL_NAV else PANEL_PFD
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun drawTextMode(canvas: Canvas) {
        textWhite.textSize = 22f * density
        val lines = listOf(
            "姿态  俯仰 ${pitch.format(2)}  倾斜 ${roll.format(2)}  航向 ${heading.format(0)}",
            "空速 ${airspeed.format(1)} km/h",
            "高度 ${altitude.format(0)} m",
            "升降率 ${verticalSpeed.format(1)} m/s",
            sensorText,
            gpsText,
            satelliteText,
            cpuText,
            networkText,
            tripText
        )
        var y = 80f * density
        for (line in lines) {
            if (line.isNotBlank()) {
                canvas.drawText(line, 20f * density, y, textWhite)
                y += 44f * density
            }
        }
    }

    private fun drawDebugMode(canvas: Canvas) {
        textGray.textSize = 13f * density
        var y = 60f * density
        for (line in debugText.split('\n')) {
            canvas.drawText(line, 20f * density, y, textGray)
            y += 24f * density
        }
    }

    private fun drawInstrumentMode(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val topHeight = h * 0.52f

        val topRect = RectF(0f, 0f, w, topHeight)
        if (currentPanel == PANEL_PFD) {
            drawPfd(canvas, topRect)
        } else {
            drawNav(canvas, topRect)
        }

        textGray.textSize = 14f * density
        canvas.drawText(
            if (currentPanel == PANEL_PFD) "PFD" else "NAV",
            w - 56f * density, 28f * density, textGray
        )

        val rows = 2
        val cols = 3
        val cellW = w / cols
        val cellH = (h - topHeight) / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val rect = RectF(
                    c * cellW, topHeight + r * cellH,
                    (c + 1) * cellW, topHeight + (r + 1) * cellH
                )
                when (r * cols + c) {
                    0 -> drawAsi(canvas, rect)
                    1 -> drawAdi(canvas, rect)
                    2 -> drawAlt(canvas, rect)
                    3 -> drawTc(canvas, rect)
                    4 -> drawHsi(canvas, rect)
                    5 -> drawVsi(canvas, rect)
                }
            }
        }
    }

    // ---------------- PFD ----------------
    private fun drawPfd(canvas: Canvas, rect: RectF) {
        val w = rect.width()
        val h = rect.height()
        val cx = rect.left + w / 2f
        val cy = rect.top + h * 0.52f
        val r = min(w, h) * 0.34f

        drawAttitude(canvas, cx, cy, r)
        drawVerticalTape(canvas, rect, airspeed, 50f, 10f, 34f * density, true)
        drawVerticalTape(canvas, rect, altitude, 1000f, 200f, 30f * density, false)

        textCyan.textSize = 15f * density
        canvas.drawText("GS ${airspeed.format(0)}", rect.left + 16f * density,
            rect.bottom - 12f * density, textCyan)
        canvas.drawText("ALT ${altitude.format(0)}", rect.right - 130f * density,
            rect.bottom - 12f * density, textCyan)
    }

    private fun drawVerticalTape(
        canvas: Canvas, rect: RectF, value: Float,
        majorStep: Float, minorStep: Float, pxPerMajor: Float, isLeft: Boolean
    ) {
        val saved = canvas.save()
        canvas.clipRect(rect)

        val baseY = rect.top + rect.height() / 2f
        val x = if (isLeft) rect.left + 30f * density else rect.right - 30f * density
        val center = (value / majorStep).roundToInt() * majorStep

        textWhite.textSize = 15f * density

        var i = -5
        while (i <= 5) {
            val v = center + i * majorStep
            val y = baseY - (v - value) / majorStep * pxPerMajor

            val len = 16f * density
            if (isLeft) {
                canvas.drawLine(x, y, x + len, y, strokeWhite)
                canvas.drawText(v.format(0), x + len + 6f * density, y + 5f * density, textWhite)
            } else {
                canvas.drawLine(x - len, y, x, y, strokeWhite)
                canvas.drawText(v.format(0), x - len - 52f * density, y + 5f * density, textWhite)
            }

            // 次刻度
            for (k in 1 until 5) {
                val vy = y - k * (pxPerMajor / 5f)
                val vlen = 6f * density
                if (isLeft) {
                    canvas.drawLine(x, vy, x + vlen, vy, strokeGray)
                } else {
                    canvas.drawLine(x - vlen, vy, x, vy, strokeGray)
                }
            }
            i++
        }

        canvas.restoreToCount(saved)
    }

    private fun drawAttitude(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val saved = canvas.save()
        canvas.clipRect(cx - r, cy - r, cx + r, cy + r)
        canvas.rotate(roll, cx, cy)

        val pitchOffset = pitch * 2.4f * density
        canvas.drawRect(cx - r * 2f, cy - r * 2f - pitchOffset, cx + r * 2f, cy - pitchOffset, fillSky)
        canvas.drawRect(cx - r * 2f, cy - pitchOffset, cx + r * 2f, cy + r * 2f, fillGround)

        // 俯仰刻度
        for (deg in -30..30 step 5) {
            val y = cy + deg * 2.4f * density - pitchOffset
            if (y in (cy - r)..(cy + r)) {
                val half = if (deg == 0) r * 0.72f else r * 0.26f
                canvas.drawLine(cx - half, y, cx + half, y, strokeWhite)
                if (deg % 10 == 0 && deg != 0) {
                    textWhite.textSize = 12f * density
                    canvas.drawText("${abs(deg)}", cx + half + 6f * density, y + 4f * density, textWhite)
                }
            }
        }

        canvas.restoreToCount(saved)

        // 外圈与固定飞机符号
        canvas.drawCircle(cx, cy, r, strokeWhite)
        canvas.drawLine(cx - 46f * density, cy, cx - 10f * density, cy, strokeAmber)
        canvas.drawLine(cx + 10f * density, cy, cx + 46f * density, cy, strokeAmber)
        canvas.drawCircle(cx, cy, 3f * density, strokeAmber)
    }

    // ---------------- NAV ----------------
    private fun drawNav(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.38f

        canvas.save()
        canvas.rotate(-heading, cx, cy)
        canvas.drawCircle(cx, cy, r, strokeWhite)
        canvas.drawCircle(cx, cy, r * 0.84f, strokeGray)

        for (i in 0 until 36) {
            val angle = Math.toRadians((i * 10).toDouble())
            val long = i % 3 == 0
            val outer = r
            val inner = if (long) r * 0.84f else r * 0.90f
            canvas.drawLine(
                cx + sin(angle).toFloat() * inner,
                cy - kotlin.math.cos(angle).toFloat() * inner,
                cx + sin(angle).toFloat() * outer,
                cy - kotlin.math.cos(angle).toFloat() * outer,
                if (long) strokeWhite else strokeGray
            )
        }
        canvas.restore()

        // 顶部航向指示
        canvas.drawLine(cx, cy - r, cx, cy - r + 18f * density, strokeCyan)
        canvas.drawLine(cx - 10f * density, cy - r + 10f * density,
            cx + 10f * density, cy - r + 10f * density, strokeCyan)

        textCyan.textSize = 16f * density
        canvas.drawText("HDG ${heading.format(0)}", rect.left + 16f * density,
            rect.bottom - 16f * density, textCyan)
        canvas.drawText("BRG ${navBearing.format(0)}", rect.right - 150f * density,
            rect.bottom - 16f * density, textCyan)
    }

    // ---------------- 小仪表 ----------------
    private fun drawAsi(canvas: Canvas, rect: RectF) {
        drawRoundGauge(canvas, rect, "ASI", "km/h", airspeed, 0f, 300f, 50f)
    }

    private fun drawAlt(canvas: Canvas, rect: RectF) {
        drawRoundGauge(canvas, rect, "ALT", "m", altitude, 0f, 12000f, 2000f)
    }

    private fun drawVsi(canvas: Canvas, rect: RectF) {
        drawRoundGauge(canvas, rect, "VSI", "m/s", verticalSpeed, -10f, 10f, 5f)
    }

    private fun drawAdi(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.34f

        val saved = canvas.save()
        canvas.clipRect(cx - r, cy - r, cx + r, cy + r)
        canvas.rotate(roll, cx, cy)
        val pitchOffset = pitch * 2.0f * density
        canvas.drawRect(cx - r * 2f, cy - r * 2f - pitchOffset, cx + r * 2f, cy - pitchOffset, fillSky)
        canvas.drawRect(cx - r * 2f, cy - pitchOffset, cx + r * 2f, cy + r * 2f, fillGround)
        canvas.restoreToCount(saved)

        canvas.drawCircle(cx, cy, r, strokeWhite)
        canvas.drawLine(cx - 26f * density, cy, cx - 6f * density, cy, strokeAmber)
        canvas.drawLine(cx + 6f * density, cy, cx + 26f * density, cy, strokeAmber)

        textGray.textSize = 13f * density
        canvas.drawText("ADI", cx - 12f * density, cy - r - 8f * density, textGray)
    }

    private fun drawTc(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.34f

        canvas.drawCircle(cx, cy, r, strokeWhite)
        canvas.drawLine(cx, cy - r, cx, cy + r, strokeGray)

        val turnAngle = (turnRate / 7f).coerceIn(-1f, 1f) * 35f
        canvas.save()
        canvas.rotate(turnAngle, cx, cy + r * 0.5f)
        canvas.drawLine(cx, cy + r * 0.5f, cx, cy - r * 0.45f, strokeCyan)
        canvas.restore()

        val ballOffset = slipSkid.coerceIn(-1f, 1f) * r * 0.55f
        canvas.drawCircle(cx + ballOffset, cy + r * 0.70f, 7f * density, fillAmber)

        textGray.textSize = 13f * density
        canvas.drawText("TC", cx - 10f * density, cy - r - 8f * density, textGray)
    }

    private fun drawHsi(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.34f

        canvas.drawCircle(cx, cy, r, strokeWhite)

        canvas.save()
        canvas.rotate(-heading, cx, cy)
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30).toDouble())
            val outer = r
            val inner = r * 0.86f
            canvas.drawLine(
                cx + sin(angle).toFloat() * inner,
                cy - kotlin.math.cos(angle).toFloat() * inner,
                cx + sin(angle).toFloat() * outer,
                cy - kotlin.math.cos(angle).toFloat() * outer,
                strokeWhite
            )
        }
        canvas.restore()

        canvas.drawLine(cx, cy - r, cx, cy - r + 14f * density, strokeCyan)

        textGray.textSize = 13f * density
        canvas.drawText("HSI", cx - 12f * density, cy - r - 8f * density, textGray)
    }

    private fun drawRoundGauge(
        canvas: Canvas, rect: RectF, title: String, unit: String,
        value: Float, minValue: Float, maxValue: Float, step: Float
    ) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.34f

        canvas.drawCircle(cx, cy, r, strokeWhite)

        val startAngle = -120f
        val sweepAngle = 240f
        val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        val needleAngle = startAngle + sweepAngle * fraction

        // 刻度
        var v = minValue
        while (v <= maxValue) {
            val f = (v - minValue) / (maxValue - minValue)
            val angle = Math.toRadians((startAngle + sweepAngle * f).toDouble())
            val outer = r
            val inner = r * 0.86f
            canvas.drawLine(
                cx + sin(angle).toFloat() * inner,
                cy - kotlin.math.cos(angle).toFloat() * inner,
                cx + sin(angle).toFloat() * outer,
                cy - kotlin.math.cos(angle).toFloat() * outer,
                strokeGray
            )
            v += step
        }

        // 指针
        canvas.save()
        canvas.rotate(needleAngle, cx, cy)
        canvas.drawLine(cx, cy, cx, cy - r + 8f * density, strokeCyan)
        canvas.restore()
        canvas.drawCircle(cx, cy, 3f * density, strokeCyan)

        textWhite.textSize = 14f * density
        canvas.drawText(title, cx - 12f * density, cy - r - 10f * density, textWhite)
        textGray.textSize = 13f * density
        canvas.drawText("${value.format(0)} $unit",
            cx - 26f * density, cy + r + 22f * density, textGray)
    }

    private fun Float.format(digits: Int): String = "%.${digits}f".format(this)

    companion object {
        const val MODE_INSTRUMENT = 0
        const val MODE_TEXT = 1
        const val MODE_DEBUG = 2

        const val PANEL_PFD = 0
        const val PANEL_NAV = 1
    }
}
