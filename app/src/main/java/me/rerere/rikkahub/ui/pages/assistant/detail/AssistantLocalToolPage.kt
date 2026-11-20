package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.permission.PermissionInfo
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import me.rerere.rikkahub.utils.openUsageAccessSettings
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    PalaceSubPage(
        title = stringResource(R.string.assistant_page_tab_local_tools),
        sub = "TOOLS",
        onBack = { navController.popBackStack() },
    ) {
        AssistantLocalToolContent(assistant = assistant, onUpdate = { vm.update(it) })
    }
}

@Composable
private fun AssistantLocalToolContent(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val permissionRequiredText =
        stringResource(R.string.assistant_page_local_tools_screen_time_permission_required)

    val calendarPermissionState = rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = Manifest.permission.READ_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_read)) },
                usage = { Text(stringResource(R.string.permission_calendar_read_desc)) },
                required = true
            ),
            PermissionInfo(
                permission = Manifest.permission.WRITE_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_write)) },
                usage = { Text(stringResource(R.string.permission_calendar_write_desc)) },
                required = true
            ),
        )
    )
    PermissionManager(permissionState = calendarPermissionState)

    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) {
        if (enabled && option == LocalToolOption.ScreenTime && !context.hasUsageStatsPermission()) {
            toaster.show(message = permissionRequiredText, type = ToastType.Warning)
            context.openUsageAccessSettings()
        }
        if (enabled && option == LocalToolOption.Calendar && !calendarPermissionState.allPermissionsGranted) {
            calendarPermissionState.requestPermissions()
            return
        }
        val newLocalTools = if (enabled) {
            assistant.localTools + option
        } else {
            assistant.localTools - option
        }
        onUpdate(assistant.copy(localTools = newLocalTools))
    }

    @Composable
    fun toolItem(option: LocalToolOption, icon: ImageVector, @StringRes title: Int, @StringRes description: Int) {
        PalaceCheckRow(
            title = stringResource(title),
            desc = stringResource(description),
            icon = icon,
            checked = assistant.localTools.contains(option),
            onChange = { toggleLocalTool(option, it) },
        )
    }

    PalaceGlass {
        toolItem(
            option = LocalToolOption.TimeInfo,
            icon = HugeIcons.Clock01,
            title = R.string.assistant_page_local_tools_time_info_title,
            description = R.string.assistant_page_local_tools_time_info_desc,
        )
        PalaceDivider()
        toolItem(
            option = LocalToolOption.ScreenTime,
            icon = HugeIcons.SmartPhone01,
            title = R.string.assistant_page_local_tools_screen_time_title,
            description = R.string.assistant_page_local_tools_screen_time_desc,
        )
        PalaceDivider()
        toolItem(
            option = LocalToolOption.Calendar,
            icon = HugeIcons.Calendar03,
            title = R.string.assistant_page_local_tools_calendar_title,
            description = R.string.assistant_page_local_tools_calendar_desc,
        )
        PalaceDivider()
        toolItem(
            option = LocalToolOption.Stash,
            icon = HugeIcons.Package01,
            title = R.string.assistant_page_local_tools_stash_title,
            description = R.string.assistant_page_local_tools_stash_desc,
        )
    }
}
