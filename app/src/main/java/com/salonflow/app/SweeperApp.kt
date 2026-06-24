package com.salonflow.app

import android.app.Application

class SweeperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SweeperScheduler.init(this)
    }
}
