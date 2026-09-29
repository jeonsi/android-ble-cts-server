package dev.jeonsi.blects

import android.app.Application
import dev.jeonsi.blects.service.Notifications

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
    }
}
