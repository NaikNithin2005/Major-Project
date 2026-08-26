package com.example.android

import android.app.Application
import com.example.android.di.AppContainer

class AegisApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
