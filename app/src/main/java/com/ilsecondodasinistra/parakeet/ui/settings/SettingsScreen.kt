package com.ilsecondodasinistra.parakeet.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.alarm.Notifications
import com.ilsecondodasinistra.parakeet.ui.main.MainUiState
import com.ilsecondodasinistra.parakeet.ui.main.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: MainUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var exactAllowed by remember { mutableStateOf(viewModel.canScheduleExact()) }
    var notificationsBlocked by remember { mutableStateOf(false) }
    var text by remember(state.exitText) { mutableStateOf(state.exitText) }

    // Coming back from the system "Alarms & reminders" / notification screens: refresh status
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val before = exactAllowed
        exactAllowed = viewModel.canScheduleExact()
        if (before != exactAllowed) viewModel.refreshExitNotification()
        if (state.notifyOnExit && Notifications.canPost(context)) notificationsBlocked = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsBlocked = !granted
        viewModel.setNotifyOnExit(granted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AlarmSection(state, viewModel)

            // Notification switch: the whole row toggles
            SettingsCard {
                Row(
                    Modifier
                        .toggleable(
                            value = state.notifyOnExit,
                            role = Role.Switch,
                            onValueChange = { enable ->
                                if (enable && Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else viewModel.setNotifyOnExit(enable)
                            },
                        )
                        .heightIn(min = 72.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.notify_on_exit), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.notify_on_exit_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(checked = state.notifyOnExit, onCheckedChange = null)
                }
            }
            if (notificationsBlocked) {
                SettingsCard(container = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.notif_permission_denied), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.open_system_settings)) }
                    }
                }
            }

            if (state.notifyOnExit) {
                SettingsCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.exact_alarm_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(if (exactAllowed) R.string.exact_alarm_granted else R.string.exact_alarm_denied),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!exactAllowed) {
                            FilledTonalButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                                    )
                                },
                                modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.exact_alarm_allow)) }
                        }
                    }
                }

                SettingsCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.exit_text_title), style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it; viewModel.setExitText(it) },
                            placeholder = { Text(stringResource(R.string.notification_exit_text)) },
                            supportingText = { Text(stringResource(R.string.exit_text_hint)) },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (text.isNotEmpty()) {
                            TextButton(onClick = { text = ""; viewModel.setExitText("") }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.exit_text_reset))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SettingsCard(
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth()) { content() }
}
