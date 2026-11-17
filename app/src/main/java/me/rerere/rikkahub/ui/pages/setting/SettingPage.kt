package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import me.rerere.rikkahub.ui.pages.assistant.detail.DetailNavRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.ImageUpload
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Megaphone01
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.isNotConfigured
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingPage(vm: SettingVM = koinViewModel()) {
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val filesManager: FilesManager = koinInject()
    val storageState by produceState(-1 to 0L) {
        value = filesManager.countChatFiles()
    }

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = stringResource(R.string.settings),
                sub = "SETTINGS",
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
                if (settings.isNotConfigured()) {
                    ProviderConfigWarningCard(
                        onClick = { navController.navigate(Screen.SettingProvider) }
                    )
                }

                PalaceSection("GENERAL", stringResource(R.string.setting_page_general_settings))
                PalaceGlass {
                    DetailNavRow(
                        icon = HugeIcons.Settings03,
                        title = stringResource(R.string.setting_page_preferences),
                        desc = stringResource(R.string.setting_page_preferences_desc),
                        onClick = { navController.navigate(Screen.SettingPreferences) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.LookTop,
                        title = stringResource(R.string.setting_page_assistant),
                        desc = stringResource(R.string.setting_page_assistant_desc),
                        onClick = { navController.navigate(Screen.Assistant) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Package,
                        title = stringResource(R.string.setting_page_extensions),
                        desc = stringResource(R.string.setting_page_extensions_desc),
                        onClick = { navController.navigate(Screen.Extensions) },
                    )
                }

                PalaceSection("MODEL", stringResource(R.string.setting_page_model_and_services))
                PalaceGlass {
                    DetailNavRow(
                        icon = HugeIcons.AiMagic,
                        title = stringResource(R.string.setting_page_default_model),
                        desc = stringResource(R.string.setting_page_default_model_desc),
                        onClick = { navController.navigate(Screen.SettingModels) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Brain02,
                        title = stringResource(R.string.setting_page_providers),
                        desc = stringResource(R.string.setting_page_providers_desc),
                        onClick = { navController.navigate(Screen.SettingProvider) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.GlobalSearch,
                        title = "网页登录",
                        desc = "登录小红书这些网站，发链接时能看到更多内容",
                        onClick = { navController.navigate(Screen.WebLogin) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Megaphone01,
                        title = stringResource(R.string.setting_page_tts_service),
                        desc = stringResource(R.string.setting_page_tts_service_desc),
                        onClick = { navController.navigate(Screen.SettingSpeech) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.McpServer,
                        title = stringResource(R.string.setting_page_mcp),
                        desc = stringResource(R.string.setting_page_mcp_desc),
                        onClick = { navController.navigate(Screen.SettingMcp) },
                    )
                }

                PalaceSection("DATA", stringResource(R.string.setting_page_data_settings))
                PalaceGlass {
                    DetailNavRow(
                        icon = HugeIcons.TransactionHistory,
                        title = stringResource(R.string.chat_page_history),
                        desc = null,
                        onClick = { navController.navigate(Screen.History) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Search01,
                        title = stringResource(R.string.chat_page_search_chats),
                        desc = null,
                        onClick = { navController.navigate(Screen.MessageSearch) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Database02,
                        title = stringResource(R.string.setting_page_data_backup),
                        desc = stringResource(R.string.setting_page_data_backup_desc),
                        onClick = { navController.navigate(Screen.Backup) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.ImageUpload,
                        title = stringResource(R.string.setting_page_chat_storage),
                        desc = if (storageState.first == -1) {
                            stringResource(R.string.calculating)
                        } else {
                            stringResource(
                                R.string.setting_page_chat_storage_desc,
                                storageState.first,
                                storageState.second / 1024 / 1024.0,
                            )
                        },
                        onClick = { navController.navigate(Screen.SettingFiles) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Bookshelf01,
                        title = stringResource(R.string.setting_page_request_logs),
                        desc = stringResource(R.string.setting_page_request_logs_desc),
                        onClick = { navController.navigate(Screen.Log) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigWarningCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.error, MaterialShapes.Cookie9Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Alert01,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_page_config_api_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        text = stringResource(R.string.setting_page_config_api_desc),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.setting_page_config))
            }
        }
    }
}
