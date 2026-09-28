package br.com.clockschool.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import br.com.clockschool.ClockOffset
import br.com.clockschool.ClockStore
import br.com.clockschool.R
import br.com.clockschool.WidgetStyle
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/** Aplica um estilo na prévia (mesmo visual do widget real). */
object WidgetPreview {
    fun bind(preview: View, style: WidgetStyle, store: ClockStore) {
        val night = preview.context.isNight()
        val text = style.textFor(night)
        preview.findViewById<ImageView>(R.id.previewBg).apply {
            setColorFilter(style.backgroundFor(night))
            imageAlpha = style.opacity * 255 / 100
        }
        preview.findViewById<LiveClockView>(R.id.previewTime).apply {
            setTextColor(text)
            offsetMillis = store.offsetMillis
        }
        preview.findViewById<TextView>(R.id.previewLegend).apply {
            visibility = if (style.showLegend) View.VISIBLE else View.GONE
            this.text = style.legendFor(ClockOffset.status(store.offsetMillis, store.isCalibrated))
            setTextColor(text)
            backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(text, 26))
        }
    }
}

/** Editor de aparência de um widget, usado na folha do app e na tela de configuração. */
class WidgetEditor(
    private val root: View,
    initial: WidgetStyle,
    eyebrow: String,
    primaryLabel: String,
    private val onPrimary: (WidgetStyle) -> Unit
) {
    var style = initial
        private set

    private val store = ClockStore(root.context)
    private val preview = root.findViewById<View>(R.id.editorPreview)
    private val bgRow = root.findViewById<LinearLayout>(R.id.bgSwatches)
    private val textRow = root.findViewById<LinearLayout>(R.id.textSwatches)
    private val opacityValue = root.findViewById<TextView>(R.id.opacityValue)
    private val legendInput = root.findViewById<EditText>(R.id.legendInput)

    private val bg = ColorSection(
        bgRow, root.findViewById(R.id.bgCustom), root.findViewById(R.id.bgPicker), root.findViewById(R.id.bgHex),
        get = { style.background }, set = { style = style.copy(background = it) }
    )
    private val text = ColorSection(
        textRow, root.findViewById(R.id.textCustom), root.findViewById(R.id.textPicker), root.findViewById(R.id.textHex),
        get = { style.text }, set = { style = style.copy(text = it) }
    )

    init {
        root.findViewById<TextView>(R.id.editorEyebrow).text = eyebrow

        val slider = root.findViewById<Slider>(R.id.opacitySlider)
        slider.value = (style.opacity / 5 * 5).toFloat()
        slider.addOnChangeListener { s, value, fromUser ->
            if (fromUser && value.toInt() % 25 == 0) s.tick()
            style = style.copy(opacity = value.toInt())
            render()
        }

        val legendSwitch = root.findViewById<MaterialSwitch>(R.id.legendSwitch)
        legendSwitch.isChecked = style.showLegend
        legendInput.visibility = if (style.showLegend) View.VISIBLE else View.GONE
        legendSwitch.setOnCheckedChangeListener { _, checked ->
            TransitionManager.beginDelayedTransition(root as ViewGroup, AutoTransition().setDuration(200))
            legendInput.visibility = if (checked) View.VISIBLE else View.GONE
            style = style.copy(showLegend = checked)
            render()
        }

        legendInput.filters = arrayOf(InputFilter.LengthFilter(WidgetStyle.LEGEND_MAX))
        legendInput.setText(style.legendText)
        legendInput.hint = "Automático · " + ClockOffset.status(store.offsetMillis, store.isCalibrated)
        legendInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                style = style.copy(legendText = s?.toString().orEmpty())
                render()
            }
        })

        root.findViewById<TextView>(R.id.editorPrimary).apply {
            text = primaryLabel
            pressable()
            setOnClickListener {
                it.confirm()
                onPrimary(style)
            }
        }
        render()
    }

    private fun render() {
        opacityValue.text = "${style.opacity}%"
        WidgetPreview.bind(preview, style, store)
    }

    private inner class ColorSection(
        private val row: LinearLayout,
        private val customPanel: View,
        private val picker: ColorPickerView,
        private val hex: EditText,
        private val get: () -> Int?,
        private val set: (Int?) -> Unit
    ) {
        private val swatches = mutableListOf<Pair<SwatchView, Int?>>()
        private val custom = SwatchView(row.context).apply { kind = SwatchView.Kind.CUSTOM }
        private var syncing = false

        init {
            for (c in WidgetStyle.SWATCHES) {
                val v = SwatchView(row.context).apply {
                    if (c == null) kind = SwatchView.Kind.AUTO else color = c
                    contentDescription = if (c == null) "Automático" else String.format("#%06X", c and 0xFFFFFF)
                    setOnClickListener { pick(c) }
                }
                addSwatch(v)
                swatches += v to c
            }
            custom.contentDescription = "Cor personalizada"
            custom.setOnClickListener { toggleCustom() }
            addSwatch(custom)

            picker.onColorChanged = { chosen ->
                apply(chosen)
                syncing = true
                hex.setText(hexOf(chosen))
                syncing = false
            }
            hex.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (syncing) return
                    val raw = s?.toString()?.removePrefix("#") ?: return
                    if (raw.length == 6) {
                        val parsed = raw.toLongOrNull(16) ?: return
                        val chosen = (0xFF000000 or parsed).toInt()
                        picker.setColor(chosen)
                        apply(chosen)
                    }
                }
            })
            refresh()
        }

        private fun addSwatch(v: SwatchView) {
            v.pressable(0.9f)
            row.addView(v, LinearLayout.LayoutParams(48.dp, 48.dp).apply { marginEnd = 4.dp })
        }

        private fun pick(c: Int?) {
            row.tick()
            showCustom(false)
            set(c)
            refresh()
            render()
        }

        private fun apply(chosen: Int) {
            set(chosen)
            refresh()
            render()
        }

        private fun toggleCustom() {
            row.tick()
            val opening = customPanel.visibility != View.VISIBLE
            showCustom(opening)
            if (opening) {
                val start = get() ?: store.let { Color.parseColor("#E60023") }
                picker.setColor(start)
                syncing = true
                hex.setText(hexOf(start))
                syncing = false
                apply(start)
            }
        }

        private fun showCustom(show: Boolean) {
            if ((customPanel.visibility == View.VISIBLE) == show) return
            TransitionManager.beginDelayedTransition(root as ViewGroup, AutoTransition().setDuration(220))
            customPanel.visibility = if (show) View.VISIBLE else View.GONE
        }

        private fun refresh() {
            val current = get()
            val inPalette = WidgetStyle.SWATCHES.contains(current)
            for ((v, c) in swatches) v.isChosen = inPalette && c == current
            custom.isChosen = !inPalette
            custom.customColor = if (inPalette) null else current
        }

        private fun hexOf(c: Int) = String.format("#%06X", c and 0xFFFFFF)
    }
}
