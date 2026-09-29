package dev.jeonsi.blects

import android.app.Application
import dev.jeonsi.blects.ble.BluetoothMonitor
import dev.jeonsi.blects.data.AppDatabase
import dev.jeonsi.blects.data.Repository
import dev.jeonsi.blects.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 앱 전역 의존성. Application 수명과 같다. */
class AppContainer(app: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = AppDatabase.create(app)
    val repository = Repository(app, database, applicationScope)
    val settings = Settings(app)
    val bluetooth = BluetoothMonitor(app)
}
