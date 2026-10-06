package br.com.timecalibrator.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.SweepGradient
import android.view.View
import androidx.core.graphics.ColorUtils
import br.com.timecalibrator.R

/** Bolinha de cor da paleta: cor fixa, automático (claro/escuro) ou personalizada. */
class SwatchView(context: Context) : View(context) {

    enum class Kind { AUTO, COLOR, CUSTOM }

    var kind = Kind.COLOR
        set(value) { field = value; invalidate() }

    var color: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    /** Na bolinha personalizada: a cor escolhida, ou null para mostrar só o arco-íris. */
    var customColor: Int? = null
        set(value) { field = value; invalidate() }

    var isChosen = false
        set(value) {
            if (field == value) return
            field = value
            animateRing(if (value) 1f else 0f)
        }

    private var ring = 0f
    private var ringAnimator: ValueAnimator? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val half = Path()
    private val accent = context.color(R.color.accent)
    private val outline = context.color(R.color.outline)
    private val surface = context.color(R.color.surface)

    init {
        isClickable = true
        isFocusable = true
        background = null
    }

    private fun animateRing(target: Float) {
        ringAnimator?.cancel()
        ringAnimator = ValueAnimator.ofFloat(ring, target).apply {
            duration = 220
            interpolator = STANDARD
            addUpdateListener {
                ring = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = 44.dp
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = minOf(cx, cy)
        val inner = outer - 6f.dp + 2f.dp * (1f - ring)

        if (ring > 0f) {
            stroke.color = ColorUtils.setAlphaComponent(accent, (255 * ring).toInt())
            stroke.strokeWidth = 2f.dp
            canvas.drawCircle(cx, cy, outer - 1f.dp, stroke)
        }

        when (kind) {
            Kind.COLOR -> {
                fill.shader = null
                fill.color = color
                canvas.drawCircle(cx, cy, inner, fill)
                outlineIfFaint(canvas, cx, cy, inner, color)
            }
            Kind.AUTO -> {
                fill.shader = null
                fill.color = Color.WHITE
                canvas.drawCircle(cx, cy, inner, fill)
                half.reset()
                half.addCircle(cx, cy, inner, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(half)
                fill.color = 0xFF0A0A0A.toInt()
                canvas.drawRect(cx, cy - inner, cx + inner, cy + inner, fill)
                canvas.restore()
                stroke.color = outline
                stroke.strokeWidth = 1f.dp
                canvas.drawCircle(cx, cy, inner, stroke)
            }
            Kind.CUSTOM -> {
                fill.shader = SweepGradient(cx, cy, HUES, null)
                canvas.drawCircle(cx, cy, inner, fill)
                fill.shader = null
                val chosen = customColor
                if (chosen != null) {
                    fill.color = surface
                    canvas.drawCircle(cx, cy, inner * 0.62f, fill)
                    fill.color = chosen
                    canvas.drawCircle(cx, cy, inner * 0.5f, fill)
                }
            }
        }
    }

    private fun outlineIfFaint(canvas: Canvas, cx: Float, cy: Float, r: Float, c: Int) {
        val contrast = ColorUtils.calculateContrast(ColorUtils.setAlphaComponent(c, 255), surface)
        if (contrast < 1.3) {
            stroke.color = outline
            stroke.strokeWidth = 1f.dp
            canvas.drawCircle(cx, cy, r, stroke)
        }
    }

    companion object {
        val HUES = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(),
            0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt()
        )
    }
}
