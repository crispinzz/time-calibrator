package br.com.clockschool

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var correctedTimeText: TextClock
    private lateinit var statusPill: TextView
    private lateinit var scheduleChip: TextView
    private lateinit var calibrationInfoText: TextView
    private lateinit var codeText: TextView

    private var scheduledHour = 7
    private var scheduledMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

        correctedTimeText = findViewById(R.id.correctedTimeText)
        statusPill = findViewById(R.id.statusPill)
        scheduleChip = findViewById(R.id.scheduleChip)
        calibrationInfoText = findViewById(R.id.calibrationInfoText)
        codeText = findViewById(R.id.codeText)

        scheduledHour = prefs.getInt(Prefs.SCHEDULED_HOUR, 7)
        scheduledMinute = prefs.getInt(Prefs.SCHEDULED_MINUTE, 0)
        updateScheduleChip()

        findViewById<View>(R.id.scheduleButton).setOnClickListener { openTimePicker() }
        findViewById<View>(R.id.calibrateButton).setOnClickListener { calibrateNow() }
        findViewById<View>(R.id.pinWidgetButton).setOnClickListener { pinWidget() }
        findViewById<View>(R.id.codeButton).setOnClickListener { openCodeDialog() }
        findViewById<View>(R.id.copyCodeIcon).setOnClickListener { copyCode() }
        findViewById<View>(R.id.themeButton).setOnClickListener { openThemeDialog() }

        refreshDisplay()
    }

    override fun onResume() {
        super.onResume()
        refreshDisplay()
    }

    private fun openTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(scheduledHour)
            .setMinute(scheduledMinute)
            .setTitleText("Horário previsto do sinal")
            .build()

        picker.addOnPositiveButtonClickListener {
            scheduledHour = picker.hour
            scheduledMinute = picker.minute
            prefs.edit()
                .putInt(Prefs.SCHEDULED_HOUR, scheduledHour)
                .putInt(Prefs.SCHEDULED_MINUTE, scheduledMinute)
                .apply()
            updateScheduleChip()
        }

        picker.show(supportFragmentManager, "schedule_time_picker")
    }

    private fun updateScheduleChip() {
        scheduleChip.text = String.format(Locale.getDefault(), "%02d:%02d", scheduledHour, scheduledMinute)
    }

    private fun calibrateNow() {
        val now = Calendar.getInstance()
        val scheduled = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, scheduledHour)
            set(Calendar.MINUTE, scheduledMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        applyOffset(now.timeInMillis - scheduled.timeInMillis, viaCode = false)

        val offsetMillis = prefs.getLong(Prefs.OFFSET_MILLIS, 0L)
        val label = if (offsetMillis >= 0) "atrasada" else "adiantada"
        Toast.makeText(this, "Escola $label ${ClockOffset.formatDuration(offsetMillis)}", Toast.LENGTH_LONG).show()
    }

    private fun applyOffset(offsetMillis: Long, viaCode: Boolean) {
        prefs.edit()
            .putLong(Prefs.OFFSET_MILLIS, offsetMillis)
            .putLong(Prefs.LAST_CALIBRATION_AT, System.currentTimeMillis())
            .putBoolean(Prefs.CALIBRATED_VIA_CODE, viaCode)
            .apply()

        ClockWidgetProvider.updateAllWidgets(this)
        refreshDisplay()
    }

    private fun refreshDisplay() {
        val offsetMillis = prefs.getLong(Prefs.OFFSET_MILLIS, 0L)
        correctedTimeText.timeZone = ClockOffset.shiftedTimeZoneId(offsetMillis)

        val lastCalibration = prefs.getLong(Prefs.LAST_CALIBRATION_AT, -1L)
        val viaCode = prefs.getBoolean(Prefs.CALIBRATED_VIA_CODE, false)

        if (lastCalibration < 0) {
            codeText.text = "—"
            calibrationInfoText.text = "nunca calibrado"
        } else {
            codeText.text = CalibrationCode.encode(offsetMillis)
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastCalibration))
            calibrationInfoText.text = if (viaCode) "código aplicado às $time" else "calibrado às $time"
        }

        val pillRes: Int
        val pillColor: Int
        val pillText: String
        when {
            lastCalibration < 0 -> {
                pillRes = R.drawable.bg_pill_neutral
                pillColor = R.color.pill_neutral_text
                pillText = "não calibrado"
            }
            offsetMillis == 0L -> {
                pillRes = R.drawable.bg_pill_success
                pillColor = R.color.pill_success_text
                pillText = "sincronizado"
            }
            offsetMillis > 0 -> {
                pillRes = R.drawable.bg_pill_warning
                pillColor = R.color.pill_warning_text
                pillText = "atrasada " + ClockOffset.formatDuration(offsetMillis)
            }
            else -> {
                pillRes = R.drawable.bg_pill_info
                pillColor = R.color.pill_info_text
                pillText = "adiantada " + ClockOffset.formatDuration(offsetMillis)
            }
        }
        statusPill.setBackgroundResource(pillRes)
        statusPill.setTextColor(ContextCompat.getColor(this, pillColor))
        statusPill.text = pillText
    }

    private fun currentCodeOrNull(): String? {
        if (prefs.getLong(Prefs.LAST_CALIBRATION_AT, -1L) < 0) return null
        return CalibrationCode.encode(prefs.getLong(Prefs.OFFSET_MILLIS, 0L))
    }

    private fun copyCode() {
        val code = currentCodeOrNull()
        if (code == null) {
            Toast.makeText(this, "Calibre primeiro para gerar um código", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Código de calibração", code))
        Toast.makeText(this, "Código $code copiado", Toast.LENGTH_SHORT).show()
    }

    private fun openCodeDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_code, null)
        val ownCode = view.findViewById<TextView>(R.id.dialogOwnCode)
        val ownCodeHint = view.findViewById<TextView>(R.id.dialogOwnCodeHint)
        val input = view.findViewById<EditText>(R.id.dialogCodeInput)

        val code = currentCodeOrNull()
        ownCode.text = code ?: "—"
        if (code == null) {
            ownCodeHint.text = "Calibre primeiro para gerar um código."
        }

        val dialog = AlertDialog.Builder(this).setView(view).create()

        view.findViewById<View>(R.id.dialogCopyButton).setOnClickListener { copyCode() }

        view.findViewById<View>(R.id.dialogApplyButton).setOnClickListener {
            val offsetMillis = CalibrationCode.decode(input.text.toString())
            if (offsetMillis == null) {
                Toast.makeText(this, "Código inválido", Toast.LENGTH_SHORT).show()
            } else {
                applyOffset(offsetMillis, viaCode = true)
                val label = if (offsetMillis >= 0) "atrasada" else "adiantada"
                Toast.makeText(
                    this,
                    "Aplicado: escola $label ${ClockOffset.formatDuration(offsetMillis)}",
                    Toast.LENGTH_LONG
                ).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun openThemeDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_theme, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()

        val appOptions = listOf(
            ThemeMode.SYSTEM to view.findViewById<TextView>(R.id.appThemeSystem),
            ThemeMode.LIGHT to view.findViewById(R.id.appThemeLight),
            ThemeMode.DARK to view.findViewById(R.id.appThemeDark)
        )
        val widgetOptions = listOf(
            ThemeMode.SYSTEM to view.findViewById<TextView>(R.id.widgetThemeSystem),
            ThemeMode.LIGHT to view.findViewById(R.id.widgetThemeLight),
            ThemeMode.DARK to view.findViewById(R.id.widgetThemeDark)
        )

        paintSegments(appOptions, prefs.getInt(Prefs.APP_THEME, ThemeMode.SYSTEM))
        paintSegments(widgetOptions, prefs.getInt(Prefs.WIDGET_THEME, ThemeMode.SYSTEM))

        for ((mode, button) in appOptions) {
            button.setOnClickListener {
                prefs.edit().putInt(Prefs.APP_THEME, mode).apply()
                paintSegments(appOptions, mode)
                ThemeMode.applyAppTheme(this)
            }
        }

        for ((mode, button) in widgetOptions) {
            button.setOnClickListener {
                prefs.edit().putInt(Prefs.WIDGET_THEME, mode).apply()
                paintSegments(widgetOptions, mode)
                ClockWidgetProvider.updateAllWidgets(this)
            }
        }

        dialog.show()
    }

    private fun paintSegments(options: List<Pair<Int, TextView>>, selected: Int) {
        for ((mode, button) in options) {
            val isSelected = mode == selected
            button.setBackgroundResource(
                if (isSelected) R.drawable.bg_segment_selected else R.drawable.bg_segment
            )
            button.setTextColor(
                ContextCompat.getColor(this, if (isSelected) R.color.card_bg else R.color.text_secondary)
            )
        }
    }

    private fun pinWidget() {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val provider = ComponentName(this, ClockWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(provider, null, null)
            } else {
                Toast.makeText(this, "Adicione o widget manualmente: segure a tela inicial > Widgets", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível fixar automaticamente. Adicione pela tela inicial > Widgets.", Toast.LENGTH_LONG).show()
        }
    }
}
