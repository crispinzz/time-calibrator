package br.com.timecalibrator

import android.app.Application

class TimeCalibratorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeMode.applyAppTheme(this)
    }
}
