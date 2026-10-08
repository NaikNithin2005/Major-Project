package com.example.android

import android.app.Application
import com.example.android.di.AppContainer
import com.example.android.sync.SyncScheduler

class AegisApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        
        // Phase 8: Schedule WorkManager background periodic & startup sync
        SyncScheduler.schedulePeriodicSync(this)
        SyncScheduler.scheduleImmediateSync(this)
    }
}

