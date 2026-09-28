package br.com.clockschool.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat

val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()
val Float.dp: Float get() = this * Resources.getSystem().displayMetrics.density

/** Curva "emphasized decelerate" do Material, usada nas entradas. */
val EMPHASIZED = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)

fun Context.color(id: Int) = ContextCompat.getColor(this, id)

fun Context.isNight(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/** Leve encolhida ao tocar; não consome o toque, então o clique segue normal. */
@SuppressLint("ClickableViewAccessibility")
fun View.pressable(scale: Float = 0.96f) {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(scale).scaleY(scale).setDuration(90).setInterpolator(STANDARD).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(2.2f)).start()
        }
        false
    }
}

fun View.tick() = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

fun View.confirm() = performHapticFeedback(
    if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
    else HapticFeedbackConstants.VIRTUAL_KEY
)

fun View.reject() = performHapticFeedback(
    if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
    else HapticFeedbackConstants.LONG_PRESS
)

/** Pulso curto de escala para confirmar visualmente uma mudança. */
fun View.pulse() {
    animate().cancel()
    scaleX = 0.94f
    scaleY = 0.94f
    animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(OvershootInterpolator(3f)).start()
}
