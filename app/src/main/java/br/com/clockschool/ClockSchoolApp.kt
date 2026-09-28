package br.com.clockschool

import android.app.Application

class ClockSchoolApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeMode.applyAppTheme(this)
    }
}
