package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Internet
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.hugeicons.stroke.PaintBoard
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.DetailNavRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar

@Composable
fun SettingPreferencesPage() {
    val navController = LocalNavController.current

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = stringResource(R.string.setting_page_preferences),
                sub = "PREFERENCES",
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
                PalaceSection("OPTIONS", "偏好")
                PalaceGlass {
                    DetailNavRow(
                        icon = HugeIcons.Notification01,
                        title = stringResource(R.string.setting_page_preferences_notification),
                        desc = stringResource(R.string.setting_page_preferences_notification_desc),
                        onClick = { navController.navigate(Screen.SettingPreferencesNotification) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Settings03,
                        title = stringResource(R.string.setting_page_preferences_general),
                        desc = stringResource(R.string.setting_page_preferences_general_desc),
                        onClick = { navController.navigate(Screen.SettingPreferencesGeneral) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.PaintBoard,
                        title = stringResource(R.string.setting_page_preferences_ui),
                        desc = stringResource(R.string.setting_page_preferences_ui_desc),
                        onClick = { navController.navigate(Screen.SettingPreferencesUI) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Internet,
                        title = stringResource(R.string.setting_page_preferences_network),
                        desc = stringResource(R.string.setting_page_preferences_network_desc),
                        onClick = { navController.navigate(Screen.SettingPreferencesNetwork) },
                    )
                }
            }
        }
    }
}
