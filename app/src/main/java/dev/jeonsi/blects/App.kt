package dev.jeonsi.blects

import android.app.Application
import dev.jeonsi.blects.service.Notifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
        container.applicationScope.launch {
            container.unlock.unlocked.first { it }
            container.settings.mirrorToBootState()
        }
    }
}
