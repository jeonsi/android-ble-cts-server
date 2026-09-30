package dev.jeonsi.blects.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.jeonsi.blects.App
import dev.jeonsi.blects.data.EventType
import dev.jeonsi.blects.ui.Perms
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 부팅·앱 업데이트 뒤 서비스를 되살린다. connectedDevice 타입은 부팅 브로드캐스트에서 시작할 수 있다.
 *
 * 잠금 해제 전 부팅 신호(LOCKED_BOOT_COMPLETED)에서 먼저 띄운다. 일반 부팅 신호는 잠금 해제 뒤에야 오고
 * 다른 앱들 뒤에 줄을 서서 늦는다(실측 4분 반). 잠금 해제 전에는 [BootState] 사본만 읽는다.
 * 일반 부팅 신호는 잠금 해제 전 신호를 못 받은 경우의 안전장치로 남긴다(서비스 시작은 중복돼도 무해).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val container = (context.applicationContext as App).container
        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                container.repository.log(EventType.BOOT)
                val boot = container.bootState
                if (boot.serviceEnabled && boot.autoStart && Perms.granted(context, Perms.connect)) {
                    TimeServerService.start(context)
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val boot = action == Intent.ACTION_BOOT_COMPLETED
                if (boot && ServiceState.running.value) return
                val pending = goAsync()
                container.applicationScope.launch {
                    try {
                        val enabled = container.settings.serviceEnabled.first()
                        val autoStart = container.settings.autoStart.first()
                        val allowed = enabled && (autoStart || !boot) && Perms.granted(context, Perms.connect)
                        if (allowed) TimeServerService.start(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
