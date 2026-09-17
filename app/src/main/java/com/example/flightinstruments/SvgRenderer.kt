package com.example.flightinstruments

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.caverock.androidsvg.SVG

/**
 * 负责加载并绘制 qfi 的 SVG 矢量贴图。
 * 资源位于 assets/qfi/images/ 下，与原始 Qt 工程一致。
 */
object SvgRenderer {

    private const val BASE_PATH = "qfi/images/"
    private val cache = HashMap<String, SVG?>()

    private fun load(context: Context, name: String): SVG? {
        if (cache.containsKey(name)) {
            return cache[name]
        }
        val svg = try {
            SVG.getFromAsset(context.assets, BASE_PATH + name)
        } catch (_: Exception) {
            null
        }
        cache[name] = svg
        return svg
    }

    /**
     * 将指定 SVG 绘制到 rect 区域。
     * rotation 以 rect 中心（或指定中心）旋转，translate 用于俯仰位移。
     */
    fun draw(
        context: Context,
        canvas: Canvas,
        name: String,
        rect: RectF,
        rotation: Float = 0f,
        rotateCx: Float = rect.centerX(),
        rotateCy: Float = rect.centerY(),
        translateX: Float = 0f,
        translateY: Float = 0f
    ) {
        val svg = load(context, name) ?: return

        canvas.save()
        if (translateX != 0f || translateY != 0f) {
            canvas.translate(translateX, translateY)
        }
        if (rotation != 0f) {
            canvas.rotate(rotation, rotateCx, rotateCy)
        }
        svg.renderToCanvas(canvas, rect)
        canvas.restore()
    }
}
