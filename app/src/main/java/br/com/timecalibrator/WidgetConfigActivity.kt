package br.com.timecalibrator

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import br.com.timecalibrator.ui.WidgetEditor
import br.com.timecalibrator.ui.dp

/** Aberta pelo launcher ao adicionar ou reconfigurar um widget. */
class WidgetConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val editor = LayoutInflater.from(this).inflate(R.layout.view_widget_editor, null)
        val scroll = NestedScrollView(this).apply {
            setBackgroundResource(R.color.screen_bg)
            addView(FrameLayout(this@WidgetConfigActivity).apply {
                setPadding(24.dp, 24.dp, 24.dp, 24.dp)
                addView(editor)
            })
        }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        Clocks.claimPending(this, intArrayOf(id))
        val clockId = Clocks.forWidget(this, id)
        WidgetEditor(editor, WidgetStyles.load(this, id), "Personalizar widget", "Salvar", clockId, Clocks.name(this, clockId)) { style, name ->
            Clocks.saveStyle(this, clockId, style)
            if (name.isNotEmpty()) Clocks.rename(this, clockId, name)
            WidgetStyles.clearLegacy(this, id)
            ClockWidgetProvider.updateAllWidgets(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
    }
}
