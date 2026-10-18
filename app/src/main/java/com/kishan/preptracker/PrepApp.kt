package com.kishan.preptracker

import android.app.Application
import com.kishan.preptracker.sync.HealthSyncWorker
import com.kishan.preptracker.train.RestAlerts

class PrepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RestAlerts.ensureChannel(this)
        HealthSyncWorker.schedule(this)
    }
}
