package dev.jeonsi.blects.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.jeonsi.blects.App
import dev.jeonsi.blects.data.EventType
import dev.jeonsi.blects.ui.Perms
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 부팅·앱 업데이트 뒤 서비스를 되살린다. connectedDevice 타입은 부팅 브로드캐스트에서 시작할 수 있다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val boot = intent.action == Intent.ACTION_BOOT_COMPLETED
        if (!boot && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val container = (context.applicationContext as App).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                if (boot) container.repository.logNow(EventType.BOOT)
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
