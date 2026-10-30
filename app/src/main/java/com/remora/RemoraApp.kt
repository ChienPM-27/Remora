package com.remora

import android.app.Application
import com.remora.di.AppContainer
import com.remora.di.DefaultAppContainer
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { container.saveCoordinator.reconcilePending() }
            catch (_: Exception) { Timber.w("Pending work reconciliation deferred") }
        }
        Timber.i("Remora application and AppContainer initialized")
    }
}
