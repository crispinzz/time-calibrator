package br.com.clockschool.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Scroller
import androidx.core.graphics.ColorUtils
import br.com.clockschool.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Roda de números estilo cilindro: arrasta, lança com inércia e sempre para
 * exatamente num valor, com um tique háptico a cada número que passa.
 */
class WheelPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var minValue = 0
        set(value) { field = value; invalidate() }
    var maxValue = 59
        set(value) { field = value; invalidate() }
    var cyclic = true

    var formatter: (Int) -> String = { String.format(Locale.US, "%02d", it) }
    var onValueChanged: ((Int) -> Unit)? = null

    val value: Int get() = valueAt(centerIndex())

    private val count get() = maxValue - minValue + 1
    private val itemHeight = 48f.dp
    private val degreesPerItem = 22f
    private val radius = (itemHeight / Math.toRadians(degreesPerItem.toDouble())).toFloat()

    /** Posição da roda em px; o índice central é offset / itemHeight. */
    private var offset = 0f
    private var lastIndex = 0

    private val scroller = Scroller(context, STANDARD)
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFling = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    private val primary = context.color(R.color.text_primary)
    private val secondary = context.color(R.color.text_secondary)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 38f.dp
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        fontFeatureSettings = "tnum"
    }

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setValue(newValue: Int, animate: Boolean) {
        val target = targetIndexFor(newValue)
        scroller.forceFinished(true)
        if (animate) {
            val dy = (target * itemHeight - offset).roundToInt()
            val duration = (260 + abs(dy) / itemHeight * 45).toInt().coerceAtMost(700)
            scroller.startScroll(0, offset.roundToInt(), 0, dy, duration)
            postInvalidateOnAnimation()
        } else {
            offset = target * itemHeight
            lastIndex = target
            invalidate()
        }
    }

    private fun targetIndexFor(newValue: Int): Int {
        val current = centerIndex()
        val wanted = (newValue - minValue).coerceIn(0, count - 1)
        if (!cyclic) return wanted
        var diff = wanted - Math.floorMod(current, count)
        if (diff > count / 2) diff -= count
        if (diff < -count / 2) diff += count
        return current + diff
    }

    private fun centerIndex() = (offset / itemHeight).roundToInt()

    private fun valueAt(index: Int): Int =
        if (cyclic) minValue + Math.floorMod(index, count) else minValue + index.coerceIn(0, count - 1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (paint.measureText("00") + 40f.dp).toInt() + paddingLeft + paddingRight
        val desiredHeight = (radius * 2 * 0.93f).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val position = offset / itemHeight
        val span = 90f / degreesPerItem
        val baseline = -(paint.ascent() + paint.descent()) / 2f

        for (i in floor(position - span).toInt()..ceil(position + span).toInt()) {
            if (!cyclic && (i < 0 || i >= count)) continue
            val delta = i - position
            val angle = Math.toRadians((delta * degreesPerItem).toDouble())
            val c = cos(angle).toFloat()
            if (c <= 0.05f) continue

            val y = cy + radius * sin(angle).toFloat()
            val focus = (1f - abs(delta)).coerceIn(0f, 1f)
            paint.color = ColorUtils.blendARGB(secondary, primary, focus)
            paint.alpha = (255 * (0.12f + 0.88f * c.pow(2.2f))).toInt()

            canvas.save()
            canvas.translate(cx, y)
            canvas.scale(1f, c)
            canvas.drawText(formatter(valueAt(i)), 0f, baseline, paint)
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                scroller.forceFinished(true)
                downY = event.y
                lastY = event.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.y - downY) > touchSlop) dragging = true
                if (dragging) {
                    moveBy(lastY - event.y)
                }
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val tracker = velocityTracker!!
                    tracker.computeCurrentVelocity(1000, maxFling.toFloat())
                    fling(-tracker.yVelocity)
                } else {
                    tapAt(event.y)
                }
                recycleTracker()
            }
            MotionEvent.ACTION_CANCEL -> {
                snap()
                recycleTracker()
            }
        }
        return true
    }

    private fun recycleTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun moveBy(dy: Float) {
        offset += dy
        if (!cyclic) offset = offset.coerceIn(0f, (count - 1) * itemHeight)
        notifyIfChanged()
        invalidate()
    }

    private fun fling(velocity: Float) {
        if (abs(velocity) < minFling * 2) {
            snap()
            return
        }
        val minY = if (cyclic) Int.MIN_VALUE / 2 else 0
        val maxY = if (cyclic) Int.MAX_VALUE / 2 else ((count - 1) * itemHeight).toInt()
        scroller.fling(0, offset.roundToInt(), 0, velocity.roundToInt(), 0, 0, minY, maxY)
        val snapped = ((scroller.finalY / itemHeight).roundToInt() * itemHeight).roundToInt()
        scroller.finalY = snapped.coerceIn(minY, maxY)
        postInvalidateOnAnimation()
    }

    private fun snap() {
        val target = (centerIndex() * itemHeight).roundToInt()
        val dy = target - offset.roundToInt()
        if (dy == 0) {
            settle()
            return
        }
        scroller.startScroll(0, offset.roundToInt(), 0, dy, 220)
        postInvalidateOnAnimation()
    }

    private fun tapAt(y: Float) {
        val rel = ((y - height / 2f) / radius).coerceIn(-0.99f, 0.99f)
        val steps = Math.toDegrees(asin(rel.toDouble())).toFloat() / degreesPerItem
        val target = centerIndex() + steps.roundToInt()
        if (target == centerIndex()) {
            snap()
            return
        }
        setValue(valueAt(target), animate = true)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offset = scroller.currY.toFloat()
            notifyIfChanged()
            postInvalidateOnAnimation()
            if (scroller.isFinished) settle()
        }
    }

    private fun settle() {
        if (cyclic) {
            // Mantém a posição perto de zero para não crescer sem limite.
            val index = Math.floorMod(centerIndex(), count)
            offset = index * itemHeight
            lastIndex = index
        }
        invalidate()
    }

    private fun notifyIfChanged() {
        val index = centerIndex()
        if (index != lastIndex) {
            lastIndex = index
            tick()
            onValueChanged?.invoke(value)
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.NumberPicker"
        info.isScrollable = true
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        info.text = formatter(value)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val step = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> 1
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> -1
            else -> return super.performAccessibilityAction(action, arguments)
        }
        setValue(valueAt(centerIndex() + step), animate = true)
        return true
    }
}
