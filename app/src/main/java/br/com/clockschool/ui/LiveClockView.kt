package br.com.clockschool.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import br.com.clockschool.ClockOffset

/**
 * Mostra a hora corrigida com precisão de segundo. O próximo tique é agendado para a
 * virada exata do segundo corrigido, então a troca acontece junto com o sinal.
 */
class LiveClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var offsetMillis: Long = 0L
        set(value) {
            field = value
            tick()
        }

    /** Cor dos segundos; null deixa tudo na cor do texto. */
    var secondsColor: Int? = null
        set(value) {
            field = value
            lastText = null
            tick()
        }

    var onTick: ((schoolMillis: Long) -> Unit)? = null

    private var lastText: String? = null
    private val ticker = Runnable { tick() }

    private fun tick() {
        removeCallbacks(ticker)
        val school = ClockOffset.schoolNow(offsetMillis)
        render(school)
        onTick?.invoke(school)
        if (isAttachedToWindow && windowVisibility == View.VISIBLE) {
            postDelayed(ticker, 1000 - Math.floorMod(school, 1000L) + 2)
        }
    }

    private fun render(school: Long) {
        val text = ClockOffset.formatClock(school)
        if (text == lastText) return
        lastText = text
        val muted = secondsColor
        if (muted == null) {
            setText(text)
        } else {
            val span = SpannableString(text)
            span.setSpan(ForegroundColorSpan(muted), text.length - 3, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setText(span)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        tick()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) tick() else removeCallbacks(ticker)
    }
}
