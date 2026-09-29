package dev.jeonsi.blects.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jeonsi.blects.R
import dev.jeonsi.blects.data.Event
import dev.jeonsi.blects.data.EventType
import dev.jeonsi.blects.data.text
import dev.jeonsi.blects.ui.SystemIntents
import dev.jeonsi.blects.ui.SystemIntents.startSafely
import dev.jeonsi.blects.ui.home.HomeViewModel
import dev.jeonsi.blects.util.Fmt
import kotlinx.coroutines.launch

/** 원칙 2 담당: 언제까지 살아 있었고 왜 못 받았는지가 보이는 화면. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    address: String,
    onBack: () -> Unit,
    vm: DeviceDetailViewModel = viewModel(key = address, factory = DeviceDetailViewModel.factory(address)),
) {
    val device by vm.device.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmRemove by remember { mutableStateOf(false) }
    val exportTitle = stringResource(R.string.export_log)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.displayName ?: address) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { context.startSafely(SystemIntents.share(vm.exportText(), exportTitle)) }
                    }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.export_log)) }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { context.startSafely(SystemIntents.bluetoothSettings()) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.open_bluetooth_settings)) }
                    OutlinedButton(
                        onClick = { confirmRemove = true },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.remove_device), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item(key = "header") {
                val nowMs = System.currentTimeMillis()
                val d = device
                Column(Modifier.padding(bottom = 12.dp)) {
                    Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val statusRes = when {
                        connected -> R.string.status_connected
                        d?.lastSyncAt != null && nowMs - d.lastSyncAt > HomeViewModel.STALE_MS -> R.string.status_stale
                        else -> R.string.status_waiting
                    }
                    val sync = d?.lastSyncAt?.let { stringResource(R.string.last_sync, Fmt.full(it)) }
                        ?: stringResource(R.string.status_never_synced)
                    Text("$sync · ${stringResource(statusRes)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.log_retention),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            if (events.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.no_events),
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(events, key = { it.id }) { event -> EventRow(event) }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_device_confirm_title, device?.displayName ?: address)) },
            text = { Text(stringResource(R.string.remove_device_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        vm.remove()
                        onBack()
                    }
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun EventRow(event: Event) {
    val context = LocalContext.current
    val global = event.address == null
    val color = when {
        event.type == EventType.ERROR -> MaterialTheme.colorScheme.error
        global -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(
            Fmt.log(event.at),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(event.text(context), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
