package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.content.Intent
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.ArrowTurnBackward
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileEdit
import me.rerere.hugeicons.stroke.FileExport
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.FileView
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.FolderOpen
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Share08
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.WorkspaceFileWriteToolNames
import me.rerere.rikkahub.data.ai.tools.resolveWorkspaceToolApproval
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceCheckRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceChip
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceEmptyState
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceField
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceNavRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRoundIcon
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSwitchRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.workspace.RootfsInstallProgress
import me.rerere.workspace.RootfsInstallStage
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

private enum class WorkspaceDetailTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    BASIC(HugeIcons.Settings03, R.string.workspace_detail_tab_basic),
    FILES(HugeIcons.File02, R.string.workspace_detail_tab_files);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
fun WorkspaceDetailPage(id: String) {
    val navController = LocalNavController.current
    val vm: WorkspaceDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val state by vm.state.collectAsStateWithLifecycle()
    val installProgress by vm.installProgress.collectAsStateWithLifecycle()
    val installError by vm.installError.collectAsStateWithLifecycle()
    val settingsError by vm.settingsError.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { WorkspaceDetailTab.entries.size }
    var deleteTarget by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    var showInstallDialog by remember { mutableStateOf(false) }
    var previewImageUri by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else null
        } ?: uri.lastPathSegment ?: "imported_file"
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
        vm.importFile(inputStream, fileName)
    }
    var exportTarget by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val entry = exportTarget.also { exportTarget = null } ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return@rememberLauncherForActivityResult
        vm.exportFile(entry, outputStream)
    }
    val directoryExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        vm.exportFilesToDirectory(uri, context.contentResolver)
    }

    val onFilesTab = pagerState.currentPage == WorkspaceDetailTab.FILES.ordinal

    BackHandler(enabled = onFilesTab && state.path.isNotBlank()) {
        vm.goUp()
    }

    val c = palaceColors()
    val canTerminal = state.workspace?.supportsTerminal() == true

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = state.workspace?.name ?: stringResource(R.string.workspace_detail_title),
                sub = "WORKSPACE",
                onBack = { navController.popBackStack() },
                rightIcon = HugeIcons.Refresh01,
                onRight = { vm.refresh() },
            )
            if (onFilesTab || canTerminal) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (onFilesTab) {
                        PalaceRoundIcon(HugeIcons.FileImport, onClick = { filePicker.launch(arrayOf("*/*")) })
                    }
                    if (canTerminal) {
                        PalaceRoundIcon(
                            HugeIcons.ComputerTerminal01,
                            onClick = { navController.navigate(Screen.WorkspaceTerminal(id)) },
                        )
                    }
                }
            }
            PagerTabs(
                pagerState = pagerState,
                tabs = WorkspaceDetailTab.entries,
                modifier = Modifier.padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding(),
            ) { page ->
                when (WorkspaceDetailTab.entries[page]) {
                    WorkspaceDetailTab.BASIC -> WorkspaceBasicPage(
                        workspace = state.workspace,
                        installProgress = installProgress,
                        onInstallRootfs = { showInstallDialog = true },
                        onToolApprovalChange = vm::setToolApproval,
                        onShellCompatibilityModeChange = vm::setShellCompatibilityMode,
                        onEditSsh = { navController.navigate(Screen.WorkspaceSshEditor(id)) },
                    )

                    WorkspaceDetailTab.FILES -> WorkspaceFilesPage(
                        state = state,
                        isActive = onFilesTab,
                        onSelectArea = vm::selectArea,
                        onGoUp = vm::goUp,
                        onResolveImage = { entry, area -> vm.resolveImageFile(entry, area) },
                        onOpen = { entry ->
                            when {
                                entry.isDirectory -> vm.open(entry)

                                entry.name.substringAfterLast('.').equals("svg", ignoreCase = true) ->
                                    navController.navigate(
                                        Screen.WorkspaceFileEditor(id, state.area.name, entry.path)
                                    )

                                else -> when (entry.detectFileType()) {
                                    WorkspaceFileType.TEXT -> navController.navigate(
                                        Screen.WorkspaceFileEditor(id, state.area.name, entry.path)
                                    )

                                    WorkspaceFileType.IMAGE -> vm.exportToCacheFile(entry, context.cacheDir) { file ->
                                        // 传绝对路径 (而非 content:// URI): Coil 可直接加载,
                                        // 预览弹窗的保存按钮 saveMessageImage 只认 "/" 开头路径, content URI 会报错
                                        previewImageUri = file.absolutePath
                                    }

                                    WorkspaceFileType.OTHER -> vm.exportToCacheFile(entry, context.cacheDir) { file ->
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file,
                                        )
                                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                                            file.extension.lowercase()
                                        ) ?: "*/*"
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mime)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(intent, null))
                                        }
                                    }
                                }
                            }
                        },
                        onDelete = { deleteTarget = it },
                        onBatchExport = { entries ->
                            if (vm.prepareBatchExport(entries)) directoryExportLauncher.launch(null)
                        },
                        onExport = { entry ->
                            exportTarget = entry
                            exportLauncher.launch(entry.name)
                        },
                        onShare = { entry ->
                            vm.exportToCacheFile(entry, context.cacheDir) { file ->
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        },
                    )
                }
            }
        }
    }

    state.exportResult?.let { result ->
        PalaceDialog(
            title = "导出结果",
            onDismiss = vm::dismissExportResult,
            confirmText = null,
            dismissText = stringResource(R.string.common_confirm),
        ) {
            Text(result, fontSize = 13.sp, lineHeight = 19.sp, color = c.ink)
        }
    }

    state.workspace?.let { workspace ->
        if (showInstallDialog) {
            InstallRootfsDialog(
                workspace = workspace,
                onDismiss = { showInstallDialog = false },
                onConfirm = { url ->
                    vm.installRootfs(url)
                    showInstallDialog = false
                },
            )
        }
    }

    installError?.let { message ->
        PalaceDialog(
            title = stringResource(R.string.workspace_detail_rootfs_install_failed),
            onDismiss = vm::dismissInstallError,
            confirmText = null,
            dismissText = stringResource(R.string.common_confirm),
        ) {
            Text(message, fontSize = 13.sp, lineHeight = 19.sp, color = c.error)
        }
    }

    settingsError?.let { message ->
        PalaceDialog(
            title = stringResource(R.string.workspace_detail_settings_save_failed),
            onDismiss = vm::dismissSettingsError,
            confirmText = null,
            dismissText = stringResource(R.string.common_confirm),
        ) {
            Text(
                message.ifBlank { stringResource(R.string.workspace_detail_settings_save_failed) },
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = c.error,
            )
        }
    }

    previewImageUri?.let { uri ->
        ImagePreviewDialog(
            images = listOf(uri),
            onDismissRequest = { previewImageUri = null },
        )
    }

    deleteTarget?.let { entry ->
        PalaceDialog(
            title = if (entry.isDirectory) stringResource(R.string.workspace_detail_delete_directory) else stringResource(R.string.workspace_detail_delete_file),
            onDismiss = { deleteTarget = null },
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                vm.delete(entry)
                deleteTarget = null
            },
            dismissText = stringResource(R.string.common_cancel),
        ) {
            Text(
                stringResource(R.string.workspace_detail_will_delete, entry.path),
                fontSize = 14.sp,
                color = c.ink,
            )
        }
    }
}

@Composable
private fun InfoLine(title: String, value: String, mono: Boolean = false) {
    val c = palaceColors()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, fontSize = 14.sp, color = c.ink)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = c.mute,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}

@Composable
private fun WorkspaceBasicPage(
    workspace: WorkspaceEntity?,
    installProgress: RootfsInstallProgress?,
    onInstallRootfs: () -> Unit,
    onToolApprovalChange: (String, Boolean) -> Unit,
    onShellCompatibilityModeChange: (Boolean) -> Unit,
    onEditSsh: () -> Unit,
) {
    val c = palaceColors()
    val shellStatus = workspace?.shellStatus
    // 加载完成前按本地工作区展示
    val hasRootfs = workspace?.capabilities()?.rootfs ?: true
    val installing = installProgress != null || shellStatus == WorkspaceShellStatus.INSTALLING.name
    val rootfsReady = shellStatus == WorkspaceShellStatus.READY.name
    val installButtonText = when {
        installing -> stringResource(R.string.workspace_detail_installing)
        rootfsReady -> stringResource(R.string.workspace_detail_reinstall_rootfs)
        else -> stringResource(R.string.workspace_detail_install_rootfs)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                PalaceSection("INFO", stringResource(R.string.workspace_detail_workspace_info))
                PalaceGlass {
                    InfoLine(
                        title = stringResource(R.string.workspace_detail_name),
                        value = workspace?.name ?: stringResource(R.string.workspace_detail_loading),
                    )
                    if (hasRootfs) {
                        PalaceDivider()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.workspace_detail_shell_status),
                                fontSize = 14.sp,
                                color = c.ink,
                                modifier = Modifier.weight(1f),
                            )
                            ShellStatusBadge(shellStatus)
                        }
                    }
                }
            }
        }

        if (workspace?.isSsh() == true) {
            item {
                SshConnectionCard(workspace = workspace, onEdit = onEditSsh)
            }
        }
        if (hasRootfs) {
            item {
                Column {
                    PalaceSection("SHELL", stringResource(R.string.workspace_detail_enable_shell))
                    PalaceGlass {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(stringResource(R.string.workspace_detail_area_rootfs), fontSize = 14.sp, color = c.ink)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.workspace_detail_enable_shell_desc),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = c.mute,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (installing) {
                                    LoadingIndicator(modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                PalaceTextButton(
                                    text = installButtonText,
                                    onClick = onInstallRootfs,
                                    enabled = workspace != null && !installing,
                                )
                            }
                            installProgress?.let { RootfsProgress(it) }
                        }
                        PalaceDivider()
                        PalaceSwitchRow(
                            title = stringResource(R.string.workspace_detail_compatibility_mode),
                            desc = stringResource(R.string.workspace_detail_compatibility_mode_desc),
                            checked = workspace?.shellCompatibilityMode ?: false,
                            onChange = onShellCompatibilityModeChange,
                            enabled = workspace != null,
                        )
                    }
                }
            }
        }

        item {
            WorkspaceToolApprovalCard(
                workspace = workspace,
                onToolApprovalChange = onToolApprovalChange,
            )
        }
    }
}

@Composable
private fun SshConnectionCard(
    workspace: WorkspaceEntity,
    onEdit: () -> Unit,
) {
    val settings = workspace.sshSettings()
    val hostKey = settings?.hostKey
    Column {
        PalaceSection("SSH", stringResource(R.string.workspace_detail_ssh_connection))
        PalaceGlass {
            InfoLine(
                title = stringResource(R.string.workspace_detail_ssh_address),
                value = settings?.let { "${it.username}@${it.host}:${it.port}" } ?: stringResource(R.string.workspace_detail_ssh_config_missing),
            )
            PalaceDivider()
            InfoLine(
                title = stringResource(R.string.workspace_detail_ssh_remote_dir),
                value = settings?.remoteDir ?: "-",
            )
            PalaceDivider()
            InfoLine(
                title = stringResource(R.string.workspace_detail_ssh_host_key),
                value = hostKey?.let { "${it.type}\n${it.fingerprint}" } ?: stringResource(R.string.workspace_detail_ssh_host_key_unconfirmed),
                mono = hostKey != null,
            )
            PalaceDivider()
            PalaceNavRow(
                title = stringResource(R.string.workspace_detail_ssh_edit),
                desc = stringResource(R.string.workspace_detail_ssh_edit_desc),
                onClick = onEdit,
            )
        }
    }
}

// Shell 状态标签，就绪和出错用不同的底色区分
@Composable
private fun ShellStatusBadge(status: String?) {
    val c = palaceColors()
    val (containerColor, contentColor) = when (status) {
        WorkspaceShellStatus.READY.name -> c.accent to c.onAccent
        WorkspaceShellStatus.BROKEN.name -> c.warnBg to c.error
        else -> c.chip to c.ink2
    }
    Text(
        text = status?.toShellStatusLabel() ?: "-",
        modifier = Modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 11.5.sp,
        color = contentColor,
        maxLines = 1,
    )
}

@Composable
private fun WorkspaceToolApprovalCard(
    workspace: WorkspaceEntity?,
    onToolApprovalChange: (String, Boolean) -> Unit,
) {
    val c = palaceColors()
    val overrides = workspace?.toolApprovalOverrides().orEmpty()
    val fileWriteTools = workspace?.capabilities()?.fileWriteTools ?: true
    val tools = workspaceToolApprovalItems().filter { (toolName) ->
        fileWriteTools || toolName !in WorkspaceFileWriteToolNames
    }

    Column {
        PalaceSection("TOOLS", stringResource(R.string.workspace_detail_tool_approval))
        Text(
            text = stringResource(R.string.workspace_detail_tool_approval_desc),
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = c.mute,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        PalaceGlass {
            tools.forEachIndexed { index, (toolName, label, icon) ->
                if (index > 0) PalaceDivider()
                PalaceCheckRow(
                    title = label,
                    desc = toolName,
                    checked = resolveWorkspaceToolApproval(toolName, overrides),
                    onChange = { onToolApprovalChange(toolName, it) },
                    icon = icon,
                    enabled = workspace != null,
                )
            }
        }
    }
}

@Composable
private fun workspaceToolApprovalItems() = listOf(
    Triple("workspace_read_file", stringResource(R.string.workspace_detail_tool_read_file), HugeIcons.FileView),
    Triple("workspace_write_file", stringResource(R.string.workspace_detail_tool_write_file), HugeIcons.PencilEdit01),
    Triple("workspace_edit_file", stringResource(R.string.workspace_detail_tool_edit_file), HugeIcons.FileEdit),
    Triple("workspace_shell", stringResource(R.string.workspace_detail_tool_shell), HugeIcons.ComputerTerminal01),
)

@Composable
private fun RootfsProgress(progress: RootfsInstallProgress) {
    val c = palaceColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val fraction = progress.totalBytes?.takeIf { it > 0 }?.let {
            (progress.bytesRead.toFloat() / it).coerceIn(0f, 1f)
        }
        if (fraction != null && progress.stage == RootfsInstallStage.DOWNLOADING) {
            LinearWavyProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = c.accent,
                trackColor = c.chip,
            )
        } else {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = c.accent,
                trackColor = c.chip,
            )
        }
        Text(
            text = when (progress.stage) {
                RootfsInstallStage.DOWNLOADING -> {
                    val total = progress.totalBytes?.let { " / ${it.fileSizeToString()}" }.orEmpty()
                    stringResource(R.string.workspace_detail_downloading, progress.bytesRead.fileSizeToString(), total)
                }

                RootfsInstallStage.EXTRACTING -> {
                    val entry = progress.currentEntry?.let { " · $it" }.orEmpty()
                    stringResource(R.string.workspace_detail_extracting, progress.entriesExtracted, entry)
                }

                RootfsInstallStage.INSTALLED -> stringResource(R.string.workspace_detail_install_complete)
            },
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = c.mute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InstallRootfsDialog(
    workspace: WorkspaceEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val c = palaceColors()
    var url by rememberSaveable(workspace.id) { mutableStateOf(DEFAULT_ROOTFS_URL) }

    PalaceDialog(
        title = stringResource(R.string.workspace_detail_install_rootfs),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.common_install),
        confirmEnabled = url.isNotBlank(),
        onConfirm = { onConfirm(url.trim()) },
        dismissText = stringResource(R.string.common_cancel),
    ) {
        Text(
            text = stringResource(R.string.workspace_detail_install_rootfs_desc, workspace.name),
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = c.mute,
        )
        PalaceField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(R.string.workspace_detail_download_url),
            minLines = 2,
        )
    }
}

@Composable
private fun WorkspaceFilesPage(
    state: WorkspaceDetailState,
    isActive: Boolean,
    onSelectArea: (WorkspaceStorageArea) -> Unit,
    onGoUp: () -> Unit,
    onResolveImage: suspend (WorkspaceFileEntry, WorkspaceStorageArea) -> File?,
    onOpen: (WorkspaceFileEntry) -> Unit,
    onDelete: (WorkspaceFileEntry) -> Unit,
    onExport: (WorkspaceFileEntry) -> Unit,
    onBatchExport: (List<WorkspaceFileEntry>) -> Unit,
    onShare: (WorkspaceFileEntry) -> Unit,
) {
    val c = palaceColors()
    var selecting by remember(state.area, state.path) { mutableStateOf(false) }
    var selectedPaths by remember(state.area, state.path) { mutableStateOf(emptySet<String>()) }
    val files = state.entries.filterNot { it.isDirectory }
    val selectedFiles = files.filter { it.path in selectedPaths }
    val allSelected = files.isNotEmpty() && selectedFiles.size == files.size
    fun toggleSelection(entry: WorkspaceFileEntry) {
        selectedPaths = if (entry.path in selectedPaths) selectedPaths - entry.path else selectedPaths + entry.path
    }
    fun exitSelection() {
        selecting = false
        selectedPaths = emptySet()
    }
    BackHandler(enabled = selecting && isActive) {
        exitSelection()
    }
    val showLoading = rememberDelayedVisibility(state.loading)
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                // 多选时底部留出悬浮工具栏的位置
                bottom = if (selecting) 128.dp else 16.dp,
            ),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WorkspaceAreaSelector(
                        selected = state.area,
                        hasRootfs = state.workspace?.capabilities()?.rootfs ?: true,
                        onSelected = onSelectArea,
                    )
                    WorkspacePathBar(
                        path = state.path,
                        canGoUp = state.path.isNotBlank(),
                        onGoUp = onGoUp,
                    )
                    // 刷新时保留现有列表, 只在顶部提示正在加载
                    if (showLoading && state.entries.isNotEmpty()) {
                        LinearWavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = c.accent,
                            trackColor = c.chip,
                        )
                    }
                    if (state.exporting) {
                        ExportProgressCard(
                            completed = state.exportCompleted,
                            total = state.exportTotal,
                        )
                    }
                    state.error?.let { ErrorCard(it) }
                }
            }

            if (showLoading && state.entries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                }
            }

            if (!state.loading && state.entries.isEmpty() && state.error == null) {
                item {
                    PalaceEmptyState(
                        text = stringResource(R.string.workspace_detail_empty_directory),
                        icon = HugeIcons.FolderOpen,
                    )
                }
            }

            // 文件列表排成一个分组：首尾大圆角，项与项之间留 2dp 的缝
            itemsIndexed(state.entries, key = { _, entry -> "${state.area.name}:${entry.path}" }) { index, entry ->
                WorkspaceFileItem(
                    entry = entry,
                    area = state.area,
                    copyPath = state.layout?.absolutePath(state.area, entry.path),
                    shape = cardGroupItemShape(index, state.entries.size),
                    modifier = Modifier.padding(bottom = if (index == state.entries.lastIndex) 0.dp else 2.dp),
                    onResolveImage = { onResolveImage(entry, state.area) },
                    selecting = selecting,
                    selected = entry.path in selectedPaths,
                    onLongClick = {
                        selecting = true
                        selectedPaths = selectedPaths + entry.path
                    },
                    onOpen = {
                        if (selecting && !entry.isDirectory) toggleSelection(entry) else onOpen(entry)
                    },
                    onDelete = { onDelete(entry) },
                    onExport = { onExport(entry) },
                    onShare = { onShare(entry) },
                )
            }
        }

        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = CustomColors.floatingToolbarColors,
                leadingContent = {
                    IconButton(onClick = { exitSelection() }) {
                        Icon(HugeIcons.Cancel01, contentDescription = "取消多选")
                    }
                },
                trailingContent = {
                    IconButton(
                        onClick = {
                            selectedPaths = if (allSelected) emptySet() else files.map { it.path }.toSet()
                        },
                    ) {
                        Icon(HugeIcons.CheckList, contentDescription = if (allSelected) "取消全选" else "全选")
                    }
                },
            ) {
                PalaceTextButton(
                    text = "导出 (${selectedFiles.size})",
                    onClick = { onBatchExport(selectedFiles) },
                    enabled = selectedFiles.isNotEmpty() && !state.exporting,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceAreaSelector(
    selected: WorkspaceStorageArea,
    hasRootfs: Boolean,
    onSelected: (WorkspaceStorageArea) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkspaceStorageArea.entries.forEach { area ->
            PalaceChip(
                // 没有 Rootfs 的工作区 (如远程) 两个区域分别是工作目录和根目录
                text = when (area) {
                    WorkspaceStorageArea.FILES -> stringResource(
                        if (hasRootfs) R.string.workspace_detail_area_files else R.string.workspace_detail_area_working_dir
                    )

                    WorkspaceStorageArea.LINUX -> stringResource(
                        if (hasRootfs) R.string.workspace_detail_area_rootfs else R.string.workspace_detail_area_root
                    )
                },
                selected = area == selected,
                onClick = { onSelected(area) },
            )
        }
    }
}

@Composable
private fun WorkspacePathBar(
    path: String,
    canGoUp: Boolean,
    onGoUp: () -> Unit,
) {
    val c = palaceColors()
    PalaceGlass(shape = CircleShape) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PalaceRoundIcon(
                HugeIcons.ArrowTurnBackward,
                modifier = Modifier.alpha(if (canGoUp) 1f else 0.4f),
                onClick = { if (canGoUp) onGoUp() },
            )
            Text(
                text = path.ifBlank { "/" },
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = c.mute,
                maxLines = 1,
                // 路径太长时省略开头，当前所在的目录始终看得到
                overflow = TextOverflow.StartEllipsis,
            )
        }
    }
}

@Composable
private fun ExportProgressCard(
    completed: Int,
    total: Int,
) {
    val c = palaceColors()
    PalaceGlass {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "正在导出 $completed/$total",
                fontSize = 14.sp,
                color = c.ink,
            )
            LinearWavyProgressIndicator(
                progress = { if (total > 0) completed.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = c.accent,
                trackColor = c.chip,
            )
        }
    }
}

@Composable
private fun WorkspaceFileItem(
    entry: WorkspaceFileEntry,
    area: WorkspaceStorageArea,
    // 复制出去的路径要能直接给 AI 工具和终端用，所以按执行环境内的绝对路径给
    copyPath: String?,
    shape: Shape,
    onResolveImage: suspend () -> File?,
    selecting: Boolean,
    selected: Boolean,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isImage = !entry.isDirectory && entry.detectFileType() == WorkspaceFileType.IMAGE
    val imageFile by produceState<File?>(
        initialValue = null,
        key1 = if (isImage) area else null,
        key2 = if (isImage) entry.path else null,
        key3 = if (isImage) "${entry.updatedAt}:${entry.sizeBytes}" else null,
    ) {
        if (isImage) {
            value = try {
                onResolveImage()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
    }
    val c = palaceColors()
    val containerColor by animateColorAsState(
        targetValue = if (selected) c.chip else c.glass,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = if (entry.isDirectory) null else onLongClick,
                onLongClickLabel = if (entry.isDirectory) null else "选择文件",
            ),
        shape = shape,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isImage -> {
                    val context = LocalContext.current
                    val imageRequest = remember(imageFile, entry.updatedAt, entry.sizeBytes) {
                        imageFile?.let {
                            ImageRequest.Builder(context)
                                .data(it)
                                .memoryCacheKey("workspace:${it.absolutePath}:${entry.updatedAt}:${entry.sizeBytes}")
                                .build()
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.chip),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = HugeIcons.File02,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = c.ink2,
                        )
                        imageRequest?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }

                entry.isDirectory -> CardGroupIcon(
                    icon = HugeIcons.Folder01,
                    containerColor = c.chip,
                    contentColor = c.ink2,
                )

                // 选中时整行换成 secondaryContainer，图标底色跟着换一档，不然会融进背景
                else -> CardGroupIcon(
                    icon = HugeIcons.File02,
                    containerColor = if (selected) c.glassStrong else c.chip,
                    contentColor = c.ink2,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.name,
                    fontSize = 14.sp,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (entry.isDirectory) entry.path else "${entry.path} · ${entry.sizeBytes.fileSizeToString()}",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = c.mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selecting) {
                // 和菜单按钮占一样大的位置，进出多选时行高不跳；点整行切换，勾选框自己不接收点击
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!entry.isDirectory) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = c.accent,
                                checkmarkColor = c.onAccent,
                                uncheckedColor = c.faint,
                            ),
                        )
                    }
                }
            } else {
                val context = LocalContext.current
                ItemActionMenu(
                    actions = buildList {
                        if (copyPath != null) {
                            add(
                                ItemAction(
                                    text = stringResource(R.string.workspace_detail_copy_path),
                                    icon = HugeIcons.Copy01,
                                    onClick = {
                                        context.writeClipboardText(copyPath)
                                        // Android 13 起系统会自己弹复制提示
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                            Toast.makeText(context, R.string.workspace_detail_path_copied, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            )
                        }
                        if (!entry.isDirectory) {
                            add(
                                ItemAction(
                                    text = stringResource(R.string.common_export),
                                    icon = HugeIcons.FileExport,
                                    onClick = onExport,
                                )
                            )
                            add(
                                ItemAction(
                                    text = stringResource(R.string.common_share),
                                    icon = HugeIcons.Share08,
                                    onClick = onShare,
                                )
                            )
                        }
                        add(
                            ItemAction(
                                text = stringResource(R.string.common_delete),
                                icon = HugeIcons.Delete01,
                                destructive = true,
                                onClick = onDelete,
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val c = palaceColors()
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.warnBg)
            .border(0.5.dp, c.error.copy(alpha = 0.3f), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = HugeIcons.Alert02,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = c.error,
        )
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = c.error,
        )
    }
}

/** 加载超过 [delayMillis] 才显示指示器, 本地工作区瞬间完成的加载不会闪一下 */
@Composable
private fun rememberDelayedVisibility(loading: Boolean, delayMillis: Long = 300): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(delayMillis)
            visible = true
        } else {
            visible = false
        }
    }
    return visible
}

/** 列表里展示的状态: 远程工作区显示连接地址, 本地工作区显示 shell 状态 */
@Composable
internal fun WorkspaceEntity.statusLabel(): String =
    if (isSsh()) {
        sshSettings()?.let { "SSH · ${it.username}@${it.host}" } ?: "SSH"
    } else {
        shellStatus.toShellStatusLabel()
    }

@Composable
internal fun String.toShellStatusLabel(): String = when (this) {
    WorkspaceShellStatus.DISABLED.name -> stringResource(R.string.workspace_detail_shell_disabled)
    WorkspaceShellStatus.INSTALLING.name -> stringResource(R.string.workspace_detail_shell_installing)
    WorkspaceShellStatus.READY.name -> stringResource(R.string.workspace_detail_shell_ready)
    WorkspaceShellStatus.BROKEN.name -> stringResource(R.string.workspace_detail_shell_broken)
    else -> lowercase()
}

private const val DEFAULT_ROOTFS_URL =
    "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.3-base-arm64.tar.gz"
