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
 * 按原始 qfi 的图层顺序、变换原点与 updateView 公式渲染仪表。
 *
 * 顶部   : PFD / NAV（左右滑动切换）
 * 第二行 : ASI  ADI  ALT
 * 第三行 : TC   HSI  VSI
 *
 * 小仪表原始尺寸 240x240，旋转中心均为 (120,120)。
 */
class FlightInstrumentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var roll = 0f
    var pitch = 0f
    var heading = 0f
    var airspeed = 0f          // 与原版一致，直接使用传入值
    var altitude = 0f
    var verticalSpeed = 0f
    var turnRate = 0f
    var slipSkid = 0f

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
            "空速 ${airspeed.format(1)}",
            "高度 ${altitude.format(0)}",
            "升降率 ${verticalSpeed.format(1)}",
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
        val gridBottom = h * 0.94f

        val topRect = RectF(0f, 0f, w, topHeight)
        if (currentPanel == PANEL_PFD) {
            drawPfdPanel(canvas, topRect)
        } else {
            drawNavPanel(canvas, topRect)
        }

        textGray.textSize = 13f * density
        canvas.drawText(
            if (currentPanel == PANEL_PFD) "PFD" else "NAV",
            w - 56f * density, 26f * density, textGray
        )

        val rows = 2
        val cols = 3
        val gridTop = topHeight
        val cellW = w / cols
        val cellH = (gridBottom - gridTop) / rows

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
    // 用已经校准的 qfi 图层在顶部拼出 PFD 面板，避免使用带黑块的 pfd.svg 底图。
    private fun drawPfdPanel(canvas: Canvas, rect: RectF) {
        val w = rect.width()
        val h = rect.height()

        val adiRect = RectF(rect.left + w * 0.26f, rect.top,
                            rect.left + w * 0.74f, rect.top + h * 0.76f)
        val asiRect = RectF(rect.left, rect.top,
                            rect.left + w * 0.26f, rect.top + h * 0.46f)
        val altRect = RectF(rect.left + w * 0.74f, rect.top,
                            rect.right, rect.top + h * 0.46f)
        val vsiRect = RectF(rect.left + w * 0.74f, rect.top + h * 0.46f,
                            rect.right, rect.top + h * 0.78f)
        val hsiRect = RectF(rect.left + w * 0.22f, rect.top + h * 0.72f,
                            rect.left + w * 0.78f, rect.bottom)

        drawAdi(canvas, adiRect)
        drawAsi(canvas, asiRect)
        drawAlt(canvas, altRect)
        drawVsi(canvas, vsiRect)
        drawHsi(canvas, hsiRect)
    }

    private fun drawNavPanel(canvas: Canvas, rect: RectF) {
        val hsiRect = RectF(rect.left + rect.width() * 0.20f, rect.top,
                            rect.left + rect.width() * 0.80f, rect.bottom)
        drawHsi(canvas, hsiRect)

        textCyan.textSize = 15f * density
        canvas.drawText("HDG ${heading.format(0)}", rect.left + 10f * density,
            rect.top + 24f * density, textCyan)
        canvas.drawText("BRG ${navBearing.format(0)}", rect.right - 130f * density,
            rect.top + 24f * density, textCyan)
    }
    // ---------------- ASI ----------------
    private fun drawAsi(canvas: Canvas, rect: RectF) {
        SvgRenderer.draw(context, canvas, "asi/asi_face.svg", rect)
        SvgRenderer.draw(context, canvas, "asi/asi_hand.svg", rect, rotation = asiAngle(airspeed))
        SvgRenderer.draw(context, canvas, "asi/asi_case.svg", rect)
    }

    private fun asiAngle(v: Float): Float = when {
        v < 40f -> 0.9f * v
        v < 70f -> 36f + 1.8f * (v - 40f)
        v < 130f -> 90f + 2.0f * (v - 70f)
        v < 160f -> 210f + 1.8f * (v - 130f)
        else -> 264f + 1.2f * (v - 160f)
    }

    // ---------------- ADI ----------------
    private fun drawAdi(canvas: Canvas, rect: RectF) {
        val rollClamped = roll.coerceIn(-180f, 180f)
        val pitchClamped = pitch.coerceIn(-25f, 25f)
        val rollRad = Math.toRadians(rollClamped.toDouble())
        val delta = 1.7f * pitchClamped
        val dx = (delta * sin(rollRad)).toFloat()
        val dy = (delta * cos(rollRad)).toFloat()

        SvgRenderer.draw(context, canvas, "adi/adi_back.svg", rect, rotation = -rollClamped)
        SvgRenderer.draw(
            context, canvas, "adi/adi_face.svg", rect,
            rotation = -rollClamped, translateX = dx, translateY = dy
        )
        SvgRenderer.draw(context, canvas, "adi/adi_ring.svg", rect, rotation = -rollClamped)
        SvgRenderer.draw(context, canvas, "adi/adi_case.svg", rect)
    }

    // ---------------- ALT ----------------
    private fun drawAlt(canvas: Canvas, rect: RectF) {
        val angleH1 = altitude * 0.036f
        val angleH2 = ((altitude.toInt() % 1000) + 1000) % 1000 * 0.36f
        val angleF3 = altitude * 0.0036f

        SvgRenderer.draw(context, canvas, "alt/alt_face_1.svg", rect)
        SvgRenderer.draw(context, canvas, "alt/alt_face_2.svg", rect)
        SvgRenderer.draw(context, canvas, "alt/alt_face_3.svg", rect, rotation = angleF3)
        SvgRenderer.draw(context, canvas, "alt/alt_hand_1.svg", rect, rotation = angleH1)
        SvgRenderer.draw(context, canvas, "alt/alt_hand_2.svg", rect, rotation = angleH2)
        SvgRenderer.draw(context, canvas, "alt/alt_case.svg", rect)
    }

    // ---------------- TC ----------------
    private fun drawTc(canvas: Canvas, rect: RectF) {
        val markAngle = (turnRate / 3.0f) * 20.0f

        SvgRenderer.draw(context, canvas, "tc/tc_back.svg", rect)
        SvgRenderer.draw(
            context, canvas, "tc/tc_ball.svg", rect,
            rotation = -slipSkid, originX = 120f, originY = -36f
        )
        SvgRenderer.draw(context, canvas, "tc/tc_face_1.svg", rect)
        SvgRenderer.draw(context, canvas, "tc/tc_face_2.svg", rect)
        SvgRenderer.draw(context, canvas, "tc/tc_mark.svg", rect, rotation = markAngle)
        SvgRenderer.draw(context, canvas, "tc/tc_case.svg", rect)
    }

    // ---------------- HSI ----------------
    private fun drawHsi(canvas: Canvas, rect: RectF) {
        SvgRenderer.draw(context, canvas, "hsi/hsi_face.svg", rect, rotation = -heading)
        SvgRenderer.draw(context, canvas, "hsi/hsi_case.svg", rect)
    }

    // ---------------- VSI ----------------
    private fun drawVsi(canvas: Canvas, rect: RectF) {
        SvgRenderer.draw(context, canvas, "vsi/vsi_face.svg", rect)
        SvgRenderer.draw(
            context, canvas, "vsi/vsi_hand.svg", rect,
            rotation = verticalSpeed * 0.086f
        )
        SvgRenderer.draw(context, canvas, "vsi/vsi_case.svg", rect)
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
