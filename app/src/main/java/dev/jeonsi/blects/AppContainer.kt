package dev.jeonsi.blects

import android.app.Application
import dev.jeonsi.blects.ble.BluetoothMonitor
import dev.jeonsi.blects.data.AppDatabase
import dev.jeonsi.blects.data.BootState
import dev.jeonsi.blects.data.Repository
import dev.jeonsi.blects.data.Settings
import dev.jeonsi.blects.data.UnlockMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 앱 전역 의존성. Application 수명과 같다.
 *
 * 잠금 해제 전 부팅에서도 만들어진다. Room 과 DataStore 는 첫 사용 때 파일을 열므로, 잠금 해제 전에는
 * [Repository] 와 [Settings] 가 [unlock] 을 보고 사용자 저장소를 건드리지 않는다.
 */
class AppContainer(app: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val unlock = UnlockMonitor(app)
    val bootState = BootState(app)
    val database = AppDatabase.create(app)
    val repository = Repository(app, database, bootState, unlock.unlocked, applicationScope)
    val settings = Settings(app, bootState)
    val bluetooth = BluetoothMonitor(app)
}
