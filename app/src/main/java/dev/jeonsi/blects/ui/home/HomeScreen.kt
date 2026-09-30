package dev.jeonsi.blects.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jeonsi.blects.R
import dev.jeonsi.blects.ui.Perms
import dev.jeonsi.blects.ui.SystemIntents
import dev.jeonsi.blects.ui.SystemIntents.startSafely
import dev.jeonsi.blects.ui.rememberPermissionRequester
import dev.jeonsi.blects.util.Fmt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDevice: (String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val perms = rememberPermissionRequester()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<DeviceUi?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.refreshSystemStatus()
        vm.ensureServiceRunning()
        onPauseOrDispose { }
    }

    // 첫 동기화 성공 직후 한 번만: 성공 경험 뒤에 물어야 수락률이 높다.
    LaunchedEffect(state.batteryPromptDue) {
        if (state.batteryPromptDue) {
            vm.markBatteryPromptDone()
            context.startSafely(SystemIntents.requestIgnoreBattery(context))
        }
    }

    val needConnectText = stringResource(R.string.need_connect_permission)
    val openAppSettingsText = stringResource(R.string.open_app_settings)
    val removedHint = stringResource(R.string.removed_hint)
    val openSettingsText = stringResource(R.string.open_settings)

    fun enableService() {
        perms.request(Perms.connect) {
            vm.refreshSystemStatus()
            if (!Perms.granted(context, Perms.connect)) {
                scope.launch {
                    val r = snackbar.showSnackbar(needConnectText, actionLabel = openAppSettingsText)
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        context.startSafely(SystemIntents.appDetails(context))
                    }
                }
                return@request
            }
            vm.setServiceEnabled(true)
            perms.request(Perms.notifications) {
                vm.refreshSystemStatus()
                vm.refreshNotification()
            }
        }
    }

    fun onBannerAction(banner: Banner) {
        when (banner) {
            Banner.Permission, is Banner.ServiceOff -> enableService()
            Banner.BluetoothOff ->
                if (Perms.granted(context, Perms.connect)) {
                    context.startSafely(SystemIntents.enableBluetooth())
                } else {
                    context.startSafely(SystemIntents.bluetoothSettings())
                }
            Banner.Battery -> context.startSafely(SystemIntents.requestIgnoreBattery(context))
            Banner.Notifications -> context.startSafely(SystemIntents.appNotificationSettings(context))
            is Banner.Stale -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_settings)) },
                            onClick = { menuOpen = false; onOpenSettings() },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.banner?.let { banner ->
                item(key = "banner") {
                    StatusBanner(banner = banner, nowMs = state.nowMs, onAction = { onBannerAction(banner) })
                }
            }
            item(key = "service") {
                ServiceCard(
                    state = state,
                    onToggle = { on -> if (on) enableService() else vm.setServiceEnabled(false) },
                    onOpenDateSettings = { context.startSafely(SystemIntents.dateSettings()) },
                )
            }
            item(key = "devices-header") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.devices), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add))
                    }
                }
            }
            if (state.devices.isEmpty()) {
                item(key = "empty") { EmptyDevices() }
            } else {
                items(state.devices, key = { it.address }) { device ->
                    DeviceCard(
                        device = device,
                        nowMs = state.nowMs,
                        onClick = { onOpenDevice(device.address) },
                        onLongClick = { removeTarget = device },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddDeviceSheet(
            vm = vm,
            bluetoothOn = state.bluetoothOn,
            perms = perms,
            onDismiss = { showAdd = false },
            onSynced = { name ->
                showAdd = false
                vm.refreshSystemStatus()
                scope.launch { snackbar.showSnackbar(resources.getString(R.string.first_sync_done, name)) }
            },
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.remove_device_confirm_title, target.displayName)) },
            text = { Text(stringResource(R.string.remove_device_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeDevice(target.address)
                    removeTarget = null
                    scope.launch {
                        val r = snackbar.showSnackbar(removedHint, actionLabel = openSettingsText)
                        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            context.startSafely(SystemIntents.bluetoothSettings())
                        }
                    }
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun StatusBanner(banner: Banner, nowMs: Long, onAction: () -> Unit) {
    val (title, body, action) = when (banner) {
        Banner.Permission -> Triple(R.string.banner_permission_title, R.string.banner_permission_body, R.string.banner_permission_action)
        Banner.BluetoothOff -> Triple(R.string.banner_bt_off_title, R.string.banner_bt_off_body, R.string.banner_bt_off_action)
        is Banner.ServiceOff ->
            if (banner.died) Triple(R.string.banner_service_died_title, R.string.banner_service_died_body, R.string.banner_service_action)
            else Triple(R.string.banner_service_off_title, R.string.banner_service_off_body, R.string.banner_service_action)
        Banner.Battery -> Triple(R.string.banner_battery_title, R.string.banner_battery_body, R.string.banner_battery_action)
        Banner.Notifications -> Triple(R.string.banner_notif_title, R.string.banner_notif_body, R.string.banner_notif_action)
        is Banner.Stale -> Triple(R.string.banner_stale_title, R.string.banner_stale_body, null)
    }
    val titleText = if (banner is Banner.Stale) stringResource(title, Fmt.ago(banner.sinceMs, nowMs)) else stringResource(title)
    val severe = banner !is Banner.Stale
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (severe) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(titleText, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(body), style = MaterialTheme.typography.bodySmall)
                if (action != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onAction) { Text(stringResource(action)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(state: HomeUiState, onToggle: (Boolean) -> Unit, onOpenDateSettings: () -> Unit) {
    val now = Fmt.at(state.nowMs)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = (if (state.serviceRunning) "● " else "○ ") +
                            stringResource(if (state.serviceRunning) R.string.service_running else R.string.service_stopped),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // 시각은 실제로 내보낼 값이다. 시계가 틀리게 나올 때 "폰이 뭘 보냈는지" 바로 볼 수 있다.
                    Text(stringResource(R.string.phone_time, Fmt.full(now)), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        Fmt.zoneLabel(now),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.serviceEnabled, onCheckedChange = onToggle)
            }
            if (!state.autoTime) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.auto_time_off),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenDateSettings) { Text(stringResource(R.string.open_settings)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceCard(device: DeviceUi, nowMs: Long, onClick: () -> Unit, onLongClick: () -> Unit) {
    val status = buildString {
        if (device.lastSyncAt != null) {
            append(stringResource(R.string.last_sync, Fmt.clock(device.lastSyncAt, nowMs)))
        } else {
            append(stringResource(R.string.status_never_synced))
        }
        append(" · ")
        append(
            stringResource(
                when {
                    device.connected -> R.string.status_connected
                    device.stale -> R.string.status_stale
                    else -> R.string.status_waiting
                }
            )
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(device.displayName, style = MaterialTheme.typography.titleMedium)
            if (device.name != null) {
                Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    device.connected -> MaterialTheme.colorScheme.primary
                    device.stale -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun EmptyDevices() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.no_devices_title), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.no_devices_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
