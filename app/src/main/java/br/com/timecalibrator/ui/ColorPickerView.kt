package br.com.timecalibrator.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Seletor HSV: quadrado de saturação/brilho e barra de matiz. */
class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onColorChanged: ((Int) -> Unit)? = null

    private val hsv = floatArrayOf(0f, 1f, 1f)
    val color: Int get() = Color.HSVToColor(hsv)

    private val svRect = RectF()
    private val hueRect = RectF()
    private val corner = 18f.dp
    private val hueHeight = 30f.dp
    private val gap = 16f.dp

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f.dp
        color = Color.WHITE
    }
    private val thumbShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f.dp
        color = 0x33000000
    }

    private var hueShader: Shader? = null
    private var dragging = Target.NONE

    private enum class Target { NONE, SV, HUE }

    fun setColor(value: Int) {
        Color.colorToHSV(value, hsv)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (170f.dp + gap + hueHeight).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val left = paddingLeft.toFloat()
        val right = (w - paddingRight).toFloat()
        hueRect.set(left, h - paddingBottom - hueHeight, right, (h - paddingBottom).toFloat())
        svRect.set(left, paddingTop.toFloat(), right, hueRect.top - gap)
        hueShader = LinearGradient(
            hueRect.left, 0f, hueRect.right, 0f,
            SwatchView.HUES, null, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val pure = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))

        paint.shader = LinearGradient(svRect.left, 0f, svRect.right, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(svRect, corner, corner, paint)
        paint.shader = LinearGradient(0f, svRect.top, 0f, svRect.bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(svRect, corner, corner, paint)

        paint.shader = hueShader
        canvas.drawRoundRect(hueRect, hueHeight / 2, hueHeight / 2, paint)
        paint.shader = null

        val svX = svRect.left + hsv[1] * svRect.width()
        val svY = svRect.top + (1f - hsv[2]) * svRect.height()
        drawThumb(canvas, svX, svY, color)

        val hueX = hueRect.left + hueHeight / 2 + (hsv[0] / 360f) * (hueRect.width() - hueHeight)
        drawThumb(canvas, hueX, hueRect.centerY(), pure)
    }

    private fun drawThumb(canvas: Canvas, x: Float, y: Float, fillColor: Int) {
        val r = 11f.dp
        thumbFill.color = fillColor
        canvas.drawCircle(x, y, r, thumbFill)
        canvas.drawCircle(x, y, r, thumbRing)
        canvas.drawCircle(x, y, r + 1.5f.dp, thumbShadow)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = when {
                    event.y >= hueRect.top - gap / 2 -> Target.HUE
                    else -> Target.SV
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                update(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> update(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = Target.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun update(x: Float, y: Float) {
        when (dragging) {
            Target.SV -> {
                hsv[1] = ((x - svRect.left) / svRect.width()).coerceIn(0f, 1f)
                hsv[2] = 1f - ((y - svRect.top) / svRect.height()).coerceIn(0f, 1f)
            }
            Target.HUE -> {
                val usable = hueRect.width() - hueHeight
                hsv[0] = ((x - hueRect.left - hueHeight / 2) / usable).coerceIn(0f, 1f) * 359.9f
            }
            Target.NONE -> return
        }
        invalidate()
        onColorChanged?.invoke(color)
    }
}
