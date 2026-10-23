package com.floatcalc.app

import android.app.Application

class FloatCalcApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
