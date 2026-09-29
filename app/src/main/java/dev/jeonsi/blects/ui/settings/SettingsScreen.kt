package dev.jeonsi.blects.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jeonsi.blects.App
import dev.jeonsi.blects.R
import dev.jeonsi.blects.ui.SystemIntents
import dev.jeonsi.blects.ui.SystemIntents.startSafely
import dev.jeonsi.blects.ui.SystemStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    val scope = rememberCoroutineScope()
    val autoStart by container.settings.autoStart.collectAsStateWithLifecycle(initialValue = true)
    var system by remember { mutableStateOf(SystemStatus.read(context)) }
    val exportTitle = stringResource(R.string.export_log)

    LifecycleResumeEffect(Unit) {
        system = SystemStatus.read(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.auto_start)) },
                supportingContent = { Text(stringResource(R.string.auto_start_desc)) },
                trailingContent = {
                    Switch(checked = autoStart, onCheckedChange = { v -> scope.launch { container.settings.setAutoStart(v) } })
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.notification_settings)) },
                supportingContent = { Text(stringResource(R.string.notification_settings_desc)) },
                modifier = Modifier.clickable { context.startSafely(SystemIntents.channelSettings(context)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.battery_optimization)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (system.ignoringBatteryOptimizations) R.string.battery_excluded else R.string.battery_not_excluded
                        )
                    )
                },
                trailingContent = {
                    if (!system.ignoringBatteryOptimizations) {
                        TextButton(onClick = { context.startSafely(SystemIntents.requestIgnoreBattery(context)) }) {
                            Text(stringResource(R.string.battery_exclude))
                        }
                    }
                },
                modifier = Modifier.clickable { context.startSafely(SystemIntents.batterySettings()) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.vendor_guide)) },
                supportingContent = { Text(stringResource(R.string.vendor_guide_desc)) },
                modifier = Modifier.clickable { context.startSafely(SystemIntents.url("https://dontkillmyapp.com/")) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.export_log)) },
                supportingContent = { Text(stringResource(R.string.export_log_desc)) },
                modifier = Modifier.clickable {
                    scope.launch { context.startSafely(SystemIntents.share(container.repository.exportText(), exportTitle)) }
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.info),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            ListItem(headlineContent = { Text(stringResource(R.string.version, version)) })
            ListItem(
                headlineContent = { Text(stringResource(R.string.served_services)) },
                supportingContent = {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.service_cts), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.service_cts_chars), style = MaterialTheme.typography.bodySmall)
                        Text(
                            stringResource(R.string.service_ancs),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(stringResource(R.string.service_ancs_desc), style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
        }
    }
}
