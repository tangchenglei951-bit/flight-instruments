package com.example.flightinstruments

import android.content.Context
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.RectF
import com.caverock.androidsvg.SVG
import kotlin.math.min

/**
 * 负责加载并绘制 qfi 的 SVG 矢量贴图。
 *
 * 与 Qt 中 QGraphicsSvgItem 的行为保持一致：
 *   - SVG 原始坐标以 240x240（小仪表）或 300x300（PFD）为准
 *   - 按目标区域等比缩放并居中
 *   - 以 originX/originY（原始坐标）为旋转中心
 *   - translateX/translateY 同样在原始坐标下，随缩放一起生效
 */
object SvgRenderer {

    private const val BASE_PATH = "qfi/images/"
    private val cache = HashMap<String, Picture?>()

    private fun load(context: Context, name: String): Picture? {
        if (cache.containsKey(name)) {
            return cache[name]
        }
        val picture = try {
            val svg = SVG.getFromAsset(context.assets, BASE_PATH + name)
            svg.renderToPicture()
        } catch (_: Exception) {
            null
        }
        cache[name] = picture
        return picture
    }

    fun draw(
        context: Context,
        canvas: Canvas,
        name: String,
        rect: RectF,
        rotation: Float = 0f,
        originX: Float = 120f,
        originY: Float = 120f,
        translateX: Float = 0f,
        translateY: Float = 0f
    ) {
        val picture = load(context, name) ?: return
        val docW = picture.width.toFloat()
        val docH = picture.height.toFloat()
        if (docW <= 0f || docH <= 0f) {
            return
        }

        val scale = min(rect.width() / docW, rect.height() / docH)
        val left = rect.centerX() - docW * scale / 2f
        val top = rect.centerY() - docH * scale / 2f

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        if (translateX != 0f || translateY != 0f) {
            canvas.translate(translateX, translateY)
        }
        if (rotation != 0f) {
            canvas.rotate(rotation, originX, originY)
        }
        canvas.drawPicture(picture)
        canvas.restore()
    }
}
