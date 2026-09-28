package br.com.clockschool.ui

import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import br.com.clockschool.ClockOffset
import br.com.clockschool.ClockStore
import br.com.clockschool.R
import br.com.clockschool.ThemeMode
import br.com.clockschool.WidgetStyle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Calendar
import java.util.Locale

object Sheets {

    private fun sheet(activity: Activity, content: View): BottomSheetDialog {
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(content)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        return dialog
    }

    fun timePicker(activity: Activity, hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_time_picker, null)
        val hours = view.findViewById<WheelPicker>(R.id.wheelHour).apply {
            minValue = 0; maxValue = 23
            setValue(hour, animate = false)
        }
        val minutes = view.findViewById<WheelPicker>(R.id.wheelMinute).apply {
            minValue = 0; maxValue = 59
            setValue(minute, animate = false)
        }

        // Sugestões: as marcas de 5 min mais próximas da hora da escola agora.
        val cal = Calendar.getInstance().apply {
            timeInMillis = ClockOffset.schoolNow(ClockStore(activity).offsetMillis)
        }
        var base = cal.get(Calendar.HOUR_OF_DAY) * 60 + (cal.get(Calendar.MINUTE) + 2) / 5 * 5
        val row = view.findViewById<LinearLayout>(R.id.suggestions)
        repeat(3) {
            val total = Math.floorMod(base, 24 * 60)
            val chip = TextView(activity, null, 0, R.style.Text_Label).apply {
                text = String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
                setBackgroundResource(R.drawable.bg_pill_outline)
                gravity = Gravity.CENTER
                setPadding(18.dp, 11.dp, 18.dp, 11.dp)
                isClickable = true
                pressable()
                setOnClickListener {
                    hours.setValue(total / 60, animate = true)
                    minutes.setValue(total % 60, animate = true)
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginStart = 4.dp; marginEnd = 4.dp })
            base += 5
        }

        val dialog = sheet(activity, view)
        view.findViewById<View>(R.id.confirmButton).apply {
            pressable()
            setOnClickListener {
                it.confirm()
                onPicked(hours.value, minutes.value)
                dialog.dismiss()
            }
        }
        dialog.behavior.isDraggable = false
        dialog.show()
    }

    fun theme(activity: Activity, onChanged: () -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_theme, null)
        val store = ClockStore(activity)
        val options = view.findViewById<LinearLayout>(R.id.themeOptions)
        val dialog = sheet(activity, view)
        val buttons = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK).map { mode ->
            mode to TextView(activity, null, 0, R.style.Text_Label).apply {
                text = ThemeMode.label(mode)
                gravity = Gravity.CENTER
                isClickable = true
                options.addView(this, LinearLayout.LayoutParams(0, -1, 1f))
            }
        }
        fun paint(selected: Int) {
            for ((mode, b) in buttons) {
                val on = mode == selected
                if (on) b.setBackgroundResource(R.drawable.bg_card) else b.background = null
                b.setTextColor(activity.color(if (on) R.color.text_primary else R.color.text_secondary))
            }
        }
        paint(store.appTheme)
        for ((mode, b) in buttons) b.setOnClickListener {
            it.tick()
            paint(mode)
            store.appTheme = mode
            it.postDelayed({
                dialog.dismiss()
                onChanged()
            }, 180)
        }
        dialog.show()
    }

    fun widgetEditor(
        activity: Activity,
        initial: WidgetStyle,
        eyebrow: String,
        primaryLabel: String,
        onPrimary: (WidgetStyle, BottomSheetDialog) -> Unit
    ) {
        val editor = LayoutInflater.from(activity).inflate(R.layout.view_widget_editor, null)
        val scroll = NestedScrollView(activity).apply {
            setBackgroundResource(R.drawable.bg_sheet)
            isFillViewport = true
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24.dp, 12.dp, 24.dp, 28.dp)
                addView(View(activity).apply { setBackgroundResource(R.drawable.bg_handle) },
                    LinearLayout.LayoutParams(40.dp, 4.dp).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = 20.dp })
                addView(editor)
            }
            addView(column)
        }
        lateinit var dialog: BottomSheetDialog
        WidgetEditor(editor, initial, eyebrow, primaryLabel) { style -> onPrimary(style, dialog) }
        dialog = sheet(activity, scroll)
        dialog.show()
    }
}
