package com.wledcontroller.app

import android.app.Application

class WledApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
        container = AppContainer(this)
    }
}
