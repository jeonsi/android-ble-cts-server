package dev.jeonsi.blects.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jeonsi.blects.R
import dev.jeonsi.blects.ble.CtsScanner
import dev.jeonsi.blects.service.ServiceState
import dev.jeonsi.blects.ui.PermissionRequester
import dev.jeonsi.blects.ui.Perms
import dev.jeonsi.blects.ui.SystemIntents
import dev.jeonsi.blects.ui.SystemIntents.startSafely
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * 기기 추가 바텀 시트. CTS 솔리시테이션을 광고하는 기기만 보여 주고, 탭하면 폰이 먼저 본딩한 뒤
 * 연결을 건다(HomeViewModel.addDevice). 페어링 대화상자는 시스템이 그린다.
 * 첫 Current Time 읽기가 관찰되면 시트가 닫힌다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceSheet(
    vm: HomeViewModel,
    bluetoothOn: Boolean,
    perms: PermissionRequester,
    onDismiss: () -> Unit,
    onSynced: (String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hasPermission by remember { mutableStateOf(Perms.granted(context, Perms.scan)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<Map<String, CtsScanner.Hit>>(emptyMap()) }
    var scanError by remember { mutableStateOf<Int?>(null) }
    var connecting by remember { mutableStateOf<CtsScanner.Hit?>(null) }
    val pairingFailed by ServiceState.pairingFailed.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            perms.request(Perms.scan) {
                hasPermission = Perms.granted(context, Perms.scan)
                permissionDenied = !hasPermission
            }
        }
    }

    // 연결 중에는 스캔을 멈춘다(연결 성립을 방해한다).
    val scanning = hasPermission && bluetoothOn && connecting == null
    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        scanError = null
        try {
            CtsScanner(context).scan().collect { hit -> hits = hits + (hit.address to hit) }
        } catch (e: CtsScanner.ScanException) {
            scanError = e.code
        }
    }

    LaunchedEffect(connecting) {
        val target = connecting ?: return@LaunchedEffect
        ServiceState.pairingFailed.value = null
        if (!vm.addDevice(target.address, target.name)) return@LaunchedEffect
        vm.device(target.address).filterNotNull().first { it.lastSyncAt != null }
        onSynced(target.name ?: target.address)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.add_device_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.add_device_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            val target = connecting
            when {
                !bluetoothOn -> {
                    Text(stringResource(R.string.bt_off_hint))
                    TextButton(onClick = {
                        context.startSafely(
                            if (Perms.granted(context, Perms.connect)) SystemIntents.enableBluetooth() else SystemIntents.bluetoothSettings()
                        )
                    }) { Text(stringResource(R.string.turn_on_bluetooth)) }
                }
                permissionDenied -> {
                    Text(stringResource(R.string.need_scan_permission))
                    TextButton(onClick = { context.startSafely(SystemIntents.appDetails(context)) }) {
                        Text(stringResource(R.string.open_app_settings))
                    }
                }
                target != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.connecting_to, target.name ?: target.address))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pairing_hint), style = MaterialTheme.typography.bodySmall)
                    if (pairingFailed == target.address) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.pairing_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = { connecting = null }) { Text(stringResource(R.string.retry)) }
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (scanError != null) stringResource(R.string.scan_failed, scanError!!) else stringResource(R.string.scanning),
                            color = if (scanError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    for (hit in hits.values.sortedByDescending { it.rssi }) {
                        ListItem(
                            headlineContent = { Text(hit.name ?: stringResource(R.string.unnamed_device)) },
                            supportingContent = { Text(hit.address) },
                            trailingContent = { Text(stringResource(R.string.rssi_dbm, hit.rssi)) },
                            modifier = Modifier.fillMaxWidth().clickable { connecting = hit },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            // 기기 쪽이 광고 중이어야 한다는 점이 가장 큰 사용성 함정. 힌트를 상시 노출한다.
            Text(stringResource(R.string.not_visible_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.not_visible_hint_1), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.not_visible_hint_2), style = MaterialTheme.typography.bodySmall)
        }
    }
}
