package dev.jeonsi.blects.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import dev.jeonsi.blects.R
import dev.jeonsi.blects.service.Notifications

/** 원칙 3: 시스템에 맡길 수 있는 건 시스템 화면으로 보낸다. */
object SystemIntents {
    /** 1시간마다 오는 BLE 요청에 응답해야 하는 connected-device 앱이라 허용되는 사용 사례다. */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBattery(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    fun batterySettings() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appNotificationSettings(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun channelSettings(context: Context) =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CHANNEL_ID)

    fun bluetoothSettings() = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    fun enableBluetooth() = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    fun dateSettings() = Intent(Settings.ACTION_DATE_SETTINGS)

    fun url(url: String) = Intent(Intent.ACTION_VIEW, url.toUri())

    fun share(text: String, title: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
            .putExtra(Intent.EXTRA_SUBJECT, title),
        title,
    )

    fun Context.startSafely(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_activity, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.no_activity, Toast.LENGTH_SHORT).show()
        }
    }
}
