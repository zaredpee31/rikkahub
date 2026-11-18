package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionNotification
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.utils.toLocalDateTime
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceNavRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSwitchRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import org.koin.androidx.compose.koinViewModel

private val UPDATE_PAUSE_DAY_OPTIONS = listOf(7, 14, 21)
private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1_000L

@Composable
fun SettingPreferencesNotificationPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var showUpdatePauseDialog by remember { mutableStateOf(false) }
    var selectedUpdatePauseDays by remember { mutableStateOf(UPDATE_PAUSE_DAY_OPTIONS.first()) }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    val updateChecksEnabled =
        displaySetting.updateCheckDisabledUntilEpochMillis <= System.currentTimeMillis()

    val permissionState = rememberPermissionState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setOf(
            PermissionNotification
        ) else emptySet(),
    )
    PermissionManager(permissionState = permissionState)

    val context = LocalContext.current
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    // 从系统设置页授权回来后刷新
    LifecycleResumeEffect(Unit) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        onPauseOrDispose {}
    }

    fun requestOverlayPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        )
    }

    val navController = LocalNavController.current
    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = stringResource(R.string.setting_page_preferences_notification),
                sub = "NOTIFICATION",
                onBack = { navController.popBackStack() },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Play 版由商店负责更新，不提供更新提醒
                if (!BuildConfig.IS_PLAY_STORE) {
                    PalaceSection("UPDATE", "更新")
                    PalaceGlass {
                        PalaceNavRow(
                            title = stringResource(R.string.setting_display_page_show_updates_title),
                            desc = if (updateChecksEnabled) {
                                stringResource(R.string.setting_update_reminder_enabled)
                            } else {
                                stringResource(
                                    R.string.setting_update_reminder_paused_until,
                                    Instant.ofEpochMilli(displaySetting.updateCheckDisabledUntilEpochMillis)
                                        .toLocalDateTime(),
                                )
                            },
                            onClick = {
                                selectedUpdatePauseDays = UPDATE_PAUSE_DAY_OPTIONS.first()
                                showUpdatePauseDialog = true
                            },
                        )
                    }
                }

                PalaceSection("BACKGROUND", "后台生成")
                PalaceGlass {
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_notification_message_generated),
                        desc = stringResource(R.string.setting_display_page_notification_message_generated_desc),
                        checked = displaySetting.enableNotificationOnMessageGeneration,
                        onChange = {
                            if (it && !permissionState.allPermissionsGranted) {
                                permissionState.requestPermissions()
                            }
                            updateDisplaySetting(displaySetting.copy(enableNotificationOnMessageGeneration = it))
                        },
                    )
                    if (displaySetting.enableNotificationOnMessageGeneration) {
                        PalaceDivider()
                        PalaceSwitchRow(
                            title = stringResource(R.string.setting_display_page_live_update_notification),
                            desc = stringResource(R.string.setting_display_page_live_update_notification_desc),
                            checked = displaySetting.enableLiveUpdateNotification,
                            onChange = {
                                updateDisplaySetting(displaySetting.copy(enableLiveUpdateNotification = it))
                            },
                        )
                    }
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_page_preferences_notification_floating_window),
                        desc = stringResource(R.string.setting_page_preferences_notification_floating_window_desc),
                        checked = displaySetting.enableFloatingChatWindow,
                        onChange = {
                            if (it && !canDrawOverlays) requestOverlayPermission()
                            updateDisplaySetting(displaySetting.copy(enableFloatingChatWindow = it))
                        },
                    )
                    if (displaySetting.enableFloatingChatWindow && !canDrawOverlays) {
                        PalaceDivider()
                        PalaceNavRow(
                            title = stringResource(R.string.setting_page_preferences_notification_overlay_permission),
                            desc = stringResource(R.string.setting_page_preferences_notification_overlay_permission_desc),
                            onClick = ::requestOverlayPermission,
                        )
                    }
                }
            }
        }
    }

    if (showUpdatePauseDialog) {
        AlertDialog(
            onDismissRequest = { showUpdatePauseDialog = false },
            title = { Text(stringResource(R.string.setting_update_reminder_pause_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.setting_update_reminder_pause_description))
                    ConnectedChoiceButtons(
                        options = UPDATE_PAUSE_DAY_OPTIONS,
                        selected = selectedUpdatePauseDays,
                        onSelect = { selectedUpdatePauseDays = it },
                        // 对话框底色和默认的按钮底色一样，换一个能分开的
                        containerColor = MaterialTheme.colorScheme.surfaceBright,
                    ) { days, selected ->
                        ConnectedChoiceLabel(
                            text = stringResource(R.string.setting_update_reminder_pause_days, days),
                            selected = selected,
                        )
                    }
                    if (!updateChecksEnabled) {
                        TextButton(
                            onClick = {
                                updateDisplaySetting(
                                    displaySetting.copy(updateCheckDisabledUntilEpochMillis = 0L)
                                )
                                showUpdatePauseDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.setting_update_reminder_resume_now))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateDisplaySetting(
                            displaySetting.copy(
                                updateCheckDisabledUntilEpochMillis =
                                    System.currentTimeMillis() + selectedUpdatePauseDays * MILLIS_PER_DAY,
                            )
                        )
                        showUpdatePauseDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdatePauseDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
