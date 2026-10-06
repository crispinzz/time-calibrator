package br.com.timecalibrator

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import br.com.timecalibrator.ui.EMPHASIZED
import br.com.timecalibrator.ui.LiveClockView
import br.com.timecalibrator.ui.Segmented
import br.com.timecalibrator.ui.color
import br.com.timecalibrator.ui.confirm
import br.com.timecalibrator.ui.dp
import br.com.timecalibrator.ui.pressable
import br.com.timecalibrator.ui.tick
import kotlin.math.abs

/** Três telas na primeira abertura, com a escolha de idioma sempre à mão no topo. */
class OnboardingActivity : AppCompatActivity() {

    private class Page(val title: Int, val body: Int, val icon: Int?)

    private val pages = listOf(
        Page(R.string.ob1_title, R.string.ob1_body, null),
        Page(R.string.ob2_title, R.string.ob2_body, R.drawable.ic_bell),
        Page(R.string.ob3_title, R.string.ob3_body, R.drawable.ic_widgets),
    )
    private var page = 0

    private lateinit var content: View
    private lateinit var clock: LiveClockView
    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var next: TextView
    private lateinit var dots: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        content = findViewById(R.id.content)
        clock = findViewById(R.id.obClock)
        clock.secondsColor = color(R.color.text_tertiary)
        icon = findViewById(R.id.obIcon)
        title = findViewById(R.id.obTitle)
        body = findViewById(R.id.obBody)
        next = findViewById(R.id.nextButton)
        dots = findViewById(R.id.dots)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        Segmented(
            findViewById(R.id.languageOptions),
            Lang.TAGS.map { Lang.label(this, it) },
            Lang.TAGS.indexOf(Lang.current(this)).coerceAtLeast(0)
        ) { i -> root.postDelayed({ Lang.set(this, Lang.TAGS[i]) }, 160) }

        repeat(pages.size) {
            dots.addView(View(this).apply { setBackgroundResource(R.drawable.bg_dot) },
                LinearLayout.LayoutParams(8.dp, 8.dp).apply { marginEnd = 8.dp })
        }

        findViewById<View>(R.id.skipButton).setOnClickListener { finishOnboarding() }
        next.pressable(0.97f)
        next.setOnClickListener {
            if (page < pages.lastIndex) {
                it.tick()
                show(page + 1)
            } else {
                it.confirm()
                finishOnboarding()
            }
        }
        setupSwipe(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (page > 0) show(page - 1) else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Trocar o idioma recria a activity; volta para a mesma tela.
        show(savedInstanceState?.getInt("page") ?: 0, animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page)
    }

    private fun show(index: Int, animate: Boolean = true) {
        val dir = if (index >= page) 1 else -1
        page = index
        val apply = {
            val p = pages[index]
            title.setText(p.title)
            body.setText(p.body)
            clock.visibility = if (p.icon == null) View.VISIBLE else View.INVISIBLE
            icon.visibility = if (p.icon == null) View.INVISIBLE else View.VISIBLE
            p.icon?.let { icon.setImageResource(it) }
            next.setText(if (index == pages.lastIndex) R.string.ob_start else R.string.ob_next)
            for (i in 0 until dots.childCount) {
                val dot = dots.getChildAt(i)
                dot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    color(if (i == index) R.color.accent else R.color.outline)
                )
                dot.animate().scaleX(if (i == index) 1.25f else 1f).scaleY(if (i == index) 1.25f else 1f)
                    .setDuration(if (animate) 240 else 0).start()
            }
        }
        if (!animate) {
            apply()
            return
        }
        content.animate().cancel()
        content.animate().alpha(0f).translationX(-dir * 24f.dp).setDuration(140).withEndAction {
            apply()
            content.translationX = dir * 32f.dp
            content.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(EMPHASIZED).start()
        }.start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipe(target: View) {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || abs(vx) < abs(vy) || abs(e2.x - e1.x) < 48.dp) return false
                if (vx < 0 && page < pages.lastIndex) show(page + 1)
                if (vx > 0 && page > 0) show(page - 1)
                return true
            }
        })
        content.setOnTouchListener { _, e -> detector.onTouchEvent(e) }
    }

    private fun finishOnboarding() {
        Prefs.of(this).edit().putBoolean(Prefs.ONBOARDING_DONE, true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    companion object {
        fun isDone(context: Context) = Prefs.of(context).getBoolean(Prefs.ONBOARDING_DONE, false)
    }
}
