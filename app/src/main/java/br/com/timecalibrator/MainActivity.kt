package br.com.timecalibrator

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import br.com.timecalibrator.ui.EMPHASIZED
import br.com.timecalibrator.ui.LiveClockView
import br.com.timecalibrator.ui.Sheets
import br.com.timecalibrator.ui.WidgetPreview
import br.com.timecalibrator.ui.color
import br.com.timecalibrator.ui.confirm
import br.com.timecalibrator.ui.dp
import br.com.timecalibrator.ui.pressable
import br.com.timecalibrator.ui.pulse
import br.com.timecalibrator.ui.reject
import br.com.timecalibrator.ui.tick
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.view.animation.OvershootInterpolator
import android.view.ViewGroup.MarginLayoutParams

class MainActivity : AppCompatActivity() {

    /** Sempre o relógio ativo. */
    private val store get() = ClockStore(this)
    private lateinit var clock: LiveClockView
    private lateinit var pages: List<View>
    private lateinit var navIcons: List<ImageView>
    private lateinit var nav: View
    private var tab = 0

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!OnboardingActivity.isDone(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        clock = findViewById(R.id.calibratedClock)
        clock.secondsColor = color(R.color.text_tertiary)
        clock.onTick = { updateGreeting(it) }

        nav = findViewById(R.id.nav)
        pages = listOf(findViewById(R.id.pageHome), findViewById(R.id.pageWidgets), findViewById(R.id.pageCode))
        navIcons = listOf(findViewById(R.id.navHome), findViewById(R.id.navWidgets), findViewById(R.id.navCode))
        navIcons.forEachIndexed { i, v -> v.setOnClickListener { it.tick(); selectTab(i) } }

        applyInsets()
        setupHome()
        setupWidgets()
        setupCode()

        findViewById<View>(R.id.themeButton).apply {
            pressable(0.9f)
            setOnClickListener { Sheets.theme(this@MainActivity) {
                ClockWidgetProvider.updateAllWidgets(this@MainActivity)
                ThemeMode.applyAppTheme(this@MainActivity)
            } }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (tab != 0) selectTab(0) else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        selectTab(savedInstanceState?.getInt("tab") ?: 0, animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onStart() {
        super.onStart()
        Prefs.of(this).registerOnSharedPreferenceChangeListener(prefsListener)
        refresh()
    }

    override fun onStop() {
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onStop()
    }

    private fun applyInsets() {
        val topBar = findViewById<View>(R.id.topBar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.updatePadding(top = bars.top + 12.dp)
            nav.updateLayoutParams<MarginLayoutParams> { bottomMargin = bars.bottom + 16.dp }
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            pages.forEach { it.updatePadding(bottom = ime) }
            nav.visibility = if (ime > 0) View.GONE else View.VISIBLE
            insets
        }
    }

    // ---------- Navegação ----------

    private fun selectTab(index: Int, animate: Boolean = true) {
        val pitch = 64.dp.toFloat()
        val indicator = findViewById<View>(R.id.navIndicator)
        if (!animate) {
            pages.forEachIndexed { i, p -> p.visibility = if (i == index) View.VISIBLE else View.GONE }
            indicator.translationX = index * pitch
            navIcons.forEachIndexed { i, v -> v.imageTintList = ColorStateList.valueOf(iconColor(i == index)) }
            tab = index
            return
        }
        if (index == tab) {
            (pages[index] as? androidx.core.widget.NestedScrollView)?.smoothScrollTo(0, 0)
            return
        }
        val dir = if (index > tab) 1 else -1
        val out = pages[tab]
        val into = pages[index]
        out.animate().alpha(0f).translationX(-dir * 24f.dp).setDuration(140).withEndAction {
            out.visibility = View.GONE
            out.alpha = 1f
            out.translationX = 0f
        }.start()
        into.alpha = 0f
        into.translationX = dir * 32f.dp
        into.visibility = View.VISIBLE
        into.animate().alpha(1f).translationX(0f).setStartDelay(60).setDuration(320).setInterpolator(EMPHASIZED).start()

        indicator.animate().translationX(index * pitch).setDuration(380).setInterpolator(OvershootInterpolator(1.1f)).start()
        animateTint(navIcons[tab], false)
        animateTint(navIcons[index], true)
        tab = index
        if (index == 1) renderWidgetList()
    }

    private fun iconColor(active: Boolean) = if (active) color(R.color.on_accent) else color(R.color.nav_icon)

    private fun animateTint(view: ImageView, active: Boolean) {
        ValueAnimator.ofObject(ArgbEvaluator(), iconColor(!active), iconColor(active)).apply {
            duration = 240
            addUpdateListener { view.imageTintList = ColorStateList.valueOf(it.animatedValue as Int) }
            start()
        }
    }

    // ---------- Início ----------

    private fun setupHome() {
        val openPicker = View.OnClickListener {
            Sheets.timePicker(this, store.scheduledHour, store.scheduledMinute) { h, m ->
                store.scheduledHour = h
                store.scheduledMinute = m
                refresh()
            }
        }
        findViewById<View>(R.id.quickSchedule).apply { pressable(0.9f); setOnClickListener(openPicker) }
        findViewById<View>(R.id.scheduleChip).apply { pressable(0.94f); setOnClickListener(openPicker) }
        findViewById<View>(R.id.quickMinus).apply { pressable(0.9f); setOnClickListener { nudge(+1000) } }
        findViewById<View>(R.id.quickPlus).apply { pressable(0.9f); setOnClickListener { nudge(-1000) } }
        findViewById<View>(R.id.quickReset).apply {
            pressable(0.9f)
            setOnClickListener { changeOffset(0L, false, getString(R.string.offset_reset)) }
        }
        findViewById<View>(R.id.homeApply).apply {
            pressable(0.97f)
            setOnClickListener { applyWidget(store.clockId) }
        }
        findViewById<View>(R.id.homePreview).setOnClickListener { selectTab(1) }
        findViewById<View>(R.id.calibrateButton).apply {
            pressable(0.97f)
            setOnClickListener { calibrate() }
        }
        findViewById<View>(R.id.codeCard).apply {
            clipToOutline = true
            pressable(0.97f)
            setOnClickListener { selectTab(2) }
        }
        findViewById<View>(R.id.widgetsCard).apply { pressable(0.97f); setOnClickListener { selectTab(1) } }
    }

    private fun calibrate() {
        val now = System.currentTimeMillis()
        val scheduled = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, store.scheduledHour)
            set(Calendar.MINUTE, store.scheduledMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val offset = ClockOffset.normalize(now - scheduled.timeInMillis)
        changeOffset(offset, false, getString(R.string.calibrated_msg, ClockOffset.status(this, offset, true)))
    }

    private fun nudge(delta: Long) {
        clock.tick()
        store.saveCalibration(store.offsetMillis + delta, store.calibratedViaCode)
        ClockWidgetProvider.updateAllWidgets(this)
        refresh()
    }

    private fun changeOffset(offset: Long, viaCode: Boolean, message: String) {
        val before = store.snapshot()
        store.saveCalibration(offset, viaCode)
        ClockWidgetProvider.updateAllWidgets(this)
        refresh()
        clock.confirm()
        clock.pulse()
        Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_LONG)
            .setAnchorView(nav)
            .setBackgroundTint(color(R.color.inverse_bg))
            .setTextColor(color(R.color.inverse_text))
            .setActionTextColor(color(R.color.accent))
            .setAction(R.string.undo) {
                store.restore(before)
                ClockWidgetProvider.updateAllWidgets(this)
                refresh()
            }
            .show()
    }

    private var greeting = ""

    private fun updateGreeting(calibrated: Long) {
        val hour = Calendar.getInstance().apply { timeInMillis = calibrated }.get(Calendar.HOUR_OF_DAY)
        val text = getString(
            when (hour) {
                in 5..11 -> R.string.greeting_morning
                in 12..17 -> R.string.greeting_afternoon
                else -> R.string.greeting_evening
            }
        )
        if (text != greeting) {
            greeting = text
            findViewById<TextView>(R.id.greetingText).text = text
        }
    }

    private fun refresh() {
        val store = store
        val offset = store.offsetMillis
        val calibrated = store.isCalibrated
        clock.offsetMillis = offset
        findViewById<TextView>(R.id.clockName).text = Clocks.name(this, store.clockId)

        val scheduled = String.format(Locale.US, "%02d:%02d", store.scheduledHour, store.scheduledMinute)
        findViewById<TextView>(R.id.quickSchedule).text = scheduled
        findViewById<TextView>(R.id.scheduleChip).text = scheduled
        findViewById<TextView>(R.id.calibrateHint).text =
            getString(R.string.calibrate_hint, scheduled)

        findViewById<TextView>(R.id.statusText).text =
            ClockOffset.status(this, offset, calibrated).replaceFirstChar { it.uppercase() }
        val dot = when {
            !calibrated -> R.color.status_idle
            offset / 1000 == 0L -> R.color.status_ok
            else -> R.color.status_off
        }
        findViewById<View>(R.id.statusDot).backgroundTintList = ColorStateList.valueOf(color(dot))
        findViewById<TextView>(R.id.calibrationInfo).text = if (calibrated) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(store.lastCalibrationAt))
            getString(if (store.calibratedViaCode) R.string.via_code_at else R.string.adjusted_at, time)
        } else ""

        val code = if (calibrated) CalibrationCode.encode(offset) else null
        findViewById<TextView>(R.id.homeCode).text = code ?: "—"
        findViewById<TextView>(R.id.codeBig).text = code ?: "—"
        findViewById<TextView>(R.id.codeHint).text = if (code == null) {
            getString(R.string.code_hint_empty)
        } else {
            getString(R.string.code_hint)
        }

        findViewById<TextView>(R.id.widgetsBadge).text = Clocks.ids(this).size.toString()

        WidgetPreview.bind(findViewById(R.id.homePreview), Clocks.style(this, store.clockId), store)
        val applied = ClockWidgetProvider.widgetsOf(this, store.clockId).isNotEmpty()
        findViewById<TextView>(R.id.homeApply).apply {
            text = getString(if (applied) R.string.widget_applied else R.string.apply_widget)
            isEnabled = !applied
            setBackgroundResource(if (applied) R.drawable.bg_pill_muted else R.drawable.bg_button_primary)
            setTextColor(color(if (applied) R.color.text_secondary else R.color.on_accent))
        }

        if (tab == 1) renderWidgetList()
    }

    private fun applyWidget(clockId: Int) {
        if (ClockWidgetProvider.widgetsOf(this, clockId).isNotEmpty()) {
            snack(getString(R.string.already_has_widget))
            return
        }
        if (!ClockWidgetProvider.requestPin(this, clockId)) snack(getString(R.string.pin_manually))
    }

    // ---------- Widgets ----------

    private fun setupWidgets() {
        findViewById<View>(R.id.newWidgetButton).apply {
            pressable(0.97f)
            setOnClickListener {
                Sheets.widgetEditor(this@MainActivity, WidgetStyle(), getString(R.string.new_clock), getString(R.string.create_clock), store.clockId, "") { style, name, dialog ->
                    dialog.dismiss()
                    val id = Clocks.create(this@MainActivity, name.ifEmpty { getString(R.string.clock_default_name, Clocks.ids(this@MainActivity).size + 1) }, style)
                    Clocks.setActive(this@MainActivity, id)
                    refresh()
                    selectTab(0)
                    applyWidget(id)
                }
            }
        }
    }

    private fun renderWidgetList() {
        val list = findViewById<LinearLayout>(R.id.widgetList)
        list.removeAllViews()
        val active = Clocks.active(this)
        for (id in Clocks.ids(this)) {
            val clockStore = ClockStore(this, id)
            val style = Clocks.style(this, id)
            val name = Clocks.name(this, id)
            val item = LayoutInflater.from(this).inflate(R.layout.item_widget, list, false)
            WidgetPreview.bind(item.findViewById(R.id.itemPreview), style, clockStore)
            item.findViewById<TextView>(R.id.itemTitle).text = if (id == active) "$name  ●" else name
            val widgets = ClockWidgetProvider.widgetsOf(this, id).size
            item.findViewById<TextView>(R.id.itemSubtitle).text =
                ClockOffset.status(this, clockStore.offsetMillis, clockStore.isCalibrated) +
                    if (widgets > 0) " · " + getString(R.string.on_screen, widgets) else ""
            item.pressable(0.98f)
            // Tocar abre o relógio na Home para calibrar.
            item.setOnClickListener {
                it.tick()
                Clocks.setActive(this, id)
                refresh()
                selectTab(0)
            }
            item.setOnLongClickListener {
                if (Clocks.ids(this).size > 1) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(getString(R.string.delete_title, name))
                        .setMessage(R.string.delete_msg)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            Clocks.delete(this, id)
                            ClockWidgetProvider.updateAllWidgets(this)
                            refresh()
                        }
                        .show()
                }
                true
            }
            item.findViewById<View>(R.id.itemEdit).setOnClickListener {
                Sheets.widgetEditor(this, style, name, getString(R.string.save), id, name) { newStyle, newName, dialog ->
                    Clocks.saveStyle(this, id, newStyle)
                    if (newName.isNotEmpty()) Clocks.rename(this, id, newName)
                    for (w in ClockWidgetProvider.widgetsOf(this, id)) WidgetStyles.clearLegacy(this, w)
                    ClockWidgetProvider.updateAllWidgets(this)
                    dialog.dismiss()
                    refresh()
                }
            }
            // Um widget por relógio: o + some quando ele já está na tela.
            item.findViewById<View>(R.id.itemPin).apply {
                visibility = if (widgets > 0) View.GONE else View.VISIBLE
                setOnClickListener { applyWidget(id) }
            }
            list.addView(item)
        }
    }
    // ---------- Código ----------

    private fun setupCode() {
        findViewById<View>(R.id.copyButton).apply { pressable(0.9f); setOnClickListener { copyCode() } }
        findViewById<View>(R.id.shareButton).apply { pressable(0.9f); setOnClickListener { shareCode() } }

        val input = findViewById<EditText>(R.id.codeInput)
        input.addTextChangedListener(object : TextWatcher {
            private var editing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (editing || s == null) return
                val clean = s.toString().uppercase().filter { it.isLetterOrDigit() }.take(6)
                val formatted = if (clean.length > 3) clean.substring(0, 3) + "-" + clean.substring(3) else clean
                if (formatted != s.toString()) {
                    editing = true
                    s.replace(0, s.length, formatted)
                    editing = false
                }
            }
        })
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { applyCode(); true } else false
        }
        findViewById<View>(R.id.applyCodeButton).apply { pressable(0.97f); setOnClickListener { applyCode() } }
    }

    private fun applyCode() {
        val input = findViewById<EditText>(R.id.codeInput)
        val offset = CalibrationCode.decode(input.text.toString())
        if (offset == null) {
            input.reject()
            input.animate().translationX(10f.dp).setDuration(60).withEndAction {
                input.animate().translationX(0f).setInterpolator(OvershootInterpolator(6f)).setDuration(260).start()
            }.start()
            Snackbar.make(findViewById(R.id.root), getString(R.string.invalid_code), Snackbar.LENGTH_SHORT).setAnchorView(nav).show()
            return
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        input.text.clear()
        input.clearFocus()
        changeOffset(offset, true, getString(R.string.code_applied, ClockOffset.status(this, offset, true)))
        selectTab(0)
    }

    private fun copyCode() {
        if (!store.isCalibrated) return snack(getString(R.string.calibrate_first))
        val code = CalibrationCode.encode(store.offsetMillis)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.calibration_code), code))
        findViewById<View>(R.id.copyButton).confirm()
        snack(getString(R.string.code_copied, code))
    }

    private fun shareCode() {
        if (!store.isCalibrated) return snack(getString(R.string.calibrate_first))
        val code = CalibrationCode.encode(store.offsetMillis)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, code))
        startActivity(Intent.createChooser(send, getString(R.string.send_code)))
    }

    private fun snack(message: String) {
        Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_SHORT).setAnchorView(nav).show()
    }
}
