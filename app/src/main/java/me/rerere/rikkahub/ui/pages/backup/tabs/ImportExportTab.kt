package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.FileExport
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.assistant.detail.DetailNavRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.backup.components.backupContentItem
import me.rerere.rikkahub.ui.theme.palaceColors
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ImportExportTab(
    vm: BackupVM,
    onShowRestartDialog: () -> Unit
) {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val selectedBackupItems by vm.localBackupItems.collectAsStateWithLifecycle()
    var isExporting by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }

    // 导入类型：local 为本地备份，chatbox 为 Chatbox 导入，cherry 为 Cherry Studio 导入
    var importType by remember { mutableStateOf("local") }

    // 创建文件保存的launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { targetUri ->
            scope.launch {
                isExporting = true
                runCatching {
                    // 导出文件
                    val exportFile = vm.exportToFile()

                    // 复制到用户选择的位置
                    context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                        FileInputStream(exportFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    // 清理临时文件
                    exportFile.delete()

                    toaster.show(
                        context.getString(R.string.backup_page_backup_success),
                        type = ToastType.Success
                    )
                }.onFailure { e ->
                    e.printStackTrace()
                    toaster.show(
                        context.getString(R.string.backup_page_restore_failed, e.message ?: ""),
                        type = ToastType.Error
                    )
                }
                isExporting = false
            }
        }
    }

    // 创建文件选择的launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { sourceUri ->
            scope.launch {
                isRestoring = true
                runCatching {
                    when (importType) {
                        "local" -> {
                            // 本地备份导入：处理zip文件
                            val tempFile =
                                File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}.zip")

                            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                                FileOutputStream(tempFile).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }

                            // 从临时文件恢复
                            vm.restoreFromLocalFile(tempFile)

                            // 清理临时文件
                            tempFile.delete()
                        }

                        "chatbox" -> {
                            // Chatbox Backup v2：处理 ZIP 文件
                            val tempFile =
                                File(context.cacheDir, "temp_chatbox_${System.currentTimeMillis()}.zip")

                            try {
                                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                                    FileOutputStream(tempFile).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }

                                vm.restoreFromChatBox(tempFile)
                            } finally {
                                tempFile.delete()
                            }
                        }

                        "cherry" -> {
                            // Cherry Studio导入：处理zip文件
                            val tempFile =
                                File(context.cacheDir, "temp_cherry_${System.currentTimeMillis()}.zip")

                            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                                FileOutputStream(tempFile).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }

                            // 从Cherry Studio备份恢复
                            vm.restoreFromCherryStudio(tempFile)

                            // 清理临时文件
                            tempFile.delete()
                        }
                    }

                    toaster.show(
                        context.getString(R.string.backup_page_restore_success),
                        type = ToastType.Success
                    )
                    onShowRestartDialog()
                }.onFailure { e ->
                    e.printStackTrace()
                    toaster.show(
                        context.getString(R.string.backup_page_restore_failed, e.message ?: ""),
                        type = ToastType.Error
                    )
                }
                isRestoring = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PalaceSection("LOCAL", stringResource(R.string.backup_page_local_backup_export))
        // backupContentItem 是 CardGroup 作用域里的扩展函数，这一块保留原控件
        CardGroup {
            backupContentItem(
                options = WebDavConfig.BackupItem.entries,
                selected = selectedBackupItems,
                onChange = { vm.updateLocalBackupItems(it) },
            ) { item ->
                when (item) {
                    WebDavConfig.BackupItem.DATABASE -> stringResource(R.string.backup_page_chat_records)
                    WebDavConfig.BackupItem.FILES -> stringResource(R.string.backup_page_files)
                }
            }
        }

        PalaceGlass {
            DetailNavRow(
                icon = HugeIcons.FileExport,
                title = stringResource(R.string.backup_page_local_backup_export),
                desc = if (isExporting) {
                    stringResource(R.string.backup_page_exporting)
                } else {
                    stringResource(R.string.backup_page_export_desc)
                },
                onClick = {
                    if (!isExporting) {
                        val timestamp = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        createDocumentLauncher.launch("rikkahub_backup_$timestamp.zip")
                    }
                },
                leading = { ActionIcon(HugeIcons.FileExport, loading = isExporting) },
            )
            PalaceDivider()
            DetailNavRow(
                icon = HugeIcons.FileImport,
                title = stringResource(R.string.backup_page_local_backup_import),
                desc = if (isRestoring && importType == "local") {
                    stringResource(R.string.backup_page_importing)
                } else {
                    stringResource(R.string.backup_page_import_desc)
                },
                onClick = {
                    if (!isRestoring) {
                        showImportConfirmDialog = true
                    }
                },
                leading = { ActionIcon(HugeIcons.FileImport, loading = isRestoring && importType == "local") },
            )
        }

        PalaceSection("IMPORT", stringResource(R.string.backup_page_import_from_other_app))
        PalaceGlass {
            DetailNavRow(
                icon = HugeIcons.FileImport,
                title = stringResource(R.string.backup_page_import_from_chatbox),
                desc = stringResource(R.string.backup_page_import_chatbox_desc),
                onClick = {
                    if (!isRestoring) {
                        importType = "chatbox"
                        openDocumentLauncher.launch(arrayOf("application/zip"))
                    }
                },
                leading = { ActionIcon(HugeIcons.FileImport, loading = isRestoring && importType == "chatbox") },
            )
            PalaceDivider()
            DetailNavRow(
                icon = HugeIcons.FileImport,
                title = stringResource(R.string.backup_page_import_from_cherry_studio),
                desc = stringResource(R.string.backup_page_import_cherry_studio_desc),
                onClick = {
                    if (!isRestoring) {
                        importType = "cherry"
                        openDocumentLauncher.launch(arrayOf("application/zip"))
                    }
                },
                leading = { ActionIcon(HugeIcons.FileImport, loading = isRestoring && importType == "cherry") },
            )
        }
    }

    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text(stringResource(R.string.backup_page_local_backup_import)) },
            text = { Text(stringResource(R.string.backup_page_import_overwrite_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        importType = "local"
                        openDocumentLauncher.launch(arrayOf("application/zip"))
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** 圆形图标位里的图标，进行中时换成转圈，写法和 SettingMcpPage 的状态图标一致 */
@Composable
private fun ActionIcon(icon: ImageVector, loading: Boolean) {
    val c = palaceColors()
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = c.ink2,
        )
    } else {
        Icon(icon, null, Modifier.size(16.dp), tint = c.ink2)
    }
}
