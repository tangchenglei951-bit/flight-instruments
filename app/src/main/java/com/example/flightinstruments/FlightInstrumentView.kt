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
import kotlin.math.cos
import kotlin.math.sin

/**
 * 使用 qfi 原始 SVG 矢量贴图渲染飞行仪表。
 * 布局参考原始 Qt 版本：
 *   顶部   : PFD / NAV（可左右滑动切换）
 *   第二行 : ASI  ADI  ALT
 *   第三行 : TC   HSI  VSI
 */
class FlightInstrumentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var roll = 0f
    var pitch = 0f
    var heading = 0f
    var airspeed = 0f          // km/h
    var altitude = 0f          // m
    var verticalSpeed = 0f     // m/s
    var turnRate = 0f          // deg/s
    var slipSkid = 0f          // deg

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

    private val textWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f * density
    }
    private val textGray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = 13f * density
    }
    private val textCyan = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 210, 255)
        textSize = 15f * density
    }

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
                if (abs(event.x - downX) > width * 0.12f) {
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
        val topHeight = h * 0.44f

        val topRect = RectF(0f, 0f, w, topHeight)
        if (currentPanel == PANEL_PFD) {
            drawPfdPanel(canvas, topRect)
        } else {
            drawNavPanel(canvas, topRect)
        }

        // 下面两行六个仪表
        val rows = 2
        val cols = 3
        val gridTop = topHeight
        val cellW = w / cols
        val cellH = (h - gridTop) / rows

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val rect = RectF(
                    c * cellW, gridTop + r * cellH,
                    (c + 1) * cellW, gridTop + (r + 1) * cellH
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

    // ---------------- 顶部 PFD / NAV ----------------
    private fun drawPfdPanel(canvas: Canvas, rect: RectF) {
        SvgRenderer.draw(context, canvas, "pfd/pfd.svg", rect)

        // 动态文字叠加
        textCyan.textSize = 15f * density
        canvas.drawText("${airspeed.format(0)}", rect.left + 10f * density,
            rect.bottom - 10f * density, textCyan)
        canvas.drawText("${altitude.format(0)}", rect.right - 110f * density,
            rect.bottom - 10f * density, textCyan)

        textGray.textSize = 13f * density
        canvas.drawText("PFD", rect.right - 52f * density, 26f * density, textGray)
    }

    private fun drawNavPanel(canvas: Canvas, rect: RectF) {
        SvgRenderer.draw(context, canvas, "nav/nav.svg", rect)

        textCyan.textSize = 15f * density
        canvas.drawText("HDG ${heading.format(0)}", rect.left + 10f * density,
            rect.bottom - 10f * density, textCyan)
        canvas.drawText("BRG ${navBearing.format(0)}", rect.right - 130f * density,
            rect.bottom - 10f * density, textCyan)

        textGray.textSize = 13f * density
        canvas.drawText("NAV", rect.right - 52f * density, 26f * density, textGray)
    }

    // ---------------- 第二行 ----------------
    private fun drawAsi(canvas: Canvas, rect: RectF) {
        val face = inset(rect, 0.02f)
        SvgRenderer.draw(context, canvas, "asi/asi_face.svg", face)
        SvgRenderer.draw(context, canvas, "asi/asi_hand.svg", face, rotation = asiAngle(airspeed))
        SvgRenderer.draw(context, canvas, "asi/asi_case.svg", rect)
    }

    private fun drawAdi(canvas: Canvas, rect: RectF) {
        val face = inset(rect, 0.02f)
        val cx = face.centerX()
        val cy = face.centerY()
        val rollRad = Math.toRadians(roll.toDouble())
        val pixPerDeg = face.height() / 100f
        val delta = pixPerDeg * pitch
        val tx = (delta * sin(rollRad)).toFloat()
        val ty = (delta * cos(rollRad)).toFloat()

        SvgRenderer.draw(context, canvas, "adi/adi_back.svg", face, rotation = -roll, rotateCx = cx, rotateCy = cy)
        SvgRenderer.draw(
            context, canvas, "adi/adi_face.svg", face,
            rotation = -roll, rotateCx = cx, rotateCy = cy,
            translateX = tx, translateY = ty
        )
        SvgRenderer.draw(context, canvas, "adi/adi_ring.svg", face, rotation = -roll, rotateCx = cx, rotateCy = cy)
        SvgRenderer.draw(context, canvas, "adi/adi_case.svg", rect)
    }

    private fun drawAlt(canvas: Canvas, rect: RectF) {
        val face = inset(rect, 0.02f)
        val altitudeMod = ((altitude % 1000f) + 1000f) % 1000f

        SvgRenderer.draw(context, canvas, "alt/alt_face_1.svg", face)
        SvgRenderer.draw(context, canvas, "alt/alt_face_2.svg", face)
        SvgRenderer.draw(context, canvas, "alt/alt_face_3.svg", face, rotation = altitude * 0.0036f)
        SvgRenderer.draw(context, canvas, "alt/alt_hand_1.svg", face, rotation = altitude * 0.036f)
        SvgRenderer.draw(context, canvas, "alt/alt_hand_2.svg", face, rotation = altitudeMod * 0.36f)
        SvgRenderer.draw(context, canvas, "alt/alt_case.svg", rect)
    }

    // ---------------- 第三行 ----------------
    private fun drawTc(canvas: Canvas, rect: RectF) {
        val face = inset(rect, 0.02f)

        SvgRenderer.draw(context, canvas, "tc/tc_back.svg", face)
        SvgRenderer.draw(context, canvas, "tc/tc_face_1.svg", face)
        SvgRenderer.draw(context, canvas, "tc/tc_face_2.svg", face)
        SvgRenderer.draw(context, canvas, "tc/tc_ball.svg", face, rotation = -slipSkid)
        SvgRenderer.draw(context, canvas, "tc/tc_mark.svg", face, rotation = (turnRate / 3f) * 20f)
        SvgRenderer.draw(context, canvas, "tc/tc_case.svg", rect)
    }

    private fun drawHsi(canvas: Canvas, rect: RectF) {
        val face = inset(rect, 0.02f)
        SvgRenderer.draw(context, canvas, "hsi/hsi_face.svg", face, rotation = -heading)
        SvgRenderer.draw(context, canvas, "hsi/hsi_case.svg", rect)
    }

    private fun drawVsi(canvas: Canvas, rect: RectF) {
        val face = inset(rect, 0.02f)
        // qfi 原始单位是 ft/min，这里把 m/s 转成 ft/min
        val feetPerMinute = verticalSpeed * 196.85f
        SvgRenderer.draw(context, canvas, "vsi/vsi_face.svg", face)
        SvgRenderer.draw(context, canvas, "vsi/vsi_hand.svg", face, rotation = feetPerMinute * 0.086f)
        SvgRenderer.draw(context, canvas, "vsi/vsi_case.svg", rect)
    }

    // ---------------- 工具 ----------------
    private fun inset(rect: RectF, ratio: Float): RectF {
        val dx = rect.width() * ratio
        val dy = rect.height() * ratio
        return RectF(rect.left + dx, rect.top + dy, rect.right - dx, rect.bottom - dy)
    }

    private fun asiAngle(v: Float): Float = when {
        v < 40f -> 0.9f * v
        v < 70f -> 36f + 1.8f * (v - 40f)
        v < 130f -> 90f + 2f * (v - 70f)
        v < 160f -> 210f + 1.8f * (v - 130f)
        else -> 264f + 1.2f * (v - 160f)
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
