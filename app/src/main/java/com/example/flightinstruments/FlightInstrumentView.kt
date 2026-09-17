package com.example.flightinstruments

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
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
    var navDeviation = 0f      // -1 .. 1
    var navDistance = 0f       // km

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

    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val gray = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY }
    private val cyan = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 200, 255) }
    private val sky = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 100, 200) }
    private val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 74, 50) }
    private val amber = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 180, 0) }

    private val density = resources.displayMetrics.density

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
        navDeviation = deviation.coerceIn(-1f, 1f)
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
                val dx = event.x - downX
                if (abs(dx) > width * 0.15f) {
                    currentPanel = if (currentPanel == PANEL_PFD) PANEL_NAV else PANEL_PFD
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun drawTextMode(canvas: Canvas) {
        white.textSize = 22f * density
        val lines = listOf(
            "姿态  俯仰:${pitch.format(2)}  倾斜:${roll.format(2)}  航向:${heading.format(0)}",
            "空速  ${airspeed.format(1)} km/h",
            "高度  ${altitude.format(0)} m",
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
                canvas.drawText(line, 20f * density, y, white)
                y += 44f * density
            }
        }
    }

    private fun drawDebugMode(canvas: Canvas) {
        gray.textSize = 14f * density
        var y = 60f * density
        for (line in debugText.split('\n')) {
            canvas.drawText(line, 20f * density, y, gray)
            y += 24f * density
        }
    }

    private fun drawInstrumentMode(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val topPanelHeight = h * 0.50f
        val bottomTop = topPanelHeight

        // 上方主仪表：PFD 或 NAV
        val topRect = RectF(0f, 0f, w, topPanelHeight)
        if (currentPanel == PANEL_PFD) {
            drawPfd(canvas, topRect)
        } else {
            drawNav(canvas, topRect)
        }

        // 面板指示
        white.textSize = 16f * density
        val panelName = if (currentPanel == PANEL_PFD) "PFD" else "NAV"
        canvas.drawText(panelName, w - 70f * density, 40f * density, white)

        // 下方六个小仪表：两行三列
        val rows = 2
        val cols = 3
        val cellW = w / cols
        val cellH = (h - bottomTop) / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val rect = RectF(
                    c * cellW, bottomTop + r * cellH,
                    (c + 1) * cellW, bottomTop + (r + 1) * cellH
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

    // ---------- PFD ----------
    private fun drawPfd(canvas: Canvas, rect: RectF) {
        val w = rect.width()
        val h = rect.height()
        val cx = rect.left + w / 2f
        val cy = rect.top + h * 0.50f
        val r = min(w, h) * 0.36f

        drawAttitude(canvas, cx, cy, r)
        drawTape(canvas, rect, airspeed, 0f, 300f, 20f, true)
        drawTape(canvas, rect, altitude, 0f, 12000f, 100f, false)

        white.textSize = 18f * density
        canvas.drawText("GS ${airspeed.format(0)}", rect.left + 18f * density, rect.bottom - 12f * density, cyan)
        canvas.drawText("ALT ${altitude.format(0)}", rect.right - 140f * density, rect.bottom - 12f * density, cyan)
    }

    private fun drawTape(
        canvas: Canvas, rect: RectF, value: Float,
        minValue: Float, maxValue: Float, step: Float, isLeft: Boolean
    ) {
        val baseY = rect.top + rect.height() / 2f
        val span = rect.height() * 0.42f
        val x = if (isLeft) rect.left + 40f * density else rect.right - 40f * density

        white.textSize = 16f * density
        var v = minValue
        while (v <= maxValue) {
            val y = baseY + (value - v) / step * 12f * density
            if (y > rect.top + 20f * density && y < rect.bottom - 20f * density) {
                if (isLeft) {
                    canvas.drawLine(x, y, x + 14f * density, y, white)
                    canvas.drawText(v.format(0), x + 20f * density, y + 4f * density, white)
                } else {
                    canvas.drawLine(x - 14f * density, y, x, y, white)
                    canvas.drawText(v.format(0), x - 76f * density, y + 4f * density, white)
                }
            }
            v += step
        }
    }

    private fun drawAttitude(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val saved = canvas.save()
        canvas.clipRect(cx - r, cy - r, cx + r, cy + r)
        canvas.rotate(roll, cx, cy)

        canvas.drawRect(cx - r * 2f, cy - r * 2f - pitch * 3f * density, cx + r * 2f, cy, sky)
        canvas.drawRect(cx - r * 2f, cy, cx + r * 2f, cy + r * 2f - pitch * 3f * density, ground)

        white.strokeWidth = 2f * density
        for (i in -20..20 step 5) {
            val y = cy + i * 3f * density - pitch * 3f * density
            if (y in (cy - r)..(cy + r)) {
                val len = if (i == 0) r * 0.7f else r * 0.30f
                canvas.drawLine(cx - len, y, cx + len, y, white)
            }
        }

        canvas.restoreToCount(saved)

        // 固定飞机符号
        white.strokeWidth = 4f * density
        canvas.drawLine(cx - 46f * density, cy, cx + 46f * density, cy, white)
        canvas.drawLine(cx, cy - 46f * density, cx, cy + 46f * density, white)
        white.strokeWidth = 2f * density
        canvas.drawCircle(cx, cy, r, white)
    }

    // ---------- NAV ----------
    private fun drawNav(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.40f

        canvas.save()
        canvas.rotate(-heading, cx, cy)

        white.strokeWidth = 2f * density
        canvas.drawCircle(cx, cy, r, white)
        canvas.drawCircle(cx, cy, r * 0.86f, gray)

        for (i in 0 until 36) {
            val angle = Math.toRadians((i * 10).toDouble())
            val x1 = cx + sin(angle).toFloat() * r
            val y1 = cy - cos(angle).toFloat() * r
            val x2 = cx + sin(angle).toFloat() * (if (i % 3 == 0) r * 0.86f else r * 0.92f)
            val y2 = cy - cos(angle).toFloat() * (if (i % 3 == 0) r * 0.86f else r * 0.92f)
            canvas.drawLine(x1, y1, x2, y2, if (i % 3 == 0) white else gray)
        }
        canvas.restore()

        // 机头方向（顶部）
        white.strokeWidth = 3f * density
        canvas.drawLine(cx, cy - r, cx, cy - r + 20f * density, white)
        canvas.drawLine(cx - 10f * density, cy - r + 10f * density, cx + 10f * density, cy - r + 10f * density, white)

        // 航向数值
        white.textSize = 20f * density
        canvas.drawText("HDG ${heading.format(0)}", rect.left + 18f * density, rect.bottom - 18f * density, cyan)
        canvas.drawText("BRG ${navBearing.format(0)}", rect.right - 170f * density, rect.bottom - 18f * density, cyan)
    }

    // ---------- 小仪表 ----------
    private fun drawAsi(canvas: Canvas, rect: RectF) {
        drawRoundGauge(canvas, rect, "ASI", "km/h", airspeed, 0f, 300f, 30f)
    }

    private fun drawAlt(canvas: Canvas, rect: RectF) {
        drawRoundGauge(canvas, rect, "ALT", "m", altitude, 0f, 12000f, 1000f)
    }

    private fun drawVsi(canvas: Canvas, rect: RectF) {
        drawRoundGauge(canvas, rect, "VSI", "m/s", verticalSpeed, -10f, 10f, 2f)
    }

    private fun drawAdi(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.38f

        val saved = canvas.save()
        canvas.clipRect(cx - r, cy - r, cx + r, cy + r)
        canvas.rotate(roll, cx, cy)
        canvas.drawRect(cx - r, cy - r - pitch * 3f * density, cx + r, cy, sky)
        canvas.drawRect(cx - r, cy, cx + r, cy + r - pitch * 3f * density, ground)
        canvas.restoreToCount(saved)

        white.strokeWidth = 2f * density
        canvas.drawCircle(cx, cy, r, white)
        canvas.drawLine(cx - 24f * density, cy, cx + 24f * density, cy, white)
        canvas.drawLine(cx, cy - 24f * density, cx, cy + 24f * density, white)
        gray.textSize = 12f * density
        canvas.drawText("ADI", cx - 14f * density, cy - r - 8f * density, gray)
    }

    private fun drawTc(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.38f

        white.strokeWidth = 2f * density
        canvas.drawCircle(cx, cy, r, white)
        canvas.drawLine(cx, cy - r, cx, cy + r, white)

        // 转弯速率指针
        val turnAngle = (turnRate / 7f).coerceIn(-1f, 1f) * 35f
        canvas.save()
        canvas.rotate(turnAngle, cx, cy + r * 0.45f)
        canvas.drawLine(cx, cy + r * 0.45f, cx, cy - r * 0.55f, cyan)
        canvas.restore()

        // 侧滑球
        val ballOffset = slipSkid.coerceIn(-1f, 1f) * r * 0.5f
        canvas.drawCircle(cx + ballOffset, cy + r * 0.72f, 7f * density, amber)

        gray.textSize = 12f * density
        canvas.drawText("TC", cx - 10f * density, cy - r - 8f * density, gray)
    }

    private fun drawHsi(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.38f

        white.strokeWidth = 2f * density
        canvas.drawCircle(cx, cy, r, white)

        canvas.save()
        canvas.rotate(-heading, cx, cy)
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30).toDouble())
            val x1 = cx + sin(angle).toFloat() * r
            val y1 = cy - cos(angle).toFloat() * r
            val x2 = cx + sin(angle).toFloat() * r * 0.88f
            val y2 = cy - cos(angle).toFloat() * r * 0.88f
            canvas.drawLine(x1, y1, x2, y2, white)
        }
        canvas.restore()

        canvas.drawLine(cx, cy - r, cx, cy - r + 16f * density, cyan)
        gray.textSize = 12f * density
        canvas.drawText("HSI", cx - 12f * density, cy - r - 8f * density, gray)
    }

    private fun drawRoundGauge(
        canvas: Canvas, rect: RectF, title: String, unit: String,
        value: Float, minValue: Float, maxValue: Float, step: Float
    ) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.38f

        white.strokeWidth = 2f * density
        canvas.drawCircle(cx, cy, r, white)

        val startAngle = -135f
        val sweepAngle = 270f
        val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        val needleAngle = startAngle + sweepAngle * fraction

        canvas.save()
        canvas.rotate(needleAngle, cx, cy)
        canvas.drawLine(cx, cy, cx, cy - r + 6f * density, cyan)
        canvas.restore()

        // 刻度
        var v = minValue
        while (v <= maxValue) {
            val f = ((v - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
            val angle = Math.toRadians((startAngle + sweepAngle * f).toDouble())
            val x1 = cx + sin(angle).toFloat() * r
            val y1 = cy - cos(angle).toFloat() * r
            val x2 = cx + sin(angle).toFloat() * r * 0.88f
            val y2 = cy - cos(angle).toFloat() * r * 0.88f
            canvas.drawLine(x1, y1, x2, y2, gray)
            v += step
        }

        white.textSize = 14f * density
        canvas.drawText(title, cx - r * 0.55f, cy - r - 10f * density, white)
        gray.textSize = 12f * density
        canvas.drawText("${value.format(0)} $unit", cx - r * 0.7f, cy + r + 24f * density, gray)
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
