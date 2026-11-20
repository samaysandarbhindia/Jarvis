package com.example

import android.app.Application
import android.content.Context
import com.example.jarvis.core.di.AppContainer

class JarvisApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        container = AppContainer(applicationContext)
        // Note: Firebase App Check is intentionally NOT initialized here.
        // It is configured in MainActivity.onCreate(intent) per app-check.md.
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
