package com.remora

import android.app.Application
import com.remora.di.AppContainer
import com.remora.di.DefaultAppContainer
import timber.log.Timber

class RemoraApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    return "Remora:${super.createStackElementTag(element)}"
                }
            })
        }
        container = DefaultAppContainer(applicationContext)
        Timber.i("Remora application and AppContainer initialized")
    }
}
