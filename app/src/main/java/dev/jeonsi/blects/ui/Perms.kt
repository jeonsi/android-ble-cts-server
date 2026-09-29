package dev.jeonsi.blects.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** SDK 별 런타임 권한 묶음. 필요한 순간에 하나씩 묻는다(usability.md 의 권한 순서). */
object Perms {
    /** 서비스 스위치 ON: GATT 서버 개설과 연결에 필요 */
    val connect: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()

    /** 기기 추가: 검색. 31+ 는 같은 "근처 기기" 그룹이라 CONNECT 와 한 번에 뜬다 */
    val scan: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** 지속 알림 표시. 거부해도 서비스는 뜬다 */
    val notifications: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

    fun granted(context: Context, perms: Array<String>): Boolean =
        perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

/** 홈 배너 판단에 쓰는 시스템 상태 스냅샷. onResume 마다 다시 읽는다. */
data class SystemStatus(
    val hasConnect: Boolean,
    val hasScan: Boolean,
    val notificationsEnabled: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val autoTime: Boolean,
) {
    companion object {
        fun read(context: Context): SystemStatus {
            val pm = context.getSystemService(PowerManager::class.java)
            return SystemStatus(
                hasConnect = Perms.granted(context, Perms.connect),
                hasScan = Perms.granted(context, Perms.scan),
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                ignoringBatteryOptimizations = pm?.isIgnoringBatteryOptimizations(context.packageName) == true,
                autoTime = Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) == 1,
            )
        }
    }
}

/** 런처 하나로 순차 권한 요청을 콜백 체인으로 쓰기 위한 얇은 래퍼. */
class PermissionRequester(
    private val launcher: ActivityResultLauncher<Array<String>>,
    private val pending: MutableState<((Map<String, Boolean>) -> Unit)?>,
) {
    fun request(perms: Array<String>, onResult: (Map<String, Boolean>) -> Unit) {
        if (perms.isEmpty()) {
            onResult(emptyMap())
            return
        }
        pending.value = onResult
        launcher.launch(perms)
    }
}

@Composable
fun rememberPermissionRequester(): PermissionRequester {
    val pending = remember { mutableStateOf<((Map<String, Boolean>) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val cb = pending.value
        pending.value = null
        cb?.invoke(result)
    }
    return remember(launcher) { PermissionRequester(launcher, pending) }
}
